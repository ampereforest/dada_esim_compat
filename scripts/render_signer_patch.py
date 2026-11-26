#!/usr/bin/env python3
"""
Render your own signing certificate SHA-256 into dada_esim_compat sources & patches.

Updates:
  1. integration/owner/org/sourceowned/n3/runtime/v2/CallerPolicy.java (CERT_SHA256)
  2. patches/services-platform-signer-compat.patch (signed byte arrays for isDadaPlatformCompat & isDadaSigner)

Usage:
    python3 scripts/render_signer_patch.py --cert /path/to/esim-release.x509.pem
    python3 scripts/render_signer_patch.py --sha256 <64-hex-chars>
"""

import argparse
import hashlib
import re
import subprocess
import sys
from pathlib import Path

DEFAULT_HEX = "879b92491d4b8623227a06e6f4dc63bbd298cd3ad91b3a1ca25d849da24bb3c0"


def cert_to_sha256(cert_path: Path) -> str:
    raw = cert_path.read_bytes()
    if b"BEGIN CERTIFICATE" in raw:
        der = subprocess.check_output(
            ["openssl", "x509", "-in", str(cert_path), "-outform", "DER"]
        )
    else:
        der = raw
    return hashlib.sha256(der).hexdigest()


def hex_to_smali_array_lines(hex_str: str, indent: str = "+        ") -> list[str]:
    bs = bytes.fromhex(hex_str)
    assert len(bs) == 32
    lines = []
    for b in bs:
        signed = b - 256 if b >= 128 else b
        if signed < 0:
            lines.append(f"{indent}-0x{-signed:x}t")
        else:
            lines.append(f"{indent}0x{signed:x}t")
    return lines


def main() -> int:
    ap = argparse.ArgumentParser(description="Inject custom certificate SHA-256 into CallerPolicy.java & PackageManagerServiceUtils patch")
    g = ap.add_mutually_exclusive_group(required=True)
    g.add_argument("--cert", type=Path, help="X.509 certificate (.pem or .der)")
    g.add_argument("--sha256", type=str, help="64-character hex SHA-256 digest of your signing certificate")
    args = ap.parse_args()

    digest = cert_to_sha256(args.cert) if args.cert else args.sha256.lower().replace(":", "").strip()
    if not re.fullmatch(r"[0-9a-f]{64}", digest):
        sys.exit(f"ERROR: Invalid SHA-256 hex digest: {digest}")

    repo = Path(__file__).resolve().parent.parent

    # 1. Update CallerPolicy.java
    caller_policy = repo / "integration/owner/org/sourceowned/n3/runtime/v2/CallerPolicy.java"
    if caller_policy.exists():
        txt = caller_policy.read_text()
        new_txt, count = re.subn(
            r'public static final String CERT_SHA256 = "[0-9a-fA-F]{64}";',
            f'public static final String CERT_SHA256 = "{digest}";',
            txt,
        )
        assert count == 1, f"Expected 1 CERT_SHA256 in {caller_policy}, found {count}"
        caller_policy.write_text(new_txt)
        print(f"Updated {caller_policy.relative_to(repo)} -> {digest}")

    # 2. Update PackageManagerServiceUtils signer arrays in services-platform-signer-compat.patch
    patch_file = repo / "patches/services-platform-signer-compat.patch"
    if patch_file.exists():
        ptxt = patch_file.read_text()
        old_block = "\n".join(hex_to_smali_array_lines(DEFAULT_HEX))
        new_block = "\n".join(hex_to_smali_array_lines(digest))
        # Also allow re-running after a previous custom replacement via marker comments
        if old_block in ptxt:
            ptxt = ptxt.replace(old_block, new_block)
            patch_file.write_text(ptxt)
            print(f"Updated {patch_file.relative_to(repo)} byte arrays -> {digest}")
        else:
            # Replace between :array_2e / :array_10 .array-data 1 blocks
            def repl_custom(m):
                return m.group(1) + "\n" + new_block + "\n" + m.group(2)
            ptxt2, n = re.subn(
                r"(\+\s*:array_(?:2e|10)\n\+\s*\.array-data 1)\n(?:\+\s*-?0x[0-9a-f]+t\n){32}(\+\s*\.end array-data)",
                repl_custom,
                ptxt,
            )
            assert n == 2, f"Expected 2 custom signer arrays in {patch_file}, found {n}"
            patch_file.write_text(ptxt2)
            print(f"Updated {patch_file.relative_to(repo)} ({n} arrays) -> {digest}")

    print("Done! Rebuild n3-runtime-v2.jar and apply patches/services-platform-signer-compat.patch.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
