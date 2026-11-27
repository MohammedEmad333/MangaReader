package eu.kanade.tachiyomi.network.interceptor

import java.util.concurrent.ConcurrentHashMap

/**
 * Suppresses repeated automatic WebView challenges for a host after one
 * unsuccessful attempt. The visible WebView remains available to the user.
 */
class CloudflareChallengeBackoff(
    private val cooldownMillis: Long,
) {
    private val retryAfterByHost = ConcurrentHashMap<String, Long>()

    init {
        require(cooldownMillis >= 0L)
    }

    fun isCoolingDown(host: String, nowMillis: Long = System.currentTimeMillis()): Boolean {
        val retryAfter = retryAfterByHost[host] ?: return false
        if (retryAfter <= nowMillis) {
            retryAfterByHost.remove(host, retryAfter)
            return false
        }
        return true
    }

    fun recordFailure(host: String, nowMillis: Long = System.currentTimeMillis()) {
        retryAfterByHost[host] = nowMillis + cooldownMillis
    }

    fun clear(host: String) {
        retryAfterByHost.remove(host)
    }
}
