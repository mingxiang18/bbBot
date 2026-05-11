#!/usr/bin/env bash
# verify.sh — 全链路验证,在 master 上跑
set -uo pipefail

NS=nso-tokens
NXAPI_NODEPORT=30445
WORKER_IP_LAN=192.168.50.227

ok()   { echo -e "\033[32m[OK]\033[0m $*"; }
fail() { echo -e "\033[31m[FAIL]\033[0m $*"; }

echo "=== 1. K8s Pods ==="
kubectl -n "$NS" get pods -o wide
echo

NXAPI_POD=$(kubectl -n "$NS" get pod -l app=nxapi-znca-api -o jsonpath='{.items[0].metadata.name}' 2>/dev/null || true)
REDROID_POD=$(kubectl -n "$NS" get pod -l app=redroid -o jsonpath='{.items[0].metadata.name}' 2>/dev/null || true)

# 我们用 nxapi pod 里的 adb 工具操作 redroid,因为 redroid 容器内部没有标准 sh/cat 等
adb_redroid() {
  kubectl -n "$NS" exec "$NXAPI_POD" -- adb -s redroid:5555 "$@" 2>&1
}

echo "=== 2. redroid Pod 状态 ==="
if [ -z "$REDROID_POD" ]; then fail "redroid Pod 未创建"; else ok "redroid Pod: $REDROID_POD"; fi

echo "=== 3. Android boot 完成 ==="
BOOT=$(adb_redroid shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')
if [ "$BOOT" = "1" ]; then ok "sys.boot_completed=1"; else fail "Android 未启动完成 (boot=$BOOT)"; fi

echo "=== 4. frida-server 状态 ==="
FRIDA_PID=$(adb_redroid shell pgrep -f frida-server 2>/dev/null | tr -d '\r')
if [ -n "$FRIDA_PID" ]; then
  ok "frida-server 进程在跑 (pid=$FRIDA_PID)"
  adb_redroid shell /data/local/tmp/frida-server --version 2>/dev/null | tr -d '\r' | xargs -I{} echo "      version: {}"
else
  fail "frida-server 未运行 — 跑: bash /root/k8s-nso/scripts/install-frida.sh"
fi

echo "=== 5. NSO app 安装状态 ==="
NSO=$(adb_redroid shell pm list packages 2>/dev/null | grep com.nintendo.znca | tr -d '\r')
if [ -n "$NSO" ]; then
  ok "NSO 已装: $NSO"
  NSO_VER=$(adb_redroid shell dumpsys package com.nintendo.znca 2>/dev/null | grep "versionName=" | head -1 | tr -d '\r')
  echo "      $NSO_VER"
else
  fail "NSO 未安装 — 跑: bash /root/k8s-nso/scripts/install-nso.sh /path/to/NSO.apk"
fi

echo "=== 6. nxapi-znca-api HTTP /api/znca/config ==="
HTTP_CODE=$(curl -s --max-time 5 -o /tmp/_nxapi_resp -w "%{http_code}" "http://${WORKER_IP_LAN}:${NXAPI_NODEPORT}/api/znca/config" 2>/dev/null || echo "000")
case "$HTTP_CODE" in
  200) ok "config 端点 200"; cat /tmp/_nxapi_resp | head -c 500; echo ;;
  503) fail "config 503 — frida attach 未完成(通常因为 NSO 未装或未登录)" ;;
  000) fail "无法连接 NodePort http://${WORKER_IP_LAN}:${NXAPI_NODEPORT}" ;;
  *)   fail "HTTP $HTTP_CODE";  cat /tmp/_nxapi_resp | head -c 500; echo ;;
esac

echo "=== 7. 最近 nxapi 日志(最后 6 行) ==="
kubectl -n "$NS" logs deployment/nxapi-znca-api --tail=6 2>&1 | sed 's/^/      /'
