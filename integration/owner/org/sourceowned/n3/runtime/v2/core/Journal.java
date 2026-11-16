package org.sourceowned.n3.runtime.v2.core;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Objects;

import static org.sourceowned.n3.runtime.v2.core.Contracts.*;
import static org.sourceowned.n3.runtime.v2.core.Model.*;

/** Versioned private record schema. No wall-clock/local deadline, raw identity, or provisioning code. */
public final class Journal {
    public static final int SCHEMA_VERSION = 1;
    public static final int MAX_FILE_BYTES = 16384;
    public static final int MAX_REOBSERVATIONS = 64;
    private Journal() { }

    public enum Phase { PREPARED, DISPATCHING, ACKNOWLEDGED, LPA_HELD, PARTIAL_OR_UNKNOWN, TIMED_OUT }
    public enum PendingCause { NONE, TRANSPORT, READBACK, AUTHORIZATION, TIMEOUT, STALE_EVIDENCE, INTERRUPTION }

    public static final class Confirmed {
        public final Choice choice;
        public final CardPortContext binding;
        public final long observedSequence;
        public final OperationId verifiedOperation;
        /** Observed enabled state is separate from choice and never used as a route-setting instruction. */
        public final ProfileEvidence enabledProfile;
        public Confirmed(Choice choice, CardPortContext binding, long observedSequence,
                         OperationId verifiedOperation, ProfileEvidence enabledProfile) {
            this.choice = Objects.requireNonNull(choice, "choice");
            this.binding = Objects.requireNonNull(binding, "binding");
            if (choice != binding.choice() || observedSequence < 0) throw new IllegalArgumentException("invalid confirmation");
            this.observedSequence = observedSequence;
            this.verifiedOperation = verifiedOperation;
            this.enabledProfile = Objects.requireNonNull(enabledProfile, "profile");
            if (!enabledProfile.validIn(binding.generation)) throw new IllegalArgumentException("stale profile confirmation");
        }
        public FullMap fullMap() { return FullMap.forChoice(choice); }
        @Override public String toString() { return "Confirmed(" + choice + ", identity redacted)"; }
    }

    /** Saved context never authorizes dispatch after process recreation. */
    public static final class Pending {
        public final OperationId operation;
        public final OperationKind kind;
        public final Digest owner;
        public final CardPortContext binding;
        public final Choice destination;
        public final long baselineSequence;
        public final ProfileEvidence priorEnabledProfile;
        public final Digest planDigest;
        public final int totalSteps;
        public final int acknowledgedSteps;
        public final Phase phase;
        public final PendingCause cause;
        public final int reobservations;
        public Pending(OperationId operation, OperationKind kind, Digest owner, CardPortContext binding,
                       Choice destination, long baselineSequence, ProfileEvidence priorEnabledProfile,
                       Digest planDigest, int totalSteps, int acknowledgedSteps, Phase phase,
                       PendingCause cause, int reobservations) {
            this.operation = Objects.requireNonNull(operation, "operation");
            this.kind = Objects.requireNonNull(kind, "kind");
            this.owner = Objects.requireNonNull(owner, "owner").requireKind(DigestKind.PRINCIPAL);
            this.binding = Objects.requireNonNull(binding, "binding");
            this.destination = destination;
            this.baselineSequence = baselineSequence;
            this.priorEnabledProfile = Objects.requireNonNull(priorEnabledProfile, "prior profile");
            this.planDigest = planDigest == null ? null : planDigest.requireKind(DigestKind.PLAN);
            this.totalSteps = totalSteps;
            this.acknowledgedSteps = acknowledgedSteps;
            this.phase = Objects.requireNonNull(phase, "phase");
            this.cause = Objects.requireNonNull(cause, "cause");
            this.reobservations = reobservations;
            if (baselineSequence < 0 || reobservations < 0 || reobservations > MAX_REOBSERVATIONS
                || acknowledgedSteps < 0 || acknowledgedSteps > totalSteps
                || !priorEnabledProfile.validIn(binding.generation)) throw new IllegalArgumentException("invalid pending bounds");
            if (kind == OperationKind.ROUTE) {
                if (destination == null || destination != binding.choice() || planDigest == null
                    || totalSteps < 1 || totalSteps > 5 || phase == Phase.LPA_HELD) {
                    throw new IllegalArgumentException("invalid route pending");
                }
            } else if (destination != null || planDigest != null || totalSteps != 0 || acknowledgedSteps != 0
                       || phase == Phase.PREPARED || phase == Phase.DISPATCHING || phase == Phase.ACKNOWLEDGED) {
                throw new IllegalArgumentException("invalid LPA pending");
            }
        }
        public Pending progress(Phase phase, PendingCause cause, int acknowledgedSteps) {
            return new Pending(operation, kind, owner, binding, destination, baselineSequence, priorEnabledProfile,
                planDigest, totalSteps, acknowledgedSteps, phase, cause, reobservations);
        }
        public Pending consumeObservation() {
            if (reobservations >= MAX_REOBSERVATIONS) throw new IllegalStateException("observation budget exhausted");
            return new Pending(operation, kind, owner, binding, destination, baselineSequence, priorEnabledProfile,
                planDigest, totalSteps, acknowledgedSteps, phase, cause, reobservations + 1);
        }
        @Override public String toString() { return "Pending(" + kind + ", " + phase + ", context redacted)"; }
    }

