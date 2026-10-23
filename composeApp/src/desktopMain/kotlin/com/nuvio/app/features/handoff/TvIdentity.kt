package com.nuvio.app.features.handoff

import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import java.math.BigInteger
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.PosixFilePermissions
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.MessageDigest
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.Date
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

internal fun X509Certificate.fingerprint(): String = MessageDigest.getInstance("SHA-256")
    .digest(encoded).joinToString("") { "%02x".format(it) }

internal fun privateDirectory(path: Path) {
    require(!Files.isSymbolicLink(path))
    Files.createDirectories(path)
    if (path.fileSystem.supportedFileAttributeViews().contains("posix")) {
        Files.setPosixFilePermissions(path, PosixFilePermissions.fromString("rwx------"))
    }
}

internal fun privateWrite(path: Path, bytes: ByteArray) {
    privateDirectory(path.parent)
    val temporary = Files.createTempFile(path.parent, ".handoff-", ".tmp")
    try {
        if (path.fileSystem.supportedFileAttributeViews().contains("posix")) {
            Files.setPosixFilePermissions(temporary, PosixFilePermissions.fromString("rw-------"))
        }
        Files.write(temporary, bytes)
        Files.move(temporary, path, java.nio.file.StandardCopyOption.REPLACE_EXISTING,
            java.nio.file.StandardCopyOption.ATOMIC_MOVE)
    } finally {
        Files.deleteIfExists(temporary)
    }
}

internal class TvIdentity(private val directory: Path) {
    private val store: KeyStore
    val certificate: X509Certificate
    private val passwordChars: CharArray
    init {
        privateDirectory(directory)
        val storeFile = directory.resolve("identity.p12")
        val passwordFile = directory.resolve("identity-password")
        val password = if (Files.exists(passwordFile)) Files.readString(passwordFile) else {
            ByteArray(32).also(SecureRandom()::nextBytes).joinToString("") { "%02x".format(it) }
                .also { privateWrite(passwordFile, it.toByteArray()) }
        }
        store = KeyStore.getInstance("PKCS12")
        if (Files.exists(storeFile)) {
            Files.newInputStream(storeFile).use { store.load(it, password.toCharArray()) }
        } else {
            store.load(null, password.toCharArray())
            val pair = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
            val name = X500Name("CN=Nuvio Desktop")
            val now = System.currentTimeMillis()
            val cert = JcaX509CertificateConverter().getCertificate(
                JcaX509v3CertificateBuilder(name, BigInteger(128, SecureRandom()), Date(now - 60_000),
                    Date(now + 3650L * 86_400_000), name, pair.public)
                    .build(JcaContentSignerBuilder("SHA256withRSA").build(pair.private)),
            )
            store.setKeyEntry("nuvio", pair.private, password.toCharArray(), arrayOf(cert))
            val output = java.io.ByteArrayOutputStream()
            store.store(output, password.toCharArray())
            privateWrite(storeFile, output.toByteArray())
        }
        certificate = store.getCertificate("nuvio") as X509Certificate
        passwordChars = password.toCharArray()
    }

    /** Pairing sockets verify the peer with the TV's displayed code; subsequent sockets pin it. */
    fun context(peerFingerprint: String? = null): SSLContext {
        val managers = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm())
        managers.init(store, passwordChars)
        return SSLContext.getInstance("TLS").apply {
            init(managers.keyManagers, arrayOf<TrustManager>(object : X509TrustManager {
                override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
                override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) = Unit
                override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {
                    require(chain.isNotEmpty())
                    if (peerFingerprint != null) require(chain.first().fingerprint() == peerFingerprint) {
                        "TV certificate changed. Pair again on the TV."
                    }
                }
            }), SecureRandom())
        }
    }
}
