package com.android.internal.telephony.euicc;

import com.android.phone.esim.integration.DadaEsimPolicy;

/** Native OEM request is NOT an authenticated N3 consent token. No pretend SET ack. */
public final class NativeRouteIngress {
    private NativeRouteIngress() {}
    public static boolean rejectLegacyGpio() {
        return DadaEsimPolicy.useNativeManagement();
    }
    public static boolean rejectEvent(int event) {
        // Original OplusTelephonyExtServiceImpl packed-switch0x1790: GET6032 SET6033 power6034.
        // Scope only SET_GPIO. GET/errors and unrelated power/call/lock events unchanged.
        return event == 0x1791 && rejectLegacyGpio();
    }
}
