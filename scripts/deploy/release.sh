#!/usr/bin/env bash
# release.sh —— 开发机一键把 bb-bot 自动部署到生产 k8s 集群。
#
# 用法：
#   scripts/deploy/release.sh                # 构建镜像 + 部署
#   scripts/deploy/release.sh --dry-run      # 只构建，不推送、不碰服务器
#   scripts/deploy/release.sh --skip-build   # 不重新构建，直接用已推送的 tag 部署
#   scripts/deploy/release.sh --config       # 只下发 ConfigMap 并重启，不构建/发镜像
#   scripts/deploy/release.sh --list-backups # 列出可回滚的备份时间戳
#   scripts/deploy/release.sh --rollback <ts># 回滚到某次备份时间戳
#
# ConfigMap 与 Deployment 解耦：日常发布只覆盖 bb-bot.yaml（Deployment+Service），
# 不动 ConfigMap；改了生产配置才用 --config 单独下发 bb-bot-config.yaml。
#
# 流程：Maven 构建 → docker buildx 构建镜像 → 推私有 registry（tag = git short SHA）
#       → SSH 主节点：备份旧清单 → envsubst 渲染新清单 → kubectl apply → rollout
#       rollout 失败自动回滚。所有 IP / 路径 / 密钥 读自 scripts/deploy/deploy.conf。
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "${ROOT_DIR}"
CONF="${ROOT_DIR}/scripts/deploy/deploy.conf"
LOG_FILE="${ROOT_DIR}/scripts/deploy/deploy.log"
K8S_DIR="${ROOT_DIR}/scripts/deploy/k8s/bb-bot"

# 服务定义（单服务）
SVC_NAME="bb-bot"
SVC_MODULE="bb-bot-server"
SVC_DOCKERFILE="Dockerfile"

log() { local m; m="$(date '+%F %T') $*"; printf '\033[1;34m[release]\033[0m %s\n' "${m}"; echo "${m}" >>"${LOG_FILE}"; }
err() { local m; m="$(date '+%F %T') ERROR $*"; printf '\033[1;31m[release]\033[0m %s\n' "${m}" >&2; echo "${m}" >>"${LOG_FILE}"; }
die() { err "$*"; exit 1; }

# ---- --help 优先处理 -------------------------------------------------------
for a in "$@"; do
  case "${a}" in
    -h|--help) awk 'NR==1{next} /^#/{sub(/^# ?/,"");print;next} {exit}' "$0"; exit 0 ;;
  esac
done

# ---- 加载配置 --------------------------------------------------------------
[[ -f "${CONF}" ]] || die "缺少 ${CONF}，请由 scripts/deploy/deploy.conf.example 拷贝填写。"
# shellcheck disable=SC1090
source "${CONF}"
: "${SSH_KEY:?deploy.conf 缺 SSH_KEY}"
: "${MASTER_SSH:?deploy.conf 缺 MASTER_SSH}"
: "${REGISTRY_PUSH:?deploy.conf 缺 REGISTRY_PUSH}"
: "${REGISTRY_PULL:?deploy.conf 缺 REGISTRY_PULL}"
: "${MASTER_K8S_DIR:?}" "${MASTER_BACKUP_DIR:?}"
NAMESPACE="${NAMESPACE:-bb-bot}"
PLATFORMS="${PLATFORMS:-linux/amd64}"
ROLLOUT_TIMEOUT="${ROLLOUT_TIMEOUT:-180s}"
KEEP_BACKUPS="${KEEP_BACKUPS:-20}"
MVN="${MVN:-mvn}"
[[ -f "${SSH_KEY}" ]] || die "SSH_KEY 不存在：${SSH_KEY}"

# ---- SSH 封装 --------------------------------------------------------------
SSH_OPTS=(-i "${SSH_KEY}" -o IdentitiesOnly=yes -o StrictHostKeyChecking=accept-new -o ConnectTimeout=15)
mssh() { ssh "${SSH_OPTS[@]}" "${MASTER_SSH}" "$@"; }

