#!/usr/bin/env bash
# bb-bot 本地端到端测试 orchestrator。
#
# 用法：
#   ./bb-dev.sh up         # 建库 + 启动 mock OpenAI + 启动 bbBot
#   ./bb-dev.sh test       # 跑 BB 协议测试客户端的全部场景
#   ./bb-dev.sh test A2    # 只跑 A2
#   ./bb-dev.sh repl       # 交互式手动收发
#   ./bb-dev.sh down       # 停 bbBot + mock
#   ./bb-dev.sh status     # 看运行状态
#   ./bb-dev.sh logs bot   # tail bbBot 日志
#   ./bb-dev.sh logs mock  # tail mock OpenAI 日志
#
# 复用 misu-server 的 MySQL（端口 3316），需要它已经在跑。

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
RUN_DIR="$SCRIPT_DIR/run"
PIDS_DIR="$RUN_DIR/pids"
LOGS_DIR="$RUN_DIR/logs"

MOCK_PORT="${MOCK_PORT:-18800}"
BB_WS_PORT="${BB_WS_PORT:-18765}"
HTTP_PORT="${HTTP_PORT:-18199}"
MYSQL_CONTAINER="${MYSQL_CONTAINER:-misu-mysql-local}"
MYSQL_USER="${MYSQL_USER:-root}"
MYSQL_PASS="${MYSQL_PASS:-root}"

MVN="${MVN:-/Applications/IntelliJ IDEA.app/Contents/plugins/maven/lib/maven3/bin/mvn}"
JAR="$REPO_DIR/bb-bot-server/target/bb-bot-server.jar"

mkdir -p "$PIDS_DIR" "$LOGS_DIR"

c_red()   { printf '\033[31m%s\033[0m' "$*"; }
c_grn()   { printf '\033[32m%s\033[0m' "$*"; }
c_ylw()   { printf '\033[33m%s\033[0m' "$*"; }
c_blu()   { printf '\033[34m%s\033[0m' "$*"; }

log() { echo "$(c_blu '[bb-dev]') $*"; }
ok()  { echo "$(c_grn '[bb-dev]') $*"; }
warn(){ echo "$(c_ylw '[bb-dev]') $*"; }
err() { echo "$(c_red '[bb-dev]') $*" >&2; }

is_running() {
  local pidfile="$1"
  [[ -f "$pidfile" ]] && kill -0 "$(cat "$pidfile")" 2>/dev/null
}

cmd_check_mysql() {
  if ! docker ps --filter "name=^${MYSQL_CONTAINER}$" --format '{{.Names}}' | grep -q "$MYSQL_CONTAINER"; then
    err "MySQL 容器 $MYSQL_CONTAINER 没在跑。请到 misu-server 那边 ./dev.sh up 把它起来。"
    exit 1
  fi
}

cmd_init_db() {
  cmd_check_mysql
  log "初始化数据库 bb_bot_local …"
  docker exec -i "$MYSQL_CONTAINER" mysql -u"$MYSQL_USER" -p"$MYSQL_PASS" \
    < "$SCRIPT_DIR/sql/01-init-bb-bot-local.sql" 2>&1 | grep -v 'Using a password' || true
  ok "数据库就绪"
}

cmd_build() {
  if [[ -f "${JAR}" && "${1:-}" != "--force" ]]; then
    log "已存在 ${JAR}，跳过 build（强制重建用 ./bb-dev.sh up --build）"
    return
  fi
  log "mvn package 中（首次约 1-2 分钟）…"
  cd "$REPO_DIR"
  # 跳过 test 编译 + 执行（项目里有过时 test 源）
  "$MVN" -pl bb-bot-server -am package -Dmaven.test.skip=true -q
  ok "build 完成：${JAR}"
}

