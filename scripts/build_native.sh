#!/bin/sh
# Host-side compile only. Never launches a device endpoint or modem operation.
set -eu
: "${NDK_CC:?Set NDK_CC to pinned aarch64-linux-android37-clang}"
: "${OUT_DIR:?Set OUT_DIR to a NEW output directory outside the source tree}"
ROOT=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
test ! -e "$OUT_DIR"
mkdir -- "$OUT_DIR"
"$NDK_CC" -DN3_ROM -std=c11 -O2 -fPIE -pie -fstack-protector-strong \
    -D_FORTIFY_SOURCE=2 -Wall -Wextra -Werror \
    -Wl,-z,relro,-z,now -Wl,--build-id=sha1 \
    "$ROOT/integration/native/route_endpoint.c" -llog -o "$OUT_DIR/n3-route-v2"
printf '%s\n' 'Compiled only. Authenticate ELF/hash; do not launch or install automatically.'