    public static final class Record {
        public final Digest board;
        public final long revision;
        public final Confirmed confirmed;
        public final Pending pending;
        public final Generation observedGeneration;
        public final long observedSequence;
        public Record(Digest board, long revision, Confirmed confirmed, Pending pending,
                      Generation observedGeneration, long observedSequence) {
            this.board = Objects.requireNonNull(board, "board").requireKind(DigestKind.BOARD);
            this.revision = revision;
            this.confirmed = confirmed;
            this.pending = pending;
            this.observedGeneration = observedGeneration;
            this.observedSequence = observedSequence;
            if (revision < 0 || observedSequence < 0 || (observedGeneration == null && observedSequence != 0)
                || (confirmed != null && !board.equals(confirmed.binding.board))
                || (pending != null && !board.equals(pending.binding.board))
                || ((confirmed != null || pending != null) && observedGeneration == null)
                || (revision == 0 && (confirmed != null || pending != null || observedGeneration != null))) {
                throw new IllegalArgumentException("invalid record");
            }
        }
        public static Record absentBaseline(Digest board) { return new Record(board, 0, null, null, null, 0); }
        private long nextRevision() {
            if (revision == Long.MAX_VALUE) throw new IllegalStateException("journal revision exhausted");
            return revision + 1;
        }
        public Record pending(Pending next, Observation before) {
            return new Record(board, nextRevision(), confirmed, Objects.requireNonNull(next, "pending"), before.generation, before.sequence);
        }
        public Record updatePending(Pending next) {
            if (pending == null || !pending.operation.equals(next.operation) || pending.kind != next.kind) {
                throw new IllegalArgumentException("operation-bound pending update required");
            }
            return new Record(board, nextRevision(), confirmed, next, observedGeneration, observedSequence);
        }
        /** Metadata only: acceptance never adopts a conflicting choice or overwrites pending intent. */
        public Record watermark(Observation observed) {
            Objects.requireNonNull(observed, "observation");
            if (!board.equals(observed.board) || !observed.generationEstablishedCurrent
                || observed.generation.isOlderThan(observedGeneration)
                || (observed.generation.equals(observedGeneration) && observed.sequence < observedSequence)) {
                throw new IllegalArgumentException("current nonregressing board observation required");
            }
            return new Record(board, nextRevision(), confirmed, pending, observed.generation, observed.sequence);
        }
        public Record confirm(Observation observed, OperationId operation) {
            if (!observed.verifies(observed.route)) throw new IllegalArgumentException("authoritative confirmation required");
            if (pending != null && (operation == null || !pending.operation.equals(operation))) {
                throw new IllegalArgumentException("matching operation required to clear pending");
            }
            Confirmed next = new Confirmed(observed.route, observed.destinationCard.boundContext(board),
                observed.sequence, operation == null && confirmed != null ? confirmed.verifiedOperation : operation, observed.enabledProfile);
            return new Record(board, nextRevision(), next, null, observed.generation, observed.sequence);
        }
        /** LPA completion is not an explicit route choice and must not replace last-confirmed selection. */
        public Record finishLpa(Observation observed, OperationId operation) {
            if (pending == null || pending.kind != OperationKind.LPA_MUTATION || !pending.operation.equals(operation)) {
                throw new IllegalArgumentException("matching LPA operation required");
            }
            return new Record(board, nextRevision(), confirmed, null, observed.generation, observed.sequence);
        }
        @Override public String toString() { return "Record(revision=" + revision + ", confirmed=" + confirmed + ", pending=" + pending + ")"; }
    }

