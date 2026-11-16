package com.android.phone.esim.integration;

import android.app.Activity;
import android.content.Intent;

/** Explicit unresolved Android ownership boundary; never a successful adapter stub. */
final class NativeLogical1Gate {
    private NativeLogical1Gate() {}

    static void acquireForManagement(Activity activity, Intent incoming) {
        throw new UnsupportedOperationException(DadaEsimPolicy.UNAVAILABLE);
    }
}
