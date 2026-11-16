package com.android.phone.esim.integration;

/** Local bridge copy; identities authenticated against signed APK manifest/source. */
public final class ReleaseContract {
    static final String PACKAGE_NAME = "im.angry.openeuicc";
    public static final String SERVICE_CLASS = "im.angry.openeuicc.service.OpenEuiccService";
    public static final String LUI_CLASS = "im.angry.openeuicc.ui.LuiActivity";
    public static final String MANAGE_CLASS = "im.angry.openeuicc.ui.EuiccManageActivity";
    public static final String RESOLUTION_CLASS = "im.angry.openeuicc.ui.EuiccResolutionActivity";
    public static final String CURRENT_SIGNER_SHA256 =
            "879b92491d4b8623227a06e6f4dc63bbd298cd3ad91b3a1ca25d849da24bb3c0";

    private ReleaseContract() {}

    static DadaEsimPolicy.ProviderIdentity identity() {
        return new DadaEsimPolicy.ProviderIdentity(
                PACKAGE_NAME, SERVICE_CLASS, LUI_CLASS, MANAGE_CLASS, CURRENT_SIGNER_SHA256);
    }
}
