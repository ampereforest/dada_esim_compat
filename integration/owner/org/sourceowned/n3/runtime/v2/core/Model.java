package org.sourceowned.n3.runtime.v2.core;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Structural contracts only. A trusted adapter, not these value constructors, establishes evidence. */
public final class Model {
    private Model() { }

    public enum Choice { PHYSICAL_SIM_2, ESIM }

    /** This is the Android hidden type namespace, never an OEM or wire value. */
    public enum SimType {
        UNKNOWN(-1), PHYSICAL(1), EUICC(2);
        public final int androidHiddenValue;
        SimType(int value) { androidHiddenValue = value; }
        public static SimType fromAndroidHiddenValue(int value) {
            for (SimType type : values()) if (type.androidHiddenValue == value) return type;
            throw new IllegalArgumentException("unsupported Android type namespace value");
        }
        public static SimType forChoice(Choice choice) {
            return choice == Choice.ESIM ? EUICC : PHYSICAL;
        }
    }

    public enum DigestKind { BOARD, CARD, PROFILE, GENERATION_EPOCH, PRINCIPAL, PLAN }

    /** No API accepts EID, ICCID, IMSI, phone numbers, or provisioning/confirmation codes. */
    public static final class Digest {
        public final DigestKind kind;
        private final byte[] bytes;
        private Digest(DigestKind kind, byte[] bytes) {
            this.kind = Objects.requireNonNull(kind, "kind");
            if (bytes == null || bytes.length != 32) throw new IllegalArgumentException("SHA-256 required");
            this.bytes = bytes.clone();
        }
        public static Digest ofSha256(DigestKind kind, byte[] bytes) { return new Digest(kind, bytes); }
        public static Digest ofSha256Hex(DigestKind kind, String hex) {
            if (hex == null || !hex.matches("[0-9a-fA-F]{64}")) {
                throw new IllegalArgumentException("SHA-256 hex required; raw identities are not accepted");
            }
            byte[] bytes = new byte[32];
            for (int i = 0; i < bytes.length; i++) {
                bytes[i] = (byte) Integer.parseInt(hex.substring(i * 2, i * 2 + 2), 16);
            }
            return new Digest(kind, bytes);
        }
        byte[] bytesForJournal() { return bytes.clone(); }
        public Digest requireKind(DigestKind expected) {
            if (kind != expected) throw new IllegalArgumentException("incorrect digest purpose");
            return this;
        }
        @Override public boolean equals(Object other) {
            if (!(other instanceof Digest)) return false;
            Digest digest = (Digest) other;
            return kind == digest.kind && Arrays.equals(bytes, digest.bytes);
        }
        @Override public int hashCode() { return 31 * kind.hashCode() + Arrays.hashCode(bytes); }
        @Override public String toString() { return "<" + kind + " digest redacted>"; }
    }

    /** Stable adapter-owned card/port generation; not a wall-clock timestamp. */
    public static final class Generation {
        public final Digest epoch;
        public final long value;
        public Generation(Digest epoch, long value) {
            this.epoch = Objects.requireNonNull(epoch, "epoch").requireKind(DigestKind.GENERATION_EPOCH);
            if (value < 0) throw new IllegalArgumentException("negative generation");
            this.value = value;
        }
        public boolean isOlderThan(Generation other) {
            return other != null && epoch.equals(other.epoch) && value < other.value;
        }
        @Override public boolean equals(Object other) {
            if (!(other instanceof Generation)) return false;
            Generation generation = (Generation) other;
            return value == generation.value && epoch.equals(generation.epoch);
        }
        @Override public int hashCode() { return 31 * epoch.hashCode() + Long.valueOf(value).hashCode(); }
        @Override public String toString() { return "<generation redacted>"; }
    }

    public static final class OperationId {
        final long high;
        final long low;
        public OperationId(long high, long low) {
            if (high == 0 && low == 0) throw new IllegalArgumentException("zero operation ID");
            this.high = high;
            this.low = low;
        }
        @Override public boolean equals(Object other) {
            if (!(other instanceof OperationId)) return false;
            OperationId id = (OperationId) other;
            return high == id.high && low == id.low;
        }
        @Override public int hashCode() { return Long.valueOf(high ^ low).hashCode(); }
        @Override public String toString() { return "<owned operation>"; }
    }