# ============================================================================
# 构建
# ============================================================================
build_image() {
  local push="$1" image="${REGISTRY_PUSH}/misuaa/${SVC_NAME}:${TAG}"
  log "Maven 构建：${SVC_MODULE}"
  "${MVN}" clean package -pl "${SVC_MODULE}" -am -f pom.xml \
    ${MAVEN_REPO:+-Dmaven.repo.local="${MAVEN_REPO}"} -Dmaven.test.skip=true -P prod
  log "镜像构建：${image}"
  if [[ "${push}" == "1" ]]; then
    docker buildx build --platform "${PLATFORMS}" -t "${image}" --push \
      "${SVC_MODULE}" -f "${SVC_MODULE}/${SVC_DOCKERFILE}"
  else
    docker buildx build --platform "${PLATFORMS}" -t "${image}" \
      "${SVC_MODULE}" -f "${SVC_MODULE}/${SVC_DOCKERFILE}"
  fi
}

# ============================================================================
# 部署 —— 主节点 k8s
# ============================================================================
deploy_k8s() {
  local tmp
  log "主节点：备份旧清单 → ${MASTER_BACKUP_DIR}/${TS}/k8s"
  mssh "mkdir -p '${MASTER_BACKUP_DIR}/${TS}/k8s'"
  mssh "cp -a '${MASTER_K8S_DIR}/${SVC_NAME}.yaml' '${MASTER_BACKUP_DIR}/${TS}/k8s/' 2>/dev/null || true"

  tmp="$(mktemp -d)"
  export REGISTRY_PULL IMAGE_TAG="${TAG}"
  log "主节点：覆盖清单 + kubectl apply（tag=${TAG}）"
  envsubst '${REGISTRY_PULL} ${IMAGE_TAG}' \
    <"${K8S_DIR}/${SVC_NAME}.yaml" >"${tmp}/${SVC_NAME}.yaml"
  scp "${SSH_OPTS[@]}" "${tmp}/${SVC_NAME}.yaml" "${MASTER_SSH}:${MASTER_K8S_DIR}/${SVC_NAME}.yaml"
  mssh "kubectl apply -f '${MASTER_K8S_DIR}/${SVC_NAME}.yaml'"
  rm -rf "${tmp}"

  log "主节点：等待 rollout..."
  if ! mssh "kubectl -n '${NAMESPACE}' rollout status 'deployment/${SVC_NAME}' --timeout='${ROLLOUT_TIMEOUT}'"; then
    err "${SVC_NAME} rollout 失败 —— 从 ${TS} 备份回滚 k8s"
    restore_k8s "${TS}"
    die "部署失败并已回滚。"
  fi
}

restore_k8s() {
  local ts="$1"
  mssh "test -f '${MASTER_BACKUP_DIR}/${ts}/k8s/${SVC_NAME}.yaml' && \
    cp -a '${MASTER_BACKUP_DIR}/${ts}/k8s/${SVC_NAME}.yaml' '${MASTER_K8S_DIR}/${SVC_NAME}.yaml' && \
    kubectl apply -f '${MASTER_K8S_DIR}/${SVC_NAME}.yaml'" \
    || err "回滚 ${SVC_NAME} 失败，请人工介入"
}

# ============================================================================
# 备份清理 / 回滚 / 列表
# ============================================================================
prune_backups() {
  mssh "ls -1d '${MASTER_BACKUP_DIR}'/*Z 2>/dev/null | sort | head -n -${KEEP_BACKUPS} | xargs -r rm -rf" || true
}

cmd_list_backups() {
  echo "[主节点 ${MASTER_BACKUP_DIR}]"; mssh "ls -1 '${MASTER_BACKUP_DIR}' 2>/dev/null" || true
}

