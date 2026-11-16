# Developer integration and host build guide

This source subset preserves custom integration logic, not a complete OEM ROM or
upstream app. Inputs must be obtained legitimately and authenticated **outside**
this repository. No automated device installation, route switching or cleanup.

## 1. Inputs and selection

- Exact V6 original images/JARs/APKs, authoritative LP prefix/layout and full original
  metadata+content manifests. Original UUIDs/timestamps/signatures must remain intact.
- Independently obtained OpenEUICC checkout at
  `b79e03bbf42f4d69b9d9dd21cd6132bb1b9314d7`, with its LPAC/cJSON provenance.
  Respect its contribution rules for any actual upstream work; this repository is
  not a submission to that project.
- Local pinned JDK21, Android API37 (`android-37.0` in the historical SDK), build-tools37,
  compatible Gradle/offline cache, NDK/AArch64 compiler, R8/D8, smali/baksmali and
  Android-enabled EROFS **V5** tools. Authenticate executable/library digests.
- Private unchanged signing keystore/password retained outside source/worktree/logs.
  APK v3 verification, separate supported-SDK v2-block verification and16KiB alignment
  were checked historically; v2-range verification is **not API24 app support**.

Use a new owned POSIX path without spaces for NDK/Gradle output; a copied Kotlin
package named `build` must **not** be excluded as though it were a generated directory.
Preserve original workspaces and failed attempts. Do not blindly reuse old absolute
path, create-only-receipt or deletion-bearing historical scripts.

## 2. Custom owner and bridge

`integration/owner/` is the final ROM-only recovery006 Java source. It excludes root
mains, standalone bootstrap, obsolete phone Binder helpers and root shell transport.
`integration/framework/` is the final enabled bridge-v2 helper source, including the
intentional no-op bootstrap ABI. No servicemanager registration is needed.

The owner needs genuine target `com.android.server.SystemService` / SystemService
contracts and target framework APIs not exported by the public SDK. Build-only
contracts may be on compilation/D8 classpaths, but **never package stubs into the
production DEX**. Example compile-only commands (set paths yourself):

```sh
python3 -B scripts/build_java.py \
  --sources integration/owner --classpath "$TARGET_CLASSPATH" \
  --out "$NEW_OWNER_OUT"
python3 -B scripts/build_java.py \
  --sources integration/framework --classpath "$TARGET_CLASSPATH" \
  --out "$NEW_BRIDGE_OUT"
```

Optional external SDK d8/Android JAR parameters produce custom DEX only. Authenticate
full emitted declarations/references/return descriptors against genuine target APIs.
In particular `UiccCardInfo.getPorts():Collection` must remain exact.

Apply R1 and R2 patches only to fresh full target disassemblies after checking parent
hashes. Require `patch --batch --fuzz=0 -p1`, reject offsets/reject files, compare the
entire unaffected class/method scope and verify exact reversal. Partial disassemblies
are not full-DEX assembly inputs. R3 files are **custom method/field fragments**, not
complete proprietary classes: integrate their additions/wrappers into authenticated
original classes with original-body relocation and full method-scope comparison.
The B1 patch is **old-R3→v2 only**, not a stock baseline patch.

## 3. SystemServer lifecycle hook

On this target the original loader was inserted in `startOtherServices` after
`setSafeMode` and before device-specific services. Preserve that location and original
register/exception structure in a newly authenticated build, not a guessed ROM hook.
Add one private method with this operation, wrapped in a logged Exception handler:

```text
mSystemServiceManager.startServiceFromJar(
    "org.sourceowned.n3.runtime.v2.N3SystemService",
    "/system/framework/n3-runtime-v2.jar")
```

It starts a lifecycle service, **not a route operation**. No `addService`, default
service-label allowance, new BCP entry or phone bootstrap daemon is permitted.
Changing services.jar requires original non-DEX/raw ZIP record preservation,
affected services ART/profile/fsverity refresh and all26 changed-CLC dependents.
Compiler classfiles alone are not installable acceptance.

