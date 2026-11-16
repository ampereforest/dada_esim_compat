package org.sourceowned.n3.runtime.v2.dada;

import android.telephony.TelephonyManager;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import org.sourceowned.n3.runtime.v2.core.Contracts;
import org.sourceowned.n3.runtime.v2.core.Model;

/** Use the maintained platform method, not a build-guessed raw Binder transaction number. */
public final class AndroidDadaMapping {
    private final TelephonyManager telephony;
    private final Contracts.MonotonicClock clock;

    public AndroidDadaMapping(TelephonyManager telephony, Contracts.MonotonicClock clock) {
        if (telephony == null || clock == null) throw new NullPointerException();
        this.telephony = telephony;
        this.clock = clock;
    }

    public Model.FullMap read() throws ReflectiveOperationException {
        Object result = TelephonyManager.class.getMethod("getSimSlotMapping").invoke(telephony);
        if (!(result instanceof Collection)) throw new IllegalStateException("Mapping unavailable");
        List<Model.Row> rows = new ArrayList<Model.Row>();
        Class<?> rowClass = Class.forName("android.telephony.UiccSlotMapping");
        for (Object row : (Collection<?>) result) {
            rows.add(new Model.Row((Integer) rowClass.getMethod("getPortIndex").invoke(row),
                    (Integer) rowClass.getMethod("getPhysicalSlotIndex").invoke(row),
                    (Integer) rowClass.getMethod("getLogicalSlotIndex").invoke(row)));
        }
        return Model.FullMap.of(rows);
    }

    public DadaBoardAdapter.Submission submit(Model.FullMap mapping, Contracts.Window window) {
        if (mapping == null || window == null || window.expired(clock.nowNanos())) {
            return DadaBoardAdapter.Submission.NOT_STARTED;
        }
        final Method setter;
        final List<Object> rows = new ArrayList<Object>();
        try {
            Class<?> type = Class.forName("android.telephony.UiccSlotMapping");
            Constructor<?> ctor = type.getConstructor(int.class, int.class, int.class);
            setter = TelephonyManager.class.getMethod("setSimSlotMapping", Collection.class);
            for (Model.Row row : mapping.rows()) {
                rows.add(ctor.newInstance(row.port, row.physicalSlot, row.logicalSlot));
            }
        } catch (ReflectiveOperationException | RuntimeException unavailable) {
            return DadaBoardAdapter.Submission.NOT_STARTED;
        }
        if (window.expired(clock.nowNanos())) return DadaBoardAdapter.Submission.NOT_STARTED;
        try {
            // Ordinary API return is admission only, not callback or authoritative completion.
            setter.invoke(telephony, rows);
            return DadaBoardAdapter.Submission.ACCEPTED;
        } catch (IllegalAccessException notInvoked) {
            return DadaBoardAdapter.Submission.NOT_STARTED;
        } catch (ReflectiveOperationException | RuntimeException uncertain) {
            return DadaBoardAdapter.Submission.AMBIGUOUS;
        }
    }
}
