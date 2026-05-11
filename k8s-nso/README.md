# k8s-nso — NSO Token 生成自部署方案

为 bbBot 项目自部署一套 `nxapi-znca-api` 服务,通过 K8s 上的 redroid Android 模拟器 +
frida-server 拦截 Nintendo Switch Online app 的 token 生成函数,替代公共 imink API。

**主部署位置**:K8s 集群(master 8.138.142.47 / worker `misu-maco` LAN 192.168.50.227)
**对外接口**:`http://192.168.50.227:30445/api/znca/f`(NodePort)
**对内接口**:`http://nxapi-znca-api.nso-tokens.svc.cluster.local:12345/api/znca/f`

---

## 已完成的工作(我离场时的状态)

| 组件 | 状态 |
|---|---|
| Namespace `nso-tokens` | ✅ 已创建 |
| Worker `misu-maco` 内核 `binder_linux` 模块 | ✅ 加载并 fstab/modules-load.d 持久化 |
| `/dev/binderfs` 挂载 + fstab 持久化 | ✅ 已挂载 |
| 数据目录 `/data/redroid` | ✅ 已建,redroid 用 hostPath 挂进容器 |
| redroid 镜像 `redroid/redroid:11.0.0-fixed` | ✅ 已重打(绕开 containerd 2.2.2 OCI unpack bug)并 import 到 worker |
| redroid Pod (Android 11 x86_64) | ✅ 1/1 Running, `sys.boot_completed=1` |
| frida-server 17.9.8 (android-x86_64) | ✅ 在 redroid 里跑,监听 127.0.0.1:27042 |
| nxapi-znca-api Pod (Node 20) | ✅ Running(retry loop 中,等 NSO 装好后能 attach 成功) |
| Service `redroid` (ClusterIP) | ✅ 5555/tcp (ADB) |
| Service `redroid-adb-nodeport` | ✅ NodePort 30555/tcp (给 scrcpy / 外部 adb 用) |
| Service `nxapi-znca-api` (ClusterIP) | ✅ 12345/tcp |
| Service `nxapi-znca-api-nodeport` | ✅ NodePort 30445/tcp (**bbBot 调这个**) |

## 还差什么(需要你手动做一次)

```
[A] 拿到 NSO apk             (~5 min)  — APKMirror / 手机导出 / Google Play
[B] 装 NSO 到 redroid         (1 min)  — bash /root/k8s-nso/scripts/install-nso.sh /path/to/nso.apk
[C] 用 scrcpy 连进 redroid 看屏幕(2 min) — scrcpy --tcpip=192.168.50.227:30555
[D] 在 redroid 里登录 Nintendo 账号(5 min)
[E] 修改 bbBot application-local.yml(1 min)
[F] 重启 bbBot,触发一次查询验证(2 min)
```

完成后总链路:
```
bbBot (Mac 192.168.50.x)
   │ POST http://192.168.50.227:30445/api/znca/f
   ▼
nxapi-znca-api Pod (10.244.1.x:12345)
   │ ADB → frida attach
   ▼
redroid Pod (10.244.1.x:5555)
   └─ NSO app + frida-server 17.9.8
       └─ Libvoipjni.genAudioH/H2 → f / request_id / timestamp
```

---

## 目录结构

```
/root/k8s-nso/             # master 上,K8s 操作的"权威位置"
├── manifests/
│   ├── 00-namespace.yaml
│   ├── 10-redroid.yaml          # Deployment + 2 Services (ClusterIP + NodePort 30555)
│   └── 20-nxapi-znca-api.yaml   # ConfigMap entrypoint + Deployment + 2 Services (ClusterIP + NodePort 30445)
├── scripts/
│   ├── bootstrap.sh             # 一键拉起(binder modprobe / apply)
│   ├── fix-redroid-image.sh     # 重打 redroid image,改绝对 symlink 为相对(containerd #12683 workaround)
│   ├── install-frida.sh         # 下载 frida-server-17.9.8 并 adb push 到 redroid
│   ├── install-nso.sh           # 把 NSO apk push 到 redroid + pm install
│   └── verify.sh                # 一键体检 7 项
├── bin/
│   └── frida-server             # frida-server 二进制(110MB,已下载)
└── README.md (本文件)
```

Mac 项目侧 `<bbBot>/k8s-nso/` 是 git-tracked 的同步副本,master 上的是部署用的"权威位置"。
两边差异主要是 `bin/` 目录 master 上保留 110MB binary,Mac 上为减小 git repo 不留(`.gitignore` 应忽略)。

---

