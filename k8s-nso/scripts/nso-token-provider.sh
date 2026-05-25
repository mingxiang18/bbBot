#!/bin/bash
# 读手机 NSO cookie 拿 gtoken + 算 bulletToken,输出 JSON。
# 按需刷新:bulletToken 拿不到(gtoken 过期)时自动 refresh 进鱿鱼圈3 重拿。
set -uo pipefail
ADB_BIN=/root/platform-tools/adb
DEV="${1:-${NSO_DEVICE:-99e0fc6d}}"; DUSER="${2:-0}"
ADB="$ADB_BIN -s $DEV"
PROXY="${NSO_PROXY:-http://127.0.0.1:7890}"
export https_proxy="$PROXY" http_proxy="$PROXY"
CK="/data/user/$DUSER/com.nintendo.znca/app_webview/Default/Cookies"
TMP=/tmp/nso-ck-$DUSER.db
read_gt(){
  $ADB shell su -c "cp $CK /sdcard/Download/nso-ck-$DUSER.db" >/dev/null 2>&1
  $ADB pull /sdcard/Download/nso-ck-$DUSER.db $TMP >/dev/null 2>&1
  python3 -c "import sqlite3
try:
 c=sqlite3.connect('$TMP');r=c.execute(\"select value from cookies where name='_gtoken' and host_key like '%av5ja%' order by creation_utc desc limit 1\").fetchone();print(r[0] if r else '')
except: print('')"
}
get_bullet(){
  curl -s --max-time 25 -X POST -H "Content-Type: application/json" -H "Content-Length: 0" \
    -H "Accept-Language: en-US" -H "User-Agent: Mozilla/4.0 (compatible; MSIE 5.5; Windows NT)" \
    -H "X-Web-View-Ver: $2" -H "X-NACOUNTRY: US" -H "Accept: */*" \
    -H "Origin: https://api.lp1.av5ja.srv.nintendo.net" -H "X-Requested-With: com.nintendo.znca" \
    -b "_dnt=1; _gtoken=$1" "https://api.lp1.av5ja.srv.nintendo.net/api/bullet_tokens" \
    | python3 -c "import sys,json
try: print(json.load(sys.stdin).get('bulletToken',''))
except: print('')"
}
JS=$(curl -s --max-time 15 "https://api.lp1.av5ja.srv.nintendo.net/" | grep -a -o 'main\.[0-9a-f]*\.js' | head -1)
NSOVER=$(curl -s --max-time 15 "https://api.lp1.av5ja.srv.nintendo.net/static/js/$JS" | python3 -c "
import sys,re;t=sys.stdin.read();m=re.search(r'([0-9a-f]{40})[\s\S]{0,300}?revision_info_not_set[\s\S]{0,300}?=\`(\d+\.\d+\.\d+)-',t);print((m.group(2)+'-'+m.group(1)[:8]) if m else '')")
[ -z "$NSOVER" ] && NSOVER="10.0.0-dfefd0af"
GT=$(read_gt); BT=""
[ -n "$GT" ] && BT=$(get_bullet "$GT" "$NSOVER")
if [ -z "$BT" ]; then
  # gtoken 过期/缺失:刷新(refresh 内部已轮询确认 gtoken 真更新),再多试几次读 cookie+换 bullet
  # (灰屏/慢加载时 cookie 落地与 bullet 服务端生效都可能略有延迟,单次易踩空)
  bash /root/k8s-nso-token/refresh-nso.sh "$DEV" 286 1076 "$DUSER" >/dev/null 2>&1
  for try in 1 2 3; do
    GT=$(read_gt)
    [ -n "$GT" ] && BT=$(get_bullet "$GT" "$NSOVER")
    [ -n "$BT" ] && break
    sleep 5
  done
fi
if [ -z "$GT" ] || [ -z "$BT" ]; then echo "{\"error\":\"token_unavailable\",\"dataUser\":\"$DUSER\"}"; exit 1; fi
python3 -c "import json;print(json.dumps({'gtoken':'$GT','bulletToken':'$BT','webViewVer':'$NSOVER','device':'$DEV','dataUser':'$DUSER'}))"
