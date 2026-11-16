# Custom method fragment for target SystemServer, not a complete stock class.
# Call once in startOtherServices after setSafeMode, before device-specific services.
# Parent hash/method register/scope checks and affected ART/CLC refresh are mandatory.
.method private startN3OwnerLifecycle()V
    .registers 4
    :n3_try_start
    iget-object v0, p0, Lcom/android/server/SystemServer;->mSystemServiceManager:Lcom/android/server/SystemServiceManager;
    const-string v1, "org.sourceowned.n3.runtime.v2.N3SystemService"
    const-string v2, "/system/framework/n3-runtime-v2.jar"
    invoke-virtual {v0, v1, v2}, Lcom/android/server/SystemServiceManager;->startServiceFromJar(Ljava/lang/String;Ljava/lang/String;)Lcom/android/server/SystemService;
    :n3_try_end
    .catch Ljava/lang/Exception; {:n3_try_start .. :n3_try_end} :n3_failed
    return-void
    :n3_failed
    move-exception v0
    const-string v1, "N3OwnerLifecycle"
    const-string v2, "Lifecycle loader unavailable; no routing operation"
    invoke-static {v1, v2, v0}, Landroid/util/Slog;->e(Ljava/lang/String;Ljava/lang/String;Ljava/lang/Throwable;)I
    return-void
.end method
