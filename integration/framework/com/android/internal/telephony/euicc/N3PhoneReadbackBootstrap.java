package com.android.internal.telephony.euicc;

/** Called outside original PhoneFactory init lock, after successful original initialization. */
public final class N3PhoneReadbackBootstrap {
    private N3PhoneReadbackBootstrap() {}
    public static synchronized void afterOriginalPhoneInit() {
        // Existing TelephonyManager map/card-port readbacks are sufficient for N3.
        // Keep the call-site ABI without registering a new servicemanager name.
    }
}
