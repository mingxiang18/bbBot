# bbBot — hard rules

## 项目结构

- Maven 多模块：`bb-bot-sdk`（库，发布到私有/中央仓库供 misu-server 等依赖）+ `bb-bot-server`（唯一可部署服务）
- Java 17，**无前端**
- 部署：k8s namespace `bb-bot`，单 Deployment `bb-bot`（NodePort 8099/8888/30210），清单 `scripts/deploy/k8s/bb-bot/bb-bot.yaml`（含 NFS PV/PVC + Deployment + Service）
- 镜像：`<registry>/misuaa/bb-bot:<tag>`，base `amazoncorretto:17-alpine3.20-jdk`

## ⚠️ 发布 = push master 自动触发（ARC self-hosted runner）

**merge / push 到 master 会自动构建并部署到生产**，没有手动步骤。改任何代码前先想清楚这条链路：

```
push master → GitHub Actions(.github/workflows/release.yml) → 集群内 runner(bbbot-runners)
  → mvn package -pl bb-bot-server -am → Kaniko build & push misuaa/bb-bot:<sha>
  → kubectl apply bb-bot.yaml + rollout
```

- 单服务，总是全量 build & deploy（无 paths-filter）
- 镜像 tag = git short SHA；`workflow_dispatch` 可手动触发
- 复用 misu-server 那套 ARC 基础设施（controller / runner 镜像 / GitHub App / maven cache）；自己只多一个 `bbbot-runners` scale set。配置在 `scripts/deploy/arc/`
- 本地手动发布兜底：`scripts/deploy/release.sh`（gitignored `deploy.conf`）

### 改代码必须保证 merge 后 auto-deploy 仍能跑通（每条都踩过）

1. **`bb-bot-sdk` pom 有 maven-javadoc-plugin(attach-javadocs)** → runner 上靠 pod env `JAVA_HOME` + `-Dmaven.javadoc.skip=true` 才不挂。**别删这俩 env**（在 `scripts/deploy/arc/02-values-runner.yaml`）；若加更多需要外部工具的 maven 插件，先确认 runner 镜像里有
2. **改 `bb-bot-server/DockerfileLocal`** → 必须保持 `FROM .../amazoncorretto:17-alpine3.20-jdk`（runner 的 kaniko-wrapper 靠它换 `-fonts` 变体 + 删 apk RUN；换别的 base 会触发 Alpine+kaniko 字体 bug）。`DockerfileLocal` **必须入 git**（workflow checkout 后要用）
3. **改 `scripts/deploy/k8s/bb-bot/bb-bot.yaml`**（resources/probes/env/端口/PV）→ 直接生效（workflow `envsubst + kubectl apply`）。但 YAML 写错会让 apply 失败 → 整个 release 失败。镜像行保留 `${REGISTRY_PULL}/misuaa/bb-bot:${IMAGE_TAG}` 占位
4. **bb-bot-sdk 版本号** → server 同 reactor 内构建依赖它（`-am`），不走外部仓库，所以改 sdk 不会有"中央仓库覆盖自定义包"的问题。但若把 sdk 发布出去给别的项目（如 misu-server）用，记得 misu-server 那边的 maven cache 要更新
5. push 后去 GitHub Actions 看绿；失败 workflow 自动 `rollout undo`，**别留着红的 release**

## 静态资源

`bb-bot-server/DockerfileLocal` 有 `ADD src/main/resources/static /bot/static`，运行时挂 NFS PVC `bb-bot-static-pvc`（`/bot/static`）。改静态资源走这条，别硬编进镜像逻辑。