    public static final class Row {
        public final int port;
        public final int physicalSlot;
        public final int logicalSlot;
        public Row(int port, int physicalSlot, int logicalSlot) {
            if (port < 0 || physicalSlot < 0 || logicalSlot < 0) {
                throw new IllegalArgumentException("negative mapping component");
            }
            this.port = port;
            this.physicalSlot = physicalSlot;
            this.logicalSlot = logicalSlot;
        }
        @Override public boolean equals(Object other) {
            if (!(other instanceof Row)) return false;
            Row row = (Row) other;
            return port == row.port && physicalSlot == row.physicalSlot && logicalSlot == row.logicalSlot;
        }
        @Override public int hashCode() { return (port * 31 + physicalSlot) * 31 + logicalSlot; }
        @Override public String toString() { return port + ":" + physicalSlot + ":" + logicalSlot; }
    }

    /** Canonicalized by logical slot. Unsupported, partial, duplicate, and logical0-changing maps reject. */
    public static final class FullMap {
        private final List<Row> rows;
        public final Choice choice;
        private FullMap(List<Row> input) {
            if (input == null || input.size() != 2 || input.contains(null)) {
                throw new IllegalArgumentException("exactly two complete mapping rows required");
            }
            List<Row> copy = new ArrayList<Row>(input);
            Collections.sort(copy, new Comparator<Row>() {
                @Override public int compare(Row left, Row right) {
                    return Integer.compare(left.logicalSlot, right.logicalSlot);
                }
            });
            if (!copy.get(0).equals(new Row(0, 0, 0))) {
                throw new IllegalArgumentException("logical0 must remain 0:0:0");
            }
            if (copy.get(1).equals(new Row(0, 1, 1))) choice = Choice.PHYSICAL_SIM_2;
            else if (copy.get(1).equals(new Row(1, 1, 1))) choice = Choice.ESIM;
            else throw new IllegalArgumentException("unsupported logical1 mapping");
            rows = Collections.unmodifiableList(copy);
        }
        public static FullMap of(List<Row> input) { return new FullMap(input); }
        public static FullMap forChoice(Choice choice) {
            Objects.requireNonNull(choice, "choice");
            return of(Arrays.asList(new Row(0, 0, 0), new Row(choice == Choice.ESIM ? 1 : 0, 1, 1)));
        }
        public List<Row> rows() { return rows; }
        @Override public boolean equals(Object other) {
            return other instanceof FullMap && rows.equals(((FullMap) other).rows);
        }
        @Override public int hashCode() { return rows.hashCode(); }
        @Override public String toString() { return rows.toString(); }
    }

    public enum MapStatus { COMPLETE, PARTIAL, UNAVAILABLE, INVALID }
    public static final class MapEvidence {
        public final MapStatus status;
        public final FullMap map;
        private MapEvidence(MapStatus status, FullMap map) { this.status = status; this.map = map; }
        public static MapEvidence complete(List<Row> rows) {
            try { return new MapEvidence(MapStatus.COMPLETE, FullMap.of(rows)); }
            catch (IllegalArgumentException invalid) { return new MapEvidence(MapStatus.INVALID, null); }
        }
        public static MapEvidence complete(FullMap map) {
            return new MapEvidence(MapStatus.COMPLETE, Objects.requireNonNull(map, "map"));
        }
        public static MapEvidence missing(MapStatus status) {
            if (status == MapStatus.COMPLETE) throw new IllegalArgumentException("complete needs map");
            return new MapEvidence(Objects.requireNonNull(status, "status"), null);
        }
        @Override public String toString() { return status.toString(); }
    }

    public enum Presence { PRESENT, ABSENT, UNKNOWN }
    public enum CardKind { PHYSICAL, EUICC, UNKNOWN }

