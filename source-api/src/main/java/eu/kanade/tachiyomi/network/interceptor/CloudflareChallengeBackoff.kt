package eu.kanade.tachiyomi.network.interceptor

import java.util.concurrent.ConcurrentHashMap

/**
 * Suppresses repeated automatic WebView challenges for a host after one
 * unsuccessful attempt. The visible WebView can clear the shared host cooldown
 * after it confirms that the protected page loaded successfully.
 */
class CloudflareChallengeBackoff(
    private val cooldownMillis: Long,
) {
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

    companion object {
        // Shared by the OkHttp interceptor and the visible app WebView. An
        // instance-local map cannot be cleared by the manual recovery screen.
        private val retryAfterByHost = ConcurrentHashMap<String, Long>()

        /** Clears a host cooldown after the visible WebView confirms success. */
        @JvmStatic
        fun clearHost(host: String) {
            retryAfterByHost.remove(host)
        }
    }
}
