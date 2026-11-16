.class public final Lcom/android/internal/telephony/uicc/EmbeddedSubscriptionSnapshot;
.super Ljava/lang/Object;
.source "EmbeddedSubscriptionSnapshot.java"

# A process-local observation, not a native inventory/completeness attestation.
.field public final blacklist cardId:I
.field public final blacklist slotIndex:I
.field public final blacklist eid:Ljava/lang/String;
.field public final blacklist slot:Lcom/android/internal/telephony/uicc/UiccSlot;
.field public final blacklist card:Lcom/android/internal/telephony/uicc/euicc/EuiccCard;
.field public final blacklist controllerGeneration:Ljava/lang/Object;
.field public final blacklist slotGeneration:Ljava/lang/Object;
.field public final blacklist cardGeneration:Ljava/lang/Object;

.method public constructor blacklist <init>(IILjava/lang/String;Lcom/android/internal/telephony/uicc/UiccSlot;Lcom/android/internal/telephony/uicc/euicc/EuiccCard;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;)V
    .registers 9
    invoke-direct {p0}, Ljava/lang/Object;-><init>()V
    iput p1, p0, Lcom/android/internal/telephony/uicc/EmbeddedSubscriptionSnapshot;->cardId:I
    iput p2, p0, Lcom/android/internal/telephony/uicc/EmbeddedSubscriptionSnapshot;->slotIndex:I
    iput-object p3, p0, Lcom/android/internal/telephony/uicc/EmbeddedSubscriptionSnapshot;->eid:Ljava/lang/String;
    iput-object p4, p0, Lcom/android/internal/telephony/uicc/EmbeddedSubscriptionSnapshot;->slot:Lcom/android/internal/telephony/uicc/UiccSlot;
    iput-object p5, p0, Lcom/android/internal/telephony/uicc/EmbeddedSubscriptionSnapshot;->card:Lcom/android/internal/telephony/uicc/euicc/EuiccCard;
    iput-object p6, p0, Lcom/android/internal/telephony/uicc/EmbeddedSubscriptionSnapshot;->controllerGeneration:Ljava/lang/Object;
    iput-object p7, p0, Lcom/android/internal/telephony/uicc/EmbeddedSubscriptionSnapshot;->slotGeneration:Ljava/lang/Object;
    iput-object p8, p0, Lcom/android/internal/telephony/uicc/EmbeddedSubscriptionSnapshot;->cardGeneration:Ljava/lang/Object;
    return-void
.end method

.method public blacklist matches(Lcom/android/internal/telephony/uicc/EmbeddedSubscriptionSnapshot;)Z
    .locals 2
    if-eqz p1, :different
    iget v0, p0, Lcom/android/internal/telephony/uicc/EmbeddedSubscriptionSnapshot;->cardId:I
    iget v1, p1, Lcom/android/internal/telephony/uicc/EmbeddedSubscriptionSnapshot;->cardId:I
    if-ne v0, v1, :different
    iget v0, p0, Lcom/android/internal/telephony/uicc/EmbeddedSubscriptionSnapshot;->slotIndex:I
    iget v1, p1, Lcom/android/internal/telephony/uicc/EmbeddedSubscriptionSnapshot;->slotIndex:I
    if-ne v0, v1, :different
    iget-object v0, p0, Lcom/android/internal/telephony/uicc/EmbeddedSubscriptionSnapshot;->slot:Lcom/android/internal/telephony/uicc/UiccSlot;
    iget-object v1, p1, Lcom/android/internal/telephony/uicc/EmbeddedSubscriptionSnapshot;->slot:Lcom/android/internal/telephony/uicc/UiccSlot;
    if-ne v0, v1, :different
    iget-object v0, p0, Lcom/android/internal/telephony/uicc/EmbeddedSubscriptionSnapshot;->card:Lcom/android/internal/telephony/uicc/euicc/EuiccCard;
    iget-object v1, p1, Lcom/android/internal/telephony/uicc/EmbeddedSubscriptionSnapshot;->card:Lcom/android/internal/telephony/uicc/euicc/EuiccCard;
    if-ne v0, v1, :different
    iget-object v0, p0, Lcom/android/internal/telephony/uicc/EmbeddedSubscriptionSnapshot;->controllerGeneration:Ljava/lang/Object;
    iget-object v1, p1, Lcom/android/internal/telephony/uicc/EmbeddedSubscriptionSnapshot;->controllerGeneration:Ljava/lang/Object;
    if-ne v0, v1, :different
    iget-object v0, p0, Lcom/android/internal/telephony/uicc/EmbeddedSubscriptionSnapshot;->slotGeneration:Ljava/lang/Object;
    iget-object v1, p1, Lcom/android/internal/telephony/uicc/EmbeddedSubscriptionSnapshot;->slotGeneration:Ljava/lang/Object;
    if-ne v0, v1, :different
    iget-object v0, p0, Lcom/android/internal/telephony/uicc/EmbeddedSubscriptionSnapshot;->cardGeneration:Ljava/lang/Object;
    iget-object v1, p1, Lcom/android/internal/telephony/uicc/EmbeddedSubscriptionSnapshot;->cardGeneration:Ljava/lang/Object;
    if-ne v0, v1, :different
    iget-object v0, p0, Lcom/android/internal/telephony/uicc/EmbeddedSubscriptionSnapshot;->eid:Ljava/lang/String;
    iget-object v1, p1, Lcom/android/internal/telephony/uicc/EmbeddedSubscriptionSnapshot;->eid:Ljava/lang/String;
    invoke-virtual {v0, v1}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z
    move-result v0
    return v0
    :different
    const/4 v0, 0x0
    return v0
.end method