cmd_up() {
  local force_build=
  for arg in "$@"; do
    case "$arg" in
      --build) force_build=--force ;;
    esac
  done
  cmd_init_db
  cmd_build $force_build

  if is_running "$PIDS_DIR/mock.pid"; then
    warn "mock OpenAI 已在跑 (pid=$(cat "$PIDS_DIR/mock.pid"))"
  else
    log "启动 mock OpenAI on :$MOCK_PORT …"
    nohup node "$SCRIPT_DIR/mock-openai-server.mjs" --port=$MOCK_PORT > "$LOGS_DIR/mock.log" 2>&1 &
    echo $! > "$PIDS_DIR/mock.pid"
    sleep 0.5
    ok "mock OpenAI pid=$(cat "$PIDS_DIR/mock.pid")"
  fi

  if is_running "$PIDS_DIR/bot.pid"; then
    warn "bbBot 已在跑 (pid=$(cat "$PIDS_DIR/bot.pid"))"
  else
    log "启动 bbBot (profile=bbtest) on http :$HTTP_PORT, bb-ws :$BB_WS_PORT …"
    # spring 会按 active=local（默认）+ bbtest profile 合并；application-bbtest.yml 里的字段优先级最高
    nohup java -jar "${JAR}" \
      --spring.profiles.active=bbtest \
      > "$LOGS_DIR/bot.log" 2>&1 &
    echo $! > "$PIDS_DIR/bot.pid"
    ok "bbBot pid=$(cat "$PIDS_DIR/bot.pid")"
  fi

  log "等待 bbBot 启动完成 (最多 60s)…"
  for i in $(seq 1 60); do
    if grep -q "WebSocket服务器启动成功\|Started.*Application in " "$LOGS_DIR/bot.log" 2>/dev/null; then
      ok "bbBot 已就绪"
      break
    fi
    sleep 1
    [[ $i -eq 60 ]] && { err "bbBot 60s 内未就绪，查看日志：$LOGS_DIR/bot.log"; exit 1; }
  done

  cmd_status
}

cmd_down() {
  for name in bot mock; do
    local pidfile="$PIDS_DIR/$name.pid"
    if is_running "$pidfile"; then
      log "kill $name (pid=$(cat "$pidfile"))"
      kill "$(cat "$pidfile")" || true
      rm -f "$pidfile"
    fi
  done
  ok "全部停止"
}

cmd_status() {
  printf '%-15s %-10s %s\n' '组件' '状态' 'pid'
  for name in mock bot; do
    local pidfile="$PIDS_DIR/$name.pid"
    if is_running "$pidfile"; then
      printf '%-15s %-10s %s\n' "$name" "$(c_grn running)" "$(cat "$pidfile")"
    else
      printf '%-15s %-10s %s\n' "$name" "$(c_red stopped)" '-'
    fi
  done
  printf '%-15s %-10s %s\n' "mysql($MYSQL_CONTAINER)" \
    "$(docker ps --filter "name=^${MYSQL_CONTAINER}$" --format '{{.Status}}' | head -1 || echo n/a)" '-'
}

cmd_logs() {
  local which="${1:-bot}"
  local path="$LOGS_DIR/$which.log"
  if [[ ! -f "$path" ]]; then err "无日志：$path"; exit 1; fi
  tail -n 200 -f "$path"
}

cmd_test() {
  log "跑 BB 协议测试客户端…"
  # bash 3.2 + set -u 下不能直接展开可能为空的数组，分两路写
  if [[ -n "${1:-}" ]]; then
    node "$SCRIPT_DIR/bb-client.mjs" "--case=$1"
  else
    node "$SCRIPT_DIR/bb-client.mjs"
  fi
}

cmd_repl() {
  node "$SCRIPT_DIR/bb-client.mjs" --interactive
}

cmd_help() {
  cat <<EOF
bb-dev.sh — bbBot 本地端到端测试 orchestrator

子命令：
  up [--build]      建库 + 启动 mock OpenAI + 启动 bbBot
                    --build 强制重新 mvn package
  down              停 bbBot + mock
  status            看运行状态
  build [--force]   仅 build jar
  init-db           仅初始化 bb_bot_local 数据库
  test [case]       跑测试客户端（无参 = 全部场景；如 A2 / A4）
  repl              交互式手动收发
  logs [bot|mock]   tail 日志
  help              本帮助

依赖：
  - misu-server 的 MySQL 容器 $MYSQL_CONTAINER 已在跑（端口 3316）
  - Node 22+ 或安装了 ws npm 包
  - Java 17、Maven（或 IntelliJ 自带的 mvn，已自动定位）
EOF
}

main() {
  local cmd="${1:-help}"
  shift || true
  case "$cmd" in
    up)      cmd_up      "$@" ;;
    down)    cmd_down    "$@" ;;
    status)  cmd_status  "$@" ;;
    build)   cmd_build   "$@" ;;
    init-db) cmd_init_db "$@" ;;
    test)    cmd_test    "$@" ;;
    repl)    cmd_repl    "$@" ;;
    logs)    cmd_logs    "$@" ;;
    help|--help|-h) cmd_help ;;
    *) err "未知命令 $cmd"; cmd_help; exit 1 ;;
  esac
}

main "$@"
