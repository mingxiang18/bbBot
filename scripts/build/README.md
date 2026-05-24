# 镜像构建（scripts/build/）

把 `bb-bot-server` 打成 jar 并构建/推送 Docker 镜像。迁移自原仓库根目录的
Windows `package.bat`（后者保留，供 Windows 开发机继续使用）。

| 脚本 | 推送目标 | 用途 |
|---|---|---|
| `build-push-bb-bot.sh` | Docker Hub `misuaa/bb-bot` | 公网发布 |
| `build-local-push-bb-bot.sh` | 内网私有 registry `${REGISTRY}/misuaa/bb-bot` | 本地 k8s 集群 |

```bash
scripts/build/build-push-bb-bot.sh 0.0.1
REGISTRY=192.168.50.227:30500 scripts/build/build-local-push-bb-bot.sh 0.0.1
```

两个脚本都：`mvn clean package -pl bb-bot-server -am -P prod`（跳过 test 编译，
项目里有过时 test 源）→ `docker buildx build --push`。

环境变量：`VERSION`（也可作首个位置参数）、`PLATFORMS`、`REGISTRY`（仅 local 版）、
`MVN` / `MAVEN_REPO`（本仓库 mvn 不在 PATH 上时覆盖）。

> 一键发布到 k8s 集群（构建 + 推送 + 部署 + rollout + 回滚）见 `scripts/deploy/`。
