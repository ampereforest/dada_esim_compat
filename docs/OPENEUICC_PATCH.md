# Complete OpenEUICC patch inventory

`patches/openeuicc-dada.patch` is the selected provider007 personal source diff
against OpenEUICC `b79e03bbf42f4d69b9d9dd21cd6132bb1b9314d7`. It contains125
text-file diffs:107 parent-tree files and18 nested LPAC files. No binary JNI,
keystore, password, build output or local SDK properties are included.

The README's rebuild section explains behavior, build/signing and runtime limits.
Paths below are relative to the fresh upstream root. `SlotMappingFragment.kt` is
deleted **in the applied source patch**; original workspace copies remain intact.
Minimal valid context avoids publishing unmodified upstream carrier fixtures.
New synthetic test identifiers are constructed from fragments without changing
their values. Obsolete root-diagnostics endpoint test cases were excluded from
`LpaDiagnosticsDraftTest` and `N3RuntimeIntegrationDraftTest`; the absent service
was not restored. No byte-identical historical APK or Android-test PASS is implied.

## Shared app:42 paths

Under `app-common/`:

- `build.gradle.kts`
- `src/main/AndroidManifest.xml`
- `src/main/java/im/angry/openeuicc/core/DefaultEuiccChannelManager.kt`
- `src/main/java/im/angry/openeuicc/core/EuiccChannelManager.kt`
- `src/main/java/im/angry/openeuicc/core/EuiccOperationContext.kt`
- `src/main/java/im/angry/openeuicc/core/OmapiApduInterface.kt`
- `src/main/java/im/angry/openeuicc/core/ProfileOperationCoordinator.kt`
- `src/main/java/im/angry/openeuicc/core/usb/UsbApduInterface.kt`
- `src/main/java/im/angry/openeuicc/core/usb/UsbCcidContext.kt`
- `src/main/java/im/angry/openeuicc/core/usb/UsbCcidTransceiver.kt`
- `src/main/java/im/angry/openeuicc/di/AppContainer.kt`
- `src/main/java/im/angry/openeuicc/service/EuiccChannelManagerService.kt`
- `src/main/java/im/angry/openeuicc/ui/BaseEuiccAccessActivity.kt`
- `src/main/java/im/angry/openeuicc/ui/BoundEuiccAccessActivity.kt`
- `src/main/java/im/angry/openeuicc/ui/EuiccInfoActivity.kt`
- `src/main/java/im/angry/openeuicc/ui/EuiccManagementFragment.kt`
- `src/main/java/im/angry/openeuicc/ui/EuiccMemoryResetFragment.kt`
- `src/main/java/im/angry/openeuicc/ui/MainActivity.kt`
- `src/main/java/im/angry/openeuicc/ui/NotificationsActivity.kt`
- `src/main/java/im/angry/openeuicc/ui/ProfileDeleteFragment.kt`
- `src/main/java/im/angry/openeuicc/ui/ProfileRenameFragment.kt`
- `src/main/java/im/angry/openeuicc/ui/SettingsFragment.kt`
- `src/main/java/im/angry/openeuicc/ui/wizard/DownloadWizardActivity.kt`
- `src/main/java/im/angry/openeuicc/ui/wizard/DownloadWizardDetailsFragment.kt`
- `src/main/java/im/angry/openeuicc/ui/wizard/DownloadWizardDiagnosticsFragment.kt`
- `src/main/java/im/angry/openeuicc/ui/wizard/DownloadWizardProgressFragment.kt`
- `src/main/java/im/angry/openeuicc/ui/wizard/DownloadWizardSlotSelectFragment.kt`
- `src/main/java/im/angry/openeuicc/ui/wizard/SimplifiedErrorMessages.kt`
- `src/main/java/im/angry/openeuicc/ui/wizard/util.kt`
- `src/main/java/im/angry/openeuicc/util/ApduChannel.kt`
- `src/main/java/im/angry/openeuicc/util/EuiccChannelFragmentUtils.kt`
- `src/main/java/im/angry/openeuicc/util/LPAUtils.kt`
- `src/main/java/im/angry/openeuicc/util/StringUtils.kt`
- `src/main/java/im/angry/openeuicc/util/TelephonyCompat.kt`
- `src/main/java/im/angry/openeuicc/util/Vendors.kt`
- `src/main/res/values/phase3_strings.xml`
- `src/test/java/im/angry/openeuicc/service/EuiccChannelManagerServiceTest.kt`
- `src/test/java/im/angry/openeuicc/testutil/Mocks.kt`
- `src/test/java/im/angry/openeuicc/testutil/TestOpenEuiccApplication.kt`
- `src/test/java/im/angry/openeuicc/ui/BaseEuiccAccessActivityTest.kt`
- `src/test/java/im/angry/openeuicc/ui/wizard/DownloadTaskLauncherTest.kt`
- `src/test/java/im/angry/openeuicc/ui/wizard/DownloadWizardFlowTest.kt`

