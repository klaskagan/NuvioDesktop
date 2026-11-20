@file:OptIn(ExperimentalForeignApi::class)

package com.nuvio.app.features.handoff

import com.nuvio.app.features.plugins.cryptointerop.CC_SHA256
import com.nuvio.app.features.plugins.cryptointerop.CC_SHA256_DIGEST_LENGTH
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.IntVar
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.refTo
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.value
import platform.CoreFoundation.CFArrayGetCount
import platform.CoreFoundation.CFArrayGetValueAtIndex
import platform.CoreFoundation.CFArrayRef
import platform.CoreFoundation.CFDataCreate
import platform.CoreFoundation.CFDataRef
import platform.CoreFoundation.CFDictionaryCreateMutable
import platform.CoreFoundation.CFDictionarySetValue
import platform.CoreFoundation.CFMutableDictionaryRef
import platform.CoreFoundation.CFNumberCreate
import platform.CoreFoundation.CFRelease
import platform.CoreFoundation.CFStringCreateWithCString
import platform.CoreFoundation.CFTypeRef
import platform.CoreFoundation.CFTypeRefVar
import platform.CoreFoundation.kCFBooleanTrue
import platform.CoreFoundation.kCFNumberIntType
import platform.CoreFoundation.kCFStringEncodingUTF8
import platform.Foundation.NSDate
import platform.Foundation.timeIntervalSince1970
import platform.Security.SecCertificateCopyData
import platform.Security.SecCertificateCreateWithData
import platform.Security.SecCertificateRefVar
import platform.Security.SecIdentityCopyCertificate
import platform.Security.SecIdentityRef
import platform.Security.SecItemAdd
import platform.Security.SecItemCopyMatching
import platform.Security.SecItemDelete
import platform.Security.SecKeyCopyExternalRepresentation
import platform.Security.SecKeyCopyPublicKey
import platform.Security.SecKeyCreateRandomKey
import platform.Security.SecKeyCreateSignature
import platform.Security.SecRandomCopyBytes
import platform.Security.errSecSuccess
import platform.Security.kSecAttrAccessible
import platform.Security.kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly
import platform.Security.kSecAttrAccount
import platform.Security.kSecAttrApplicationTag
import platform.Security.kSecAttrIsPermanent
import platform.Security.kSecAttrKeySizeInBits
import platform.Security.kSecAttrKeyType
import platform.Security.kSecAttrKeyTypeRSA
import platform.Security.kSecAttrLabel
import platform.Security.kSecAttrService
import platform.Security.kSecClass
import platform.Security.kSecClassCertificate
import platform.Security.kSecClassGenericPassword
import platform.Security.kSecClassIdentity
import platform.Security.kSecClassKey
import platform.Security.kSecKeyAlgorithmRSASignatureMessagePKCS1v15SHA256
import platform.Security.kSecMatchLimit
import platform.Security.kSecMatchLimitAll
import platform.Security.kSecMatchLimitOne
import platform.Security.kSecPrivateKeyAttrs
import platform.Security.kSecRandomDefault
import platform.Security.kSecReturnData
import platform.Security.kSecReturnRef
import platform.Security.kSecValueData
import platform.Security.kSecValueRef
import platform.Security.sec_identity_create
import platform.darwin.NSObject

private const val KEYCHAIN_SERVICE = "com.nuvio.tv-handoff"
private const val KEY_TAG = "com.nuvio.tv-handoff.identity"
private const val CERTIFICATE_LABEL = "Nuvio TV handoff"
private const val COMMON_NAME = "Nuvio iOS"

internal fun iosSha256(value: ByteArray): ByteArray {
    val output = UByteArray(CC_SHA256_DIGEST_LENGTH.toInt())
    if (value.isEmpty()) CC_SHA256(null, 0u, output.refTo(0))
    else CC_SHA256(value.refTo(0), value.size.toUInt(), output.refTo(0))
    return output.toByteArray()
}