## 完整接入流程(回来时按这个跑)

### Step 1 — 拿 NSO apk

包名 `com.nintendo.znca`。三个来源任选:

- **APKMirror**(推荐,有官方签名校验):https://www.apkmirror.com/apk/nintendo-co-ltd/nintendo-switch-online/
  下载 *Android 5+* 的 universal/x86_64 变体均可
- 用一台真 Android 手机:从 Play Store 装,再用 [App Backup & Restore](https://play.google.com/store/apps/details?id=mobi.infolife.appbackup) 等工具导出 base apk
- 朋友给一份

放到你 Mac 上,记住路径。

### Step 2 — 装 NSO 到 redroid

```bash
# 你 Mac 上:
scp -i ~/Documents/Codex/2026-05-05/ssh-linux-linux/codex_temp_ed25519 \
  ~/Downloads/nso.apk root@8.138.142.47:/root/k8s-nso/bin/nso.apk

# master 上:
ssh -i ~/Documents/Codex/2026-05-05/ssh-linux-linux/codex_temp_ed25519 root@8.138.142.47
bash /root/k8s-nso/scripts/install-nso.sh /root/k8s-nso/bin/nso.apk
```

预期输出末尾应该有 `package:com.nintendo.znca`。

### Step 3 — 用 scrcpy 看屏幕 + 登录 Nintendo

**你 Mac 上**先装 scrcpy(`brew install scrcpy`),然后:

```bash
adb connect 192.168.50.227:30555
adb devices                              # 看到 192.168.50.227:30555  device
scrcpy --tcpip=192.168.50.227:30555      # 弹出 redroid 屏幕
```

在 scrcpy 窗口里:
1. 找 NSO app(Nintendo Switch Online),点开
2. 按提示登录你的 Nintendo 账号
3. **不需要做任何"开始游戏"等操作** —— 只要登录成功 + app 进入主界面 即可。
   frida 只需要 NSO 进程跑着且 `Libvoipjni` 类已加载。

> NSO app 在 redroid Android 11 无 GMS 环境下能登录 —— Nintendo 账号登录走自家 OAuth,不依赖 Google Play Services。
> 如果遇到 "Google Play Services Required" 错误,需要切到带 GMS 的 redroid 镜像(`redroid:11.0.0-gms-latest`),按同样方法重新做 fix-redroid-image。

### Step 4 — 验证整条链路

```bash
ssh -i ~/Documents/Codex/2026-05-05/ssh-linux-linux/codex_temp_ed25519 root@8.138.142.47
bash /root/k8s-nso/scripts/verify.sh
```

7 项应该全 OK,特别注意第 6 项 `/api/znca/config` 应该返回 200 + JSON like:
```json
{
  "nso_version":"2.10.1",
  "versions":[{"platform":"Android","name":"com.nintendo.znca","version":"2.10.1",...}]
}
```

如果第 6 项还是 503,看 `kubectl -n nso-tokens logs deployment/nxapi-znca-api --tail=40` —
通常是 frida attach NSO 失败,解决:scrcpy 进去手动点开 NSO 让它跑起来。

### Step 5 — 改 bbBot

[`application-local.yml`](../bb-bot-server/src/main/resources/application-local.yml) 第 119 行附近的 `nso.fGenerationApi` 改:

```yaml
nso:
  # 自部署 nxapi-znca-api
  fGenerationApi: http://192.168.50.227:30445/api/znca/f
```

并把 [`NsoApiCaller.java`](../bb-bot-server/src/main/java/com/bb/bot/common/util/nso/NsoApiCaller.java) 第 170 行附近的 User-Agent 调整:nxapi 要求 `project/version (+url)` 格式(public-api-terms),自部署虽然不强制但建议改:

```java
headers.set("User-Agent", "bbBot/1.0.0 (+https://github.com/<your>/bbBot)");
```

这一改动**只需要在你切换到 nxapi 时做**;继续用 imink 不用动。

### Step 6 — 触发一次 bbBot 查询验证

启动 bbBot,在群里发 `/查询战绩` 之类指令。看日志:
- `获取到 nso 的 app 版本` 应该没 9403
- `webApiServerCredential.accessToken` 应该有值
- 战绩数据应该正常返回

---

## 故障排查

### "Failed to attach to process" / `pm list` 没看到 NSO

NSO app 没装或 frida 找不到它。重跑:
```
bash /root/k8s-nso/scripts/install-nso.sh /root/k8s-nso/bin/nso.apk
```

### redroid Pod 启动失败 `path escapes from parent`

Worker `containerd 2.2.2 + Go 1.24` 已知 bug(issue #12683)。本仓库的 fix 是在 worker 上重打镜像
(`fix-redroid-image.sh`),把 redroid 根级别 33 个绝对 symlink 改成相对。如果镜像被覆盖丢失,
重新运行:
```
scp /root/k8s-nso/scripts/fix-redroid-image.sh root@192.168.50.227:/tmp/
ssh root@192.168.50.227 bash /tmp/fix-redroid-image.sh
```

### frida-server 报 "Address already in use"

之前实例还在跑。先 kill:
```
NXAPI=$(kubectl -n nso-tokens get pod -l app=nxapi-znca-api -o jsonpath='{.items[0].metadata.name}')
kubectl -n nso-tokens exec $NXAPI -- adb -s redroid:5555 shell pkill -9 -f frida-server
bash /root/k8s-nso/scripts/install-frida.sh
```

### NodePort 30445 在 Mac 上不通

确认 worker 192.168.50.227 和 Mac 在同 LAN。`nc -zv 192.168.50.227 30445`。
如果跨网段,改用 master 公网 8.138.142.47 + 在 master 上加一个 iptables / nginx 转发。

### nxapi pod 一直 0/1 (Not Ready)

正常 —— readiness probe 走 `/api/znca/health`,只有 frida attach NSO 成功后才会 200。
NSO 没装时 pod 一直 NotReady 但容器在 retry loop,这是设计行为。
**装好 NSO 并登录后,/api/znca/health 会自动开始返回 200,pod 自动 Ready。**

### 模拟器需要重置(账号搞乱了)

`/data/redroid` 是 hostPath,删了就重置:
```
kubectl -n nso-tokens scale deploy/redroid --replicas=0
ssh root@192.168.50.227 rm -rf /data/redroid/*
kubectl -n nso-tokens scale deploy/redroid --replicas=1
```
然后从 Step 2 重做。

### 公共 imink API 出问题想立即切回

```yaml
nso:
  fGenerationApi: https://api.imink.app/f
```
imink 仍可用,只是它作者推荐迁移到 nxapi。

---

## 资源消耗参考(misu-maco worker 上)

- redroid Pod:实测内存 ~1.5GB,CPU 启动峰值 4 core,稳态 0.5 core
- nxapi-znca-api Pod:~300MB,基本不占 CPU(只在请求来时 spike)
- 磁盘 `/data/redroid`:Android 11 base + NSO ~3GB,登录后 ~5GB
- 网络:NodePort 30445 + 30555 暴露

## 安全提示

- worker 上 `redroid` 是 `privileged: true`(redroid 要求,挂 binder 设备必需),容器内 root = host root
- frida-server 在 redroid Android 里 listen 127.0.0.1:27042,不暴露
- `redroid-adb-nodeport:30555` **没有鉴权** —— 谁都能 adb connect。如果 worker 所在 LAN 不可信,
  改成只让 master 的 IP 访问(NetworkPolicy 或 kube-proxy 防火墙)。本部署默认 LAN 信任
- NSO apk 来源建议用 APKMirror 等带签名验证的源,不要从国内随便一个网站下载

---

## 维护

- frida-server 升级:改 `install-frida.sh` 顶部 `FRIDA_VERSION`,重跑
- NSO app 升级:重跑 `install-nso.sh` 装新 apk(pm install -r 会覆盖)
- nxapi-znca-api 升级:nxapi pod 每次重启都会 `npm install -g nxapi-znca-api@latest`,
  所以删 pod 即升级:`kubectl -n nso-tokens delete pod -l app=nxapi-znca-api`
- redroid 升级到更新 Android(13/14)版本:等 worker containerd 升到 2.3.0+ 后,可以直接用
  `redroid/redroid:13.0.0-latest`(不再需要 fix-redroid-image 工具),否则继续走 `fix-redroid-image.sh`

---

## 关于 bbBot 端的最小代码改动

本次没修改 bbBot Java 代码(切换 fGenerationApi 配置即可)。
如果未来要正式接 nxapi 公共服务(而不是自部署),需要按 [public-api-terms.md](https://github.com/samuelthomas2774/nxapi-znca-api/blob/master/docs/public-api-terms.md):
1. 添加 `Authorization: Bearer <nxapi-auth token>` header
2. 添加 `X-znca-Client-Version` header
3. 用户首次登录前告知 id_token 会发送到第三方
4. 缓存 + 删除支持等运营改造

自部署绕过了所有这些要求,但你的 nxapi-znca-api 服务自身的安全责任由你承担。
