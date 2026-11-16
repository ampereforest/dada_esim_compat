# Xiaomi 15 (`dada`) · ColorOS 17 V6 · eSIM / pSIM2 integration

A **standalone personal architecture and developer guide**, not an upstream
OpenEUICC contribution, official ROM, turnkey installer or firmware download.
It records a narrowly scoped integration of a maintained LPA with Xiaomi routing
hardware under a ported Oplus framework. Only documentation, custom integration
sources, small patches, init/SELinux/XML configs and host scripts are included.

**Current evidence boundary:** corrected images passed complete host packaging
checks. A corrected native listener passed real-init routing and automatic restart
with **temporary root bind-mount/policy glue**. Corrected permanent ROM reflash,
fresh unrooted switching, neverallow compliance and new OEM AVB authentication
are **not proved**. Do not infer production safety from compilation or this guide.

## Contents

- [Baseline and SIM geometry](#baseline-and-sim-geometry)
- [R1/R2/R3/N1/N2/N3 architecture](#integration-layers)
- [Authentication, journal and leases](#permanent-owner-and-authentication)
- [Native init correction](#native-init-correction)
- [ART, profiles, CLC and fsverity](#art-profiles-clc-and-fsverity)
- [Metadata, EROFS and exact LP super](#metadata-erofs-and-lp-preservation)
- [Flash findings and manual sequence](#flash-findings--manual-sequence)
- [Operational caveats](#operational-caveats)
- [Build/repacking guide](docs/DEVELOPER.md), [evidence ledger](docs/OUTCOMES.md),
  [physicalSlot0 / native Settings roadmap](docs/ROADMAP.md)

## Baseline and SIM geometry

| Item | Target |
| --- | --- |
| Hardware | Xiaomi 15, `dada`, model `24129PN74G` |
| Port baseline | ColorOS 17 V6 CN, OnePlus `PLZ110` / `OP64DDL1` |
| Platform | Android 17, API 37; active slot `_a` |
| Kernel baseline requested for this work | `6.6.118`; not a general 6.6.x compatibility claim |
| Exact fingerprint | `OnePlus/PLZ110/OP64DDL1:17/CP2A.260605.016/B.2a5e7c5-41b0cd-45d7d3:user/release-keys` |
| Upstream reference only | [PeterCxy/OpenEUICC](https://gitea.angry.im/PeterCxy/OpenEUICC), actual checkout commit `b79e03bbf42f4d69b9d9dd21cd6132bb1b9314d7` |

The full commit above was verified from the actual checkout; a previously supplied
shortened/mistyped identifier is not the build pin. Fetch upstream independently
if needed; **no upstream OpenEUICC source or binaries are bundled here**.

Tuple order is **`port:physicalSlot:logicalSlot`**, not slot:port:phone:

| Choice | Complete mapping | Fixed vendor route argv (reference, not automatic execution) |
| --- | --- | --- |
| pSIM1 invariant | `0:0:0` | Never changed by this integration |
| pSIM2 | `[0:0:0, 0:1:1]` | `/vendor/bin/mtb 5 10 2 0` |
| eSIM | `[0:0:0, 1:1:1]` | `/vendor/bin/mtb 5 10 1 0` |

pSIM2 and eSIM share **physicalSlot1 / logicalSlot1** through different ports;
they are not three simultaneously independent logical modems. A current complete
map and matching active UICC card/port are required. Historical `cardId=-2`, an
absent card, stale EID or an app cache is not a durable routing identity. Physical0
was absent in many tests: preserving its tuple does **not** prove pSIM1 service.

Routing is distinct from profile management. Choosing pSIM2 must not erase or
gratuitously disable eSIM profiles. Selecting an already verified route must emit
**zero modem/map mutations** (`MATCH_NO_WRITE`); a journal update is not a modem
operation. Boots/restarts must not replay a saved route command.

> **Never invoke `/vendor/bin/mtb 5 10 5 0`.** That selector is the destructive
> eUICC reset/erase path, not a routing refresh, disable command or recovery tool.
> The native endpoint accepts only destination 0/1 and constructs only selectors
> 2/1. No deletion/reset workflow belongs in validation or rollback.

## Integration layers

| Layer | Purpose and shipped personal material | Important boundary |
| --- | --- | --- |
| **R1** | Exact-board/build guard around Oplus's **post-validation forced physical-type SET**, `patches/r1/` | Keep original startup, GET, phone-count and error paths; all other builds retain original behavior. No guessed early setter. |
| **R2** | Protected management/provider bridge in `telephony-common`, native OEM GPIO/6033 fencing, `integration/framework/`, `patches/r2/` | Stock TeleService and its platform/sharedUID signature stay intact. No permission/signature exemptions. |
| **R3** | Fresh **per-card** embedded-subscription reconciliation, custom method fragments under `integration/r3-methods/` | Failure/unknown/incomplete inventory never means successful empty inventory or authority to clear another card. |
| **N1** | Maintained OpenEUICC's existing EuiccService/LPA, with custom routing/lease/recovery/context additions under `integration/provider-additions/` | These additions are not an independent complete app; obtain and build upstream separately. |
| **N2** | Three demonstrated product privileged grants and real eUICC feature XML under `config/etc/permissions/` | APK and grant XML must occupy the **same product partition**; no permission catch-all. |
| **N3** | Single ROM-owned logical1 coordinator in genuine `system_server` plus authenticated init native listener | Durable admission/consent/readbacks/leases, not a root selector or modem replay service. |

Conditional C1 capability exposure requires actual provider/hardware evidence.
C2 early-route repair remains unjustified: R1 persistence did not establish an
independent initial-routing defect. C3 native UI integration is bounded by ingress
and legitimate signing feasibility. C4 metadata, image size, integrity and affected
ART/AVB obligations cover **every** image-affecting edit. An XML-only change does
not automatically imply unrelated ART recompilation.

### R2: bridge-v2 instead of re-signing TeleService

Original EuiccConnector permission validation runs before custom installed-package,
component, signer and grant checks; binding revalidates the approved provider.
ConnectedState dispatch permits only supported commands behind context/identity
gates, including metadata/download/switch/nickname as applicable. Delete, Reset
and OTA mutation remain denied. Physical0 ordinary work is admitted only when
positively classified; unknown or logical1 contexts fail closed.

The genuine `UiccCardInfo.getPorts()` descriptor returns **Collection**, not List.
Historical helper002 linked to List and remains blocked. Helper003 corrected the
descriptor, but its static/API closure alone was never end-to-end integration.
The final **enabled bridge-v2 pair** supersedes early helper/bootstrap experiments;
it retains a no-op `N3PhoneReadbackBootstrap` call-site ABI and packages **no obsolete
phone Binder helpers and no new servicemanager names**. Historical registration
scaffolds must not be reintroduced.

The stock OEM master switch can disable a profile **before** GPIO routing and
discard setter failures. Fencing a later setter cannot undo that prior mutation.
It is therefore **not a safe route-only UI**, even when a framework bridge exists.
Native Settings is a roadmap, not a completed unrestricted interface.

### R3: authoritative per-card inventory and B1 correction

Snapshot card/slot object identities, EID, card ID and process-local generations;
obtain fresh complete **unfiltered** inventory; revalidate identity after waits
and again before any card-bound sweep. A successful authoritative zero-profile
result may clear stale **metadata for that card only**, never hardware profiles.
Transport/native/parser failures, duplicate/incomplete records, unknown identity
and stale generations defer. Ordinary readiness, ICCID reuse and loaded events
must keep their established behavior. Process-local generations are observations,
not native completeness attestations or power-loss durability.

The original R3 candidate had source-derived **B1 lock inversion**: async DB
initialization could hold the instance monitor while waiting for its write lock;
reconciliation could hold acceptance/write locks while waiting for that monitor
during insertion. No actual Android deadlock was observed.

Corrected v2 reads monotonic `mDatabaseInitialized` under the same instance monitor
**before any acceptance-lock setup**, releases the monitor, defers false, then
retains controller → slot/card → DB write acceptance and post-wait identity checks.
See `patches/r3/b1-readiness-before-locks.patch`. Finite/interpreter tests cover the
source-bound cycle but do not establish global runtime deadlock freedom.

The historical global preservation check failed and attribution remains unknown.
Its later scoped waiver was permission to integrate the pinned candidate, **not a
retroactive PASS**. Source replay also had a 33-byte assembler layout/integrity
difference; byte-deterministic source→DEX reproduction is not claimed.

## Permanent owner and authentication

The final ROM-only Java owner is **recovery006**, and the final provider integration
is **provider007** (provider006 is the prior direct-UID handoff step).

1. A private SystemServer lifecycle hook uses existing
   `SystemServiceManager.startServiceFromJar` to load
   `/system/framework/n3-runtime-v2.jar`. `N3SystemService` starts after activity
   manager readiness, on a worker, with no routing on boot.
2. An explicit permission-protected broadcast offers a Binder to the provider.
   Provider007 authenticates the **direct synchronous callback UID1000**, descriptor,
   protocol and bounded payload. This proves the system **shared UID**, not uniquely
   system_server; reflected SID/PID-context APIs are not a working dependency.
3. Owner-side callers must be the sole privileged installed provider package,
   with the pinned release signer, refreshed package/grant facts and foreground
   consent/safety. Root/shell/app proxies are not production authority.
4. The native socket separately requires **UID1000 AND `u:r:system_server:s0` via
   SO_PEERCRED/SO_PEERSEC**. The provider has no direct native/modem admission.

The reference public provider certificate digest is
`879b92491d4b8623227a06e6f4dc63bbd298cd3ad91b3a1ca25d849da24bb3c0`.
It is public identity, not a private key. The original local encrypted RSA4096
PKCS12 identity/password remains private and must be preserved for update continuity.
A new signer requires intentional repinning and legitimate upgrade handling; it
does not permit signing a sharedUID-compatible replacement TeleService without
the OEM private key.

### Durable transaction / recovery semantics

- Exclusive owner lock and journal under `/data/system/n3-routing-v2`, directory
  `0700`, files `0600`, system-owned. Durable PREPARED intent precedes hardware
  admission; staged file sync/atomic replacement/directory sync protect ordering.
- Slow fsync must not make stale consent sufficient: refresh authentication,
  complete-map and call/SIM/airplane/foreground safety before admitting a change.
- Native replies distinguish NOT_STARTED, ACK and AMBIGUOUS. **Exit0, EID text,
  GPIO setter return or joined task are not route completion.** After submission,
  transient map failure remains pending; there is no blind repeat.
- COMMITTED requires **three consecutive fresh complete map AND active card-port
  matches**. Already matching OEM map avoids a redundant setter.
- Recovery can reconcile matching destination evidence without replay. Recovery006
  can also verify that the **original route is stable before aborting/clearing
  pending**. `ABORTED_ORIGINAL_ROUTE` is **not COMMITTED**, not an automatic corrective
  modem switch and not blanket self-healing hardware rollback.
- Unresolved journal/store/identity states fail closed. Reboot, daemon death or
  repeated read-only status never supplies fresh user consent for another mutation.

### LPA leases

Route and LPA operations share a non-reentrant admission gate. Binder-lifetime,
bounded leases bind operation/card/port/generation and freshly reobserve context.
Death, revocation, expiry and recreation do not resume a saved capability. Quiescent
reconciliation is not proof of backend/profile success. Profile operations close
stale APDU channels before routing and re-resolve on return; cache finalization
must survive a remote invalid-channel close exception. This fencing controls this
integration's owner/provider, **not every vendor/internal writer globally**.

## Native init correction

Init's descriptor publishing replaces punctuation with underscores:

```text
socket pathname:       /dev/socket/n3-route-v2
init environment key:  ANDROID_SOCKET_n3_route_v2
old incorrect lookup:  ANDROID_SOCKET_n3-route-v2
```

The old native exited on missing/malformed socket FD environment. The corrected
source uses the sanitized key while retaining root/build/fixed-path/0600 system
socket checks, descriptor type/address validation, close-on-exec, authenticated
peer checks, pinned mtb identity in Java and fixed argv/five-second deadline.
Startup diagnostics contain fixed stage codes, **not profiles or modem payloads**.

`config/system/etc/init/n3-route-v2.rc` removes `oneshot` and the permanent attempted
latch, retains `disabled`, explicitly starts on boot with a framework-ready fallback,
and sets `restart_period 5`. `start` is idempotent for a running listener. A listener
restart **only listens**; normal init reaps the process group. An unresolved child
halts native admission, and Java's durable journal remains authoritative.

Permanent CIL adds observed init→n3_route **noatsecure**, socket-directory **getattr**,
and mtb self qipcrtr_socket **setopt**, alongside dedicated types/transition/socket
edges. The temporary `/data` bind-mount **nosuid_transition is excluded**. The CIL
includes the existing system/vendor-execution exception attribute; that is a
compatibility concern, **not proof of neverallow compliance**.

Merge into consumed platform CIL; mirror into the existing debug CIL where required,
add file/property contexts and update policy-selection identity consistently. The
local content-derived platform CIL+mapping ID deliberately mismatched stale ODM
precompiled metadata. This is **not the canonical Android hashing recipe**, and
neither policy loading nor neverallow validation follows from a textual match.
No policy compiler/binary policy parser was run during this publication.

## ART, profiles, CLC and fsverity

Changing `telephony-common` at canonical BCP index9 affects boot dependencies;
changing services for the owner loader affects dependent **class loader contexts**.
A standalone `PCL[]` R1 probe is not sufficient for an R2/R3 combined stack.

- Target formats include ART119 / OAT275 / VDEX027. Preserve original DEX locations,
  ISA/features, filters, collector, OAT locations, app-image links, profile semantics
  and startup counters, not just paths or superficial checksums.
- Remap profiles by descriptors against changed DEX and validate H/S/P flags,
  inline-cache PCs/receivers and identities. The services retry canonically compared
  receiver **set order only**, leaving actual membership/flags/PC checks strict.
  `bprof` is length-prefixed binary locations/ordered pairs, not a text profile.
- Build three **fresh `-j1` variants**: normal ARM, normal ARM64 and Oplus ARM64.
  Normal variants remain **non-CC**. Explicit `--runtime-arg -Xgc:CC`, OEM integer
  flag/reason and Xmx512m apply to the selected **Oplus CC path/extension**, not all
  variants. A failed Xmx1536m CC extension needed a contiguous **3072MB** reservation
  (twice Xmx); no allocator check was bypassed.
- Older `-j2` verifier-dependency byte disagreements remain evidence. Fresh `-j1`
  output produced **24 byte-identical shared VDEXs** across the three variants,
  confirmed against matching structures/full loads without rewriting bytes.
  Publish shared targets once, preserve **144 ISA symlink targets**, never mix sets.
- All **42 saved mapped ODEX families** had isolated matching loads. AppSearch's
  captured profile produced `[0,3]` rather than original `[0,0]`; a **fresh explicitly
  no-profile** verify/app-image retry preserved `[0,0]`. This was not a profile edit
  or retroactive acceptance of the failed output.
- The services loader changed the services CLC checksum; **26 dependent families**
  were refreshed and matching-load checked, with **57** dependent companions.
- Athena, Stk, TeleService and Telecom old installed caches were stably captured
  against **old installed boot dependencies**. Fresh four-owner scratch recipes
  subsequently passed isolated compiles/loads; neither establishes in-place old-cache
  replacement compatibility or undocumented OEM FD/profile selection.
- Broader historical closure remains unresolved: **414 additional OAT275 families**,
  an OAT259 outlier, **171 VDEX-only** candidates, missing QTI-location profiles and
  an original ARM Oplus own-DEX mismatch. Do not infer closure from selected loads.

`.fsv_meta` requires independent **full descriptor, data-size, root and Merkle-tree
byte comparison**. One original R1 companion described a different JAR size; retain
that failure instead of pretending it reproduces. Empty-tree companions can be
only268 bytes; do not invent padding. Preserve original unsigned container mode
`(0,0)` when generating selected companions: that is not OEM signing/trust approval.
Selected boot/JAR companions, shared-VDEX layout and new services companions were
checked separately. There is no global ART-cache deletion recipe here.

## Metadata, EROFS and LP preservation

NTFS/exFAT host permissions are **not Android image metadata**. Capture manifests
from authenticated original read-only images, then use an owned **POSIX ext4**
workspace for copies/builds. Keep numeric uid/gid, type/mode, capabilities, all xattrs
(including exact SELinux absences), links/nlink, UUIDs and inode/build timestamps.
Never chmod through a symlink. ext4's fixed symlink0777 host behavior does not replace
an original symlink mode recorded in fs_config.

Original metadata validation covered16,240 entries,431 links and48,723 actual label
lookups; seven complete original trees later covered23,872 entries. Three original
unlabeled system PNGs remain exact `<<none>>` exceptions; manifest v1 was rejected
and corrected v2 used. Original payloads and pinned V5 tools remain immutable.

The corrected three-partition build used pinned Android-capable EROFS V5 tools,
`lz4hc,level=12`, maximum64KiB pclusters, original UUID/build+inode timestamps and
the selected existing product/system dedupe recipes. Original images prove LZ4
format, **not the original encoder's level**. Partition-specific compression/fit
hints must be retained rather than applying one guessed generic recipe everywhere.
Product/system dedupe was introduced in earlier fixed-extent fit passes and then
retained for the corrected rebuild; it is not asserted to be original V6 feature parity.
fsck plus a **full mounted path/content/inode/xattr/link comparison** passed; mere
fsck/size success from the included example script does not reproduce that gate.

### Exact raw super, not auto-sized lpmake output

| LP invariant | Preserved value |
| --- | --- |
| Hardware size | **11,811,160,064 bytes** |
| Version / slot metadata | LP10.2, metadata max65536, **3 slots**, virtual_AB |
| Geometry | logical block4096, alignment1048576, offset0, first logical sector2048 |
| Authenticated prefix | **405,504 bytes**, both geometries and all **six** primary/backup copies |
| Tables | **14 entries**, seven populated `_a`, seven empty `_b`; exact attributes/extents |
| Groups | default maximum0; qti_dynamic_partitions_a/b each maximum11,811,160,064 on **one** physical device; never sum them |

Preserve all seven original physical extents. Rebuild only product/system_ext/system;
odm, system_dlkm, vendor and vendor_dlkm stay original. Verify every payload, logical
padding and unallocated zero range, then pinned lpdump slots0–2. Embedded `my_*`
directories are **not invented LP partitions**. A5456-byte metadata-only lpmake
export is not raw super; `file` saying `data` is not an LP validity verdict.

The final local publication authenticated **482 payload pins** and144 links. Image
hashes are listed in [the outcomes ledger](docs/OUTCOMES.md); binaries stay local.

## Flash findings — manual sequence

**Destructive manual reference requested by the owner; not executed by this
repository or publication assistant.** It erases user data and metadata. Back up
first; verify the exact target, unlocked bootloader, original V6 inputs and final
hashes. Disconnected devices/implicit target selection are unsafe. No bundled
flasher was executed. Recovery and acceptance remain the owner's responsibility.

```text
fastboot flash super super.img
fastboot erase userdata
fastboot erase metadata
fastboot reboot
```

For real use, bind **each** fastboot command to `-s <ADB_SERIAL>` after independently
confirming its fastboot identity. This block preserves the requested ordering, not
permission to select whichever phone happens to be attached. No script here runs it.

Retain the **byte-identical original V6 vbmeta flags1 / vbmeta_system flags0** pair.
The separate flags3 pair was hardware-rejected in a fastboot loop; originals restored
boot according to the owner's report. Do not retry flags3 as a generic fix. Causation
was not conclusively isolated to one flag/chain/signature condition.

Original top-level signed digest mismatched its supplied flags1 header but matched
after flags0 restoration in an isolated inspection; the original vbmeta_system
digest matched. Several historical hashtree descriptors already differ from/exceed
current logical payloads. Preserving originals is **not new OEM signing**, not
stale-descriptor repair and not authenticated AVB for modified images. No OEM private
key exists in this release.

Host `make_f2fs`1.16.0 failed exit255 with `Error: Sparse mode is only supported for
android`; subsequent device init/fs_mgr formatting and the prior clean-data Enforcing
boot are **user-reported**, not assistant-witnessed flashing. Corrected permanent
RC/CIL/new-super boot has not been demonstrated.

## Operational caveats

- Working ingress is the permission-protected legacy secret code **3418** / explicit
  provider route-choice UI, with `NEW_TASK`, `FLAG_SECURE`, no launcher and no QS tile.
  Unlock normally; black screenshots are intentional, not missing UI evidence to bypass.
- Close/re-resolve LPA channels on route changes. Use fresh complete map/card-port
  observations, idle call/SIM/airplane/consent gates and fail closed on unknown data.
- Normal profile re-registration is **approximately20 seconds in some observations**,
  not a guaranteed deadline. Other observations recovered near6 seconds, a historical
  restore near164 seconds, or included brief data dips. Five-second settling plus
  three same-subscription **voice** samples is not voice+data completion.
- After successful profile command acceptance, the provider requests embedded
  subscription refresh inside the lease, re-resolves active subscription and requests
  scoped carrier-config/actions. No hidden-test-API bypass, airplane toggle, blanket
  radio reset, automatic profile replay or reset selector is used.
- A Qualcomm `.qtidataservices` `NetworkCallback was not registered` crash occurred
  around some switches, before explicit carrier refresh in a recorded case. Causation
  is unknown. A good restoration window does not erase that crash or establish
  **crash-free/all-switch reliability**, call operation or actual data-transfer tests.
- No live activation/download with valid SM-DP+ input, last-profile disable, artificial
  contention/death injection or deletion/reset acceptance is claimed.
- Root NoMount/module success, Enforcing mode and matched installed hashes are useful
  evidence but not fresh unrooted init/policy/OEM AVB acceptance. Historical init-child
  aborts/app-image rejections remain limitations, not silently classified clean boots.
- All device identities/profile/carrier/subscriber records are omitted. If sharing
  your own evidence, use `<ADB_SERIAL>`, `<REDACTED_ICCID>`, `<REDACTED_IMSI>`,
  `<REDACTED_MSISDN>`, `<REDACTED_EID>` and `<REDACTED_ACTIVATION_CODE>`.

## Repository safety and scope

Run `python3 -B tests/test_host_tools.py` and `python3 -B scripts/source_guard.py`
before staging; repeat the guard with `--index` before committing. The guard enforces
UTF-8 source-only content and **strictly less than10,000,000 bytes**; private deny-list
input must remain outside the repository. Pattern scanners do not replace manual
review of carrier names, screenshots, logs, serials or upstream file provenance.

No firmware/APK/JAR/DEX/native binaries, full proprietary disassemblies, upstream
OpenEUICC checkout, private audit outputs, toolchains or signing material are shipped.
The selected custom additions are not a turnkey reproducer of every historical build.
All old originals, accepted artifacts, failed receipts and POSIX/toolchain/signing
workspaces remain local. **No workspace cleanup is authorized by publishing this
repository; selective deletion requires separate explicit approval.**
