# bbBot ARC release —— push master 自动构建 + 部署

复用 misu-server 已经装好的整套 ARC 基础设施（一个集群只需装一次 controller）：
- ARC controller（`arc-systems` ns）—— **已装，复用**
- runner 镜像 `10.8.0.26:30500/misuaa/misu-ci-runner:latest`（jdk17+maven+kaniko+kubectl）—— **复用**
  - 里面的 kaniko wrapper 自动处理 Alpine 3.20 字体问题（FROM 换 -fonts、删 apk RUN），对 bbBot 的 DockerfileLocal 同样生效
- 字体 base 镜像 `10.8.0.26:30500/library/amazoncorretto:17-alpine3.20-jdk-fonts` —— **复用**
- GitHub App 凭据 secret `arc-runners/gh-app-creds` —— **复用**（同账号 mingxiang18）
- maven cache PVC `misu-ci-maven-repo` —— **复用**（同节点同 .m2）

bbBot 只需新增：一个 scale set `bbbot-runners` + bb-bot ns 的 RBAC。

---

## 链路

```
push master → GitHub → broker
  → bbbot-runners listener (arc-systems, 复用 controller)
  → runner pod (misu-maco, 镜像 misu-ci-runner)
      mvn package -pl bb-bot-server -am
      kaniko build & push misuaa/bb-bot:<sha>
      kubectl apply bb-bot.yaml (PV/PVC/Deployment/Service) + rollout
  → bb-bot ns 滚动更新
```

## 一次性安装（SSH 到主节点，需要 key 可用）

> ⚠️ 当前 SSH key 在 `~/Documents/Codex/...` 被 macOS TCC 锁了（Claude 进程读不了）。
> 先在「访达 → 系统设置 → 隐私与安全性 → 完全磁盘访问/文件与文件夹」给跑 Claude 的
> 终端授予 Documents 访问，或把 key 拷到 `~/IdeaProjects` 下用。恢复后执行：

### 1. misu-maco 上建 bbBot 专用 workspace 目录

```bash
ssh root@10.8.0.26 '
  mkdir -p /mnt/misu/ci/bbbot-runner-work
  chown root:1001 /mnt/misu/ci/bbbot-runner-work
  chmod 775 /mnt/misu/ci/bbbot-runner-work
'
```

### 2. apply RBAC（bb-bot ns Role + PV ClusterRole，绑到现有 misu-deployer SA）

```bash
cat scripts/deploy/arc/01-rbac.yaml | ssh root@10.8.0.1 'kubectl apply -f -'
```

### 3. helm install bbbot-runners scale set

```bash
# chart 已在 misu-server ARC 时 pull 到 master /tmp/；若没了重新 pull：
ssh root@10.8.0.1 '
  export HTTPS_PROXY=http://127.0.0.1:7890 HTTP_PROXY=http://127.0.0.1:7890
  export NO_PROXY=localhost,127.0.0.1,10.8.0.1,10.0.0.0/8,.cluster.local,.svc
  ls /tmp/gha-runner-scale-set-0.9.3.tgz 2>/dev/null || \
    helm pull oci://ghcr.io/actions/actions-runner-controller-charts/gha-runner-scale-set --version 0.9.3 --destination /tmp/
'

# 把 values 拷过去装
cat scripts/deploy/arc/02-values-runner.yaml | ssh root@10.8.0.1 'cat > /tmp/bbbot-values.yaml && \
  helm upgrade --install bbbot-runners -n arc-runners -f /tmp/bbbot-values.yaml \
    /tmp/gha-runner-scale-set-0.9.3.tgz --no-hooks --wait=false --timeout 5m'
```

### 4. GitHub App 加 bbBot repo 访问权

App `misu-server-ci`（App ID 3780742）当初只装到了 `misu-server`。给它加 bbBot：
- GitHub → Settings → Developer settings → GitHub Apps → 你的 App → Install App
- 编辑安装的 Repository access，**勾上 `mingxiang18/bbBot`**（或改成 All repositories）

> 不加的话 listener 认证后看不到 bbBot 的 job，dispatch 不会触发 runner。

### 5. 验证

```bash
# listener 起来了吗
ssh root@10.8.0.1 'kubectl -n arc-systems get pods | grep bbbot'
# GitHub repo Settings → Actions → Runners 应能看到 bbbot-runners (Idle)

# 推一个空 commit 或 workflow_dispatch 触发
git commit --allow-empty -m "ci: smoke test bbBot ARC" && git push origin master
```

## 日常使用

push master 自动触发（单服务，总是 build & deploy）；或 Actions UI → Run workflow 手动。

| 想做 | 做法 |
|---|---|
| 发布 | push master，或 workflow_dispatch |
| 看日志 | GitHub Actions 页面，或 `kubectl -n arc-runners logs -l app.kubernetes.io/component=runner -f` |
| 紧急停 | `kubectl -n arc-runners delete pod -l app.kubernetes.io/scale-set-name=bbbot-runners` |
| 手动回滚 | `kubectl -n bb-bot rollout undo deploy/bb-bot` |
| 本地兜底 | 原 `scripts/deploy/release.sh` 保留 |

## 文件清单

```
.github/workflows/release.yml          ← push master 触发
scripts/deploy/arc/
├── README.md                          ← 本文件
├── 01-rbac.yaml                       ← bb-bot ns Role + PV ClusterRole → misu-deployer SA
└── 02-values-runner.yaml              ← bbbot-runners scale set helm values
```

## 与 misu-server 的差异

| 项 | misu-server | bbBot |
|---|---|---|
| 服务数 | 3 Java + 前端 | 1 Java（bb-bot-server） |
| 前端 | 有（vite → nginx html） | 无 |
| paths-filter | 有（按目录选服务） | 无（单服务总是 build） |
| k8s 清单 | 每服务一份 Deployment+Service | 一份含 PV+PVC+Deployment+Service |
| PV 权限 | 不需要 | 需要（NFS PV，加了 ClusterRole） |
| maven test | -DskipTests | -Dmaven.test.skip=true |
| scale set | misu-runners | bbbot-runners |
| workspace | /mnt/misu/ci/runner-work | /mnt/misu/ci/bbbot-runner-work |
