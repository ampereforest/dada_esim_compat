# Internal reconciliation gate only. No schema, SQL, cache or persistence helper is changed.
.method public blacklist getEmbeddedSubscriptionUpdateLock()Ljava/util/concurrent/locks/Lock;
    .locals 1
    iget-object v0, p0, Lcom/android/internal/telephony/subscription/SubscriptionDatabaseManager;->mReadWriteLock:Ljava/util/concurrent/locks/ReadWriteLock;
    invoke-interface {v0}, Ljava/util/concurrent/locks/ReadWriteLock;->writeLock()Ljava/util/concurrent/locks/Lock;
    move-result-object v0
    return-object v0
.end method

# B1: the same instance monitor is held through protected initial load and its true write.
# Read only; no write lock, callback, schema/cache mutation, or readiness reset.
.method public blacklist isDatabaseInitializedForEmbeddedSubscriptionUpdate()Z
    .locals 1
    monitor-enter p0
    :ready_start
    iget-boolean v0, p0, Lcom/android/internal/telephony/subscription/SubscriptionDatabaseManager;->mDatabaseInitialized:Z
    :ready_end
    .catchall {:ready_start .. :ready_end} :ready_exception
    monitor-exit p0
    return v0
    :ready_exception
    move-exception v0
    monitor-exit p0
    throw v0
.end method