    /** Caller-provided digest/absence expectation is NOT evidence until an adapter establishes it. */
    public static final class CardPortContext {
        public final Digest board;
        public final Generation generation;
        public final int physicalSlot;
        public final int port;
        public final Presence expectedPresence;
        public final Digest expectedCard;
        public CardPortContext(Digest board, Generation generation, int physicalSlot, int port,
                               Presence expectedPresence, Digest expectedCard) {
            this.board = Objects.requireNonNull(board, "board").requireKind(DigestKind.BOARD);
            this.generation = Objects.requireNonNull(generation, "generation");
            if (physicalSlot != 1 || (port != 0 && port != 1)) {
                throw new IllegalArgumentException("only physical1 port0/1 contexts admitted");
            }
            this.physicalSlot = physicalSlot;
            this.port = port;
            this.expectedPresence = Objects.requireNonNull(expectedPresence, "presence");
            if (expectedPresence == Presence.UNKNOWN) throw new IllegalArgumentException("unknown context");
            if ((expectedPresence == Presence.PRESENT) != (expectedCard != null)) {
                throw new IllegalArgumentException("present requires digest; absent forbids digest");
            }
            this.expectedCard = expectedCard == null ? null : expectedCard.requireKind(DigestKind.CARD);
        }
        public Choice choice() { return port == 1 ? Choice.ESIM : Choice.PHYSICAL_SIM_2; }
        @Override public boolean equals(Object other) {
            if (!(other instanceof CardPortContext)) return false;
            CardPortContext context = (CardPortContext) other;
            return board.equals(context.board) && generation.equals(context.generation)
                && physicalSlot == context.physicalSlot && port == context.port
                && expectedPresence == context.expectedPresence && Objects.equals(expectedCard, context.expectedCard);
        }
        @Override public int hashCode() { return Objects.hash(board, generation, physicalSlot, port, expectedPresence, expectedCard); }
        @Override public String toString() { return "<card/port context redacted>"; }
    }

    public static final class CardEvidence {
        public final Generation generation;
        public final int physicalSlot;
        public final int port;
        public final Presence presence;
        public final CardKind kind;
        public final Digest card;
        public final boolean established;
        public CardEvidence(Generation generation, int physicalSlot, int port, Presence presence,
                            CardKind kind, Digest card, boolean established) {
            this.generation = Objects.requireNonNull(generation, "generation");
            this.physicalSlot = physicalSlot;
            this.port = port;
            this.presence = Objects.requireNonNull(presence, "presence");
            this.kind = Objects.requireNonNull(kind, "kind");
            this.card = card == null ? null : card.requireKind(DigestKind.CARD);
            this.established = established;
            if (presence != Presence.PRESENT && card != null) throw new IllegalArgumentException("no absent-card digest");
        }
        public boolean validFor(Choice choice, Generation current) {
            if (!established || !generation.equals(current) || physicalSlot != 1
                || port != (choice == Choice.ESIM ? 1 : 0) || presence == Presence.UNKNOWN) return false;
            if (presence == Presence.ABSENT) return card == null;
            return card != null && kind == (choice == Choice.ESIM ? CardKind.EUICC : CardKind.PHYSICAL);
        }
        public boolean establishes(CardPortContext context) {
            return validFor(context.choice(), context.generation) && presence == context.expectedPresence
                && Objects.equals(card, context.expectedCard);
        }
        public CardPortContext boundContext(Digest board) {
            if (!validFor(port == 1 ? Choice.ESIM : Choice.PHYSICAL_SIM_2, generation)) {
                throw new IllegalStateException("identity not established");
            }
            return new CardPortContext(board, generation, physicalSlot, port, presence, card);
        }
        @Override public String toString() { return "CardEvidence(" + presence + ", redacted)"; }
    }

    /** Genuine empty is distinct from inaccessible/failed. Route selection never enables a profile. */
    public enum ProfileStatus { VERIFIED_ENABLED, VERIFIED_EMPTY, INACCESSIBLE, FAILED }
    public static final class ProfileEvidence {
        public final ProfileStatus status;
        public final Generation generation;
        public final Digest euiccCard;
        public final Digest enabledProfile;
        public final boolean established;
        public ProfileEvidence(ProfileStatus status, Generation generation, Digest euiccCard,
                               Digest enabledProfile, boolean established) {
            this.status = Objects.requireNonNull(status, "status");
            this.generation = Objects.requireNonNull(generation, "generation");
            this.euiccCard = euiccCard == null ? null : euiccCard.requireKind(DigestKind.CARD);
            this.enabledProfile = enabledProfile == null ? null : enabledProfile.requireKind(DigestKind.PROFILE);
            this.established = established;
            if (status == ProfileStatus.VERIFIED_ENABLED && (euiccCard == null || enabledProfile == null)) {
                throw new IllegalArgumentException("enabled needs established card/profile digests");
            }
            if (status != ProfileStatus.VERIFIED_ENABLED && enabledProfile != null) {
                throw new IllegalArgumentException("only verified-enabled may carry profile identity");
            }
            if (status == ProfileStatus.VERIFIED_EMPTY && euiccCard == null) {
                throw new IllegalArgumentException("valid empty requires identified eUICC");
            }
        }
        public boolean accessible() {
            return status == ProfileStatus.VERIFIED_ENABLED || status == ProfileStatus.VERIFIED_EMPTY;
        }
        public boolean validIn(Generation current) {
            return generation.equals(current) && (!accessible() || established);
        }
        public boolean sameAccessibleIdentity(ProfileEvidence other) {
            return other != null && status == other.status && Objects.equals(euiccCard, other.euiccCard)
                && Objects.equals(enabledProfile, other.enabledProfile);
        }
        @Override public String toString() { return "ProfileEvidence(" + status + ", redacted)"; }
    }

