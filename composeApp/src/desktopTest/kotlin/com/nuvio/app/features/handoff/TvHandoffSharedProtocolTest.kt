package com.nuvio.app.features.handoff

import java.io.ByteArrayInputStream
import java.math.BigInteger
import java.net.URLEncoder
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.Signature
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.security.interfaces.RSAPublicKey
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

/** The shared code iOS uses where it has no JDK; verified here against the JDK's own implementations. */
class TvHandoffSharedProtocolTest {
    private val keys = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
    private fun sha256(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes)

    @Test fun `subject public key info matches the JDK encoding`() {
        val pkcs1 = TvHandoffDer.pkcs1FromSubjectPublicKeyInfo(keys.public.encoded)
        assertContentEquals(keys.public.encoded, TvHandoffDer.subjectPublicKeyInfo(pkcs1))
        val parts = TvHandoffDer.rsaPublicKey(pkcs1)
        val jdk = keys.public as RSAPublicKey
        assertEquals(jdk.modulus, BigInteger(1, parts.modulus))
        assertEquals(jdk.publicExponent, BigInteger(1, parts.exponent))
        assertEquals(256, parts.modulus.size)
    }

    @Test fun `self-signed sender certificate parses and verifies like a BouncyCastle one`() {
        val pkcs1 = TvHandoffDer.pkcs1FromSubjectPublicKeyInfo(keys.public.encoded)
        val now = 1_795_000_000_000L
        val serial = ByteArray(16) { (it * 37 + 129).toByte() }
        val tbs = TvHandoffDer.tbsCertificate("Nuvio iOS", serial, now - 60_000, now + 3650L * 86_400_000, pkcs1)
        val signature = Signature.getInstance("SHA256withRSA").apply { initSign(keys.private); update(tbs) }.sign()
        val der = TvHandoffDer.certificate(tbs, signature)
        val certificate = CertificateFactory.getInstance("X.509")
            .generateCertificate(ByteArrayInputStream(der)) as X509Certificate
        certificate.verify(keys.public)
        assertEquals(3, certificate.version)
        assertEquals("CN=Nuvio iOS", certificate.subjectX500Principal.name)
        assertEquals(BigInteger(1, serial), certificate.serialNumber)
        assertEquals((now - 60_000) / 1000, certificate.notBefore.time / 1000)
        assertEquals((now + 3650L * 86_400_000) / 1000, certificate.notAfter.time / 1000)
        assertContentEquals(keys.public.encoded, certificate.publicKey.encoded)
        val parameters = tvHandoffLaunchParameters("192.168.1.2", 4433, der, certificate.publicKey.encoded, "t",
            TvHandoffRequest(requestId = "r", senderDeviceId = "s", targetDeviceId = "tv", accountId = "a",
                profileId = 1, contentType = "movie", contentId = "c", videoId = "v", title = "T", positionMs = 1,
                durationMs = 2, createdAtEpochMs = 1, expiresAtEpochMs = 2), ::sha256)
        assertEquals(certificate.fingerprint(), parameters.getValue("certificatePin").toString().trim('"'))
        assertEquals("\"sha256//" + java.util.Base64.getEncoder().encodeToString(sha256(keys.public.encoded)) + "\"",
            parameters.getValue("publicKeyPin").toString())
    }

    @Test fun `android TV pairing secret checks the code prefix`() {
        val client = TvHandoffDer.rsaPublicKey(TvHandoffDer.pkcs1FromSubjectPublicKeyInfo(keys.public.encoded))
        val server = RsaPublicKeyParts(byteArrayOf(1, 2, 3), byteArrayOf(1, 0, 1))
        val suffix = "a1b2"
        val digest = sha256(client.modulus + client.exponent + server.modulus + server.exponent +
            byteArrayOf(0xa1.toByte(), 0xb2.toByte()))
        val prefix = "%02x".format(digest.first().toInt() and 255)
        assertContentEquals(digest, AndroidTvPairing.secretFor(client, server, prefix + suffix, ::sha256))
        val wrong = "%02x".format((digest.first().toInt() + 1) and 255)
        assertFailsWith<IllegalArgumentException> { AndroidTvPairing.secretFor(client, server, wrong + suffix, ::sha256) }
        assertFailsWith<IllegalArgumentException> { AndroidTvPairing.secretFor(client, server, "12345", ::sha256) }
    }

    @Test fun `launch links use the JDK form encoding`() {
        listOf("{\"token\":\"a+b c\"}", "ä/€?&=*~-._", "").forEach {
            assertEquals(URLEncoder.encode(it, "UTF-8"), formUrlEncode(it))
        }
    }

    @Test fun `TV addresses are literal private IPv4 only`() {
        assertEquals("192.168.1.4", requirePrivateTvAddress("192.168.1.4"))
        listOf("10.0.0.1", "172.16.0.1", "172.31.255.255", "169.254.3.3").forEach { requirePrivateTvAddress(it) }
        listOf("8.8.8.8", "172.32.0.1", "127.0.0.1", "224.0.0.251", "192.168.1.256", "tv.local", "192.168.1.4:80")
            .forEach { assertFailsWith<IllegalArgumentException> { requirePrivateTvAddress(it) } }
    }

    @Test fun `android TV mDNS answers yield the advertised service name`() {
        fun name(vararg labels: String) = labels.fold(ByteArray(0)) { acc, label ->
            acc + byteArrayOf(label.length.toByte()) + label.toByteArray()
        } + byteArrayOf(0)
        val service = name("_androidtvremote2", "_tcp", "local")
        val instance = name("Living Room", "_androidtvremote2", "_tcp", "local")
        val packet = byteArrayOf(0, 0, 0x84.toByte(), 0, 0, 0, 0, 1, 0, 0, 0, 0) + service +
            byteArrayOf(0, 12, 0, 1, 0, 0, 0, 120, 0, instance.size.toByte()) + instance
        assertEquals("Living Room", androidTvServiceName(packet))
        assertNull(androidTvServiceName(packet.copyOf(packet.size - 4)))
        val loop = byteArrayOf(0, 0, 0, 0, 0, 0, 0, 1, 0, 0, 0, 0, 0xc0.toByte(), 12)
        assertNull(androidTvServiceName(loop))
    }
}
