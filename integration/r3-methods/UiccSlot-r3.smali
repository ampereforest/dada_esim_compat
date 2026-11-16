.method private blacklist r3$invalidateEmbeddedCardGenerationLocked()V
    .locals 1
    new-instance v0, Ljava/lang/Object;
    invoke-direct {v0}, Ljava/lang/Object;-><init>()V
    iput-object v0, p0, Lcom/android/internal/telephony/uicc/UiccSlot;->mEmbeddedCardGeneration:Ljava/lang/Object;
    return-void
.end method

.method public blacklist getEmbeddedSubscriptionLock()Ljava/lang/Object;
    .locals 1
    iget-object v0, p0, Lcom/android/internal/telephony/uicc/UiccSlot;->mLock:Ljava/lang/Object;
    return-object v0
.end method

.method public blacklist matchesEmbeddedSubscriptionIdentity(Ljava/lang/String;)Z
    .locals 3
    iget-object v0, p0, Lcom/android/internal/telephony/uicc/UiccSlot;->mLock:Ljava/lang/Object;
    monitor-enter v0
    :try_start
    iget-object v1, p0, Lcom/android/internal/telephony/uicc/UiccSlot;->mEid:Ljava/lang/String;
    invoke-virtual {p1, v1}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z
    move-result v2
    if-nez v2, :done
    iget-object v1, p0, Lcom/android/internal/telephony/uicc/UiccSlot;->mUiccCard:Lcom/android/internal/telephony/uicc/UiccCard;
    if-eqz v1, :done
    invoke-virtual {v1}, Lcom/android/internal/telephony/uicc/UiccCard;->getCardId()Ljava/lang/String;
    move-result-object v1
    invoke-virtual {p1, v1}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z
    move-result v2
    if-nez v2, :done
    iget-object v1, p0, Lcom/android/internal/telephony/uicc/UiccSlot;->mUiccCard:Lcom/android/internal/telephony/uicc/UiccCard;
    instance-of v2, v1, Lcom/android/internal/telephony/uicc/euicc/EuiccCard;
    if-eqz v2, :done
    check-cast v1, Lcom/android/internal/telephony/uicc/euicc/EuiccCard;
    invoke-virtual {v1}, Lcom/android/internal/telephony/uicc/euicc/EuiccCard;->getEid()Ljava/lang/String;
    move-result-object v1
    invoke-virtual {p1, v1}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z
    move-result v2
    :done
    :try_end
    .catchall {:try_start .. :try_end} :caught
    monitor-exit v0
    return v2
    :caught
    move-exception v1
    monitor-exit v0
    throw v1
.end method

.method public blacklist getEmbeddedSubscriptionSnapshot(IILjava/lang/String;Ljava/lang/Object;)Lcom/android/internal/telephony/uicc/EmbeddedSubscriptionSnapshot;
    .locals 11
    iget-object v0, p0, Lcom/android/internal/telephony/uicc/UiccSlot;->mLock:Ljava/lang/Object;
    monitor-enter v0
    :try_start
    iget-boolean v10, p0, Lcom/android/internal/telephony/uicc/UiccSlot;->mIsEuicc:Z
    if-eqz v10, :unidentified
    iget-boolean v10, p0, Lcom/android/internal/telephony/uicc/UiccSlot;->mActive:Z
    if-eqz v10, :unidentified
    invoke-virtual {p0}, Lcom/android/internal/telephony/uicc/UiccSlot;->isStateUnknown()Z
    move-result v10
    if-nez v10, :unidentified
    iget-object v6, p0, Lcom/android/internal/telephony/uicc/UiccSlot;->mUiccCard:Lcom/android/internal/telephony/uicc/UiccCard;
    instance-of v10, v6, Lcom/android/internal/telephony/uicc/euicc/EuiccCard;
    if-eqz v10, :unidentified
    check-cast v6, Lcom/android/internal/telephony/uicc/euicc/EuiccCard;
    invoke-virtual {v6}, Lcom/android/internal/telephony/uicc/UiccCard;->getCardState()Lcom/android/internal/telephony/uicc/IccCardStatus$CardState;
    move-result-object v1
    sget-object v2, Lcom/android/internal/telephony/uicc/IccCardStatus$CardState;->CARDSTATE_PRESENT:Lcom/android/internal/telephony/uicc/IccCardStatus$CardState;
    if-ne v1, v2, :unidentified
    invoke-virtual {v6}, Lcom/android/internal/telephony/uicc/euicc/EuiccCard;->getEid()Ljava/lang/String;
    move-result-object v1
    invoke-virtual {p3, v1}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z
    move-result v10
    if-eqz v10, :unidentified
    invoke-virtual {v6}, Lcom/android/internal/telephony/uicc/UiccCard;->getCardId()Ljava/lang/String;
    move-result-object v1
    invoke-virtual {p3, v1}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z
    move-result v10
    if-eqz v10, :unidentified
    iget-object v1, p0, Lcom/android/internal/telephony/uicc/UiccSlot;->mEid:Ljava/lang/String;
    invoke-static {v1}, Landroid/text/TextUtils;->isEmpty(Ljava/lang/CharSequence;)Z
    move-result v10
    if-nez v10, :identity_ready
    invoke-virtual {p3, v1}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z
    move-result v10
    if-eqz v10, :unidentified
    :identity_ready
    iget-object v8, p0, Lcom/android/internal/telephony/uicc/UiccSlot;->mEmbeddedCardGeneration:Ljava/lang/Object;
    if-nez v8, :generation_ready
    invoke-direct {p0}, Lcom/android/internal/telephony/uicc/UiccSlot;->r3$invalidateEmbeddedCardGenerationLocked()V
    iget-object v8, p0, Lcom/android/internal/telephony/uicc/UiccSlot;->mEmbeddedCardGeneration:Ljava/lang/Object;
    :generation_ready
    invoke-virtual {v6}, Lcom/android/internal/telephony/uicc/UiccCard;->getEmbeddedCardGeneration()Ljava/lang/Object;
    move-result-object v9
    new-instance v1, Lcom/android/internal/telephony/uicc/EmbeddedSubscriptionSnapshot;
    move v2, p1
    move v3, p2
    move-object v4, p3
    move-object v5, p0
    move-object v7, p4
    invoke-direct/range {v1 .. v9}, Lcom/android/internal/telephony/uicc/EmbeddedSubscriptionSnapshot;-><init>(IILjava/lang/String;Lcom/android/internal/telephony/uicc/UiccSlot;Lcom/android/internal/telephony/uicc/euicc/EuiccCard;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;)V
    goto :done
    :unidentified
    const/4 v1, 0x0
    :done
    :try_end
    .catchall {:try_start .. :try_end} :caught
    monitor-exit v0
    return-object v1
    :caught
    move-exception v10
    monitor-exit v0
    throw v10
.end method