cmd_rollback() {
  local ts="$1"
  [[ -n "${ts}" ]] || die "--rollback 需要时间戳参数（用 --list-backups 查看）"
  log "回滚到 ${ts}"
  restore_k8s "${ts}"
  mssh "kubectl -n '${NAMESPACE}' rollout status 'deployment/${SVC_NAME}' --timeout='${ROLLOUT_TIMEOUT}'" \
    || err "${SVC_NAME} 回滚后 rollout 未就绪"
  log "回滚完成。"
}

# ============================================================================
# 单独下发 ConfigMap（与日常镜像发布解耦）
# ============================================================================
cmd_config() {
  local ts; ts="$(date -u +%Y%m%dT%H%M%SZ)"
  log "下发 ConfigMap：${SVC_NAME}  ts=${ts}"
  mssh "mkdir -p '${MASTER_BACKUP_DIR}/${ts}/k8s'"
  mssh "cp -a '${MASTER_K8S_DIR}/${SVC_NAME}-config.yaml' '${MASTER_BACKUP_DIR}/${ts}/k8s/' 2>/dev/null || true"

  log "主节点：覆盖 ConfigMap 清单 + kubectl apply"
  scp "${SSH_OPTS[@]}" "${K8S_DIR}/${SVC_NAME}-config.yaml" \
    "${MASTER_SSH}:${MASTER_K8S_DIR}/${SVC_NAME}-config.yaml"
  mssh "kubectl apply -f '${MASTER_K8S_DIR}/${SVC_NAME}-config.yaml'"

  # ConfigMap 走 subPath 挂载，kubelet 不会热更新，必须重启 pod 才能生效
  log "主节点：重启 deployment 让新配置生效"
  mssh "kubectl -n '${NAMESPACE}' rollout restart 'deployment/${SVC_NAME}'"
  mssh "kubectl -n '${NAMESPACE}' rollout status 'deployment/${SVC_NAME}' --timeout='${ROLLOUT_TIMEOUT}'" \
    || err "${SVC_NAME} 重启后 rollout 未就绪"
  log "ConfigMap 下发完成（备份时间戳 ${ts}）。"
}

# ============================================================================
# 主流程
# ============================================================================
main() {
  local dry=0 skip_build=0 action="deploy" rb_ts=""
  while [[ $# -gt 0 ]]; do
    case "$1" in
      --dry-run)      dry=1 ;;
      --skip-build)   skip_build=1 ;;
      --config)       action="config" ;;
      --list-backups) action="list" ;;
      --rollback)     action="rollback"; rb_ts="${2:-}"; shift ;;
      *)              die "未知选项：$1（-h 看帮助）" ;;
    esac
    shift
  done

  if [[ "${action}" == "list" ]];     then cmd_list_backups; exit 0; fi
  if [[ "${action}" == "rollback" ]]; then cmd_rollback "${rb_ts}"; exit 0; fi
  if [[ "${action}" == "config" ]];   then cmd_config; exit 0; fi

  TAG="$(git rev-parse --short HEAD)"
  TS="$(date -u +%Y%m%dT%H%M%SZ)"
  local branch; branch="$(git rev-parse --abbrev-ref HEAD)"
  [[ "${branch}" == "master" ]] || err "当前分支是 ${branch}（非 master）—— 将按该 SHA 发布，请确认。"
  log "发布目标：${SVC_NAME}  tag=${TAG}  ts=${TS}"

  if [[ "${skip_build}" -eq 1 ]]; then
    log "跳过镜像构建（--skip-build）"
  else
    build_image "$([[ "${dry}" -eq 1 ]] && echo 0 || echo 1)"
  fi

  if [[ "${dry}" -eq 1 ]]; then
    log "[dry-run] 构建完成、未推送、未触碰服务器。正式发布请去掉 --dry-run。"
    exit 0
  fi

  deploy_k8s
  prune_backups
  log "发布成功：${TAG} 已上线（备份时间戳 ${TS}）"
}

main "$@"