    public static final class CodecFailure extends Exception {
        private static final long serialVersionUID = 1L;
        public final StoreReason reason;
        CodecFailure(StoreReason reason) { super("journal " + reason); this.reason = reason; }
    }

    /** SHA-256 detects corruption; it is not an authenticity MAC or Android permission substitute. */
    public static final class Codec {
        private static final byte[] MAGIC = new byte[] { 'N', '3', 'C', 'O', 'R', 'E', 'J', 0 };
        private Codec() { }

        public static byte[] encode(Record record) {
            if (record.revision <= 0) throw new IllegalArgumentException("absence is not a stored record");
            try {
                ByteArrayOutputStream payloadBytes = new ByteArrayOutputStream();
                DataOutputStream payload = new DataOutputStream(payloadBytes);
                digest(payload, record.board);
                payload.writeLong(record.revision);
                payload.writeBoolean(record.confirmed != null);
                if (record.confirmed != null) confirmed(payload, record.confirmed);
                payload.writeBoolean(record.pending != null);
                if (record.pending != null) pending(payload, record.pending);
                payload.writeBoolean(record.observedGeneration != null);
                if (record.observedGeneration != null) generation(payload, record.observedGeneration);
                payload.writeLong(record.observedSequence);
                payload.flush();
                byte[] body = payloadBytes.toByteArray();
                ByteArrayOutputStream envelopeBytes = new ByteArrayOutputStream();
                DataOutputStream envelope = new DataOutputStream(envelopeBytes);
                envelope.write(MAGIC);
                envelope.writeInt(SCHEMA_VERSION);
                envelope.writeInt(body.length);
                envelope.write(body);
                envelope.flush();
                byte[] checksummed = envelopeBytes.toByteArray();
                envelope.write(sha256(checksummed));
                envelope.flush();
                byte[] bytes = envelopeBytes.toByteArray();
                if (bytes.length > MAX_FILE_BYTES) throw new IllegalArgumentException("journal too large");
                return bytes;
            } catch (IOException impossible) { throw new IllegalStateException("in-memory journal encode failed"); }
        }

