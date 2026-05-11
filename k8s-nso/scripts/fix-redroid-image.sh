#!/usr/bin/env bash
# fix-redroid-image.sh
# 把 redroid 镜像里 /etc -> /system/etc(及其它指向绝对路径的根级 symlink)
# 改成相对 symlink,绕开 containerd 2.2.2 (Go 1.24) securejoin 严格路径校验。
# containerd issue #12683;fix 在 containerd 2.3.0(本 worker 是 2.2.2)。
#
# 用法:bash /tmp/fix-redroid-image.sh [src_tag] [dst_tag]
set -euo pipefail

SRC="${1:-docker.io/redroid/redroid:11.0.0-latest}"
DST="${2:-docker.io/redroid/redroid:11.0.0-fixed}"
WORK=/tmp/redroid-fix2
TARBALL="$WORK/redroid.tar"

rm -rf "$WORK" && mkdir -p "$WORK"
echo "[fix] 1/7 ctr image export $SRC -> $TARBALL"
ctr -n k8s.io image export --platform linux/amd64 "$TARBALL" "$SRC"

echo "[fix] 2/7 unpack OCI layout"
mkdir "$WORK/img" && tar -xf "$TARBALL" -C "$WORK/img"

# 找到最大的 blob(就是 rootfs layer)
ROOT_LAYER=$(ls -1S "$WORK/img/blobs/sha256/" | head -1)
ROOT_LAYER_PATH="$WORK/img/blobs/sha256/$ROOT_LAYER"
echo "[fix] 3/7 rootfs layer: $ROOT_LAYER  ($(stat -c%s "$ROOT_LAYER_PATH") bytes)"

echo "[fix] 4/7 extract layer & rewrite root-level absolute symlinks"
LDIR="$WORK/layer"; mkdir -p "$LDIR"
tar -xzf "$ROOT_LAYER_PATH" -C "$LDIR"

