package org.sourceowned.n3.runtime.v2.dada;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;
import org.sourceowned.n3.runtime.v2.core.Model;

/** No reset/debug/GPIO/type-SET command can be expressed by this binding. */
public final class DadaCommands {
    public static final String DEVICE = "dada";
    public static final String FINGERPRINT = "OnePlus/PLZ110/OP64DDL1:17/CP2A.260605.016/B.2a5e7c5-41b0cd-45d7d3:user/release-keys";
    public static final String MTB_SHA256 = "c3d7f847fcb516b28d98b12082d36d1f4b5cc86d71020461b3d8a581281d3fef";
    private static final Pattern EID_ACK = Pattern.compile("(?:^|[\\s,;])eID=89[0-9]{30}(?![0-9])");
    private static final Pattern ERROR = Pattern.compile("(?i)\\b(error|failed|failure)\\b");

    private DadaCommands() { }

    public static List<String> modem(Model.Choice choice) {
        Objects.requireNonNull(choice, "choice");
        return Collections.unmodifiableList(Arrays.asList("/vendor/bin/mtb", "5", "10",
                choice == Model.Choice.ESIM ? "1" : "2", "0"));
    }

    /** Redacted status only; never return or persist modem output containing an EID. */
    public static DadaBoardAdapter.Submission classify(Model.Choice choice, int exitCode,
                                                       String output, boolean truncated) {
        Objects.requireNonNull(choice, "choice");
        if (truncated || exitCode != 0 || output == null || ERROR.matcher(output).find()) {
            return DadaBoardAdapter.Submission.AMBIGUOUS;
        }
        if (choice == Model.Choice.ESIM && !EID_ACK.matcher(output).find()) {
            return DadaBoardAdapter.Submission.AMBIGUOUS;
        }
        // pSIM2 has no proven positive response token. Exit0 is submission only, not success.
        return DadaBoardAdapter.Submission.ACCEPTED;
    }
}
