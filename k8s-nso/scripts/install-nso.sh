#!/usr/bin/env bash
# install-nso.sh — 把 NSO app 安装到 redroid
# 支持两种输入:
#   * 单个 .apk  → pm install -r
#   * APKMirror .apkm / split bundle (.apkm / .xapk / .apks) → adb install-multiple
#
# 用法:bash /root/k8s-nso/scripts/install-nso.sh /path/to/nso.{apk,apkm,xapk}
set -euo pipefail

NS=nso-tokens
INPUT="${1:-}"

if [ -z "$INPUT" ] || [ ! -f "$INPUT" ]; then
  cat <<EOF
Usage: $0 /path/to/nso.{apk,apkm,xapk}

NSO apk 获取(任选一,放到 Mac 上):
  - APKMirror BUNDLE (.apkm,本脚本自动处理):
    https://www.apkmirror.com/apk/nintendo-co-ltd/nintendo-switch-online/
  - APKPure / APKCombo 单 .apk:
    https://apkpure.com/nintendo-switch-online/com.nintendo.znca
EOF
  exit 1
fi

NXAPI_POD=$(kubectl -n "$NS" get pod -l app=nxapi-znca-api -o jsonpath='{.items[0].metadata.name}')
WORK=/tmp/nso-install
rm -rf "$WORK" && mkdir -p "$WORK"
cp "$INPUT" "$WORK/"
BASENAME=$(basename "$INPUT")
EXT="${BASENAME##*.}"

case "$EXT" in
  apk)
    echo "[install-nso] 单 APK,走 pm install"
    # stdin pipe 避免 kubectl cp 大文件截断
    kubectl -n "$NS" exec -i "$NXAPI_POD" -- bash -c "cat > /tmp/nso.apk" < "$WORK/$BASENAME"
    kubectl -n "$NS" exec "$NXAPI_POD" -- bash -c '
      adb connect redroid:5555 >/dev/null
      adb -s redroid:5555 push /tmp/nso.apk /data/local/tmp/nso.apk
      adb -s redroid:5555 shell pm install -r /data/local/tmp/nso.apk
      adb -s redroid:5555 shell rm /data/local/tmp/nso.apk
      adb -s redroid:5555 shell pm list packages | grep com.nintendo.znca
    '
    ;;
  apkm|xapk|apks|zip)
    echo "[install-nso] APK Bundle($EXT),解包后走 adb install-multiple"
    # .apkm/.xapk/.apks 都是标准 zip,解出来有多个 .apk
    cd "$WORK" && unzip -q "$BASENAME" -d unpacked
    APKS=( unpacked/*.apk )
    echo "[install-nso] bundle 内 APKs:"
    for a in "${APKS[@]}"; do echo "  $a ($(stat -c%s "$a" 2>/dev/null || stat -f%z "$a") bytes)"; done

    # stdin pipe 每个 split 到 nxapi pod
    REMOTE_LIST=()
    for a in "${APKS[@]}"; do
      NAME=$(basename "$a")
      kubectl -n "$NS" exec -i "$NXAPI_POD" -- bash -c "cat > /tmp/nso-$NAME" < "$a"
      REMOTE_LIST+=("/tmp/nso-$NAME")
    done

    # 推到 redroid /data/local/tmp + install-multiple
    kubectl -n "$NS" exec "$NXAPI_POD" -- bash -c "
      set -e
      adb connect redroid:5555 >/dev/null
      REMOTE_APKS=()
      for f in ${REMOTE_LIST[*]}; do
        REMOTE_NAME=\$(basename \$f)
        adb -s redroid:5555 push \$f /data/local/tmp/\$REMOTE_NAME >/dev/null
        REMOTE_APKS+=(\"/data/local/tmp/\$REMOTE_NAME\")
      done
      echo 'install-multiple started:'
      adb -s redroid:5555 shell pm install-create -r 2>&1
      adb -s redroid:5555 install-multiple -r ${REMOTE_LIST[*]/#\\/tmp\\/nso-/\\/tmp\\/nso-}
      adb -s redroid:5555 shell rm -f /data/local/tmp/nso-*.apk
      adb -s redroid:5555 shell pm list packages | grep com.nintendo.znca
    " || {
      # fallback:本地 adb 在 nxapi pod 内用 stream-based install-multiple
      echo "[install-nso] fallback: stream install-multiple"
      kubectl -n "$NS" exec "$NXAPI_POD" -- bash -c "
        adb connect redroid:5555 >/dev/null
        adb -s redroid:5555 install-multiple -r ${REMOTE_LIST[*]}
        adb -s redroid:5555 shell pm list packages | grep com.nintendo.znca
      "
    }
    ;;
  *)
    echo "ERROR: 不支持的扩展名 .$EXT" >&2
    echo "支持:.apk / .apkm / .xapk / .apks" >&2
    exit 1
    ;;
esac

echo
echo "[install-nso] 完成。下一步用 scrcpy 登录 Nintendo 账号:"
echo "  Mac:  brew install scrcpy && scrcpy --tcpip=192.168.50.227:30555"
echo "  打开 NSO app(图标可能写 'Nintendo Switch')→ 完成账号登录"