        public static Record decode(byte[] bytes, Digest expectedBoard) throws CodecFailure {
            if (bytes == null || bytes.length < 48 || bytes.length > MAX_FILE_BYTES) throw corrupt();
            try {
                DataInputStream envelope = new DataInputStream(new ByteArrayInputStream(bytes));
                byte[] magic = new byte[MAGIC.length];
                envelope.readFully(magic);
                if (!Arrays.equals(magic, MAGIC)) throw corrupt();
                if (envelope.readInt() != SCHEMA_VERSION) throw new CodecFailure(StoreReason.UNSUPPORTED_SCHEMA);
                int length = envelope.readInt();
                if (length < 0 || length != bytes.length - 48) throw corrupt();
                byte[] expectedChecksum = Arrays.copyOfRange(bytes, bytes.length - 32, bytes.length);
                if (!MessageDigest.isEqual(expectedChecksum, sha256(Arrays.copyOf(bytes, bytes.length - 32)))) throw corrupt();
                byte[] body = new byte[length];
                envelope.readFully(body);
                DataInputStream payload = new DataInputStream(new ByteArrayInputStream(body));
                Digest board = digest(payload, DigestKind.BOARD);
                if (!board.equals(expectedBoard)) throw new CodecFailure(StoreReason.BOARD_MISMATCH);
                long revision = payload.readLong();
                if (revision <= 0) throw corrupt();
                Confirmed confirmed = flag(payload) ? confirmed(payload) : null;
                Pending pending = flag(payload) ? pending(payload) : null;
                Generation observedGeneration = flag(payload) ? generation(payload) : null;
                long observedSequence = payload.readLong();
                if (payload.available() != 0) throw corrupt();
                return new Record(board, revision, confirmed, pending, observedGeneration, observedSequence);
            } catch (EOFException invalid) { throw corrupt(); }
            catch (IOException invalid) { throw corrupt(); }
            catch (IllegalArgumentException invalid) { throw corrupt(); }
        }

        public static Digest planDigest(Plan plan) {
            try {
                ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                DataOutputStream out = new DataOutputStream(bytes);
                operation(out, plan.operation);
                out.writeInt(plan.destination.choice.ordinal());
                context(out, plan.establishedContext);
                out.writeBoolean(plan.provenLogical0Preservation);
                out.writeInt(plan.steps().size());
                for (Step step : plan.steps()) {
                    out.writeInt(step.kind.ordinal());
                    int effects = 0;
                    for (Dimension effect : step.effects()) effects |= 1 << effect.ordinal();
                    out.writeInt(effects);
                }
                out.flush();
                return Digest.ofSha256(DigestKind.PLAN, sha256(bytes.toByteArray()));
            } catch (IOException impossible) { throw new IllegalStateException("in-memory plan encode failed"); }
        }

