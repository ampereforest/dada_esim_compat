package org.sourceowned.n3.runtime.v2;

import android.content.Context;
import android.os.*;
import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;
import org.sourceowned.n3.runtime.v2.core.Model;
import org.sourceowned.n3.runtime.v2.dada.DadaCommands;

/** Source-built system_server lifecycle hook. No standalone bootstrap, root runner or shell. */
public final class RomOwnerFactory {
    public static RoutingOwnerBinder create(Context systemContext) throws Exception {
        if (android.os.Process.myUid() != 1000 || !DadaCommands.DEVICE.equals(Build.DEVICE)
                || !DadaCommands.FINGERPRINT.equals(Build.FINGERPRINT)) throw new SecurityException("exact ROM board only");
        Model.Digest board = Model.Digest.ofSha256(Model.DigestKind.BOARD,
                MessageDigest.getInstance("SHA-256").digest(Build.FINGERPRINT.getBytes(StandardCharsets.UTF_8)));
        return new RoutingOwnerBinder(new CallerPolicy(Binder::getCallingUid, new AndroidCallerPackages(systemContext), board),
                new ForegroundPolicy(systemContext), new RealReadback(systemContext, SystemClock::elapsedRealtimeNanos),
                new ExperimentalIntent(new AndroidJournalFilesystem(false)), false);
    }
    private RomOwnerFactory() { }
}
