#!/bin/bash
# GitHub Release 发布脚本（在本项目根目录运行）
# 用法：bash release.sh <版本号>   例：bash release.sh v0.7
# 前提：release-notes.md 已写好；APK 已构建（app/build/outputs/apk/debug/app-debug.apk）
# 凭据取自 Git Credential Manager（github.com），无需 gh CLI。
set -u
cd "$(dirname "$0")"

VER="${1:?用法: bash release.sh <版本号 如 v0.7>}"
REPO="a435630144/OneUILauncher"
APK_SRC="app/build/outputs/apk/debug/app-debug.apk"
BODY_FILE="D:/AndroidDevelopment/release-body.json"

TOKEN=$(printf "protocol=https\nhost=github.com\n\n" | git credential fill 2>/dev/null | grep '^password=' | cut -d= -f2-)
[ -z "$TOKEN" ] && { echo "ERROR: 无 GitHub 凭据"; exit 1; }
[ -f "$APK_SRC" ] || { echo "ERROR: 先构建 ./gradlew assembleDebug"; exit 1; }
[ -f release-notes.md ] || { echo "ERROR: 缺 release-notes.md"; exit 1; }

echo "== 1/4 推送 tag $VER =="
git tag -f "$VER" >/dev/null
for i in $(seq 1 8); do
  git push origin "$VER" 2>&1 | tail -1 | grep -q "$VER" && { echo "tag 已推送"; break; } || { echo "重试 $i"; sleep 6; }
done

echo "== 2/4 创建 Release（JSON 走文件，避开 MSYS 参数转换）=="
node -e "const fs=require('fs');fs.writeFileSync('$BODY_FILE'.replaceAll('/','\\\\'), JSON.stringify({tag_name:'$VER',name:'OneUI 桌面 $VER',body:fs.readFileSync('release-notes.md','utf8'),draft:false,prerelease:false}))"
CREATED=""
for i in $(seq 1 8); do
  CREATED=$(curl -sL --fail --connect-timeout 20 -X POST \
    -H "Authorization: token $TOKEN" -H "Content-Type: application/json" \
    --data-binary @"$BODY_FILE" "https://api.github.com/repos/$REPO/releases")
  RID=$(echo "$CREATED" | node -e "let d='';process.stdin.on('data',c=>d+=c).on('end',()=>{try{console.log(JSON.parse(d).id)}catch(e){console.log('')}})")
  [ -n "$RID" ] && [ "$RID" != "" ] && { echo "release 已创建 id=$RID"; break; } || { echo "重试 $i: $(echo "$CREATED" | head -c 120)"; sleep 8; }
done
[ -z "$RID" ] && { echo "ERROR: 创建失败"; exit 1; }

echo "== 3/4 上传 APK（uploads 域必须用数字 ID，不能用 tag 名）=="
ASSET="OneUiDesktop-${VER}-debug.apk"
for i in $(seq 1 8); do
  RESP=$(curl -sL --fail --connect-timeout 30 -X POST \
    -H "Authorization: token $TOKEN" -H "Content-Type: application/vnd.android.package-archive" \
    --data-binary @"$APK_SRC" "https://uploads.github.com/repos/$REPO/releases/$RID/assets?name=$ASSET")
  echo "$RESP" | grep -q '"state": "uploaded"' && { echo "APK 已上传"; break; } || { echo "重试 $i"; sleep 8; }
done

echo "== 4/4 验证 =="
curl -sL --fail -H "Authorization: token $TOKEN" "https://api.github.com/repos/$REPO/releases/tags/$VER" | \
  node -e "let d='';process.stdin.on('data',c=>d+=c).on('end',()=>{const r=JSON.parse(d);console.log('Release:',r.name);console.log('URL   :',r.html_url);(r.assets||[]).forEach(a=>console.log('Asset :',a.name,(a.size/1048576).toFixed(1)+'MB'))})"
echo "完成"