## Privileged app:46 paths

Under `app/`:

- `build.gradle.kts`
- `src/main/AndroidManifest.xml`
- `src/main/java/im/angry/openeuicc/PrivilegedOpenEuiccApplication.kt`
- `src/main/java/im/angry/openeuicc/core/Logical1SelectionOwner.kt`
- `src/main/java/im/angry/openeuicc/core/PrivilegedEuiccChannelFactory.kt`
- `src/main/java/im/angry/openeuicc/core/PrivilegedEuiccChannelManager.kt`
- `src/main/java/im/angry/openeuicc/core/ProfileSwitchRecovery.kt`
- `src/main/java/im/angry/openeuicc/core/RegistrationObservationWindow.kt`
- `src/main/java/im/angry/openeuicc/core/TelephonyManagerApduInterface.kt`
- `src/main/java/im/angry/openeuicc/di/PrivilegedAppContainer.kt`
- `src/main/java/im/angry/openeuicc/n3/N3Gateway.kt`
- `src/main/java/im/angry/openeuicc/n3/N3ProfileCoordinator.kt`
- `src/main/java/im/angry/openeuicc/n3/N3Report.kt`
- `src/main/java/im/angry/openeuicc/n3/N3SelectionSession.kt`
- `src/main/java/im/angry/openeuicc/service/EuiccSecretCodeReceiver.kt`
- `src/main/java/im/angry/openeuicc/service/LpaDiagnostics.kt`
- `src/main/java/im/angry/openeuicc/service/N3EndpointReceiver.kt`
- `src/main/java/im/angry/openeuicc/service/OpenEuiccService.kt`
- `src/main/java/im/angry/openeuicc/service/PlatformDownloadFence.kt`
- `src/main/java/im/angry/openeuicc/ui/DadaRouteChoiceActivity.kt`
- `src/main/java/im/angry/openeuicc/ui/EuiccManageActivity.kt`
- `src/main/java/im/angry/openeuicc/ui/EuiccResolutionActivity.kt`
- `src/main/java/im/angry/openeuicc/ui/LuiActivity.kt`
- `src/main/java/im/angry/openeuicc/ui/PrivilegedEuiccManagementFragment.kt`
- `src/main/java/im/angry/openeuicc/ui/PrivilegedMainActivity.kt`
- `src/main/java/im/angry/openeuicc/ui/RouteChoiceSafety.java`
- `src/main/java/im/angry/openeuicc/ui/RouteChoiceSession.java`
- `src/main/java/im/angry/openeuicc/ui/SlotMappingFragment.kt` (removed)
- `src/main/java/im/angry/openeuicc/util/EuiccUiContextResolver.kt`
- `src/main/java/im/angry/openeuicc/util/PrivilegedTelephonyCompat.kt`
- `src/main/java/im/angry/openeuicc/util/PrivilegedTelephonyUtils.kt`
- `src/main/java/im/angry/openeuicc/util/PrivilegedUtils.kt`
- `src/main/java/org/sourceowned/n3/runtime/v2/Protocol.java`
- `src/main/java/org/sourceowned/n3/runtime/v2/RoutingOwnerClient.java`
- `src/main/res/layout/activity_euicc_manage.xml`
- `src/main/res/layout/activity_n3_selection.xml`
- `src/main/res/values/n3_strings.xml`
- `src/main/res/values/strings.xml`
- `src/main/res/xml/private_data_extraction_rules.xml`
- `src/test/java/im/angry/openeuicc/core/Logical1SelectionOwnerTest.kt`
- `src/test/java/im/angry/openeuicc/core/RegistrationObservationWindowTest.kt`
- `src/test/java/im/angry/openeuicc/n3/N3RepeatableRouteTest.kt`
- `src/test/java/im/angry/openeuicc/n3/N3RuntimeIntegrationDraftTest.kt`
- `src/test/java/im/angry/openeuicc/service/EuiccSecretCodeReceiverTest.kt`
- `src/test/java/im/angry/openeuicc/service/LpaDiagnosticsDraftTest.kt`
- `src/test/java/im/angry/openeuicc/service/OpenEuiccServiceDownloadTest.kt`

