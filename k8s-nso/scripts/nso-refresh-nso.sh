#!/bin/bash
# 唤醒 + 处理 MIUI 双开选择窗 + 进鱿鱼圈3 刷新 gtoken
# 用法: refresh-nso.sh [device] [tap_x] [tap_y] [android_user]
DEV="${1:-99e0fc6d}"
TX="${2:-286}"; TY="${3:-1076}"
AUSER="${4:-0}"
ADB="/root/platform-tools/adb -s $DEV"
S(){ $ADB shell su -c "$1" >/dev/null 2>&1; }
# MIUI 双开选择窗: app1(左,账号1/user0)=x295, app2(右,账号2/双开)=x784, y=1854
RX=295; [ "$AUSER" != "0" ] && RX=784
S "input keyevent KEYCODE_WAKEUP"
sleep 1
S "input swipe 540 1900 540 700"   # 解锁(无密码)
S "input keyevent KEYCODE_HOME"
sleep 1
S "am start -n com.nintendo.znca/com.nintendo.coral.ui.boot.BootActivity"
sleep 4
# 双开选择窗弹出 -> 点对应账号
FOCUS=$($ADB shell dumpsys window 2>/dev/null | grep -o "ResolverActivity")
if [ -n "$FOCUS" ]; then S "input tap $RX 1854"; sleep 7; fi
# 确认进了 NSO 再点鱿鱼圈3
U=$($ADB shell dumpsys window 2>/dev/null | grep -oE "u[0-9]+ com.nintendo.znca/[^ }]*top" | head -1)
echo "into NSO: $U"
S "input tap $TX $TY"
sleep 13
S "input keyevent KEYCODE_HOME"
echo "refresh done (user=$AUSER picked x=$RX)"
