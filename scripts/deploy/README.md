# 自动部署流程（scripts/deploy/）

开发机一键把 `bb-bot` 发布到生产 k8s 集群。开发流程不变：在 `claude/*` 分支干活、
开 PR、合并到 master，然后在开发机跑一条命令完成上线。参考 misu-server 的
`scripts/deploy/` 实现。

## 工作原理

```
合并到 master  →  在开发机执行 scripts/deploy/release.sh
   │
   ├─ 1. Maven 构建 bb-bot-server → docker buildx 构建镜像
   │     → 推送到私有 registry，tag = master 的 git short SHA
   └─ 2. SSH 主节点：
           备份旧清单 → /root/backups/<UTC时间戳>/k8s/
           渲染新清单(填入 registry+SHA) 覆盖 /root/k8s/bb-bot/
           kubectl apply + rollout status
   rollout 失败 → 自动回滚到本次部署前的清单。
```

所有 IP / 路径 / SSH key 集中在 **一个本地配置文件** `scripts/deploy/deploy.conf`，
`release.sh` 执行时自动读取。

## 一次性设置

开发机需具备：JDK 17、Maven、`docker buildx`、`envsubst`(gettext)、`git`；已
`docker login` 私有 registry 且把它配进 docker daemon 的 `insecure-registries`；
能用 SSH key 登录主节点。

```bash
cp scripts/deploy/deploy.conf.example scripts/deploy/deploy.conf
vi scripts/deploy/deploy.conf      # 填 SSH key 路径、主节点、registry、Maven 路径

# bb-bot-config.yaml（ConfigMap，含生产密钥）不入库，首次从集群现有 ConfigMap 取一份：
scp root@<master>:/root/k8s/bb-bot/bb-bot-config.yaml scripts/deploy/k8s/bb-bot/
# 或：ssh root@<master> kubectl get cm bb-bot-config -n bb-bot -o yaml > ...
```

`deploy.conf` 与 `bb-bot-config.yaml`（含生产密钥）都在 `scripts/deploy/.gitignore`
中、不会提交。

集群侧首次需准备好 namespace 与一套可用的 MySQL（`bb-bot` 不含 DB）：

```bash
ssh root@<master> kubectl create namespace bb-bot
scripts/deploy/release.sh --config           # 下发 bb-bot-config.yaml
```

## 日常使用

合并 PR 到 master 后，在仓库根目录执行：

```bash
scripts/deploy/release.sh              # 构建 + 推镜像 + 部署 + rollout
scripts/deploy/release.sh --dry-run    # 只构建，不推送、不碰服务器（验证用）
scripts/deploy/release.sh --skip-build # 镜像已推过，只重新部署
```

## ConfigMap 与 Deployment 解耦

k8s 清单拆成两个文件：

- `k8s/bb-bot/bb-bot.yaml` —— PV + PVC + Deployment + Service，**日常 `release.sh` 只覆盖它**（镜像 tag 变更）。
- `k8s/bb-bot/bb-bot-config.yaml` —— ConfigMap（`application-prod.yaml`），含生产密钥、已 gitignore（不入库），日常发布**不会动它**。

改了生产配置（DB、AI key、启用的机器人等）后单独下发：

```bash
scripts/deploy/release.sh --config
```

`--config` 会备份旧 ConfigMap → apply 新的 → `kubectl rollout restart`（ConfigMap 走
subPath 挂载，kubelet 不热更新，必须重启 pod 才生效）。

## 回滚

```bash
scripts/deploy/release.sh --list-backups          # 列出备份时间戳
scripts/deploy/release.sh --rollback 20260517T083000Z
```

部署中途 rollout 失败时，`release.sh` 会**自动回滚**到本次部署前的清单。

## k8s 清单的真源

`scripts/deploy/k8s/bb-bot/` 是清单的唯一真源：

- `bb-bot.yaml`（PV+PVC+Deployment+Service）—— 镜像行参数化为
  `${REGISTRY_PULL}/misuaa/bb-bot:${IMAGE_TAG}`，发布时 `envsubst` 渲染。
- `bb-bot-config.yaml`（ConfigMap，含生产密钥、gitignore、不入库）—— 无模板变量，
  `--config` 时原样下发。首次按上面「一次性设置」从集群现有 ConfigMap 取一份。

改部署配置（副本数、资源、探针等）改前者、改生产运行配置改后者，合并到 master 后
分别用 `release.sh` / `release.sh --config` 生效。

## 日志与备份

- 发布日志：开发机 `scripts/deploy/deploy.log`（已 gitignore）。
- 备份：主节点 `/root/backups/<UTC时间戳>/`，自动保留最近 `KEEP_BACKUPS` 份。
