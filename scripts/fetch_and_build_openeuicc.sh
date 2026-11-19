#!/usr/bin/env bash
# Host-only source build. Retains every checkout, key, log and failed output.
set -euo pipefail
umask 077

BASE=b79e03bbf42f4d69b9d9dd21cd6132bb1b9314d7
LPAC_BASE=50cdf15ab40942b00f8819a1c2ca19997f1a6d34
CJSON_BASE=6d9f2443ab071f86e5d9b43025a40929ec41c46c
REPO=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd -P)
PATCH=$REPO/patches/openeuicc-dada.patch
fail() { printf 'ERROR: %s\n' "$*" >&2; exit 1; }
if [[ ${1:-} == --help ]]; then
    cat <<'HELP'
Usage: ANDROID_HOME=/absolute/sdk JAVA_HOME=/absolute/jdk21 [options] bash scripts/fetch_and_build_openeuicc.sh
Required SDK: platform android-37.0 (API37), build-tools37.0.0, NDK26.1.10909125.
Options are environment variables:
  BUILD_DIR              New absolute POSIX directory without spaces (default: retained mktemp directory).
  GRADLE_BIN             Gradle9.5.0 executable; otherwise use pinned upstream wrapper.
  GRADLE_USER_HOME       External dependency cache (default: inside BUILD_DIR).
  OFFLINE=1              Gradle offline mode only; source fetch still needs network.
  MAX_WORKERS            Gradle worker count (default: 2).
  KEYSTORE_PATH          Existing PKCS12/JKS identity; otherwise generate RSA4096 PKCS12.
  KEYSTORE_PASSWORD_FILE Private file containing its password, or KEYSTORE_PASSWORD environment variable.
  KEY_ALIAS              Existing alias (default: openeuicc).
  KEY_PASSWORD           Optional separate key password (default: store password).
All outputs and failures are retained. No device operations, cleanup or install.
New signing identities require deliberate ROM owner/bridge repinning; not an update of the original signer.
HELP
    exit 0
