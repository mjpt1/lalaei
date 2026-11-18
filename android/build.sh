#!/usr/bin/env bash
# Builds lalaei.apk without Gradle. Run from Git Bash: bash android/build.sh
# Work happens in an ASCII-only temp folder because the Android tools break on Persian paths.
set -euo pipefail

HERE="$(cd "$(dirname "$0")" && pwd)"
ROOT="$(dirname "$HERE")"
SDK="${ANDROID_HOME:-$LOCALAPPDATA/Android/Sdk}"
BT="$SDK/build-tools/35.0.1"
JAR="$SDK/platforms/android-35/android.jar"
W="${TMP:-/tmp}/lalaei-build"

mkdir -p "$W"
find "$W" -mindepth 1 -delete
mkdir -p "$W/assets/www" "$W/classes" "$W/dex"
cp -r "$HERE/res" "$HERE/src" "$HERE/AndroidManifest.xml" "$W/"
cp -r "$ROOT/app/index.html" "$ROOT/app/songs.js" "$ROOT/app/audio" "$ROOT/app/fonts" "$W/assets/www/"
cd "$W"

"$BT/aapt2.exe" compile --dir res -o res.zip
"$BT/aapt2.exe" link -o base.apk -I "$JAR" --manifest AndroidManifest.xml --java gen ${PKG:+--rename-manifest-package "$PKG"} res.zip

javac -nowarn -source 11 -target 11 -cp "$JAR" -d classes src/ir/lalaei/app/*.java $(find gen -name "*.java") 2>&1 | grep -v "^warning" || true
cmd //c "$BT\\d8.bat" --lib "$JAR" --min-api 24 --output dex $(find classes -name '*.class')

# add assets and dex with forward-slash paths (aapt2 on Windows writes backslashes)
python -I - <<'PY'
import zipfile,os
src=zipfile.ZipFile('base.apk'); out=zipfile.ZipFile('full.apk','w')
for i in src.infolist(): out.writestr(i,src.read(i.filename))
out.write('dex/classes.dex','classes.dex',zipfile.ZIP_DEFLATED)
for d,_,fs in os.walk('assets'):
    for f in fs:
        p=os.path.join(d,f); n=p.replace(os.sep,'/')
        out.write(p,n,zipfile.ZIP_STORED if f.endswith(('.opus','.woff2')) else zipfile.ZIP_DEFLATED)
out.close()
PY
"$BT/zipalign.exe" -p -f 4 full.apk aligned.apk

# Default: own release key in an ASCII path (keytool fails on Persian paths).
# To update the old Myket app, set before running:
#   PKG=com.andromo.dev262370.app244853  KS=/path/to/old.keystore  KS_ALIAS=alias  KS_PASS=password
# KS_KEY_PASS is only needed if the key password differs from the store password.
if [ -n "${KS:-}" ]; then
  cp "$KS" ks.keystore
  KEY="${KS_KEY_PASS:-$KS_PASS}"
  cmd //c "$BT\apksigner.bat" sign --ks ks.keystore --ks-key-alias "$KS_ALIAS" --ks-pass env:KS_PASS --key-pass pass:"$KEY" --out lalaei.apk aligned.apk
  rm -f ks.keystore
else
  # release key for Myket (backup in keys/). Never change it, or updates stop installing.
  KS="$USERPROFILE/.android/lalaei-release.jks"
  export KS_PASS="$(cat "$USERPROFILE/.android/lalaei-release.pass")"
  cmd //c "$BT\apksigner.bat" sign --ks "$KS" --ks-key-alias lalaei --ks-pass env:KS_PASS --out lalaei.apk aligned.apk
fi
cmd //c "$BT\apksigner.bat" verify --print-certs lalaei.apk | head -1

cp lalaei.apk "$ROOT/lalaei.apk"
ls -la "$ROOT/lalaei.apk"
