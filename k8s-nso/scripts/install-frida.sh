#!/usr/bin/env bash
# install-frida.sh — 把 frida-server 二进制下放到 redroid 容器并启动
# 默认架构 x86_64;通过环境变量可覆盖。
# 用法:bash /root/k8s-nso/scripts/install-frida.sh
set -euo pipefail

NS=nso-tokens
FRIDA_VERSION="${FRIDA_VERSION:-17.9.8}"
FRIDA_ARCH="${FRIDA_ARCH:-x86_64}"   # arm64 / x86 / x86_64
FRIDA_URL="https://github.com/frida/frida/releases/download/${FRIDA_VERSION}/frida-server-${FRIDA_VERSION}-android-${FRIDA_ARCH}.xz"
BIN_DIR="$(cd "$(dirname "$0")"/.. && pwd)/bin"
mkdir -p "$BIN_DIR"

FRIDA_BIN="$BIN_DIR/frida-server-${FRIDA_VERSION}-android-${FRIDA_ARCH}"

if [ ! -f "$FRIDA_BIN" ]; then
  echo "[install-frida] 下载 $FRIDA_URL"
  curl -fL "$FRIDA_URL" -o "${FRIDA_BIN}.xz"
  xz -d -f "${FRIDA_BIN}.xz"
  chmod +x "$FRIDA_BIN"
fi

REDROID_POD=$(kubectl -n "$NS" get pod -l app=redroid -o jsonpath='{.items[0].metadata.name}')
echo "[install-frida] 通过 ADB(NodePort 30555)推 frida-server 到 redroid"

# 需要 adb 工具(本机/master),如果没有就用一个临时 K8s Pod 跑 adb push
WORKER_LAN="${WORKER_LAN:-192.168.50.227}"
ADB_TARGET="${ADB_TARGET:-${WORKER_LAN}:30555}"

if command -v adb >/dev/null 2>&1; then
  adb kill-server 2>/dev/null || true
  adb connect "$ADB_TARGET"
  adb -s "$ADB_TARGET" push "$FRIDA_BIN" /data/local/tmp/frida-server
  adb -s "$ADB_TARGET" shell '
    chmod 755 /data/local/tmp/frida-server
    pkill -f frida-server 2>/dev/null || true
    nohup /data/local/tmp/frida-server >/data/local/tmp/frida-server.log 2>&1 &
    sleep 1
    pgrep -f frida-server && echo "frida-server started" || echo "WARN: not detected"
  '
else
  echo "[install-frida] master 没装 adb,跑一次性 K8s pod 来推"
  # base64 编码 frida-server,通过 ConfigMap-less 方式注入到临时 pod
  # 但 K8s 一次 transfer 上限有限,frida-server 30MB,base64 后 ~40MB 用 args 不行
  # 改方案:HTTP file server in nso-tokens namespace serving frida-server
  # 简化:让 redroid pod 自己拉(redroid 有 wget/curl 吗?Android base 通常无)
  # 最稳:让 nxapi pod 当中转,它已经有 adb + 可访问 frida-server.bin
  NXAPI_POD=$(kubectl -n "$NS" get pod -l app=nxapi-znca-api -o jsonpath='{.items[0].metadata.name}')
  kubectl -n "$NS" cp "$FRIDA_BIN" "${NXAPI_POD}:/tmp/frida-server" -c nxapi
  kubectl -n "$NS" exec "$NXAPI_POD" -c nxapi -- bash -c "
    adb connect redroid:5555 >/dev/null
    adb -s redroid:5555 push /tmp/frida-server /data/local/tmp/frida-server
    adb -s redroid:5555 shell 'chmod 755 /data/local/tmp/frida-server'
    adb -s redroid:5555 shell 'pkill -f frida-server 2>/dev/null; nohup /data/local/tmp/frida-server >/data/local/tmp/frida-server.log 2>&1 &'
    sleep 1
    adb -s redroid:5555 shell 'pgrep -f frida-server && echo OK || echo NOT_RUNNING'
  "
fi
echo "[install-frida] 完成"
