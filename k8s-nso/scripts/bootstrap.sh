#!/usr/bin/env bash
# bootstrap.sh — 一键拉起 NSO token 链路
# 在 master 上执行:bash /root/k8s-nso/scripts/bootstrap.sh
set -euo pipefail

HERE="$(cd "$(dirname "$0")"/.. && pwd)"
MANIFESTS="$HERE/manifests"

echo "[bootstrap] 1/4 worker 侧前置检查(binder 模块/数据目录)"
WORKER_HOST="${WORKER_HOST:-misu-maco}"
WORKER_SSH="${WORKER_SSH:-root@192.168.50.227}"
ssh -o StrictHostKeyChecking=accept-new "$WORKER_SSH" '
  set -e
  modprobe binder_linux num_binder_devices=128 2>/dev/null || true
  if [ ! -f /etc/modules-load.d/binder.conf ]; then
    echo "binder_linux num_binder_devices=128" > /etc/modules-load.d/binder.conf
  fi
  mkdir -p /dev/binderfs
  mountpoint -q /dev/binderfs || mount -t binder binder /dev/binderfs
  grep -q "binder /dev/binderfs binder" /etc/fstab || echo "binder /dev/binderfs binder defaults 0 0" >> /etc/fstab
  mkdir -p /data/redroid
  lsmod | grep -E "binder" || (echo "ERROR: binder module not loaded" >&2; exit 1)
  ls /dev/binderfs/binder-control >/dev/null || (echo "ERROR: binderfs not mounted" >&2; exit 1)
  echo "worker prerequisites OK"
' || { echo "[bootstrap] worker 前置失败,放弃 apply" >&2; exit 1; }

echo "[bootstrap] 2/4 apply manifests"
kubectl apply -f "$MANIFESTS/00-namespace.yaml"
kubectl apply -f "$MANIFESTS/10-redroid.yaml"
kubectl apply -f "$MANIFESTS/20-nxapi-znca-api.yaml"

echo "[bootstrap] 3/4 等待 redroid Pod ready(最长 5 分钟)"
kubectl -n nso-tokens rollout status deployment/redroid --timeout=300s

echo "[bootstrap] 4/4 等待 nxapi-znca-api Pod ready(最长 6 分钟,首跑要装 npm 包)"
kubectl -n nso-tokens rollout status deployment/nxapi-znca-api --timeout=360s || true

echo
echo "===状态汇总==="
kubectl -n nso-tokens get all -o wide
echo
echo "===下一步==="
echo "1) 装 NSO apk + 登录:bash $HERE/scripts/install-nso.sh /path/to/NintendoSwitchOnline.apk"
echo "2) 装 frida-server:    bash $HERE/scripts/install-frida.sh"
echo "3) 验证全链路:         bash $HERE/scripts/verify.sh"
echo "4) bbBot 接入(application-local.yml):"
echo "   nso:"
echo "     fGenerationApi: http://192.168.50.227:30445/api/znca/f"