# 遍历 layer 根目录,发现所有指向绝对路径的 symlink,改为相对
FIXED=0
cd "$LDIR"
for entry in $(find . -maxdepth 1 -type l); do
  TGT=$(readlink "$entry")
  if [[ "$TGT" = /* ]]; then
    # 绝对路径 → 去掉前导 /,变成相对
    NEW="${TGT#/}"
    echo "  $entry  $TGT  ->  $NEW"
    rm -f "$entry"
    ln -s "$NEW" "$entry"
    FIXED=$((FIXED+1))
  fi
done
# bin 也是绝对 symlink → /system/bin,同样在根级别,find 已覆盖
# 但更深的目录里也可能有,比如 odm/* 都是 /vendor/odm/*。
# 对 nested symlink 也修一遍以保险:仅当 link 的 target 以 / 开头时改成相对
for entry in $(find . -mindepth 2 -maxdepth 3 -type l); do
  TGT=$(readlink "$entry")
  if [[ "$TGT" = /* ]]; then
    # 计算相对路径前缀
    DEPTH=$(echo "$entry" | tr -cd '/' | wc -c)
    PREFIX=""
    for ((i=1; i<DEPTH; i++)); do PREFIX="../$PREFIX"; done
    NEW="${PREFIX}${TGT#/}"
    echo "  $entry  $TGT  ->  $NEW"
    rm -f "$entry"
    ln -s "$NEW" "$entry"
    FIXED=$((FIXED+1))
  fi
done
cd -
echo "  fixed: $FIXED symlinks"
if [ "$FIXED" -eq 0 ]; then
  echo "[fix] no absolute symlinks found, abort" >&2
  exit 1
fi

echo "[fix] 5/7 repack layer + recompute digest"
(cd "$LDIR" && tar --numeric-owner --no-acls --no-xattrs -cf - .) \
  | gzip -1 > "$WORK/layer-new.tar.gz"

NEW_HASH=$(sha256sum "$WORK/layer-new.tar.gz" | awk '{print $1}')
NEW_SIZE=$(stat -c%s "$WORK/layer-new.tar.gz")
NEW_PATH="$WORK/img/blobs/sha256/$NEW_HASH"
mv "$WORK/layer-new.tar.gz" "$NEW_PATH"
OLD_HASH="$ROOT_LAYER"
echo "  old layer: sha256:$OLD_HASH"
echo "  new layer: sha256:$NEW_HASH ($NEW_SIZE bytes)"

echo "[fix] 6/7 patch manifest (digest + size) & config diff_ids"
# manifest 文件是 OCI image manifest, 其中 layers[].digest 引用 OLD_HASH
# config 文件是 OCI image config, 其中 rootfs.diff_ids[] 是 *uncompressed* layer hash —— 我们改的是 gzip 压缩 layer
# 由于我们改了内容,uncompressed 内容也变了,所以 diff_id 也得算 — 这里简化:计算未压缩 hash
NEW_DIFF_ID=$( (cd "$LDIR" && tar --numeric-owner --no-acls --no-xattrs -cf - .) | sha256sum | awk '{print $1}')
echo "  new diff_id (uncompressed): sha256:$NEW_DIFF_ID"

# 找 manifest (OCI image manifest, mediaType=...image.manifest.v1+json)
MANIFEST_FILE=""
CONFIG_FILE=""
for f in "$WORK/img/blobs/sha256/"*; do
  if grep -q "image.manifest.v1+json" "$f" 2>/dev/null && grep -q "$OLD_HASH" "$f"; then
    MANIFEST_FILE="$f"
  fi
done
[ -n "$MANIFEST_FILE" ] || { echo "manifest not found"; exit 1; }
echo "  manifest: $(basename $MANIFEST_FILE)"

# 找 config (referenced in manifest as config.digest)
CONFIG_DIGEST=$(grep -oE '"sha256:[a-f0-9]{64}"' "$MANIFEST_FILE" | head -1 | tr -d '"' | cut -d: -f2)
CONFIG_FILE="$WORK/img/blobs/sha256/$CONFIG_DIGEST"
[ -f "$CONFIG_FILE" ] || { echo "config $CONFIG_FILE not found"; exit 1; }
echo "  config: $(basename $CONFIG_FILE)"

# 在 config 的 rootfs.diff_ids 列表里,把对应该 layer 的 OLD diff_id 替换为 NEW_DIFF_ID
# 但我们不知道 OLD diff_id (只知道 OLD compressed hash)。简化:config 里 diff_ids 是一个列表,
# 通常 layer 顺序 = diff_ids 顺序。redroid 只有 1 个 rootfs layer,所以 diff_ids[0] 就是它。
# 用 python 安全改 JSON。
python3 - <<PYEOF
import json,sys
mf = "$MANIFEST_FILE"
cf = "$CONFIG_FILE"
old = "$OLD_HASH"
new = "$NEW_HASH"
new_size = $NEW_SIZE
new_diff = "$NEW_DIFF_ID"

with open(mf) as f: m=json.load(f)
for l in m.get("layers",[]):
    if l.get("digest","").endswith(old):
        l["digest"]="sha256:"+new
        l["size"]=new_size
with open(mf,"w") as f: json.dump(m,f,separators=(",",":"))

with open(cf) as f: c=json.load(f)
rfs = c.get("rootfs",{})
diff_ids = rfs.get("diff_ids",[])
# redroid 单 layer:替换第一个
if diff_ids:
    diff_ids[0] = "sha256:"+new_diff
rfs["diff_ids"] = diff_ids
c["rootfs"]=rfs
with open(cf,"w") as f: json.dump(c,f,separators=(",",":"))

# 重新计算 config blob hash,因为 manifest 引用它
import hashlib
ch = hashlib.sha256(open(cf,"rb").read()).hexdigest()
# rename config file to new hash
import os, shutil
new_config_path = os.path.dirname(cf)+"/"+ch
if cf != new_config_path:
    shutil.move(cf, new_config_path)
    # 更新 manifest 里 config.digest 和 size
    with open(mf) as f: m=json.load(f)
    m["config"]["digest"]="sha256:"+ch
    m["config"]["size"]=os.path.getsize(new_config_path)
    with open(mf,"w") as f: json.dump(m,f,separators=(",",":"))

# rename manifest blob to new hash too
mh = hashlib.sha256(open(mf,"rb").read()).hexdigest()
new_manifest_path = os.path.dirname(mf)+"/"+mh
if mf != new_manifest_path:
    shutil.move(mf, new_manifest_path)
print("new manifest:",mh)
print("new config:", ch)

# 更新 index.json 里对 manifest 的引用
idx = os.path.dirname(os.path.dirname(mf))+"/../index.json"
import os.path as P
# 寻找 index.json
root = P.dirname(P.dirname(mf))
while not P.exists(P.join(root,"index.json")):
    root = P.dirname(root)
    if root in ("/",""): raise SystemExit("index.json not found")
idx_path = P.join(root,"index.json")
with open(idx_path) as f: idx=json.load(f)

# Walk index manifests, if any sub-manifest is a OCI image-index (manifest-list),
# we may need to update there too. Redroid是 multi-arch index, manifest_list 包含
# linux/amd64 manifest 的 digest, 我们已经改了这个 manifest 的 hash。
def patch_obj(obj, old_digest, new_digest, new_size):
    if isinstance(obj, dict):
        if obj.get("digest","").endswith(old_digest):
            obj["digest"] = "sha256:"+new_digest
            obj["size"] = new_size
        for k,v in obj.items():
            patch_obj(v, old_digest, new_digest, new_size)
    elif isinstance(obj, list):
        for x in obj: patch_obj(x, old_digest, new_digest, new_size)

# 旧的 manifest hash 是它原本的名字, but file 已经 renamed. 这里要从外面拿到 OLD MANIFEST hash.
# 上面我们没记录 old manifest hash,先从命令行获取
old_manifest_hash = "$(basename $MANIFEST_FILE)"
patch_obj(idx, old_manifest_hash, mh, os.path.getsize(new_manifest_path))

# 同时 OCI manifest list 也可能在 blobs 下作为单独 JSON, 也要修
for fname in os.listdir(os.path.dirname(new_manifest_path)):
    fp = P.join(os.path.dirname(new_manifest_path), fname)
    if not P.isfile(fp): continue
    try:
        with open(fp) as f: data=json.load(f)
    except: continue
    if isinstance(data,dict) and data.get("mediaType","").endswith("image.index.v1+json"):
        patch_obj(data, old_manifest_hash, mh, os.path.getsize(new_manifest_path))
        # rename this index blob too since its content changed
        with open(fp,"w") as f: json.dump(data,f,separators=(",",":"))
        nh = hashlib.sha256(open(fp,"rb").read()).hexdigest()
        if P.basename(fp) != nh:
            shutil.move(fp, P.join(P.dirname(fp), nh))
            patch_obj(idx, P.basename(fp), nh, os.path.getsize(P.join(P.dirname(fp), nh)))

with open(idx_path,"w") as f: json.dump(idx,f,separators=(",",":"))
print("patched index.json")
PYEOF

# 删旧 layer blob
rm -f "$ROOT_LAYER_PATH"

echo "[fix] 7/7 repack OCI tarball & import"
(cd "$WORK/img" && tar -cf "$WORK/redroid-fixed.tar" .)
ctr -n k8s.io image rm "$DST" 2>/dev/null || true
ctr -n k8s.io image import --no-unpack "$WORK/redroid-fixed.tar" 2>&1
echo "---imported images---"
ctr -n k8s.io image ls 2>&1 | grep -E "redroid" | head
echo "---trying to tag---"
# import 通常会保留原 tag (redroid:11.0.0-latest 或新 ref),用 ctr image tag 复制一份方便用
EXISTING=$(ctr -n k8s.io image ls -q 2>&1 | grep redroid | head -1)
echo "imported ref: $EXISTING"
if [ -n "$EXISTING" ]; then
  ctr -n k8s.io image tag --force "$EXISTING" "$DST" 2>&1 || true
fi
ctr -n k8s.io image ls 2>&1 | grep -E "redroid" | head