internal fun iosRandomBytes(size: Int): ByteArray = ByteArray(size).also {
    check(SecRandomCopyBytes(kSecRandomDefault, size.toULong(), it.refTo(0)) == errSecSuccess) { "Secure random failed" }
}

/** CoreFoundation values created for one Keychain call and released afterwards. */
private class CfScope : AutoCloseable {
    private val owned = mutableListOf<CFTypeRef?>()
    fun <T : CFTypeRef?> own(value: T): T = value.also { owned += it }
    fun string(value: String) = own(CFStringCreateWithCString(null, value, kCFStringEncodingUTF8))
    fun data(value: ByteArray) = own(CFDataCreate(null, value.toUByteArray().refTo(0), value.size.toLong()))
    fun number(value: Int): CFTypeRef? = memScoped {
        val number = alloc<IntVar>().also { it.value = value }
        own(CFNumberCreate(null, kCFNumberIntType, number.ptr))
    }
    fun dictionary(vararg entries: Pair<CFTypeRef?, CFTypeRef?>): CFMutableDictionaryRef {
        val dictionary = own(CFDictionaryCreateMutable(null, 0, null, null)) ?: error("Keychain query failed")
        entries.forEach { (key, value) -> CFDictionarySetValue(dictionary, key, value) }
        return dictionary
    }
    override fun close() { owned.asReversed().forEach { if (it != null) CFRelease(it) } }
}

/** Device-wide Keychain records, matching desktop's owner-only ~/.nuvio/tv-handoff files. */
internal object IosTvHandoffKeychain {
    fun read(name: String): String? = CfScope().use { cf ->
        val query = cf.dictionary(kSecClass to kSecClassGenericPassword, kSecAttrService to cf.string(KEYCHAIN_SERVICE),
            kSecAttrAccount to cf.string(name), kSecReturnData to kCFBooleanTrue, kSecMatchLimit to kSecMatchLimitOne)
        memScoped {
            val result = alloc<CFTypeRefVar>()
            if (SecItemCopyMatching(query, result.ptr) != errSecSuccess) return null
            val data: CFDataRef = result.value?.reinterpret() ?: return null
            try { data.bytes().decodeToString() } finally { CFRelease(data) }
        }
    }

    fun write(name: String, text: String) = CfScope().use { cf ->
        val match = cf.dictionary(kSecClass to kSecClassGenericPassword, kSecAttrService to cf.string(KEYCHAIN_SERVICE),
            kSecAttrAccount to cf.string(name))
        SecItemDelete(match)
        val item = cf.dictionary(kSecClass to kSecClassGenericPassword, kSecAttrService to cf.string(KEYCHAIN_SERVICE),
            kSecAttrAccount to cf.string(name), kSecValueData to cf.data(text.encodeToByteArray()),
            kSecAttrAccessible to kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly)
        check(SecItemAdd(item, null) == errSecSuccess) { "Unable to store TV pairing" }
    }
}

/**
 * The sender's RSA client/server identity. The private key never leaves the Keychain; the self-signed
 * certificate is built with shared DER code because iOS has no public X.509 builder.
 */
