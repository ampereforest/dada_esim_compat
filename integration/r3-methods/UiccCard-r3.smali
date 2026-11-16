# Caller holds the card/slot shared lock; reference replacement detects observed ABA.
.method protected blacklist r3$invalidateEmbeddedCardGenerationLocked()V
    .locals 1
    new-instance v0, Ljava/lang/Object;
    invoke-direct {v0}, Ljava/lang/Object;-><init>()V
    iput-object v0, p0, Lcom/android/internal/telephony/uicc/UiccCard;->mEmbeddedCardGeneration:Ljava/lang/Object;
    return-void
.end method

.method public blacklist getEmbeddedCardGeneration()Ljava/lang/Object;
    .locals 2
    iget-object v0, p0, Lcom/android/internal/telephony/uicc/UiccCard;->mLock:Ljava/lang/Object;
    monitor-enter v0
    :try_start
    iget-object v1, p0, Lcom/android/internal/telephony/uicc/UiccCard;->mEmbeddedCardGeneration:Ljava/lang/Object;
    if-nez v1, :ready
    invoke-virtual {p0}, Lcom/android/internal/telephony/uicc/UiccCard;->r3$invalidateEmbeddedCardGenerationLocked()V
    iget-object v1, p0, Lcom/android/internal/telephony/uicc/UiccCard;->mEmbeddedCardGeneration:Ljava/lang/Object;
    :ready
    :try_end
    .catchall {:try_start .. :try_end} :caught
    monitor-exit v0
    return-object v1
    :caught
    move-exception v1
    monitor-exit v0
    throw v1
.end method
