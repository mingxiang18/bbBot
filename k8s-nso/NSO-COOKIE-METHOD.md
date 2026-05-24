# NSO Token 获取 — Cookie 读取方案(验证成功)

## 背景:为什么是这个方案

bbBot 的 NSO 功能 2024 年中失效,根因是 Nintendo 在 2024-2025 持续加强 NSO app 的 **Pairip 完整性保护 + Play Integrity 设备认证**,把所有"自动 token 生成"工具逼死。

本仓库探索过的失败路径(都验证过不可行):
- **redroid 模拟器 + nxapi-znca-api(frida)**:模拟器过不了 Play Integrity(NSO 启动 Communication Error 2817-0583)
- **真机 + nxapi-znca-api(frida 注入)**:frida 注入触发 Pairip 反调试,libpairipcore.so SIGSEGV 自毁;且公开的 nxapi-znca-api npm 版停更在 2023-12 的 1.5.0,对 NSO 3.x 无招架
- **mitmproxy 抓包**:需装 MITM 根证书削弱 TLS(安全顾虑)

## 最终可行方案:root 读 NSO 自己存的 cookie

来源:[blog.northwestw.in 2025-06 "SplatNet 3 Token Guide for NSO App ≥3.0.1"](https://blog.northwestw.in/p/2025/06/20/splatnet-3-token-guide-for)

**原理**:NSO app 正常登录 + 打开 Splatoon3 后,它的 WebView 把 `_gtoken` 存进自己的 cookie 数据库。我们用 root **被动读取**这个 cookie 拿 gtoken,再 curl 换 bulletToken。

**为什么绕开所有坑**:
- 不注入 NSO 进程 → 不触发 Pairip 反调试
- 不装 MITM 证书 → 不削弱 TLS
- 不依赖 nxapi/imink 第三方
- NSO 正常跑(真机过 Play Integrity),我们只读它自己生成的 token

## 验证过的环境

- 手机:Redmi K20 Pro (raphael), Android 11, arm64, MIUI 12.5
- root:Magisk Alpha (vvb2060),已配隐藏环境过 Play Integrity(过牛头)
- 关键:**真机过 Pairip**(NSO 能登录 + 生成 gtoken),模拟器做不到这点
- 手机走 worker clash 代理(`192.168.50.227:7890`)访问 Nintendo(国内被墙)
- worker (misu-maco 192.168.50.227) 上:platform-tools(adb)、python3、clash

## 验证过的完整链路(全部实测通过)

```
真机 NSO(已登录,过 Pairip)
  → root cp cookie: /data/user/0/com.nintendo.znca/app_webview/Default/Cookies
  → python sqlite3 提取 _gtoken (host=api.lp1.av5ja.srv.nintendo.net, 958 字符)
  → curl POST /api/bullet_tokens → bulletToken (124 字符, HTTP 201)
```

### 关键坑

1. **cookie 里有两个 `_gtoken`**(host `acbaa` 和 `av5ja`),必须取 **av5ja** 那个,否则 bullet_tokens 返回 401
2. bullet_tokens 必须带完整 header:`User-Agent: Mozilla/4.0 (compatible; MSIE 5.5; Windows NT)` + `Origin` + `X-Requested-With: com.nintendo.znca` + `X-Web-View-Ver` + cookie `_dnt=1; _gtoken=`
3. web view version 从 `https://api.lp1.av5ja.srv.nintendo.net/` 的 main.*.js 正则提取(当前 10.0.0-dfefd0af)
4. `/data/local/tmp` 下 su 创建的文件 adb pull 读不了(权限),中转走 `/sdcard/Download/`

## token-provider 脚本

worker 上:`/root/k8s-nso-token/provider.sh`(本仓库副本 `scripts/nso-token-provider.sh`)

输出 JSON:`{"gtoken":"...","bulletToken":"...","webViewVer":"10.0.0-dfefd0af","device":"99e0fc6d","dataUser":"0"}`

参数:`provider.sh [device_serial] [data_user]` —— data_user 用于多账号(不同 /data/user/{N})

## 当前状态(全部完成,已端到端验证)

- ✅ token 获取自动化:provider.sh 读 cookie 产出 gtoken + bulletToken
- ✅ 持久化 token 服务:`nso-token.service`(systemd 常驻 + Restart),`GET /token?dataUser=N`
- ✅ gtoken 自动刷新:`nso-refresh.timer`(每 90min)跑 refresh-all.sh,adb 唤醒 NSO + 点鱿鱼圈3 触发 GetWebServiceToken 刷新 cookie(`su input` 绕过 MIUI INJECT_EVENTS 限制)
- ✅ 多账号:user 0(账号1)+ user 999(账号2,MIUI 应用双开),provider/refresh 按 dataUser 区分,两账号 token 实测都有效
- ✅ bbBot 接入:NsoTokenProvider + resetSplatoon3UserToken(按 userId→dataUser 映射),编译通过
- ✅ 端到端:token 服务取 token → graphql 查到真实战绩(win/lose)

NSO **不支持** app 内切账号(换号要登出登入),所以多账号靠每号一个独立 NSO 实例。

## worker 常驻组件(misu-maco)

```
/root/k8s-nso-token/
  provider.sh        # 读 /data/user/{N} cookie → gtoken → bulletToken,输出 JSON
  token-server.py    # HTTP GET /token?dataUser=N(60s 缓存)
  refresh-nso.sh     # 唤醒+进鱿鱼圈3刷新单个 user 的 gtoken
  refresh-all.sh     # 刷新所有账号(USERS="0 999",加号在此加 user id)
systemd:
  nso-token.service  # 常驻 token 服务(enable,Restart=always)
  nso-refresh.timer  # 每 90min 触发 nso-refresh.service → refresh-all.sh
```

依赖:worker clash 代理(127.0.0.1:7890,访问 Nintendo)+ 手机 USB adb(99e0fc6d)+ 手机 NSO 保持登录态。

## bbBot 接入(你更新 bbBot 后即可)

1. 部署本分支改好的 bbBot(NsoTokenProvider + BbSplatoonUserHandler)
2. 配置(均有代码默认值,通常无需改 yml):
   - owner 复用已有 `aiAgent.owners`(逗号分隔的 userId),不新增配置
   - `nso.tokenProviderUrl` 默认 `http://192.168.50.227:18080/token`
   - `nso.accountMap` 默认 `1:0,2:999`(账号编号→Android dataUser;加号在此加映射)
3. **owner 绑定账号**(群里发,需 @bot;仅 `aiAgent.owners` 里的用户可用):
   - `绑定喷喷账号 1` → 把自己绑到账号1
   - `绑定喷喷账号 2 @某人` → 把某人绑到账号2
   - `绑定喷喷账号 1 2` → 一个用户绑多个账号(查战绩跨账号聚合)
   - 绑定写入 UserConfigValue(userId, NSO, dataUser=逗号分隔),查战绩/上传据此遍历各账号取 token
4. **查战绩**:绑定后,该 userId 发"对战记录"/"打工记录"等即用对应账号数据;绑多个账号则聚合展示。战绩按 `nso-{dataUser}` 入库区分账号
5. **自动上传战绩**(已自动适配):该 userId 发"自动上传喷喷记录"开启 → `SplatoonRecordsUploadSchedule`(每4h)对开启的用户跑 syncCoopRecords/syncBattleRecords(遍历其绑定的每个账号),token 走 cookie 方案,无需改调度
6. 加更多号:MIUI 手机分身开新空间装 NSO → `refresh-all.sh` 的 `USERS` 加新 user id → `nso.accountMap` 加 `3:<新user>` → owner `绑定喷喷账号 3`

## 下一步:bbBot 接入设计

bbBot 当前失效在 f-API 那段。新方案绕过它:

```
旧(失效): session_token → getUserToken → getLoginToken(f) → getWebServiceToken(f) → gtoken → bulletToken
新(可行): token-provider(读 cookie) → gtoken + bulletToken → 直接查询
```

改造点:
1. **worker token 服务**:provider.sh 包装成定时刷新 + HTTP `GET /token/{account}` 返回 JSON
2. **bbBot Splatoon3ApiCaller**:从 token 服务拿 gtoken+bulletToken,跳过 NsoApiCaller 整个 f-API 链;`callSplatoon3Api` 查询逻辑不变
3. **query hash**:bbBot 的 `translateRid` 里 sha256Hash 可能过时(graphql 手动测返回 400/500),需同步 s3s 最新 hash 匹配 SplatNet 10.0.0
4. **gtoken 刷新**:adb 定时唤醒 NSO + 进 SplatNet 触发 cookie 刷新

## 多账号方案(几个号)

- NSO **不能** app 内切账号(已确认,换号要登出登入走 OAuth)
- 正确做法:每号一个独立 NSO 实例,并行读各自 cookie:
  - MIUI 应用双开(+1)
  - MIUI 手机分身/多用户(每空间 +1,读 /data/user/{N})
  - 多台手机(每台 1 个)
- 每号首次人工登录一次,之后 token 自动刷新
- provider.sh 已支持 `data_user` 参数读不同 /data/user/{N}
