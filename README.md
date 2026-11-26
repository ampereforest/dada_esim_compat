# Xiaomi 15 (`dada`) — Persistent eSIM & pSIM2 Compatibility for ColorOS 17

Persistent dual-mode **SIM 2 (eSIM ↔ Physical SIM 2)** hardware routing, **OpenEUICC** LPA integration, automatic `N3Owner` endpoint death recovery, and a native **ColorOS 17 COUI `Manage eSIM`** UI for **Xiaomi 15 (`dada`, SM8750)** ported ROMs (ColorOS 17 V6 CN / Android 17, API 37).

> **Note:** This is a standalone developer integration repository containing clean source code, unified `.patch` files, SELinux/init configs, and build scripts (verified through **ColorOS 17 V6 MOD `v7.3.1`** pure non-root clean flash). No prebuilt `.apk`/`.jar` binaries, ROM images, or signing keystores are included. For the full all-in-one ColorOS 17 V6 mod suite, see [`ampereforest/ColorOS-V6-Standalone-Mods`](https://github.com/ampereforest/ColorOS-V6-Standalone-Mods); for thermal/idle/IMS optimizations (including the `v7.3.1` `QtiImsExtConnector` bind-storm fix), see [`ampereforest/coloros_heat_optimization_findings`](https://github.com/ampereforest/coloros_heat_optimization_findings).

---

## Overview & Features

- **Persistent eSIM ↔ Physical SIM 2 Switching:** Safely toggles SIM slot 2 between the embedded eUICC (`[0:0:0, 1:1:1]`) and physical SIM 2 (`[0:0:0, 0:1:1]`) without erasing eSIM profiles, preserving routing across reboots and modem restarts.
- **OpenEUICC LPA Bridge (`IDadaEsimBridge`):** Reuses [PeterCxy/OpenEUICC](https://gitea.angry.im/PeterCxy/OpenEUICC) (pinned upstream commit `b79e03bbf42f4d69b9d9dd21cd6132bb1b9314d7`) over a signature-verified Binder bridge to list, enable/disable, rename, and download eSIM profiles (QR code or manual SM-DP+ activation code).
- **Automatic Endpoint Death Recovery (`n3-runtime-v2`):** Registers `IBinder.linkToDeath()` on the provider endpoint inside `RoutingOwnerBinder` and triggers an immediate `500 ms` re-offer in `N3SystemService` whenever Android's cached-app killer recycles `im.angry.openeuicc` — preventing `IllegalArgumentException: owner unavailable` after idle time.
- **Native ColorOS 17 COUI `Manage eSIM` UI (`dada-esim-ui`):** Jetpack Compose UI calibrated 1:1 to ColorOS 17 COUI design tokens (`#F0F1F3` light canvas, `#FFFFFF` `20dp` continuous squircle cards, clean text-first rows with zero inline colored status pills, **2-pass separable AGSL progressive frosted top-bar blur** `0dp → 6.5dp` with 100% opaque replacement and zero bloom, **`40dp` floating `Kyant0/AndroidLiquidGlass` circular back button** with exact stock ColorOS 17 `coui_back_arrow_normal` vector & real-time `LayerBackdrop` lens refraction, `44dp × 24dp` COUI switches with ROM haptic `302`, **`suqi8/coui`-aligned ColorOS 17 `SpringOverScroller` RK4 overscroll** via `couiOverScrollVertical` `LayoutModifierNode` + edge haptic `307`, and Light/Dark AMOLED themes), accessible directly from **Settings → Mobile network → Manage eSIM** (with `*#*#3418#*#*` OpenEUICC fallback).
- **Rootless Clean-Flash Ready:** Uses native `init`, static SELinux CIL policies, and privileged permissions XMLs — no root manager or Xposed/LSPosed module required at runtime.

---

## Architecture Summary

| Component | Location in Repo | Role |
| :--- | :--- | :--- |
| **`n3-route-v2`** (native daemon) | `integration/native/`, `config/selinux/` | `init`-managed native listener (`u:r:n3_route:s0`) verifying `SO_PEERCRED`/`SO_PEERSEC` (`UID 1000` + `u:r:system_server:s0`) and executing fixed non-destructive `/vendor/bin/mtb` route selectors (`1` = eSIM, `2` = pSIM2; destructive selector `5` is never exposed). |
| **`n3-runtime-v2`** (`system_server` service) | `integration/owner/`, `patches/n3-runtime-v2-death-recovery.patch` | Loaded from `/system/framework/n3-runtime-v2.jar` via `SystemServer.startN3OwnerLifecycle()`. Owns route admission, durable journal, LPA leases (`N3Lease`), and `linkToDeath()` automatic provider handoff recovery. |
| **`OpenEUICC` Bridge** (LPA provider) | `patches/openeuicc-dada.patch`, `integration/provider-additions/` | Privileged `/product/priv-app/OpenEUICC` provider exposing signature-protected `DadaEsimBridgeService` (`IDadaEsimBridge`) and `N3EndpointReceiver`. |
| **Framework & Telephony Patches** | `patches/r1/`, `patches/r2/`, `patches/r3/`, `patches/services-n3-and-selinuxmmac.patch`, `patches/services-platform-signer-compat.patch`, `patches/teleservice-manage-esim-entry.patch` | Preserves SIM 2 route across Oplus forced-type checks (`R1`), bridges `EuiccConnector` (`R2`), reconciles per-card embedded subscriptions (`R3`), boots `N3SystemService` + `SELinuxMMAC` & `PackageManagerServiceUtils.isDadaPlatformCompat` signer compat, and adds the **Manage eSIM** row in `TeleService` (`Mobile network`). |
| **`DadaEsimUI`** (ColorOS 17 COUI app) | `dada-esim-ui/` | Standalone Jetpack Compose UI (`com.dada.coloros.esim`) signed with the same release certificate as `OpenEUICC` to bind `IDadaEsimBridge` securely. |

### SIM Slot Geometry (`port:physicalSlot:logicalSlot`)

| Mode | Complete Slot Mapping | Vendor Route Command |
| :--- | :--- | :--- |
| **pSIM1** (invariant) | `0:0:0` | Never modified |
| **Physical SIM 2** | `[0:0:0, 0:1:1]` | `/vendor/bin/mtb 5 10 2 0` |
| **Embedded eSIM** | `[0:0:0, 1:1:1]` | `/vendor/bin/mtb 5 10 1 0` |

> **Warning:** Never invoke `/vendor/bin/mtb 5 10 5 0`. Selector `5` is a destructive eUICC wipe/reset path and is intentionally blocked by `n3-route-v2`.

---

## How to Build & Integrate

### 1. Generate Your Own Signing Keystore

`OpenEUICC` and `DadaEsimUI` must be signed with the **same** release key so `DadaEsimBridgeService` accepts bridge calls (`SIGNATURE_MATCH`), and its SHA-256 certificate digest must be pinned in `CallerPolicy.java`.

```bash
keytool -genkeypair -v \
  -keystore esim-release.p12 -storetype PKCS12 \
  -alias esim-release -keyalg RSA -keysize 4096 -validity 10000
```

Extract your certificate (`.x509.pem` or lowercase 64-hex SHA-256 digest) and run `scripts/render_signer_patch.py` to stamp your own signing identity into **both** `CallerPolicy.java` and `patches/services-platform-signer-compat.patch` (`PackageManagerServiceUtils.isDadaPlatformCompat` & `isDadaSigner`):

```bash
python3 scripts/render_signer_patch.py --cert /path/to/esim-release.x509.pem
# Or pass --sha256 <64-hex-chars> directly
```

### 2. Build `n3-runtime-v2.jar` & `n3-route-v2`

```bash
# Build native daemon (requires Android NDK)
bash scripts/build_native.sh

# Build /system/framework/n3-runtime-v2.jar (includes linkToDeath recovery fix)
python3 scripts/build_java.py
```

If updating an existing `n3-runtime-v2` checkout, apply `patches/n3-runtime-v2-death-recovery.patch`:

```bash
patch -p1 < patches/n3-runtime-v2-death-recovery.patch
```

### 3. Build Patched `OpenEUICC`

```bash
# Fetches pinned upstream OpenEUICC, applies patches/openeuicc-dada.patch, and builds APK
bash scripts/fetch_and_build_openeuicc.sh
```

Sign the output APK with your `esim-release.p12` keystore (`zipalign -P 16 -f 4` + `apksigner` v3).

### 4. Build `DadaEsimUI` (ColorOS 17 COUI)

```bash
cd dada-esim-ui
# Generate Gradle wrapper if gradle-wrapper.jar is not present locally, or use system gradle
gradle assembleRelease

# Align & sign with the same keystore used for OpenEUICC
zipalign -P 16 -f 4 app/build/outputs/apk/release/app-release-unsigned.apk DadaEsimUI-aligned.apk
apksigner sign --ks /path/to/esim-release.p12 --ks-type PKCS12 \
  --v1-signing-enabled true --v2-signing-enabled true --v3-signing-enabled true --v4-signing-enabled false \
  --out DadaEsimUI-signed.apk DadaEsimUI-aligned.apk
```

### 5. Apply Framework, Telephony & ROM Configs

1. Apply the unified patches in `patches/`:
   - `patches/services-n3-and-selinuxmmac.patch` → `services.jar` (`SystemServer.startN3OwnerLifecycle` in `classes.dex` + `SELinuxMMAC` in `classes3.dex`)
   - `patches/services-platform-signer-compat.patch` → `services.jar` (`PackageManagerServiceUtils.isDadaPlatformCompat` & `isDadaSigner` in `classes3.dex`, required so re-signed `TeleService.apk` can share `android.uid.phone` with Oplus platform-signed APKs)
   - `patches/r1/`, `patches/r2/`, `patches/r3/` → `telephony-common` / `oplus-telephony`
   - `patches/teleservice-manage-esim-entry.patch` → `TeleService.apk` (`Settings → Mobile network → Manage eSIM`)
2. Install built artifacts and configs into their matching partitions:
   - `/system/framework/n3-runtime-v2.jar`
   - `/system/bin/n3-route-v2` + `config/system/etc/init/` + `config/selinux/` (`n3-route-v2.cil`, `file_contexts`, `property_contexts`)
   - `/product/priv-app/OpenEUICC/OpenEUICC.apk` + `config/etc/permissions/`
   - `/product/priv-app/DadaEsimUI/DadaEsimUI.apk` + `dada-esim-ui/privapp-permissions-dadaesimui.xml`

For deep technical notes, packaging checks, and historical test ledgers, see [`docs/DEVELOPER.md`](docs/DEVELOPER.md), [`docs/OPENEUICC_PATCH.md`](docs/OPENEUICC_PATCH.md), [`docs/OUTCOMES.md`](docs/OUTCOMES.md), and [`docs/ROADMAP.md`](docs/ROADMAP.md).

---

## Credits & Disclaimer

- **OpenEUICC / LPAC:** [PeterCxy/OpenEUICC](https://gitea.angry.im/PeterCxy/OpenEUICC) and upstream `lpac` contributors.
- **Continuous Squircle Geometry (`com.kyant.capsule`):** [Kyant0/Capsule](https://github.com/Kyant0/Capsule) (used inside `dada-esim-ui` for authentic ColorOS 17 squircle cards).
- **Liquid Glass Backdrop Refraction (`com.kyant.backdrop`):** [Kyant0/AndroidLiquidGlass](https://github.com/Kyant0/AndroidLiquidGlass) (Apache 2.0 — used inside `dada-esim-ui` for the floating `40dp` ColorOS 17 circular back button with real-time `LayerBackdrop` blur, vibrancy, specular highlight rim, diffuse shadow, AGSL lens refraction, and 2-pass separable AGSL progressive frosted top-bar blur).
- **ColorOS 17 RK4 Overscroll Physics (`CouiOverscroll.kt`):** [suqi8/coui](https://github.com/suqi8/coui) (Apache 2.0 — exact `SpringOverScroller` RK4 spring-back, drag resistance, over-fling saturation, and `LayoutModifierNode` placement architecture with ROM edge haptic `307`).
- **Disclaimer:** Intended for Xiaomi 15 (`dada`) ported ROM development and educational reference. Modifying telephony frameworks, radio slot routing, or eUICC partitions carries risk — always back up your partitions and never run destructive eUICC reset commands.
