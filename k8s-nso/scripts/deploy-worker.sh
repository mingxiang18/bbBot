#!/usr/bin/env bash
# 把 NSO cookie token 服务部署到 worker(插着真机的那台机器)。
# 幂等:重复跑会覆盖脚本 + 重启 systemd。在有本仓库 + 能 SSH 到 worker 的机器上运行。
#
# 用法:
#   ./deploy-worker.sh                      # 用下面的默认值
#   WORKER_SSH=root@192.168.50.227 SSH_KEY=~/.ssh/id_ed25519 ./deploy-worker.sh
#   NSO_DEVICE=abcd1234 NSO_USERS="0 999 1010" ./deploy-worker.sh   # 换手机/加账号
#
# 前置(worker 上需手动备好,本脚本只检查不安装):
#   - /root/platform-tools/adb,且 `adb devices` 能看到手机(已 root、NSO 已登录)
#   - python3
#   - clash 代理在 NSO_PROXY(访问 Nintendo,国内被墙)
set -euo pipefail

WORKER_SSH="${WORKER_SSH:-root@192.168.50.227}"
SSH_KEY="${SSH_KEY:-}"
SSH_PORT="${SSH_PORT:-22}"
REMOTE_DIR="${REMOTE_DIR:-/root/k8s-nso-token}"
NSO_DEVICE="${NSO_DEVICE:-99e0fc6d}"
NSO_USERS="${NSO_USERS:-0 999}"
NSO_PORT="${NSO_PORT:-18080}"
NSO_PROXY="${NSO_PROXY:-http://127.0.0.1:7890}"
FORCE="${FORCE:-0}"

SRC_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

SSH_OPTS=(-p "$SSH_PORT" -o StrictHostKeyChecking=accept-new)
SCP_OPTS=(-P "$SSH_PORT" -o StrictHostKeyChecking=accept-new)
if [ -n "$SSH_KEY" ]; then SSH_OPTS+=(-i "$SSH_KEY"); SCP_OPTS+=(-i "$SSH_KEY"); fi
RSH() { ssh "${SSH_OPTS[@]}" "$WORKER_SSH" "$@"; }
PUT() { scp "${SCP_OPTS[@]}" "$1" "$WORKER_SSH:$2"; }

echo "==> 部署目标: $WORKER_SSH:$REMOTE_DIR  device=$NSO_DEVICE  users=[$NSO_USERS]  port=$NSO_PORT"

# 校验源文件齐全
for f in nso-token-provider.sh nso-token-server.py nso-refresh-nso.sh nso-refresh-all.sh; do
  [ -f "$SRC_DIR/$f" ] || { echo "缺少源文件 $SRC_DIR/$f"; exit 1; }
done

# --- 前置检查(失败仅警告,FORCE=1 可跳过阻断) ---
echo "==> 检查 worker 前置依赖"
CHECK=$(RSH bash -s <<EOF || true
command -v python3 >/dev/null || echo "MISSING:python3"
[ -x /root/platform-tools/adb ] || echo "MISSING:adb(/root/platform-tools/adb)"
/root/platform-tools/adb -s "$NSO_DEVICE" get-state >/dev/null 2>&1 || echo "MISSING:device($NSO_DEVICE 未连接/未授权)"
EOF
)
if [ -n "$CHECK" ]; then
  echo "!! 前置检查有问题:"; echo "$CHECK" | sed 's/^/   - /'
  if [ "$FORCE" != "1" ]; then echo "   修好后重跑,或 FORCE=1 强制继续。"; exit 1; fi
  echo "   FORCE=1,继续部署。"
fi

# --- 拷贝脚本(去掉 nso- 前缀,worker 上叫 provider.sh 等) ---
echo "==> 同步脚本到 $REMOTE_DIR"
RSH "mkdir -p '$REMOTE_DIR'"
PUT "$SRC_DIR/nso-token-provider.sh" "$REMOTE_DIR/provider.sh"
PUT "$SRC_DIR/nso-token-server.py"   "$REMOTE_DIR/token-server.py"
PUT "$SRC_DIR/nso-refresh-nso.sh"    "$REMOTE_DIR/refresh-nso.sh"
PUT "$SRC_DIR/nso-refresh-all.sh"    "$REMOTE_DIR/refresh-all.sh"
RSH "chmod +x '$REMOTE_DIR'/provider.sh '$REMOTE_DIR'/refresh-nso.sh '$REMOTE_DIR'/refresh-all.sh"

# --- 写 systemd units(把 device/users/port/proxy 注入 Environment) ---
echo "==> 写 systemd units 并启用"
RSH bash -s <<EOF
set -e
cat > /etc/systemd/system/nso-token.service <<UNIT
[Unit]
Description=NSO Token Server (reads phone NSO cookie, serves gtoken+bulletToken)
After=network.target

[Service]
Type=simple
ExecStart=/usr/bin/python3 $REMOTE_DIR/token-server.py $NSO_PORT
Restart=always
RestartSec=5
Environment=HOME=/root
Environment=NSO_DEVICE=$NSO_DEVICE
Environment=NSO_PROXY=$NSO_PROXY

[Install]
WantedBy=multi-user.target
UNIT

cat > /etc/systemd/system/nso-refresh.service <<UNIT
[Unit]
Description=Refresh NSO gtoken cookie (open SplatNet to trigger GetWebServiceToken)

[Service]
Type=oneshot
Environment=NSO_DEVICE=$NSO_DEVICE
Environment=NSO_USERS=$NSO_USERS
ExecStart=/bin/bash $REMOTE_DIR/refresh-all.sh
UNIT

cat > /etc/systemd/system/nso-refresh.timer <<UNIT
[Unit]
Description=Periodic NSO gtoken refresh (every 90min, gtoken ~2h TTL)

[Timer]
OnBootSec=3min
OnUnitActiveSec=90min
Persistent=true

[Install]
WantedBy=timers.target
UNIT

systemctl daemon-reload
systemctl enable --now nso-token.service
systemctl enable --now nso-refresh.timer
systemctl restart nso-token.service
EOF

# --- 健康检查:取第一个账号的 token ---
FIRST_USER="$(echo "$NSO_USERS" | awk '{print $1}')"
echo "==> 健康检查 GET /token?dataUser=$FIRST_USER"
sleep 2
HEALTH=$(RSH "curl -s -m 100 'http://127.0.0.1:$NSO_PORT/token?dataUser=$FIRST_USER'" || true)
if echo "$HEALTH" | grep -q '"gtoken"' && echo "$HEALTH" | grep -q '"bulletToken"'; then
  GT_LEN=$(echo "$HEALTH" | python3 -c 'import sys,json;print(len(json.load(sys.stdin)["gtoken"]))' 2>/dev/null || echo "?")
  BT_LEN=$(echo "$HEALTH" | python3 -c 'import sys,json;print(len(json.load(sys.stdin)["bulletToken"]))' 2>/dev/null || echo "?")
  echo "==> OK: gtoken=$GT_LEN bulletToken=$BT_LEN 字符,token 服务可用"
else
  echo "!! 健康检查未拿到 token,返回: $HEALTH"
  echo "   排查: ssh $WORKER_SSH 'journalctl -u nso-token -n 50 --no-pager'"
  echo "   手动跑: ssh $WORKER_SSH 'bash $REMOTE_DIR/provider.sh $NSO_DEVICE $FIRST_USER'"
  exit 1
fi

echo "==> 部署完成。bbBot 侧 nso.tokenProviderUrl 指向 http://<worker_ip>:$NSO_PORT/token 即可。"
