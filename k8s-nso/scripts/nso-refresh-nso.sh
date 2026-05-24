#!/bin/bash
# 唤醒 NSO + 进鱿鱼圈3 触发 GetWebServiceToken 刷新 gtoken cookie
# 用法: refresh-nso.sh [device] [tap_x] [tap_y] [android_user]
#   android_user: 0=主, 999=MIUI应用双开, 其它=手机分身空间
DEV="${1:-99e0fc6d}"
TX="${2:-286}"; TY="${3:-1076}"
AUSER="${4:-0}"
ADB="/root/platform-tools/adb -s $DEV"
S(){ $ADB shell su -c "$1" >/dev/null 2>&1; }
UFLAG=""; [ "$AUSER" != "0" ] && UFLAG="--user $AUSER"
S "input keyevent KEYCODE_WAKEUP"
sleep 1
S "input swipe 540 1900 540 700"
S "input keyevent KEYCODE_HOME"
sleep 1
S "am start $UFLAG -n com.nintendo.znca/com.nintendo.coral.ui.boot.BootActivity"
sleep 9
S "input tap $TX $TY"
sleep 13
S "input keyevent KEYCODE_HOME"
echo "refresh done (device=$DEV user=$AUSER)"
