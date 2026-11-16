.method private synthetic blacklist lambda$updateEmbeddedSubscriptions$11(Ljava/lang/Runnable;Ljava/util/List;)V
    .locals 9
    move-object v0, p0
    move-object v1, p1
    iget-object v2, v0, Lcom/android/internal/telephony/subscription/SubscriptionManagerService;->mEuiccManager:Landroid/telephony/euicc/EuiccManager;
    if-eqz v2, :cond_285
    invoke-virtual {v2}, Landroid/telephony/euicc/EuiccManager;->isEnabled()Z
    move-result v2
    if-eqz v2, :cond_285
    iget-object v2, v0, Lcom/android/internal/telephony/subscription/SubscriptionManagerService;->mEuiccController:Lcom/android/internal/telephony/euicc/EuiccController;
    if-eqz v2, :cond_285
    const/4 v2, 0x0
    new-instance v3, Landroid/util/ArraySet;
    invoke-direct {v3}, Landroid/util/ArraySet;-><init>()V
    const-string v5, "updateEmbeddedSubscriptions: start to get euicc profiles."
    invoke-direct {v0, v5}, Lcom/android/internal/telephony/subscription/SubscriptionManagerService;->log(Ljava/lang/String;)V
    if-eqz p2, :cond_229
    invoke-interface {p2}, Ljava/util/List;->iterator()Ljava/util/Iterator;
    move-result-object v4
    :next_card
    invoke-interface {v4}, Ljava/util/Iterator;->hasNext()Z
    move-result v5
    if-eqz v5, :cond_229
    invoke-interface {v4}, Ljava/util/Iterator;->next()Ljava/lang/Object;
    move-result-object v5
    instance-of v6, v5, Ljava/lang/Integer;
    if-eqz v6, :defer_card
    check-cast v5, Ljava/lang/Integer;
    invoke-virtual {v5}, Ljava/lang/Integer;->intValue()I
    move-result v6
    if-ltz v6, :defer_card
    invoke-interface {v3, v5}, Ljava/util/Set;->add(Ljava/lang/Object;)Z
    move-result v5
    if-eqz v5, :next_card
    :try_card_start
    iget-object v5, v0, Lcom/android/internal/telephony/subscription/SubscriptionManagerService;->mUiccController:Lcom/android/internal/telephony/uicc/UiccController;
    invoke-virtual {v5, v6}, Lcom/android/internal/telephony/uicc/UiccController;->getEmbeddedSubscriptionSnapshot(I)Lcom/android/internal/telephony/uicc/EmbeddedSubscriptionSnapshot;
    move-result-object v7
    if-eqz v7, :defer_card
    # No UICC/controller/database lock is held across the blocking service call.
    iget-object v5, v0, Lcom/android/internal/telephony/subscription/SubscriptionManagerService;->mEuiccController:Lcom/android/internal/telephony/euicc/EuiccController;
    invoke-virtual {v5, v6}, Lcom/android/internal/telephony/euicc/EuiccController;->blockingGetEuiccProfileInfoList(I)Landroid/service/euicc/GetEuiccProfileInfoListResult;
    move-result-object v8
    invoke-direct {v0, v7, v8}, Lcom/android/internal/telephony/subscription/SubscriptionManagerService;->r3$reconcileEmbeddedCard(Lcom/android/internal/telephony/uicc/EmbeddedSubscriptionSnapshot;Landroid/service/euicc/GetEuiccProfileInfoListResult;)Z
    move-result v5
    if-eqz v5, :defer_card
    const/4 v2, 0x1
    :try_card_end
    .catch Ljava/lang/RuntimeException; {:try_card_start .. :try_card_end} :card_exception
    goto :next_card
    :card_exception
    move-exception v5
    :defer_card
    const-string v5, "updateEmbeddedSubscriptions: defer failed, malformed, stale or unidentified card inventory."
    invoke-direct {v0, v5}, Lcom/android/internal/telephony/subscription/SubscriptionManagerService;->loge(Ljava/lang/String;)V
    goto :next_card
    :cond_229
    if-eqz v2, :cond_270
    # R3_ORIGINAL_FINISH_TAIL

