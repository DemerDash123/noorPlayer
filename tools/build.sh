#!/bin/bash
# Builds, aligns and signs Noor Player using Ubuntu's packaged Android tools:
# aapt2 (resources), javac against the API 23 android.jar, dx (dex), zipalign, apksigner (v2 + v3).
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
B="$ROOT/build/apk"
OUT="$ROOT/build/NoorPlayer.apk"
FW=/usr/share/android-framework-res/framework-res.apk
AJ=/usr/lib/android-sdk/platforms/android-23/android.jar
VERSION_CODE=1
VERSION_NAME=1.0
KS="$ROOT/keystore/noor.jks"

python3 - "$B" <<'EOF'
import shutil, sys, os
shutil.rmtree(sys.argv[1], ignore_errors=True)
os.makedirs(os.path.join(sys.argv[1], "gen"))
os.makedirs(os.path.join(sys.argv[1], "classes"))
EOF

echo "== resources"
aapt2 compile --dir "$ROOT/res" -o "$B/res.zip"
aapt2 link -o "$B/base.apk" -I "$FW" --manifest "$ROOT/AndroidManifest.xml" --java "$B/gen" \
  --min-sdk-version 24 --target-sdk-version 34 \
  --version-code "$VERSION_CODE" --version-name "$VERSION_NAME" \
  --auto-add-overlay "$B/res.zip"

echo "== java"
# Compile-only stand-ins for hidden framework types that Ubuntu's android.jar references (never packaged).
mkdir -p "$B/stubs"
javac -nowarn -source 8 -target 8 -Xlint:-options -bootclasspath "$AJ" -d "$B/stubs" \
  $(find "$ROOT/tools/stubs" -name '*.java') 2>&1 | grep -v '^Picked up' || true
javac -encoding UTF-8 -source 8 -target 8 -Xlint:all -Xlint:-options -Xlint:-serial -Xlint:-deprecation \
  -bootclasspath "$AJ" -cp "$B/stubs" -d "$B/classes" \
  $(find "$ROOT/src" "$B/gen" -name '*.java') 2>&1 | grep -v '^Picked up' || true
test -f "$B/classes/com/ahmed/noorplayer/PlayerActivity.class"

echo "== dex"
dalvik-exchange --dex --min-sdk-version=24 --output="$B/classes.dex" "$B/classes" 2>&1 | grep -v '^Picked up' || true
test -f "$B/classes.dex"

echo "== package"
python3 - "$B/base.apk" "$B/classes.dex" "$B/unaligned.apk" <<'EOF'
import shutil, sys, zipfile
base, dex, out = sys.argv[1:4]
shutil.copy(base, out)
with zipfile.ZipFile(out, "a", compression=zipfile.ZIP_DEFLATED) as z:
    z.write(dex, "classes.dex")
EOF
zipalign -f -p 4 "$B/unaligned.apk" "$B/aligned.apk"

if [ ! -f "$KS" ]; then
  echo "== new signing key"
  mkdir -p "$ROOT/keystore"
  python3 -c "import secrets; print(secrets.token_urlsafe(18))" > "$ROOT/keystore/password.txt"
  PW="$(cat "$ROOT/keystore/password.txt")"
  keytool -genkeypair -keystore "$KS" -storetype PKCS12 -storepass "$PW" -keypass "$PW" -alias noor \
    -keyalg RSA -keysize 2048 -validity 10950 -dname "CN=Noor Player, OU=Ahmed, C=OM" 2>&1 | grep -v '^Picked up' || true
fi
PW="$(cat "$ROOT/keystore/password.txt")"

echo "== sign"
apksigner sign --ks "$KS" --ks-pass "pass:$PW" --key-pass "pass:$PW" --ks-key-alias noor \
  --out "$OUT" "$B/aligned.apk" 2>&1 | grep -v '^Picked up' || true
apksigner verify "$OUT" 2>&1 | grep -v '^Picked up' || true
ls -la "$OUT"
