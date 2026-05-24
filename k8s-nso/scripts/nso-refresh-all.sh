#!/bin/bash
# 刷新所有账号的 gtoken。每个 user 对应一个 NSO 实例。
# user 0=账号1(主), 999=账号2(MIUI双开)。加账号就在 USERS 里加 user id。
USERS="0 999"
for u in $USERS; do
  echo "--- refresh user $u ---"
  bash /root/k8s-nso-token/refresh-nso.sh 99e0fc6d 286 1076 "$u"
done