## JNI:17 paths

Under `libs/lpac-jni/`:

- `build.gradle.kts`
- `src/main/java/net/typeblog/lpac_jni/EuiccInfo2.kt`
- `src/main/java/net/typeblog/lpac_jni/LocalProfileAssistant.kt`
- `src/main/java/net/typeblog/lpac_jni/LocalProfileInfo.kt`
- `src/main/java/net/typeblog/lpac_jni/LpacJni.kt`
- `src/main/java/net/typeblog/lpac_jni/ProfileClass.kt`
- `src/main/java/net/typeblog/lpac_jni/RemoteProfileInfo.kt`
- `src/main/java/net/typeblog/lpac_jni/impl/HttpInterfaceImpl.kt`
- `src/main/java/net/typeblog/lpac_jni/impl/LocalProfileAssistantImpl.kt`
- `src/main/jni/lpac-jni.mk`
- `src/main/jni/lpac-jni/interface-wrapper.c`
- `src/main/jni/lpac-jni/interface-wrapper.h`
- `src/main/jni/lpac-jni/lpac-download.c`
- `src/main/jni/lpac-jni/lpac-download.h`
- `src/main/jni/lpac-jni/lpac-jni.c`
- `src/main/jni/lpac-jni/lpac-jni.h`
- `src/main/jni/lpac-jni/lpac-notifications.c`

## Nested LPAC:18 paths

Under `libs/lpac-jni/src/main/jni/lpac/euicc/`:

- `base64.c`, `base64.h`
- `derutil.c`, `derutil.h`
- `es10a.c`
- `es10b.c`, `es10b.h`
- `es10c.c`, `es10c.h`
- `es10c_ex.c`, `es10c_ex.h`
- `es8p.c`, `es8p.h`
- `es9p.c`
- `euicc.c`, `euicc.private.h`
- `hexutil.c`
- `interface.c`

## Root configuration:2 paths

- `privapp_whitelist_im.angry.openeuicc.xml`: remove unrelated grants, retain exactly
  READ_PRIVILEGED_PHONE_STATE, WRITE_EMBEDDED_SUBSCRIPTIONS and MODIFY_PHONE_STATE.
- `settings.gradle.kts`: freeze dynamic generatebp dependency at1.4.

## Validation ledger

Release compilation, Android unit tests, ZIP/signature checks and real device
acceptance are different gates. Host-source/privacy tests do not establish
unrooted switching, merged-policy acceptance, OEM AVB or upgrade continuity.

Publication validation performed from a **fresh network fetch** with initialized
submodules and copied external toolchains/cache (original inputs untouched):

| Gate | Result |
| --- | --- |
| Corrected upstream/submodule pins; clean pre-apply checkout | PASS |
| Root `git apply --check`, application and reverse check | PASS |
| Fresh `:app:assembleRelease`, native compilation for all four ABIs | PASS;232 tasks executed |
| Four nonempty, uncompressed JNI libraries packaged | PASS |
| Generated RSA4096 PKCS12,16KiB ZIP alignment before/after signing | PASS |
| v3 verification at minSdk31; independent v2 verification over SDK24–27 | PASS; SDK24–27 is a signing-block check, not app support |
| `:app:compileReleaseUnitTestKotlin`, `:app-common:compileReleaseUnitTestKotlin` | PASS; remaining tests compile, not a claim they executed |
| Host tools | PASS;4 tests including35 synthetic native endpoint assertions |

Android unit-test execution was **not** part of this validation. Compiler
deprecation warnings remain. The v2-range verifier reports an unknown scheme-ID3
reference while validating v2; v3 was checked independently. Original APK/key,
new outputs/keys, source copies and failed sanitization evidence remain private
and retained. The script preserves the obsolete source before applying its removal;
no cleanup, policy compilation, installation or device operation was performed.
