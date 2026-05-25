#!/bin/bash
# 唤醒 + 处理 MIUI 双开选择窗 + 进鱿鱼圈3 刷新 gtoken。
# 关键:不再盲等后谎报 done,而是【轮询 gtoken cookie 的 creation_utc 是否真的变新】来确认
# 鱿鱼圈3 WebView 真加载成功;若一直不更新(灰屏卡死)则强杀 NSO 重进一次。
# 用法: refresh-nso.sh [device] [tap_x] [tap_y] [android_user]
DEV="${1:-${NSO_DEVICE:-99e0fc6d}}"
TX="${2:-286}"; TY="${3:-1076}"
AUSER="${4:-0}"
ADB="/root/platform-tools/adb -s $DEV"
S(){ $ADB shell su -c "$1" >/dev/null 2>&1; }
CK="/data/user/$AUSER/com.nintendo.znca/app_webview/Default/Cookies"
# MIUI 双开选择窗: app1(左,账号1/user0)=x295, app2(右,账号2/双开)=x784, y=1854
RX=295; [ "$AUSER" != "0" ] && RX=784

# 读 av5ja _gtoken 的 creation_utc(微秒);失败返回 0
gt_ctime(){
  S "cp $CK /sdcard/Download/ckchk-$AUSER.db"
  $ADB pull /sdcard/Download/ckchk-$AUSER.db /tmp/ckchk-$AUSER.db >/dev/null 2>&1
  python3 -c "import sqlite3
try:
 c=sqlite3.connect('/tmp/ckchk-$AUSER.db');r=c.execute(\"select max(creation_utc) from cookies where name='_gtoken' and host_key like '%av5ja%'\").fetchone();print(int(r[0]) if r and r[0] else 0)
except: print(0)"
}

enter_squid(){   # 唤醒解锁 + 启动 NSO + 处理双开窗 + 点鱿鱼圈3
  S "input keyevent KEYCODE_WAKEUP"; sleep 1
  S "input swipe 540 1900 540 700"; S "input keyevent KEYCODE_HOME"; sleep 1
  S "am start -n com.nintendo.znca/com.nintendo.coral.ui.boot.BootActivity"; sleep 4
  FOCUS=$($ADB shell dumpsys window 2>/dev/null | grep -o "ResolverActivity")
  [ -n "$FOCUS" ] && { S "input tap $RX 1854"; sleep 7; }
  U=$($ADB shell dumpsys window 2>/dev/null | grep -oE "u[0-9]+ com.nintendo.znca/[^ }]*top" | head -1)
  echo "into NSO: $U"
  S "input tap $TX $TY"
}

BASE=$(gt_ctime)
enter_squid
ok=0
# 轮询看 gtoken 是否真刷新(每轮 sleep4 + 读cookie≈3s ≈ 7s);鱿鱼圈3 经加速器加载常要 30~48s,
# 所以给足耐心;只有久久不更新(疑似真卡死灰屏)才强杀重进,避免误伤慢加载。
for i in $(seq 1 14); do
  sleep 4
  NOW=$(gt_ctime)
  if [ "${NOW:-0}" -gt "${BASE:-0}" ] 2>/dev/null; then ok=1; break; fi
  if [ "$i" = "9" ]; then   # ~60s 仍未刷新,才判定卡死 -> 强杀 NSO 重进
    echo "grey suspected (>60s no refresh), force-restart NSO (user=$AUSER)"
    S "am force-stop --user $AUSER com.nintendo.znca"
    sleep 2
    enter_squid
  fi
done
S "input keyevent KEYCODE_HOME"
echo "refresh user=$AUSER picked x=$RX gtoken_refreshed=$ok"
[ "$ok" = "1" ]
