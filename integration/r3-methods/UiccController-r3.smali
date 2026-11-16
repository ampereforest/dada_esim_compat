# Additive internal observation APIs. All callers of the locked invalidator hold mLock.
.method private blacklist r3$invalidateEmbeddedCardGenerationLocked()V
    .locals 1
    new-instance v0, Ljava/lang/Object;
    invoke-direct {v0}, Ljava/lang/Object;-><init>()V
    iput-object v0, p0, Lcom/android/internal/telephony/uicc/UiccController;->mEmbeddedCardGeneration:Ljava/lang/Object;
    return-void
.end method

.method public static blacklist getEmbeddedSubscriptionLock()Ljava/lang/Object;
    .locals 1
    sget-object v0, Lcom/android/internal/telephony/uicc/UiccController;->mLock:Ljava/lang/Object;
    return-object v0
.end method

.method public blacklist getEmbeddedSubscriptionSnapshot(I)Lcom/android/internal/telephony/uicc/EmbeddedSubscriptionSnapshot;
    .locals 9
    sget-object v0, Lcom/android/internal/telephony/uicc/UiccController;->mLock:Ljava/lang/Object;
    monitor-enter v0
    :try_start
    if-ltz p1, :unidentified
    invoke-virtual {p0, p1}, Lcom/android/internal/telephony/uicc/UiccController;->convertToCardString(I)Ljava/lang/String;
    move-result-object v1
    if-eqz v1, :unidentified
    const-string v7, "[0-9]{32}"
    invoke-virtual {v1, v7}, Ljava/lang/String;->matches(Ljava/lang/String;)Z
    move-result v7
    if-eqz v7, :unidentified
    invoke-virtual {p0, v1}, Lcom/android/internal/telephony/uicc/UiccController;->convertToPublicCardId(Ljava/lang/String;)I
    move-result v7
    if-ne v7, p1, :unidentified
    iget-object v2, p0, Lcom/android/internal/telephony/uicc/UiccController;->mUiccSlots:[Lcom/android/internal/telephony/uicc/UiccSlot;
    if-eqz v2, :unidentified
    const/4 v3, 0x0
    const/4 v4, -0x1
    :scan
    array-length v7, v2
    if-ge v3, v7, :scanned
    aget-object v5, v2, v3
    if-eqz v5, :next
    invoke-virtual {v5, v1}, Lcom/android/internal/telephony/uicc/UiccSlot;->matchesEmbeddedSubscriptionIdentity(Ljava/lang/String;)Z
    move-result v7
    if-eqz v7, :next
    # Even an inactive duplicate can misdirect the retained connector's first-match lookup.
    if-gez v4, :unidentified
    move v4, v3
    :next
    add-int/lit8 v3, v3, 0x1
    goto :scan
    :scanned
    if-ltz v4, :unidentified
    iget-object v6, p0, Lcom/android/internal/telephony/uicc/UiccController;->mEmbeddedCardGeneration:Ljava/lang/Object;
    if-nez v6, :generation_ready
    invoke-direct {p0}, Lcom/android/internal/telephony/uicc/UiccController;->r3$invalidateEmbeddedCardGenerationLocked()V
    iget-object v6, p0, Lcom/android/internal/telephony/uicc/UiccController;->mEmbeddedCardGeneration:Ljava/lang/Object;
    :generation_ready
    aget-object v5, v2, v4
    invoke-virtual {v5, p1, v4, v1, v6}, Lcom/android/internal/telephony/uicc/UiccSlot;->getEmbeddedSubscriptionSnapshot(IILjava/lang/String;Ljava/lang/Object;)Lcom/android/internal/telephony/uicc/EmbeddedSubscriptionSnapshot;
    move-result-object v7
    goto :done
    :unidentified
    const/4 v7, 0x0
    :done
    :try_end
    .catchall {:try_start .. :try_end} :caught
    monitor-exit v0
    return-object v7
    :caught
    move-exception v8
    monitor-exit v0
    throw v8
.end method

.method public blacklist isEmbeddedSubscriptionSnapshotCurrent(Lcom/android/internal/telephony/uicc/EmbeddedSubscriptionSnapshot;)Z
    .locals 2
    if-eqz p1, :unidentified
    iget v0, p1, Lcom/android/internal/telephony/uicc/EmbeddedSubscriptionSnapshot;->cardId:I
    invoke-virtual {p0, v0}, Lcom/android/internal/telephony/uicc/UiccController;->getEmbeddedSubscriptionSnapshot(I)Lcom/android/internal/telephony/uicc/EmbeddedSubscriptionSnapshot;
    move-result-object v1
    invoke-virtual {p1, v1}, Lcom/android/internal/telephony/uicc/EmbeddedSubscriptionSnapshot;->matches(Lcom/android/internal/telephony/uicc/EmbeddedSubscriptionSnapshot;)Z
    move-result v0
    return v0
    :unidentified
    const/4 v0, 0x0
    return v0
.end method
