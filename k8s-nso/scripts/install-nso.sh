#!/usr/bin/env bash
# install-nso.sh — 把 NSO app apk 推到 redroid 容器并安装
# 用法:bash /root/k8s-nso/scripts/install-nso.sh /path/to/NintendoSwitchOnline.apk
# NSO apk 自己提供 — Google Play Store 下载或 APKMirror 等可信源
set -euo pipefail

NS=nso-tokens
APK="${1:-}"

if [ -z "$APK" ] || [ ! -f "$APK" ]; then
  cat <<EOF
Usage: $0 /path/to/NintendoSwitchOnline.apk

NSO apk 在 redroid 内通过 sideload 安装(无 Google Play)。获取方式:
  - 用一台真 Android 手机从 Play Store 安装 NSO,用 apk-extractor 导出 apk
  - 或者从 APKMirror / APKPure 等仓库下载官方签名 apk
    包名:com.nintendo.znca
EOF
  exit 1
fi

REDROID_POD=$(kubectl -n "$NS" get pod -l app=redroid -o jsonpath='{.items[0].metadata.name}')
echo "[install-nso] 推 apk 到 $REDROID_POD"
kubectl -n "$NS" cp "$APK" "${REDROID_POD}:/data/local/tmp/nso.apk"
echo "[install-nso] pm install"
kubectl -n "$NS" exec "$REDROID_POD" -- sh -c '
  pm install -r /data/local/tmp/nso.apk 2>&1
  rm -f /data/local/tmp/nso.apk
  pm list packages | grep com.nintendo.znca
'
echo "[install-nso] 完成 — 现在需要打开 NSO app 登录你的 Nintendo 账号"
echo
echo "登录方式 1(推荐):scrcpy"
echo "  在 Mac 上跑:scrcpy --tcpip=192.168.50.227:30555"
echo "  会弹出 redroid 画面,在画面里打开 NSO app 登录"
echo
echo "登录方式 2:通过 adb am 启动"
echo "  kubectl -n $NS exec $REDROID_POD -- am start -n com.nintendo.znca/.MainActivity"
echo "  然后用 scrcpy 看屏幕"