    public enum OperationKind { ROUTE, LPA_MUTATION }
    public enum Dimension { ROUTE, NORMALIZED_TYPE, COMPLETE_MAP }
    /** Abstract effects only; no transport/API/order is inferred by the core. No profile mutation kind exists. */
    public enum MutationKind { LOGICAL1_ROUTE, NORMALIZED_SIM_TYPE, LOGICAL1_GPIO, COMPLETE_SLOT_MAP, BOARD_COMPOSITE }

    public static final class Step {
        public final MutationKind kind;
        private final Set<Dimension> effects;
        public Step(MutationKind kind, Set<Dimension> effects) {
            this.kind = Objects.requireNonNull(kind, "kind");
            if (effects == null || effects.isEmpty()) throw new IllegalArgumentException("step needs declared effects");
            this.effects = Collections.unmodifiableSet(EnumSet.copyOf(effects));
            if (kind == MutationKind.LOGICAL1_ROUTE || kind == MutationKind.LOGICAL1_GPIO) {
                if (!effects.contains(Dimension.ROUTE)) throw new IllegalArgumentException("route effect required");
            } else if (kind == MutationKind.NORMALIZED_SIM_TYPE) {
                if (!effects.contains(Dimension.NORMALIZED_TYPE)) throw new IllegalArgumentException("type effect required");
            } else if (kind == MutationKind.COMPLETE_SLOT_MAP) {
                if (!effects.contains(Dimension.COMPLETE_MAP)) throw new IllegalArgumentException("map effect required");
            }
        }
        public Set<Dimension> effects() { return effects; }
    }

    public static final class Plan {
        public final OperationId operation;
        public final FullMap destination;
        public final CardPortContext establishedContext;
        public final CardEvidence targetEvidence;
        public final boolean provenLogical0Preservation;
        private final List<Step> steps;
        public Plan(OperationId operation, FullMap destination, CardPortContext context,
                    CardEvidence targetEvidence, boolean provenLogical0Preservation, List<Step> steps) {
            this.operation = Objects.requireNonNull(operation, "operation");
            this.destination = Objects.requireNonNull(destination, "destination");
            this.establishedContext = Objects.requireNonNull(context, "context");
            this.targetEvidence = Objects.requireNonNull(targetEvidence, "target evidence");
            this.provenLogical0Preservation = provenLogical0Preservation;
            if (steps == null || steps.isEmpty() || steps.size() > 5 || steps.contains(null)) {
                throw new IllegalArgumentException("bounded nonempty plan required");
            }
            Set<MutationKind> unique = EnumSet.noneOf(MutationKind.class);
            for (Step step : steps) if (!unique.add(step.kind)) throw new IllegalArgumentException("duplicate mutation kind");
            this.steps = Collections.unmodifiableList(new ArrayList<Step>(steps));
        }
        public List<Step> steps() { return steps; }
        public boolean covers(Observation before) {
            Set<Dimension> effects = EnumSet.noneOf(Dimension.class);
            for (Step step : steps) effects.addAll(step.effects());
            return (before.route == destination.choice || effects.contains(Dimension.ROUTE))
                && (before.type == SimType.forChoice(destination.choice) || effects.contains(Dimension.NORMALIZED_TYPE))
                && (destination.equals(before.mapping.map) || effects.contains(Dimension.COMPLETE_MAP));
        }
    }

    public static final class Ticket {
        public final OperationId localDomain;
        public final long number;
        public Ticket(OperationId localDomain, long number) {
            this.localDomain = Objects.requireNonNull(localDomain, "domain");
            if (number <= 0) throw new IllegalArgumentException("positive ticket number required");
            this.number = number;
        }
        @Override public boolean equals(Object other) {
            if (!(other instanceof Ticket)) return false;
            Ticket ticket = (Ticket) other;
            return number == ticket.number && localDomain.equals(ticket.localDomain);
        }
        @Override public int hashCode() { return Objects.hash(localDomain, number); }
    }