## 4. Provider007 unified patch, build and reference additions

Use `scripts/fetch_and_build_openeuicc.sh` and `patches/openeuicc-dada.patch` for
the complete selected provider007 source build, including modified upstream classes
and native LPAC/JNI sources. See the README rebuild section and
[complete inventory](OPENEUICC_PATCH.md). The script initializes pinned submodules
before ordinary root-worktree `git apply --check`/`git apply`; no parent gitlink bump.

`integration/provider-additions/` still preserves only custom files **absent from
the reference upstream tracked tree** as a reference subset. Do not overlay it on
top of the unified patch; those files are already included. It is not standalone.

Required integration seams in your separately obtained checkout:

1. Existing EuiccService remains the selected platform LPA. Scope service dispatch
   and download context with `PlatformDownloadFence`/fresh UI context, not a new LPA.
2. Register the exact protected N3 offer receiver and its direct synchronous UID1000
   callback. Do not substitute old UID0, reflected SID or PID-context candidates.
3. Register only the `MODIFY_PHONE_STATE`-protected legacy SECRET_CODE receiver for
   exact `android_secret_code://3418`; no launcher/QS tile. It opens Manage using
   NEW_TASK; sensitive activities use FLAG_SECURE and backup/direct-boot rules must
   remain intentionally configured. Existing service/resolution components keep
   their required `BIND_EUICC_SERVICE` protection; it is **not an app privileged grant**.
4. Wire explicit route-choice proposal/confirmation to N3SelectionSession and
   recovery-aware abort reporting. Never auto-confirm/retry. Close cached channels
   before route UI, invalidate/re-resolve on return and finalize stale entries even
   if remote channel close throws.
5. Hold Binder-lifetime N3 leases around profile/backend commands. Hide Delete and
   unsafe last-profile disable, reject Reset/OTA. The diff now includes modified
   upstream deletion/reset source, fenced by policy; it is never automatically
   exercised by the host build or validation.
6. After command acceptance request embedded-subscription refresh inside the lease;
   after profile-state verification re-resolve active logical1 context and issue
   scoped carrier-config/actions. `RegistrationObservationWindow` reports settled
   same-subscription **voice observations**, not guaranteed data recovery.

Keep versionCode/update continuity and the original local signer. A new identity
requires reviewing **both** owner `CallerPolicy` and bridge `ReleaseContract` pins,
selection/grants, upgrade compatibility and tests. Never embed the keystore/password
in Gradle files or commit a full upstream checkout, binaries or private build inputs.

N2 installs the APK under `/product/priv-app/OpenEUICC/` and the three-grant XML
under `/product/etc/permissions/`. Real eUICC feature XML is conditional on actual
hardware/provider capability, not a fake feature added to bypass dispatch.

## 5. Native init and policy

```sh
NDK_CC="$PINNED_AARCH64_CLANG" OUT_DIR="$NEW_NATIVE_OUT" sh scripts/build_native.sh
```

Always compile `N3_ROM`; the preserved source also contains historical root-mode
branches, but this recipe neither compiles those as production nor launches them.
Host fixtures replace execve and never execute mtb. Verify AArch64 hardened PIE,
dependencies/build fingerprint/fixed argv, peer authentication and startup FD checks.
Compiler changes can change the binary hash without validating a new runtime.

Install the native binary `/system/bin/n3-route-v2`, RC in system init and custom CIL
into consumed platform policy/debug mirror with matching contexts. Preserve socket
path, ownership0600, UID1000 and system_server SO_PEERSEC. Socket init seclabel names
the service domain; file_contexts has the dedicated path type. Actual merged-policy
socket labeling must be validated, not inferred from a file-context line alone.