.method private blacklist r3$reconcileEmbeddedCard(Lcom/android/internal/telephony/uicc/EmbeddedSubscriptionSnapshot;Landroid/service/euicc/GetEuiccProfileInfoListResult;)Z
    .locals 12
    const/4 v11, 0x0
    if-eqz p2, :early_defer
    invoke-virtual {p2}, Landroid/service/euicc/GetEuiccProfileInfoListResult;->getResult()I
    move-result v6
    if-nez v6, :early_defer
    invoke-virtual {p2}, Landroid/service/euicc/GetEuiccProfileInfoListResult;->getProfiles()Ljava/util/List;
    move-result-object v3
    # Null is failure/unknown, while a non-null empty list is valid empty under N1's contract.
    if-eqz v3, :early_defer
    new-instance v6, Ljava/util/ArrayList;
    invoke-direct {v6, v3}, Ljava/util/ArrayList;-><init>(Ljava/util/Collection;)V
    move-object v3, v6
    invoke-virtual {p2}, Landroid/service/euicc/GetEuiccProfileInfoListResult;->getIsRemovable()Z
    move-result v5
    iget-object v9, p1, Lcom/android/internal/telephony/uicc/EmbeddedSubscriptionSnapshot;->eid:Ljava/lang/String;
    iget v10, p1, Lcom/android/internal/telephony/uicc/EmbeddedSubscriptionSnapshot;->cardId:I
    new-instance v4, Landroid/util/ArraySet;
    invoke-direct {v4}, Landroid/util/ArraySet;-><init>()V
    # B1: wait/read on the initializer's instance monitor BEFORE any acceptance lock setup.
    iget-object v6, p0, Lcom/android/internal/telephony/subscription/SubscriptionManagerService;->mSubscriptionDatabaseManager:Lcom/android/internal/telephony/subscription/SubscriptionDatabaseManager;
    invoke-virtual {v6}, Lcom/android/internal/telephony/subscription/SubscriptionDatabaseManager;->isDatabaseInitializedForEmbeddedSubscriptionUpdate()Z
    move-result v6
    if-eqz v6, :early_defer
    invoke-static {}, Lcom/android/internal/telephony/uicc/UiccController;->getEmbeddedSubscriptionLock()Ljava/lang/Object;
    move-result-object v0
    iget-object v1, p1, Lcom/android/internal/telephony/uicc/EmbeddedSubscriptionSnapshot;->slot:Lcom/android/internal/telephony/uicc/UiccSlot;
    invoke-virtual {v1}, Lcom/android/internal/telephony/uicc/UiccSlot;->getEmbeddedSubscriptionLock()Ljava/lang/Object;
    move-result-object v1
    monitor-enter v0
    :controller_start
    monitor-enter v1
    :slot_start
    iget-object v6, p0, Lcom/android/internal/telephony/subscription/SubscriptionManagerService;->mUiccController:Lcom/android/internal/telephony/uicc/UiccController;
    invoke-virtual {v6, p1}, Lcom/android/internal/telephony/uicc/UiccController;->isEmbeddedSubscriptionSnapshotCurrent(Lcom/android/internal/telephony/uicc/EmbeddedSubscriptionSnapshot;)Z
    move-result v6
    if-eqz v6, :slot_end
    iget-object v2, p0, Lcom/android/internal/telephony/subscription/SubscriptionManagerService;->mSubscriptionDatabaseManager:Lcom/android/internal/telephony/subscription/SubscriptionDatabaseManager;
    invoke-virtual {v2}, Lcom/android/internal/telephony/subscription/SubscriptionDatabaseManager;->getEmbeddedSubscriptionUpdateLock()Ljava/util/concurrent/locks/Lock;
    move-result-object v2
    invoke-interface {v2}, Ljava/util/concurrent/locks/Lock;->lock()V
    :database_start
    # Prevalidate the whole list and known ownership before the first metadata mutation.
    invoke-direct {p0, p1, v3, v4}, Lcom/android/internal/telephony/subscription/SubscriptionManagerService;->r3$validateEmbeddedProfiles(Lcom/android/internal/telephony/uicc/EmbeddedSubscriptionSnapshot;Ljava/util/List;Ljava/util/Set;)Z
    move-result v6
    if-eqz v6, :database_end
    invoke-interface {v3}, Ljava/util/List;->iterator()Ljava/util/Iterator;
    move-result-object v7
    :upsert_next
    invoke-interface {v7}, Ljava/util/Iterator;->hasNext()Z
    move-result v6
    if-eqz v6, :upserted
    invoke-interface {v7}, Ljava/util/Iterator;->next()Ljava/lang/Object;
    move-result-object v8
    check-cast v8, Landroid/service/euicc/EuiccProfileInfo;
    invoke-direct {p0, v8, v10, v9, v5}, Lcom/android/internal/telephony/subscription/SubscriptionManagerService;->r3$upsertEmbeddedProfile(Landroid/service/euicc/EuiccProfileInfo;ILjava/lang/String;Z)Z
    move-result v6
    if-eqz v6, :database_end
    goto :upsert_next
    :upserted
    iget-object v6, p0, Lcom/android/internal/telephony/subscription/SubscriptionManagerService;->mUiccController:Lcom/android/internal/telephony/uicc/UiccController;
    invoke-virtual {v6, p1}, Lcom/android/internal/telephony/uicc/UiccController;->isEmbeddedSubscriptionSnapshotCurrent(Lcom/android/internal/telephony/uicc/EmbeddedSubscriptionSnapshot;)Z
    move-result v6
    if-eqz v6, :database_end
    iget-object v6, p0, Lcom/android/internal/telephony/subscription/SubscriptionManagerService;->mSubscriptionDatabaseManager:Lcom/android/internal/telephony/subscription/SubscriptionDatabaseManager;
    invoke-virtual {v6}, Lcom/android/internal/telephony/subscription/SubscriptionDatabaseManager;->getAllSubscriptions()Ljava/util/List;
    move-result-object v6
    invoke-interface {v6}, Ljava/util/List;->iterator()Ljava/util/Iterator;
    move-result-object v7
    :clear_next
    invoke-interface {v7}, Ljava/util/Iterator;->hasNext()Z
    move-result v6
    if-eqz v6, :accepted
    invoke-interface {v7}, Ljava/util/Iterator;->next()Ljava/lang/Object;
    move-result-object v8
    check-cast v8, Lcom/android/internal/telephony/subscription/SubscriptionInfoInternal;
    invoke-virtual {v8}, Lcom/android/internal/telephony/subscription/SubscriptionInfoInternal;->isEmbedded()Z
    move-result v6
    if-eqz v6, :clear_next
    invoke-virtual {v8}, Lcom/android/internal/telephony/subscription/SubscriptionInfoInternal;->getCardId()I
    move-result v6
    if-ne v6, v10, :clear_next
    invoke-virtual {v8}, Lcom/android/internal/telephony/subscription/SubscriptionInfoInternal;->getCardString()Ljava/lang/String;
    move-result-object v6
    invoke-virtual {v9, v6}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z
    move-result v6
    if-eqz v6, :clear_next
    invoke-virtual {v8}, Lcom/android/internal/telephony/subscription/SubscriptionInfoInternal;->getIccId()Ljava/lang/String;
    move-result-object v6
    invoke-interface {v4, v6}, Ljava/util/Set;->contains(Ljava/lang/Object;)Z
    move-result v6
    if-nez v6, :clear_next
    # Reuse the unchanged target metadata-only clearing helper; never delete a profile.
    invoke-direct {p0, v8}, Lcom/android/internal/telephony/subscription/SubscriptionManagerService;->lambda$updateEmbeddedSubscriptions$8(Lcom/android/internal/telephony/subscription/SubscriptionInfoInternal;)V
    goto :clear_next
    :accepted
    const/4 v11, 0x1
    :database_end
    .catchall {:database_start .. :database_end} :database_exception
    invoke-interface {v2}, Ljava/util/concurrent/locks/Lock;->unlock()V
    goto :slot_end
    :database_exception
    move-exception v8
    invoke-interface {v2}, Ljava/util/concurrent/locks/Lock;->unlock()V
    throw v8
    :slot_end
    # DEX try ranges must be disjoint even though the resource scopes are nested.
    .catchall {:slot_start .. :database_start} :slot_exception
    .catchall {:database_end .. :slot_end} :slot_exception
    monitor-exit v1
    goto :controller_end
    :slot_exception
    move-exception v8
    monitor-exit v1
    throw v8
    :controller_end
    .catchall {:controller_start .. :slot_start} :controller_exception
    .catchall {:slot_end .. :controller_end} :controller_exception
    monitor-exit v0
    return v11
    :controller_exception
    move-exception v8
    monitor-exit v0
    throw v8
    :early_defer
    return v11
