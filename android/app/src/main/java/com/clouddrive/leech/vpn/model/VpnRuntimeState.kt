package com.clouddrive.leech.vpn.model

enum class VpnStatus {
    IDLE,
    PARSING,
    VALIDATING,
    TCP_CHECK,
    STARTING_CORE,
    HANDSHAKING,
    ESTABLISHING_TUN,
    CONNECTIVITY_TEST,
    CONNECTED,
    DEGRADED,
    DISCONNECTING,
    DISCONNECTED,
    FAILED
}

data class VpnRuntimeState(
    val status: VpnStatus = VpnStatus.IDLE,
    val server: String? = null,
    val port: Int? = null,
    val protocol: String? = null,
    val latencyMs: Long? = null,
    val countryCode: String? = null,
    val countryName: String? = null,
    val countryFlag: String? = null,
    val uploadBytes: Long = 0L,
    val downloadBytes: Long = 0L,
    val uploadMbps: Double = 0.0,
    val downloadMbps: Double = 0.0,
    val sessionStartTime: Long? = null,
    val lastCheckedAt: Long? = null,
    val errorCode: String? = null,
    val errorMessage: String? = null
) {
    fun isRunning(): Boolean = status == VpnStatus.CONNECTED || status == VpnStatus.DEGRADED
}
