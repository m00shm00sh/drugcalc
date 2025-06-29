package com.moshy.drugcalc.server.util

import io.ktor.network.tls.certificates.buildKeyStore
import io.r2.simplepemkeystore.*
import java.security.KeyStore
import java.security.cert.X509Certificate
import java.util.Date
import javax.naming.ldap.LdapName
import javax.net.ssl.KeyManagerFactory

internal data class KeystoreWithAlias(
    val keyStore: KeyStore,
    val alias: String,
    val keyPassword: String,
    val forDomains: List<String>
)

internal fun generateSelfSignedKeystoreForLocalhost(): KeystoreWithAlias {
    val alias = "localhostSS"
    val kp = "AAAAAAAA"
    val domains = listOf("127.0.0.1", "::1", "localhost")
    return KeystoreWithAlias(
        buildKeyStore {
            certificate(alias) {
                password = kp
                this.domains = domains
            }
        },
        alias, kp, domains
    )
}

@Throws(IllegalArgumentException::class) // if certificate is invalid
internal fun loadPemKeystore(ssl: AppConfig.Http.SslKey): KeystoreWithAlias {
    SimplePemKeyStoreProvider.register()
    if (ssl.refresh != null) {
        KeyStore.getInstance(PEM_RELOAD_SSL_PROVIDER).let { ks ->
            ReloadablePemKeyStoreConfig()
                .apply {
                    addCertificate(PEM_ALIAS, ssl.pemPath.toTypedArray())
                    withRefreshInterval(ssl.refresh)
                }
                .asInputStream()
                .let { ks.load(it, EMPTY_CHAR_ARRAY) }
            KeyManagerFactory.getInstance(PEM_RELOAD_SSL_PROVIDER).let { km ->
                ExpiringCacheKeyManagerParameters.forKeyStore(ks)
                    .apply {
                        if (ssl.refresh > 0)
                            withRevalidation(ssl.refresh)
                    }
                    .let { km.init(it) }
            }
            val domains = validateCertificate(ks.getCertificate(PEM_ALIAS) as X509Certificate)
            return KeystoreWithAlias(ks, PEM_ALIAS, "", domains)
        }
    } else {
        KeyStore.getInstance(PEM_SSL_PROVIDER).let { ks ->
            MultiFileConcatSource()
                .apply { ssl.pemPath.forEach { add(it) } }
                .build()
                .let { ks.load(it, EMPTY_CHAR_ARRAY) }
            val domains = validateCertificate(ks.getCertificate(PEM_ALIAS) as X509Certificate)
            return KeystoreWithAlias(ks, PEM_ALIAS, "", domains)
        }
    }
}

private const val PEM_SSL_PROVIDER = "simplepem"
private const val PEM_RELOAD_SSL_PROVIDER = "simplepemreload"
private const val PEM_ALIAS = "server"
private val EMPTY_CHAR_ARRAY = CharArray(0)

// verify that certificate is not expired and extract domains listed on it; no checks against self signing is done
private fun validateCertificate(c: X509Certificate): List<String> =
    buildList {
        val now = Date()
        require(now >= c.notBefore && now < c.notAfter) {
            "invalid (expired) certificate"
        }
        LdapName(c.subjectX500Principal.name).apply {
            for (rdn in rdns) {
                if (rdn.type.lowercase() == "cn") {
                    add(rdn.value.toString())
                }
            }
        }
        for (san in c.subjectAlternativeNames) {
            val typ = san[0] as Int
            if (typ !in SAN_TYPE_DNS_OR_IP)
                continue
            val str = san[1] as String
            add(str)
        }
    }

private val SAN_TYPE_DNS_OR_IP = listOf(2 /* DNS: */, 7 /* IP: */)