internal class IosTvIdentity private constructor(
    val secIdentity: NSObject,
    val certificateDer: ByteArray,
    val subjectPublicKeyInfo: ByteArray,
) {
    companion object {
        private var cached: IosTvIdentity? = null

        fun load(): IosTvIdentity = cached ?: (find() ?: create()).also { cached = it }

        private fun find(): IosTvIdentity? = CfScope().use { cf ->
            val query = cf.dictionary(kSecClass to kSecClassIdentity, kSecReturnRef to kCFBooleanTrue,
                kSecMatchLimit to kSecMatchLimitAll)
            memScoped {
                val result = alloc<CFTypeRefVar>()
                if (SecItemCopyMatching(query, result.ptr) != errSecSuccess) return null
                val identities: CFArrayRef = result.value?.reinterpret() ?: return null
                try {
                    (0 until CFArrayGetCount(identities)).firstNotNullOfOrNull { index ->
                        val identity: SecIdentityRef = CFArrayGetValueAtIndex(identities, index)?.reinterpret()
                            ?: return@firstNotNullOfOrNull null
                        fromIdentity(identity)
                    }
                } finally { CFRelease(identities) }
            }
        }

        private fun fromIdentity(identity: SecIdentityRef): IosTvIdentity? = memScoped {
            val certificate = alloc<SecCertificateRefVar>()
            if (SecIdentityCopyCertificate(identity, certificate.ptr) != errSecSuccess) return null
            val certificateRef = certificate.value ?: return null
            try {
                val data = SecCertificateCopyData(certificateRef) ?: return null
                val der = try { data.bytes() } finally { CFRelease(data) }
                if (!der.containsSubsequence(COMMON_NAME.encodeToByteArray())) return null
                val key = platform.Security.SecCertificateCopyKey(certificateRef) ?: return null
                val pkcs1 = try {
                    SecKeyCopyExternalRepresentation(key, null)?.let { try { it.bytes() } finally { CFRelease(it) } }
                } finally { CFRelease(key) } ?: return null
                val secIdentity = sec_identity_create(identity) ?: return null
                IosTvIdentity(secIdentity, der, TvHandoffDer.subjectPublicKeyInfo(pkcs1))
            } finally { CFRelease(certificateRef) }
        }

        private fun create(): IosTvIdentity {
            CfScope().use { cf ->
                SecItemDelete(cf.dictionary(kSecClass to kSecClassKey, kSecAttrApplicationTag to cf.data(KEY_TAG.encodeToByteArray())))
                SecItemDelete(cf.dictionary(kSecClass to kSecClassCertificate, kSecAttrLabel to cf.string(CERTIFICATE_LABEL)))
                val privateAttributes = cf.dictionary(kSecAttrIsPermanent to kCFBooleanTrue,
                    kSecAttrApplicationTag to cf.data(KEY_TAG.encodeToByteArray()),
                    kSecAttrAccessible to kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly)
                val parameters = cf.dictionary(kSecAttrKeyType to kSecAttrKeyTypeRSA,
                    kSecAttrKeySizeInBits to cf.number(2048), kSecPrivateKeyAttrs to privateAttributes)
                val privateKey = cf.own(SecKeyCreateRandomKey(parameters, null)) ?: error("Unable to create TV identity key")
                val publicKey = cf.own(SecKeyCopyPublicKey(privateKey)) ?: error("Unable to read TV identity key")
                val pkcs1 = SecKeyCopyExternalRepresentation(publicKey, null)?.let { try { it.bytes() } finally { CFRelease(it) } }
                    ?: error("Unable to export TV identity key")
                val now = (NSDate().timeIntervalSince1970 * 1000).toLong()
                val serial = iosRandomBytes(16).also { it[0] = (it[0].toInt() and 0x7f).toByte() }
                val tbs = TvHandoffDer.tbsCertificate(COMMON_NAME, serial, now - 60_000, now + 3650L * 86_400_000, pkcs1)
                val signature = cf.own(SecKeyCreateSignature(privateKey, kSecKeyAlgorithmRSASignatureMessagePKCS1v15SHA256,
                    cf.data(tbs), null))?.bytes() ?: error("Unable to sign TV identity")
                val der = TvHandoffDer.certificate(tbs, signature)
                val certificate = cf.own(SecCertificateCreateWithData(null, cf.data(der))) ?: error("Invalid TV identity certificate")
                val stored = SecItemAdd(cf.dictionary(kSecClass to kSecClassCertificate, kSecValueRef to certificate,
                    kSecAttrLabel to cf.string(CERTIFICATE_LABEL),
                    kSecAttrAccessible to kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly), null)
                check(stored == errSecSuccess) { "Unable to store TV identity ($stored)" }
            }
            return find() ?: error("TV identity is unavailable")
        }
    }
}

private fun ByteArray.containsSubsequence(needle: ByteArray): Boolean =
    (0..size - needle.size).any { start -> needle.indices.all { this[start + it] == needle[it] } }