.end method

.method private blacklist r3$validateEmbeddedProfiles(Lcom/android/internal/telephony/uicc/EmbeddedSubscriptionSnapshot;Ljava/util/List;Ljava/util/Set;)Z
    .locals 8
    iget-object v0, p1, Lcom/android/internal/telephony/uicc/EmbeddedSubscriptionSnapshot;->eid:Ljava/lang/String;
    iget v1, p1, Lcom/android/internal/telephony/uicc/EmbeddedSubscriptionSnapshot;->cardId:I
    invoke-interface {p2}, Ljava/util/List;->iterator()Ljava/util/Iterator;
    move-result-object v2
    :next_profile
    invoke-interface {v2}, Ljava/util/Iterator;->hasNext()Z
    move-result v7
    if-eqz v7, :valid
    invoke-interface {v2}, Ljava/util/Iterator;->next()Ljava/lang/Object;
    move-result-object v3
    instance-of v7, v3, Landroid/service/euicc/EuiccProfileInfo;
    if-eqz v7, :invalid
    check-cast v3, Landroid/service/euicc/EuiccProfileInfo;
    invoke-virtual {v3}, Landroid/service/euicc/EuiccProfileInfo;->getIccid()Ljava/lang/String;
    move-result-object v3
    if-eqz v3, :invalid
    const-string v7, "[0-9]{1,22}"
    invoke-virtual {v3, v7}, Ljava/lang/String;->matches(Ljava/lang/String;)Z
    move-result v7
    if-eqz v7, :invalid
    invoke-interface {p3, v3}, Ljava/util/Set;->add(Ljava/lang/Object;)Z
    move-result v7
    if-eqz v7, :invalid
    iget-object v4, p0, Lcom/android/internal/telephony/subscription/SubscriptionManagerService;->mSubscriptionDatabaseManager:Lcom/android/internal/telephony/subscription/SubscriptionDatabaseManager;
    invoke-virtual {v4, v3}, Lcom/android/internal/telephony/subscription/SubscriptionDatabaseManager;->getSubscriptionInfoInternalByIccId(Ljava/lang/String;)Lcom/android/internal/telephony/subscription/SubscriptionInfoInternal;
    move-result-object v4
    # Unknown ICCID is a legitimate new profile if the inventory's card is identified.
    if-eqz v4, :next_profile
    invoke-virtual {v4}, Lcom/android/internal/telephony/subscription/SubscriptionInfoInternal;->getCardId()I
    move-result v5
    if-ltz v5, :check_string
    if-ne v5, v1, :invalid
    :check_string
    invoke-virtual {v4}, Lcom/android/internal/telephony/subscription/SubscriptionInfoInternal;->getCardString()Ljava/lang/String;
    move-result-object v6
    invoke-virtual {v0, v6}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z
    move-result v7
    if-nez v7, :next_profile
    # Permit only unbound legacy ICCID/empty records to be identity-bound by a fresh list.
    if-gez v5, :invalid
    invoke-static {v6}, Landroid/text/TextUtils;->isEmpty(Ljava/lang/CharSequence;)Z
    move-result v7
    if-nez v7, :next_profile
    invoke-virtual {v3, v6}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z
    move-result v7
    if-nez v7, :next_profile
    goto :invalid
    :valid
    const/4 v7, 0x1
    return v7
    :invalid
    const/4 v7, 0x0
    return v7
.end method