    /** Must reflect established attribution/quiescence, not an adapter echo of the request token. */
    public static final class CompletionEvidence {
        public final OperationId operation;
        public final OperationKind kind;
        public final Generation generation;
        public final long baselineSequence;
        public final boolean complete;
        public final boolean quiescent;
        public CompletionEvidence(OperationId operation, OperationKind kind, Generation generation,
                                  long baselineSequence, boolean complete, boolean quiescent) {
            this.operation = Objects.requireNonNull(operation, "operation");
            this.kind = Objects.requireNonNull(kind, "kind");
            this.generation = Objects.requireNonNull(generation, "generation");
            if (baselineSequence < 0) throw new IllegalArgumentException("negative baseline");
            this.baselineSequence = baselineSequence;
            this.complete = complete;
            this.quiescent = quiescent;
        }
    }

    public static final class Observation {
        public final Ticket ticket;
        public final Digest board;
        public final Generation generation;
        public final long sequence;
        public final boolean generationEstablishedCurrent;
        public final Choice route;
        public final boolean routeAuthoritative;
        public final MapEvidence mapping;
        public final SimType type;
        public final boolean typeAuthoritative;
        public final CardEvidence destinationCard;
        public final ProfileEvidence enabledProfile;
        public final CompletionEvidence completion;
        public Observation(Ticket ticket, Digest board, Generation generation, long sequence,
                           boolean generationEstablishedCurrent, Choice route, boolean routeAuthoritative,
                           MapEvidence mapping, SimType type, boolean typeAuthoritative,
                           CardEvidence destinationCard, ProfileEvidence enabledProfile,
                           CompletionEvidence completion) {
            this.ticket = Objects.requireNonNull(ticket, "ticket");
            this.board = Objects.requireNonNull(board, "board").requireKind(DigestKind.BOARD);
            this.generation = Objects.requireNonNull(generation, "generation");
            if (sequence < 0) throw new IllegalArgumentException("negative observation sequence");
            this.sequence = sequence;
            this.generationEstablishedCurrent = generationEstablishedCurrent;
            this.route = route;
            this.routeAuthoritative = routeAuthoritative;
            this.mapping = Objects.requireNonNull(mapping, "mapping");
            this.type = Objects.requireNonNull(type, "type");
            this.typeAuthoritative = typeAuthoritative;
            this.destinationCard = Objects.requireNonNull(destinationCard, "card evidence");
            this.enabledProfile = Objects.requireNonNull(enabledProfile, "profile evidence");
            this.completion = completion;
        }
        public boolean completeRouting() {
            return generationEstablishedCurrent && routeAuthoritative && route != null
                && mapping.status == MapStatus.COMPLETE && typeAuthoritative && type != SimType.UNKNOWN;
        }
        public boolean verifies(Choice choice) {
            if (choice == null || !completeRouting() || route != choice || mapping.map.choice != choice
                || type != SimType.forChoice(choice) || !destinationCard.validFor(choice, generation)
                || !enabledProfile.validIn(generation)) return false;
            if (choice == Choice.ESIM && enabledProfile.status == ProfileStatus.FAILED) return false;
            return choice != Choice.ESIM || !enabledProfile.accessible()
                || (destinationCard.presence == Presence.PRESENT
                    && Objects.equals(destinationCard.card, enabledProfile.euiccCard));
        }
        @Override public String toString() {
            return "Observation(route=" + route + ", map=" + mapping.status + ", profile=" + enabledProfile.status + ")";
        }
    }

    public static final class Selection {
        public final FullMap map;
        public final CardPortContext context;
        /** Marker only. Injected live authentication must independently establish explicit consent. */
        public final boolean explicitUserSelection;
        public Selection(FullMap map, CardPortContext context, boolean explicitUserSelection) {
            this.map = Objects.requireNonNull(map, "map");
            this.context = Objects.requireNonNull(context, "context");
            if (map.choice != context.choice()) throw new IllegalArgumentException("map/context destination mismatch");
            this.explicitUserSelection = explicitUserSelection;
        }
    }

    public static final class Hint {
        public final OperationId operation;
        public final Generation generation;
        public Hint(OperationId operation, Generation generation) {
            this.operation = Objects.requireNonNull(operation, "operation");
            this.generation = Objects.requireNonNull(generation, "generation");
        }
    }
}