        private static byte[] sha256(byte[] bytes) {
            try { return MessageDigest.getInstance("SHA-256").digest(bytes); }
            catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException("SHA-256 unavailable"); }
        }
        private static CodecFailure corrupt() { return new CodecFailure(StoreReason.CORRUPT); }
        private static boolean flag(DataInputStream in) throws IOException, CodecFailure {
            int value = in.readUnsignedByte();
            if (value != 0 && value != 1) throw corrupt();
            return value == 1;
        }
        private static <E> E enumeration(DataInputStream in, E[] values) throws IOException, CodecFailure {
            int value = in.readInt();
            if (value < 0 || value >= values.length) throw corrupt();
            return values[value];
        }
        private static void digest(DataOutputStream out, Digest value) throws IOException { out.write(value.bytesForJournal()); }
        private static Digest digest(DataInputStream in, DigestKind kind) throws IOException {
            byte[] value = new byte[32];
            in.readFully(value);
            return Digest.ofSha256(kind, value);
        }
        private static void nullableDigest(DataOutputStream out, Digest value) throws IOException {
            out.writeBoolean(value != null);
            if (value != null) digest(out, value);
        }
        private static Digest nullableDigest(DataInputStream in, DigestKind kind) throws IOException, CodecFailure {
            return flag(in) ? digest(in, kind) : null;
        }
        private static void generation(DataOutputStream out, Generation value) throws IOException {
            digest(out, value.epoch); out.writeLong(value.value);
        }
        private static Generation generation(DataInputStream in) throws IOException {
            return new Generation(digest(in, DigestKind.GENERATION_EPOCH), in.readLong());
        }
        private static void operation(DataOutputStream out, OperationId operation) throws IOException {
            out.writeLong(operation.high); out.writeLong(operation.low);
        }
        private static OperationId operation(DataInputStream in) throws IOException { return new OperationId(in.readLong(), in.readLong()); }
        private static void context(DataOutputStream out, CardPortContext context) throws IOException {
            digest(out, context.board); generation(out, context.generation);
            out.writeInt(context.physicalSlot); out.writeInt(context.port);
            out.writeInt(context.expectedPresence.ordinal()); nullableDigest(out, context.expectedCard);
        }
        private static CardPortContext context(DataInputStream in) throws IOException, CodecFailure {
            Digest board = digest(in, DigestKind.BOARD);
            Generation generation = generation(in);
            int slot = in.readInt(); int port = in.readInt();
            Presence presence = enumeration(in, Presence.values());
            return new CardPortContext(board, generation, slot, port, presence, nullableDigest(in, DigestKind.CARD));
        }
        private static void profile(DataOutputStream out, ProfileEvidence profile) throws IOException {
            out.writeInt(profile.status.ordinal()); generation(out, profile.generation);
            nullableDigest(out, profile.euiccCard); nullableDigest(out, profile.enabledProfile);
            out.writeBoolean(profile.established);
        }
        private static ProfileEvidence profile(DataInputStream in) throws IOException, CodecFailure {
            ProfileStatus status = enumeration(in, ProfileStatus.values());
            Generation generation = generation(in);
            Digest card = nullableDigest(in, DigestKind.CARD);
            Digest enabled = nullableDigest(in, DigestKind.PROFILE);
            return new ProfileEvidence(status, generation, card, enabled, flag(in));
        }
        private static void confirmed(DataOutputStream out, Confirmed confirmed) throws IOException {
            out.writeInt(confirmed.choice.ordinal()); context(out, confirmed.binding);
            out.writeLong(confirmed.observedSequence); out.writeBoolean(confirmed.verifiedOperation != null);
            if (confirmed.verifiedOperation != null) operation(out, confirmed.verifiedOperation);
            profile(out, confirmed.enabledProfile);
        }
        private static Confirmed confirmed(DataInputStream in) throws IOException, CodecFailure {
            Choice choice = enumeration(in, Choice.values()); CardPortContext context = context(in);
            long sequence = in.readLong(); OperationId operation = flag(in) ? operation(in) : null;
            return new Confirmed(choice, context, sequence, operation, profile(in));
        }
        private static void pending(DataOutputStream out, Pending pending) throws IOException {
            operation(out, pending.operation); out.writeInt(pending.kind.ordinal()); digest(out, pending.owner);
            context(out, pending.binding); out.writeBoolean(pending.destination != null);
            if (pending.destination != null) out.writeInt(pending.destination.ordinal());
            out.writeLong(pending.baselineSequence); profile(out, pending.priorEnabledProfile);
            nullableDigest(out, pending.planDigest); out.writeInt(pending.totalSteps); out.writeInt(pending.acknowledgedSteps);
            out.writeInt(pending.phase.ordinal()); out.writeInt(pending.cause.ordinal()); out.writeInt(pending.reobservations);
        }
        private static Pending pending(DataInputStream in) throws IOException, CodecFailure {
            OperationId operation = operation(in); OperationKind kind = enumeration(in, OperationKind.values());
            Digest owner = digest(in, DigestKind.PRINCIPAL); CardPortContext context = context(in);
            Choice choice = flag(in) ? enumeration(in, Choice.values()) : null;
            long sequence = in.readLong(); ProfileEvidence profile = profile(in);
            Digest plan = nullableDigest(in, DigestKind.PLAN); int steps = in.readInt(); int acknowledged = in.readInt();
            Phase phase = enumeration(in, Phase.values()); PendingCause cause = enumeration(in, PendingCause.values());
            return new Pending(operation, kind, owner, context, choice, sequence, profile, plan,
                steps, acknowledged, phase, cause, in.readInt());
        }
    }
}
