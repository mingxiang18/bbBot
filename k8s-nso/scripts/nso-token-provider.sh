#!/bin/bash
# nso-token-provider: 读手机 NSO cookie 拿 gtoken + 算 bulletToken,输出 JSON
# 用法: provider.sh [device_serial] [data_user]
set -uo pipefail
ADB_BIN=/root/platform-tools/adb
DEV="${1:-99e0fc6d}"
DUSER="${2:-0}"   # 多账号时不同 /data/user/{N}
ADB="$ADB_BIN -s $DEV"
export https_proxy=http://127.0.0.1:7890 http_proxy=http://127.0.0.1:7890

COOKIE_PATH="/data/user/$DUSER/com.nintendo.znca/app_webview/Default/Cookies"
TMP=/tmp/nso-ck-$DUSER.db

# 1. 读 cookie
$ADB shell su -c "cp $COOKIE_PATH /sdcard/Download/nso-ck-$DUSER.db" >/dev/null 2>&1
$ADB pull /sdcard/Download/nso-ck-$DUSER.db $TMP >/dev/null 2>&1
GT=$(python3 -c "
import sqlite3,sys
try:
    c=sqlite3.connect('$TMP')
    r=c.execute(\"select value from cookies where name='_gtoken' and host_key like '%av5ja%' order by creation_utc desc limit 1\").fetchone()
    print(r[0] if r else '')
except: print('')
")
if [ -z "$GT" ]; then echo '{"error":"no_gtoken_in_cookie"}'; exit 1; fi

# 2. web view version
JS=$(curl -s --max-time 15 "https://api.lp1.av5ja.srv.nintendo.net/" | grep -a -o 'main\.[0-9a-f]*\.js' | head -1)
NSOVER=$(curl -s --max-time 15 "https://api.lp1.av5ja.srv.nintendo.net/static/js/$JS" | python3 -c "
import sys,re
t=sys.stdin.read()
m=re.search(r'([0-9a-f]{40})[\s\S]{0,300}?revision_info_not_set[\s\S]{0,300}?=\`(\d+\.\d+\.\d+)-',t)
print((m.group(2)+'-'+m.group(1)[:8]) if m else '')
")
[ -z "$NSOVER" ] && NSOVER="10.0.0-dfefd0af"

# 3. bulletToken
RESP=$(curl -s --max-time 25 -X POST \
  -H "Content-Type: application/json" -H "Content-Length: 0" \
  -H "Accept-Language: en-US" \
  -H "User-Agent: Mozilla/4.0 (compatible; MSIE 5.5; Windows NT)" \
  -H "X-Web-View-Ver: $NSOVER" -H "X-NACOUNTRY: US" -H "Accept: */*" \
  -H "Origin: https://api.lp1.av5ja.srv.nintendo.net" \
  -H "X-Requested-With: com.nintendo.znca" \
  -b "_dnt=1; _gtoken=$GT" \
  "https://api.lp1.av5ja.srv.nintendo.net/api/bullet_tokens")
BT=$(echo "$RESP" | python3 -c "import sys,json;
try: print(json.load(sys.stdin).get('bulletToken',''))
except: print('')")
if [ -z "$BT" ]; then echo "{\"error\":\"bullet_token_failed\",\"resp\":$(echo "$RESP"|python3 -c 'import sys,json;print(json.dumps(sys.stdin.read()[:200]))')}"; exit 1; fi

# 4. 输出 JSON
python3 -c "import json;print(json.dumps({'gtoken':'$GT','bulletToken':'$BT','webViewVer':'$NSOVER','device':'$DEV','dataUser':'$DUSER'}))"
