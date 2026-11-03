#!/bin/sh
# Build the aa-dpi HMG-API mod into build/hmgapi/mod/aa-dpi/ (and, with an author
# key, the signed package dist/aa-dpi-<version>.zip).
#
#   sh hmgapi-mod/build.sh                 the mod folder (unsigned)
#   sh hmgapi-mod/build.sh <author key>    plus the signed package, through
#                                          HMG-API's tools/mkmodpkg.sh
#
# Needs an HMG-API checkout (HMGAPI, default ../HMG-API next to this repo; its
# runtime and client are built here: a JDK and the Android SDK, no clang), a JDK
# and the Android SDK (build-tools, a platform android.jar; ANDROID_HOME).
# A new author key: java -cp "$HMGAPI/out/engine-host" hmgapi.engine.Signing keygen <file>
#
# The app (AaDpi.apk) is signed with AADPI_APP_KEY (a .p12; default
# ~/hmg-keys/aa-dpi-app.p12, made there once if missing, outside CI), password in
# AADPI_APP_KEY_PASS or the .pass file next to it. Keep using one key, so an update
# installs over the app.
set -e
cd "$(dirname "$0")/.."
REPO=$PWD
HMGAPI=${HMGAPI:-$REPO/../HMG-API}
[ -f "$HMGAPI/tools/env.sh" ] || { echo "no HMG-API checkout at $HMGAPI (set HMGAPI)" >&2; exit 1; }
HMGAPI=$(cd "$HMGAPI" && pwd)
. "$HMGAPI/tools/env.sh"
[ -x "$D8" ] && [ -f "$ANDROID_JAR" ] || { echo "Android SDK not found (ANDROID_HOME)" >&2; exit 1; }

ID=aa-dpi
VER=$(sed -n 's/.*"version": *"\([^"]*\)".*/\1/p' hmgapi-mod/mod.json | head -1)
B=build/hmgapi
MOD=$B/mod/$ID
rm -rf "$B/hooks" "$B/app" "$MOD"
mkdir -p "$B/hooks" "$B/app/classes" "$MOD/app"

echo "== HMG-API runtime and client ($HMGAPI)"
(cd "$HMGAPI" && sh build.sh runtime client >/dev/null)

echo "== hooks.jar"
javac --release 8 -Xlint:-options -nowarn -cp "$ANDROID_JAR:$HMGAPI/out/classes" -d "$B/hooks" $(find hmgapi-mod/hooks -name '*.java')
"$D8" --release --min-api 19 --lib "$ANDROID_JAR" --classpath "$HMGAPI/out/classes" --output "$MOD/hooks.jar" $(find "$B/hooks" -name '*.class')

echo "== AaDpi.apk"
W=$B/app
"$BT/aapt2" compile --dir hmgapi-mod/app/res -o "$W/res.zip"
"$BT/aapt2" link -I "$ANDROID_JAR" --manifest hmgapi-mod/app/AndroidManifest.xml --min-sdk-version 19 --target-sdk-version 19 \
    --java "$W/gen" -o "$W/base.apk" "$W/res.zip"
javac --release 8 -Xlint:-options -nowarn -cp "$ANDROID_JAR:$HMGAPI/out/hmgapi-client.jar" -d "$W/classes" \
    $(find hmgapi-mod/app/java "$W/gen" -name '*.java')
(cd "$W/classes" && jar xf "$HMGAPI/out/hmgapi-client.jar" hmgapi)
"$D8" --release --min-api 19 --lib "$ANDROID_JAR" --output "$W" $(find "$W/classes" -name '*.class')
cp "$W/base.apk" "$W/unsigned.apk"
(cd "$W" && zip -q -X unsigned.apk classes.dex)
"$BT/zipalign" -f 4 "$W/unsigned.apk" "$W/aligned.apk"
K=${AADPI_APP_KEY:-$HOME/hmg-keys/aa-dpi-app.p12}
if [ ! -f "$K" ]; then
    if [ -n "$CI" ]; then
        echo "no app key at $K (CI: set the AADPI_APP_KEY and AADPI_APP_KEY_PASS secrets)" >&2
        exit 1
    fi
    mkdir -p "$(dirname "$K")"
    (umask 077; openssl rand -hex 24 | tr -d '\n' > "${K%.p12}.pass")
    keytool -genkeypair -keystore "$K" -storetype pkcs12 -storepass "$(cat "${K%.p12}.pass")" \
        -keypass "$(cat "${K%.p12}.pass")" -alias app -keyalg RSA -keysize 2048 -validity 10000 -dname "CN=AA DPI" >/dev/null 2>&1
    chmod 600 "$K"
    echo "made an app key: $K (keep it and ${K%.p12}.pass backed up: updates must be signed with it)"
fi
P=${AADPI_APP_KEY_PASS:-$(cat "${K%.p12}.pass" 2>/dev/null)}
[ -n "$P" ] || { echo "no password for $K (AADPI_APP_KEY_PASS, or ${K%.p12}.pass next to it)" >&2; exit 1; }
"$BT/apksigner" sign --ks "$K" --ks-pass "pass:$P" --min-sdk-version 9 \
    --v2-signing-enabled false --v3-signing-enabled false --out "$MOD/app/AaDpi.apk" "$W/aligned.apk"

echo "== mod folder"
cp hmgapi-mod/mod.json hmgapi-mod/settings.json "$MOD/"
(cd "$MOD" && find . -type f | sort | xargs md5 -r 2>/dev/null || find . -type f | sort | xargs md5sum)

if [ -n "$1" ]; then
    echo "== signed package"
    Z=$(sh "$HMGAPI/tools/mkmodpkg.sh" "$REPO/$MOD" "$1" | tail -1)
    mkdir -p dist
    cp "$Z" "dist/$ID-$VER.zip"
    echo "dist/$ID-$VER.zip"
fi
