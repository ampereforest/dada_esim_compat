package org.sourceowned.n3.runtime.v2;

import android.content.Context;
import android.os.Bundle;

/** Source-backed Binder wrapper; NOT an uncached GET_SIM_TYPE result. */
public final class OplusRouteReadback {
    // Original OplusTelephonyExtServiceImpl.requestTelephonyEvent packed-switch 0x1790 -> getEsimGpio.
    private static final int GET_ESIM_GPIO = 0x1790;
    private final Context context;
    private final BoundedCalls calls = new BoundedCalls();
    public OplusRouteReadback(Context context) { this.context = context; }
    public Bundle read() {
        Bundle result = new Bundle();
        result.putString("source", "OPLUS_EVENT_0x1790_gpioStatus");
        result.putBoolean("fresh_type_callback", false);
        try {
            Integer value = calls.get(() -> {
                Class<?> manager = Class.forName("android.telephony.OplusTelephonyManager");
                Object instance = manager.getMethod("getInstance", Context.class).invoke(null, context);
                Object reply = manager.getMethod("requestForTelephonyEvent", int.class, int.class, Bundle.class)
                        .invoke(instance, 1, GET_ESIM_GPIO, new Bundle());
                if (!(reply instanceof Bundle) || !((Bundle) reply).containsKey("gpioStatus"))
                    throw new IllegalStateException("missing gpioStatus");
                Object status = ((Bundle) reply).get("gpioStatus");
                if (!(status instanceof Integer)) throw new IllegalStateException("invalid gpioStatus");
                return (Integer) status;
            }, 3000);
            result.putInt("gpio_status", value);
            result.putString("status", value == 0 || value == 1 ? "REPORTED_MAY_BE_CACHED" : "UNAVAILABLE_VALUE");
        } catch (Exception failure) { result.putString("status", "UNAVAILABLE_OR_TIMED_OUT"); }
        return result;
    }
}
