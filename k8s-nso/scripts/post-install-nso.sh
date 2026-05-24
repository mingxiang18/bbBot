#!/usr/bin/env bash
# post-install-nso.sh — NSO 装好后的一次性配置(redroid 重启后需要重跑)
# 1) disable FirebaseMessagingService 避免 startForegroundService crash
# 2) 设 Android 系统 HTTP 代理走 worker clash(让 NSO WebView 能连 accounts.nintendo.com)
# 3) 启动 NSO 到 LoginActivity
#
# 用法:bash /root/k8s-nso/scripts/post-install-nso.sh
#
# 注意:env 可覆盖
#   CLASH_HOST  默认 10.8.0.26(worker K8s overlay IP)
#   CLASH_PORT  默认 7890(clash mixed-port,允许 LAN)
set -euo pipefail

NS=nso-tokens
CLASH_HOST="${CLASH_HOST:-10.8.0.26}"
CLASH_PORT="${CLASH_PORT:-7890}"

NXAPI_POD=$(kubectl -n "$NS" get pod -l app=nxapi-znca-api -o jsonpath='{.items[0].metadata.name}')

run_adb() {
  kubectl -n "$NS" exec "$NXAPI_POD" -- adb -s redroid:5555 "$@"
}

echo "[post-install] 1/4 adb root + connect"
kubectl -n "$NS" exec "$NXAPI_POD" -- adb connect redroid:5555 >/dev/null
run_adb root >/dev/null 2>&1 || true
sleep 1
kubectl -n "$NS" exec "$NXAPI_POD" -- adb connect redroid:5555 >/dev/null

echo "[post-install] 2/4 disable FirebaseMessagingService(redroid 无 GMS 时启动会触发 5s ANR crash)"
run_adb shell am force-stop com.nintendo.znca
run_adb shell pm enable  com.nintendo.znca/com.google.firebase.provider.FirebaseInitProvider 2>&1 | head -1
run_adb shell pm disable com.nintendo.znca/com.google.firebase.messaging.FirebaseMessagingService 2>&1 | head -1

echo "[post-install] 3/4 设 Android 系统 HTTP 代理 $CLASH_HOST:$CLASH_PORT(走 worker clash)"
run_adb shell settings put global http_proxy "$CLASH_HOST:$CLASH_PORT"
PROXY=$(run_adb shell settings get global http_proxy | tr -d '\r')
echo "  active proxy: $PROXY"

echo "[post-install] 4/4 launch NSO BootActivity"
run_adb shell am start -n com.nintendo.znca/com.nintendo.coral.ui.boot.BootActivity 2>&1 | head -2

sleep 5
echo
echo "===状态汇总==="
echo "NSO pid:  $(run_adb shell pidof com.nintendo.znca | tr -d '\r')"
echo "top:      $(run_adb shell dumpsys window | grep mCurrentFocus | head -1 | tr -d '\r')"
echo
echo "===下一步==="
echo "Mac 上双击 ~/Desktop/launch-redroid-scrcpy.command 弹 scrcpy 窗口完成 Nintendo 登录"
echo "或:cd <bbBot>/k8s-nso/scripts && ./mac-launch-scrcpy.sh"