Keep the corrected environment name and restart semantics. No KernelSU/module/
bind-mount launcher/nosuid_transition belongs in permanent policy. The local
content-derived policy selection ID is not canonical Android hashing or a neverallow
proof; permanent merged policy and enforcing boot remain independent acceptance gates.
The example scripts do **not** compile or parse binary SELinux policy.

## 6. Metadata and EROFS

Capture full authenticated original fs_config/file_contexts plus content/inode/xattr/
capability/link manifests. Materialize only in a new owned POSIX workspace. Preserve
exact known SELinux absences, original links/UUIDs/timestamps and unrelated content.
Source host chmod/mode does not authenticate Android metadata.

`scripts/repack_erofs.py` pins mkfs/fsck hashes from a local JSON map keyed by tool
basename. Supply captured UUID/time and unchanged logical extent; dedupe is enabled
**only for product/system** in the selected final recipe, not system_ext. It uses
HC12/max64KiB and `--mkfs-time --preserve-mtime`; failures remain in the new output
directory. It never mounts, deletes or touches originals.

```sh
python3 -B scripts/repack_erofs.py \
  --tree "$POSIX_TREE" --fs-config "$FS_CONFIG" --file-contexts "$FILE_CONTEXTS" \
  --mkfs "$PINNED_MKFS" --fsck "$PINNED_FSCK" --tool-pins "$TOOL_PINS" \
  --uuid "$ORIGINAL_UUID" --timestamp "$ORIGINAL_TIME" --mount-point /product \
  --extent-bytes 617418752 --dedupe --out "$NEW_EROFS_OUT"
```

This only establishes fsck/size. Require independent full mounted content/metadata
comparison, original superblock timestamp nanoseconds/features, max-pcluster/layout
checks and all selected pins before accepting. No sampling. Target kernel dedupe
support was observed; it does not prove all kernel/ROM combinations.

## 7. Exact raw super

`config/lp-layout.json` is the sanitized authoritative **single-extent** layout.
`scripts/exact_super.py` preserves the original prefix byte-for-byte, rejects sparse
inputs/overlaps/oversize/missing partitions, allocates exact hardware bytes and verifies
every payload/padding/unallocated zero range. Output is create-only and remains on
failure. It is not an LP parser or generic multi-extent builder.

```sh
python3 -B scripts/exact_super.py pack --layout config/lp-layout.json \
  --original "$ORIGINAL_RAW_SUPER" \
  --image odm_a="$ORIGINAL_ODM" --image product_a="$NEW_PRODUCT" \
  --image system_a="$NEW_SYSTEM" --image system_dlkm_a="$ORIGINAL_SYSTEM_DLKM" \
  --image system_ext_a="$NEW_SYSTEM_EXT" --image vendor_a="$ORIGINAL_VENDOR" \
  --image vendor_dlkm_a="$ORIGINAL_VENDOR_DLKM" --output "$NEW_RAW_SUPER"
```

Run `verify` independently with the same authenticated inputs. Separately require
pinned lpdump slots0–2 and semantic equality of **all14 entries,all six copies,both
geometries,attributes,groups and extents**; check payload mounts/pins and original
vbmeta flags1/flags0. Bytes/checksums alone are not bootloader acceptance or OEM AVB.
No fastboot/flasher is invoked by any build/repacking script.

## 8. Publication checks

Publication-only normalization removed whitespace on two owner blank lines and
split synthetic native/new Android test identifiers into string fragments;
production behavior and corrected native/RC/CIL bytes remain unchanged. Historical
test cases referencing the absent root diagnostics service were excluded. Patch
context bytes remain exact, with Git whitespace checking disabled **only for
`.patch` files**. Host tests retain their temporary fixtures without cleanup.

```sh
python3 -B tests/test_host_tools.py
python3 -B scripts/source_guard.py
python3 -B scripts/source_guard.py --index
```

Use an external private deny list for your serial/carrier/profile/activation/secret
values and review the index manually. Never stage raw audit/log inventories.
No broad license grant is asserted over interoperability patch contexts; see NOTICE.
