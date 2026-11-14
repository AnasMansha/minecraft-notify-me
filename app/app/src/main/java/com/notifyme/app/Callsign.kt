package com.notifyme.app

import java.security.MessageDigest
import java.util.Locale

/**
 * Callsign derived from the phone's public key, e.g. "Odd Deer 68".
 * MUST match mod/src/main/java/com/notifyme/util/Callsign.java and protocol/callsign_ref.py.
 */
class Callsign(private val adjectives: List<String>, private val nouns: List<String>) {

    init {
        require(adjectives.size == 256 && nouns.size == 256) { "word lists must have 256 entries" }
    }

    /** adjective = h[0], noun = h[1], number = h[2] % 100, e.g. "Odd Deer 68". */
    fun fromPublicKey(encodedKey: ByteArray): String {
        val h = MessageDigest.getInstance("SHA-256").digest(encodedKey)
        val num = (h[2].toInt() and 0xFF) % 100
        return "${adjectives[h[0].toInt() and 0xFF]} ${nouns[h[1].toInt() and 0xFF]} ${String.format(Locale.ROOT, "%02d", num)}"
    }
}
