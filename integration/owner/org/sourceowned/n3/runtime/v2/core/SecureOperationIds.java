package org.sourceowned.n3.runtime.v2.core;

import java.security.SecureRandom;

public final class SecureOperationIds implements Contracts.OperationIds {
    private final SecureRandom random;
    public SecureOperationIds() { this(new SecureRandom()); }
    public SecureOperationIds(SecureRandom random) {
        if (random == null) throw new NullPointerException("random");
        this.random = random;
    }
    @Override public synchronized Model.OperationId next() {
        long high = random.nextLong();
        long low = random.nextLong();
        if (high == 0 && low == 0) low = 1;
        return new Model.OperationId(high, low);
    }
}
