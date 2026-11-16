package org.sourceowned.n3.runtime.v2;

import android.net.LocalSocket;
import android.net.LocalSocketAddress;
import android.os.Process;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.FileInputStream;
import java.security.MessageDigest;
import org.sourceowned.n3.runtime.v2.dada.DadaCommands;
import org.sourceowned.n3.runtime.v2.dada.DadaBoardAdapter;

/** Internal owner-to-root/native protocol. No app UID or string command can be transmitted. */
public final class NativeRouteClient {

    private final String endpoint;
    private final BoundedCalls calls = new BoundedCalls();
    public NativeRouteClient(boolean rootTest) { if (rootTest || Process.myUid() != 1000) throw new SecurityException("ROM owner required"); endpoint = "/dev/socket/n3-route-v2"; }
    public DadaBoardAdapter.Submission submit(int destination) {
        long start = android.os.SystemClock.elapsedRealtimeNanos();
        try { return calls.get(() -> submitInternal(destination, start), 7000); }
        catch (Exception uncertain) { return DadaBoardAdapter.Submission.AMBIGUOUS; }
    }
    private static boolean admissionExpired(long start) {
        long age = android.os.SystemClock.elapsedRealtimeNanos() - start;
        return age < 0 || age >= 1_000_000_000L;
    }
    private DadaBoardAdapter.Submission submitInternal(int destination, long start) {
        if (destination != 0 && destination != 1 || Process.myUid() != 1000)
            return DadaBoardAdapter.Submission.NOT_STARTED;
        boolean sent = false;
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (FileInputStream in = new FileInputStream("/vendor/bin/mtb")) {
                byte[] block = new byte[8192]; int count;
                while ((count = in.read(block)) != -1) digest.update(block, 0, count);
            }
            StringBuilder hex = new StringBuilder();
            for (byte b : digest.digest()) hex.append(String.format(java.util.Locale.ROOT, "%02x", b & 255));
            if (!DadaCommands.MTB_SHA256.equals(hex.toString()) || admissionExpired(start)) return DadaBoardAdapter.Submission.NOT_STARTED;
            try (LocalSocket socket = new LocalSocket()) {
                socket.connect(new LocalSocketAddress(endpoint, LocalSocketAddress.Namespace.FILESYSTEM));
                socket.setSoTimeout(6500);
                if (socket.getPeerCredentials().getUid() != 0) return DadaBoardAdapter.Submission.NOT_STARTED;
                if (admissionExpired(start)) return DadaBoardAdapter.Submission.NOT_STARTED;
                DataOutputStream out = new DataOutputStream(socket.getOutputStream());
                sent = true;
                out.writeInt(0x4e335232); out.writeInt(destination); out.flush();
                int status = new DataInputStream(socket.getInputStream()).readInt();
                return status == 1 ? DadaBoardAdapter.Submission.ACCEPTED
                        : status == 0 ? DadaBoardAdapter.Submission.NOT_STARTED : DadaBoardAdapter.Submission.AMBIGUOUS;
            }
        } catch (Exception unavailable) {
            return sent ? DadaBoardAdapter.Submission.AMBIGUOUS : DadaBoardAdapter.Submission.NOT_STARTED;
        }
    }
}
