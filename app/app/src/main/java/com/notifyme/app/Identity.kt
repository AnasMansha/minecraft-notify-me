package com.notifyme.app

import android.content.Context
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.PrivateKey
import java.security.Signature
import java.security.spec.ECGenParameterSpec

/**
 * The phone's identity: an EC P-256 key pair kept in the Android Keystore (private key never leaves it).
 * The callsign is derived from the public key, so it can be verified by the mod.
 */
class Identity(context: Context) {

    private val alias = "notifyme_identity"
    private val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
    private val callsigns = Callsign(
        readList(context, "callsign_adjectives.txt"),
        readList(context, "callsign_nouns.txt"),
    )

    @Volatile
    var callsign: String = ""
        private set

    @Volatile
    var publicKeyB64: String = ""
        private set

    init {
        if (!keyStore.containsAlias(alias)) generate()
        load()
    }

    private fun generate() {
        val gen = KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_EC, "AndroidKeyStore")
        gen.initialize(
            KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_SIGN)
                .setAlgorithmParameterSpec(ECGenParameterSpec("secp256r1"))
                .setDigests(KeyProperties.DIGEST_SHA256)
                .build()
        )
        gen.generateKeyPair()
    }

    private fun load() {
        val pub = keyStore.getCertificate(alias).publicKey.encoded // X.509 SubjectPublicKeyInfo
        publicKeyB64 = Base64.encodeToString(pub, Base64.NO_WRAP)
        callsign = callsigns.fromPublicKey(pub)
    }

    /** Throws away the key and makes a new callsign. Every world must approve the phone again. */
    fun reroll() {
        keyStore.deleteEntry(alias)
        generate()
        load()
    }

    fun sign(payload: String): String {
        val key = keyStore.getKey(alias, null) as PrivateKey
        val s = Signature.getInstance("SHA256withECDSA")
        s.initSign(key)
        s.update(payload.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(s.sign(), Base64.NO_WRAP)
    }

    companion object {
        fun authPayload(nonceB64: String, serverId: String) = "notifyme-auth|$nonceB64|$serverId"

        fun deviceName(): String = "${Build.MANUFACTURER} ${Build.MODEL}".trim()

        private fun readList(context: Context, name: String): List<String> =
            context.assets.open(name).bufferedReader().readLines().map { it.trim() }.filter { it.isNotEmpty() }
    }
}