fi
[[ $# == 0 ]] || fail 'Unexpected arguments; use --help.'
for tool in git python3 sha256sum; do command -v "$tool" >/dev/null || fail "Missing $tool"; done
[[ -f $PATCH ]] || fail 'Missing unified patch.'
export JAVA_HOME=${JAVA_HOME:?Set JAVA_HOME to JDK21}
export ANDROID_HOME=${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}
[[ -n $ANDROID_HOME ]] || fail 'Set ANDROID_HOME to the SDK.'
export ANDROID_SDK_ROOT=$ANDROID_HOME
[[ $ANDROID_HOME == /* && ! $ANDROID_HOME =~ [[:space:]] ]] || fail 'SDK must have an absolute space-free path for ndk-build.'
[[ -x $JAVA_HOME/bin/java && -x $JAVA_HOME/bin/keytool ]] || fail 'Invalid JAVA_HOME.'
export PATH=$JAVA_HOME/bin:$PATH
"$JAVA_HOME/bin/java" -version 2>&1 | grep -Eq 'version "21[.]' || fail 'JDK21 required.'
BT=$ANDROID_HOME/build-tools/37.0.0
[[ -x $BT/zipalign && -x $BT/apksigner ]] || fail 'Install build-tools37.0.0.'
[[ -d $ANDROID_HOME/ndk/26.1.10909125 ]] || fail 'Install NDK26.1.10909125.'
[[ -f $ANDROID_HOME/platforms/android-37.0/android.jar || -f $ANDROID_HOME/platforms/android-37/android.jar ]] || fail 'Install API37 platform.'
if [[ -n ${BUILD_DIR:-} ]]; then
    [[ $BUILD_DIR == /* && ! $BUILD_DIR =~ [[:space:]] ]] || fail 'BUILD_DIR must be absolute and space-free.'
    [[ ! -e $BUILD_DIR && ! -L $BUILD_DIR ]] || fail 'BUILD_DIR already exists; choose a new directory.'
    mkdir -- "$BUILD_DIR"
else
    BUILD_DIR=$(mktemp -d "${TMPDIR:-/tmp}/openeuicc-dada.XXXXXXXX")
fi
BUILD_DIR=$(cd -- "$BUILD_DIR" && pwd -P)
[[ ! $BUILD_DIR =~ [[:space:]] && $BUILD_DIR != "$REPO" && $BUILD_DIR != "$REPO/"* ]] || fail 'Build outside this repository, without spaces.'
printf 'Retained build directory: %s\n' "$BUILD_DIR"
SRC=$BUILD_DIR/source
OUT=$BUILD_DIR/output
mkdir -- "$OUT" "$BUILD_DIR/tmp" "$BUILD_DIR/android-user"
export TMPDIR=$BUILD_DIR/tmp ANDROID_USER_HOME=$BUILD_DIR/android-user
export GRADLE_USER_HOME=${GRADLE_USER_HOME:-$BUILD_DIR/gradle-home}
trap 'printf "Build stopped; all files retained in %s\n" "$BUILD_DIR" >&2' ERR

git init -- "$SRC"
git -C "$SRC" remote add origin https://gitea.angry.im/PeterCxy/OpenEUICC.git
git -C "$SRC" fetch --depth=1 origin "$BASE"
git -C "$SRC" checkout --detach "$BASE"
[[ $(git -C "$SRC" rev-parse HEAD) == "$BASE" ]] || fail 'Upstream pin mismatch.'
git -C "$SRC" submodule update --init --recursive
LPAC=$SRC/libs/lpac-jni/src/main/jni/lpac
CJSON=$SRC/libs/lpac-jni/src/main/jni/cjson/cjson
[[ $(git -C "$LPAC" rev-parse HEAD) == "$LPAC_BASE" ]] || fail 'LPAC pin mismatch.'
[[ $(git -C "$CJSON" rev-parse HEAD) == "$CJSON_BASE" ]] || fail 'cJSON pin mismatch.'
[[ -z $(git -C "$SRC" status --porcelain) ]] || fail 'Checkout not clean before patch.'
# Nested LPAC changes are ordinary worktree diffs, not a parent gitlink update.
git -C "$SRC" apply --check "$PATCH"
# Preserve the obsolete source that this authorized diff removes from the new app.
REMOVED=app/src/main/java/im/angry/openeuicc/ui/SlotMappingFragment.kt
mkdir -p -- "$BUILD_DIR/preserved-patch-removals/$(dirname -- "$REMOVED")"
git -C "$SRC" show "HEAD:$REMOVED" > "$BUILD_DIR/preserved-patch-removals/$REMOVED"
git -C "$SRC" apply --whitespace=nowarn "$PATCH"
git -C "$SRC" apply --reverse --check "$PATCH"
sha256sum "$PATCH" > "$OUT/patch.sha256"
printf 'upstream=%s\nlpac=%s\ncjson=%s\n' "$BASE" "$LPAC_BASE" "$CJSON_BASE" > "$OUT/source-pins.txt"

GRADLE=${GRADLE_BIN:-$SRC/gradlew}
[[ -x $GRADLE ]] || fail 'Gradle executable missing.'
"$GRADLE" --version > "$OUT/gradle-version.txt"
grep -qx 'Gradle 9.5.0' "$OUT/gradle-version.txt" || fail 'Gradle9.5.0 required.'
ARGS=(--no-daemon --max-workers "${MAX_WORKERS:-2}" --console=plain)
[[ ${OFFLINE:-0} == 0 || ${OFFLINE:-0} == 1 ]] || fail 'OFFLINE must be 0 or 1.'
if [[ ${OFFLINE:-0} == 1 ]]; then ARGS+=(--offline); fi
(cd -- "$SRC" && "$GRADLE" "${ARGS[@]}" :app:assembleRelease) 2>&1 | tee "$OUT/build.log"
APK=$SRC/app/build/outputs/apk/release/app-release-unsigned.apk
[[ -s $APK ]] || fail 'Unsigned release APK missing; external signing is required.'
python3 - "$APK" <<'PY'
import sys, zipfile
abis = {'arm64-v8a', 'armeabi-v7a', 'x86', 'x86_64'}
with zipfile.ZipFile(sys.argv[1]) as z:
    names = z.namelist()
    expected = {f'lib/{abi}/liblpac-jni.so' for abi in abis}
    found = {n for n in names if n.endswith('/liblpac-jni.so')}
    assert found == expected and all(names.count(n) == 1 for n in expected), found
    assert all(z.getinfo(n).file_size > 0 and z.getinfo(n).compress_type == zipfile.ZIP_STORED for n in expected)
print('PASS: four freshly built uncompressed JNI ABIs')
PY

KEY_ALIAS=${KEY_ALIAS:-openeuicc}
if [[ -n ${KEYSTORE_PATH:-} ]]; then
    [[ $KEYSTORE_PATH == /* && -f $KEYSTORE_PATH ]] || fail 'KEYSTORE_PATH must be an existing absolute path.'
    if [[ -n ${KEYSTORE_PASSWORD_FILE:-} ]]; then
        [[ -f $KEYSTORE_PASSWORD_FILE ]] || fail 'Password file missing.'
        KEYSTORE_PASSWORD=$(cat -- "$KEYSTORE_PASSWORD_FILE")
    fi
    [[ -n ${KEYSTORE_PASSWORD:-} ]] || fail 'Provide password via private file or environment.'
else
    mkdir -- "$BUILD_DIR/signing"
    KEYSTORE_PATH=$BUILD_DIR/signing/esim-release.p12
    KEYSTORE_PASSWORD=$(python3 -c 'import secrets; print(secrets.token_urlsafe(48))')
    KEY_PASSWORD=$KEYSTORE_PASSWORD
    printf '%s\n' "$KEYSTORE_PASSWORD" > "$BUILD_DIR/signing/keystore-password.private"
    export KEYSTORE_PASSWORD
    "$JAVA_HOME/bin/keytool" -genkeypair -noprompt -alias "$KEY_ALIAS" \
        -keyalg RSA -keysize 4096 -sigalg SHA256withRSA -validity 10000 \
        -dname 'CN=OpenEUICC Local Build' -storetype PKCS12 -keystore "$KEYSTORE_PATH" \
        -storepass:env KEYSTORE_PASSWORD -keypass:env KEYSTORE_PASSWORD
fi
export KEYSTORE_PASSWORD
export KEY_PASSWORD=${KEY_PASSWORD:-$KEYSTORE_PASSWORD}
ALIGNED=$OUT/OpenEUICC-aligned-unsigned.apk
"$BT/zipalign" -P 16 -f -v 4 "$APK" "$ALIGNED" > "$OUT/zipalign.log"
"$BT/zipalign" -c -P 16 -v 4 "$ALIGNED" >> "$OUT/zipalign.log"
# A v2 block is deliberately included even though the manifest's minSdk is31.
"$BT/apksigner" sign --ks "$KEYSTORE_PATH" --ks-key-alias "$KEY_ALIAS" \
    --ks-pass env:KEYSTORE_PASSWORD --key-pass env:KEY_PASSWORD \
    --min-sdk-version 24 --v1-signing-enabled false --v2-signing-enabled true \
    --v3-signing-enabled true --v4-signing-enabled false --out "$OUT/OpenEUICC.apk" "$ALIGNED"
unset KEYSTORE_PASSWORD KEY_PASSWORD
"$BT/apksigner" verify --verbose --print-certs --min-sdk-version 31 "$OUT/OpenEUICC.apk" > "$OUT/signature-v3.txt"
"$BT/apksigner" verify --verbose --min-sdk-version 24 --max-sdk-version 27 "$OUT/OpenEUICC.apk" > "$OUT/signature-v2.txt"
grep -Eq '^Verified using v3 scheme .*: true$' "$OUT/signature-v3.txt" || fail 'v3 verification failed.'
grep -Eq '^Verified using v2 scheme .*: true$' "$OUT/signature-v2.txt" || fail 'v2 verification failed.'
"$BT/zipalign" -c -P 16 -v 4 "$OUT/OpenEUICC.apk" > "$OUT/zipalign-signed.log"
mkdir -p -- "$OUT/product/etc/permissions"
cp -- "$SRC/privapp_whitelist_im.angry.openeuicc.xml" "$OUT/product/etc/permissions/privapp-permissions-openeuicc.xml"
python3 - "$OUT/product/etc/permissions/privapp-permissions-openeuicc.xml" <<'PY'
import sys, xml.etree.ElementTree as ET
root = ET.parse(sys.argv[1]).getroot()
groups = root.findall('privapp-permissions')
assert len(groups) == 1 and groups[0].get('package') == 'im.angry.openeuicc'
assert {p.get('name') for p in groups[0]} == {
    'android.permission.READ_PRIVILEGED_PHONE_STATE',
    'android.permission.WRITE_EMBEDDED_SUBSCRIPTIONS',
    'android.permission.MODIFY_PHONE_STATE'}
PY
(cd -- "$OUT" && sha256sum OpenEUICC.apk product/etc/permissions/privapp-permissions-openeuicc.xml > SHA256SUMS)
printf 'Built and verified: %s/OpenEUICC.apk\nPrivileged XML: %s/product/etc/permissions/privapp-permissions-openeuicc.xml\n' "$OUT" "$OUT"
printf 'No installation performed. Preserve the key privately; new signers need ROM owner/bridge repinning.\n'
