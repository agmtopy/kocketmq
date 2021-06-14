package com.agmtopy.kocketmq.remoting.netty

import com.agmtopy.kocketmq.logging.InternalLogger
import com.agmtopy.kocketmq.logging.inner.InternalLoggerFactory
import com.agmtopy.kocketmq.remoting.common.RemotingHelper
import com.agmtopy.kocketmq.remoting.netty.TlsSystemConfig.TLS_CLIENT_AUTHSERVER
import com.agmtopy.kocketmq.remoting.netty.TlsSystemConfig.TLS_CLIENT_CERTPATH
import com.agmtopy.kocketmq.remoting.netty.TlsSystemConfig.TLS_CLIENT_KEYPASSWORD
import com.agmtopy.kocketmq.remoting.netty.TlsSystemConfig.TLS_CLIENT_KEYPATH
import com.agmtopy.kocketmq.remoting.netty.TlsSystemConfig.TLS_CLIENT_TRUSTCERTPATH
import com.agmtopy.kocketmq.remoting.netty.TlsSystemConfig.TLS_SERVER_AUTHCLIENT
import com.agmtopy.kocketmq.remoting.netty.TlsSystemConfig.TLS_SERVER_CERTPATH
import com.agmtopy.kocketmq.remoting.netty.TlsSystemConfig.TLS_SERVER_KEYPASSWORD
import com.agmtopy.kocketmq.remoting.netty.TlsSystemConfig.TLS_SERVER_KEYPATH
import com.agmtopy.kocketmq.remoting.netty.TlsSystemConfig.TLS_SERVER_NEED_CLIENT_AUTH
import com.agmtopy.kocketmq.remoting.netty.TlsSystemConfig.TLS_SERVER_TRUSTCERTPATH
import com.agmtopy.kocketmq.remoting.netty.TlsSystemConfig.TLS_TEST_MODE_ENABLE
import com.agmtopy.kocketmq.remoting.netty.TlsSystemConfig.tlsClientAuthServer
import com.agmtopy.kocketmq.remoting.netty.TlsSystemConfig.tlsClientCertPath
import com.agmtopy.kocketmq.remoting.netty.TlsSystemConfig.tlsClientKeyPassword
import com.agmtopy.kocketmq.remoting.netty.TlsSystemConfig.tlsClientKeyPath
import com.agmtopy.kocketmq.remoting.netty.TlsSystemConfig.tlsClientTrustCertPath
import com.agmtopy.kocketmq.remoting.netty.TlsSystemConfig.tlsServerAuthClient
import com.agmtopy.kocketmq.remoting.netty.TlsSystemConfig.tlsServerCertPath
import com.agmtopy.kocketmq.remoting.netty.TlsSystemConfig.tlsServerKeyPassword
import com.agmtopy.kocketmq.remoting.netty.TlsSystemConfig.tlsServerKeyPath
import com.agmtopy.kocketmq.remoting.netty.TlsSystemConfig.tlsServerNeedClientAuth
import com.agmtopy.kocketmq.remoting.netty.TlsSystemConfig.tlsServerTrustCertPath
import com.agmtopy.kocketmq.remoting.netty.TlsSystemConfig.tlsTestModeEnable
import io.netty.handler.ssl.*
import io.netty.handler.ssl.util.InsecureTrustManagerFactory
import io.netty.handler.ssl.util.SelfSignedCertificate
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.io.InputStream
import java.security.cert.CertificateException
import java.util.*

class TlsHelper {
    interface DecryptionStrategy {
        /**
         * Decrypt the target encrpted private key file.
         */
        @Throws(IOException::class)
        fun decryptPrivateKey(privateKeyEncryptPath: String?, forClient: Boolean): InputStream?
    }

    companion object {
        private val LOGGER: InternalLogger = InternalLoggerFactory.getLogger(RemotingHelper.ROCKETMQ_REMOTING)
        fun registerDecryptionStrategy(decryptionStrategy: DecryptionStrategy) {
            TlsHelper.decryptionStrategy = decryptionStrategy
        }

        private fun extractTlsConfigFromFile(configFile: File) {
            if (!(configFile.exists() && configFile.isFile && configFile.canRead())) {
                LOGGER.info("Tls config file doesn't exist, skip it")
                return
            }
            val properties: Properties
            properties = Properties()
            var inputStream: InputStream? = null
            try {
                inputStream = FileInputStream(configFile)
                properties.load(inputStream)
            } catch (ignore: IOException) {
            } finally {
                if (null != inputStream) {
                    try {
                        inputStream.close()
                    } catch (ignore: IOException) {
                    }
                }
            }
            tlsTestModeEnable = java.lang.Boolean.parseBoolean(
                properties.getProperty(
                    TLS_TEST_MODE_ENABLE,
                    java.lang.String.valueOf(tlsTestModeEnable)
                )
            )
            tlsServerNeedClientAuth = properties.getProperty(TLS_SERVER_NEED_CLIENT_AUTH, tlsServerNeedClientAuth)
            tlsServerKeyPath = properties.getProperty(TLS_SERVER_KEYPATH, tlsServerKeyPath)
            tlsServerKeyPassword = properties.getProperty(TLS_SERVER_KEYPASSWORD, tlsServerKeyPassword)
            tlsServerCertPath = properties.getProperty(TLS_SERVER_CERTPATH, tlsServerCertPath)
            tlsServerAuthClient = java.lang.Boolean.parseBoolean(
                properties.getProperty(
                    TLS_SERVER_AUTHCLIENT,
                    java.lang.String.valueOf(tlsServerAuthClient)
                )
            )
            tlsServerTrustCertPath = properties.getProperty(TLS_SERVER_TRUSTCERTPATH, tlsServerTrustCertPath)
            tlsClientKeyPath = properties.getProperty(TLS_CLIENT_KEYPATH, tlsClientKeyPath)
            tlsClientKeyPassword = properties.getProperty(TLS_CLIENT_KEYPASSWORD, tlsClientKeyPassword)
            tlsClientCertPath = properties.getProperty(TLS_CLIENT_CERTPATH, tlsClientCertPath)
            tlsClientAuthServer = java.lang.Boolean.parseBoolean(
                properties.getProperty(
                    TLS_CLIENT_AUTHSERVER,
                    java.lang.String.valueOf(tlsClientAuthServer)
                )
            )
            tlsClientTrustCertPath = properties.getProperty(TLS_CLIENT_TRUSTCERTPATH, tlsClientTrustCertPath)
        }

        private fun logTheFinalUsedTlsConfig() {
            LOGGER.info("Log the final used tls related configuration")
            LOGGER.info("{} = {}", TLS_TEST_MODE_ENABLE, tlsTestModeEnable)
            LOGGER.info("{} = {}", TLS_SERVER_NEED_CLIENT_AUTH, tlsServerNeedClientAuth)
            LOGGER.info("{} = {}", TLS_SERVER_KEYPATH, tlsServerKeyPath)
            LOGGER.info("{} = {}", TLS_SERVER_KEYPASSWORD, tlsServerKeyPassword)
            LOGGER.info("{} = {}", TLS_SERVER_CERTPATH, tlsServerCertPath)
            LOGGER.info("{} = {}", TLS_SERVER_AUTHCLIENT, tlsServerAuthClient)
            LOGGER.info("{} = {}", TLS_SERVER_TRUSTCERTPATH, tlsServerTrustCertPath)
            LOGGER.info("{} = {}", TLS_CLIENT_KEYPATH, tlsClientKeyPath)
            LOGGER.info("{} = {}", TLS_CLIENT_KEYPASSWORD, tlsClientKeyPassword)
            LOGGER.info("{} = {}", TLS_CLIENT_CERTPATH, tlsClientCertPath)
            LOGGER.info("{} = {}", TLS_CLIENT_AUTHSERVER, tlsClientAuthServer)
            LOGGER.info("{} = {}", TLS_CLIENT_TRUSTCERTPATH, tlsClientTrustCertPath)
        }

        private fun parseClientAuthMode(authMode: String?): ClientAuth? {
            if (null == authMode || authMode.trim { it <= ' ' }.isEmpty()) {
                return ClientAuth.NONE
            }
            for (clientAuth in ClientAuth.values()) {
                if (clientAuth.name == authMode.toUpperCase()) {
                    return clientAuth
                }
            }
            return ClientAuth.NONE
        }

        /**
         * Determine if a string is `null` or [String.isEmpty] returns `true`.
         */
        private fun isNullOrEmpty(s: String?): Boolean {
            return s == null || s.isEmpty()
        }

        private var decryptionStrategy: DecryptionStrategy = object : DecryptionStrategy {
            @Throws(IOException::class)
            override fun decryptPrivateKey(
                privateKeyEncryptPath: String?,
                forClient: Boolean
            ): InputStream? {
                return FileInputStream(privateKeyEncryptPath)
            }
        }

        @Throws(IOException::class, CertificateException::class)
        fun buildSslContext(forClient: Boolean): SslContext? {
            val configFile = File(TlsSystemConfig.tlsConfigFile)
            extractTlsConfigFromFile(configFile)
            logTheFinalUsedTlsConfig()
            val provider: SslProvider
            if (OpenSsl.isAvailable()) {
                provider = SslProvider.OPENSSL
                LOGGER.info("Using OpenSSL provider")
            } else {
                provider = SslProvider.JDK
                LOGGER.info("Using JDK SSL provider")
            }
            return if (forClient) {
                if (tlsTestModeEnable) {
                    SslContextBuilder
                        .forClient()
                        .sslProvider(SslProvider.JDK)
                        .trustManager(InsecureTrustManagerFactory.INSTANCE)
                        .build()
                } else {
                    val sslContextBuilder = SslContextBuilder.forClient().sslProvider(SslProvider.JDK)
                    if (!TlsSystemConfig.tlsClientAuthServer) {
                        sslContextBuilder.trustManager(InsecureTrustManagerFactory.INSTANCE)
                    } else {
                        if (!isNullOrEmpty(TlsSystemConfig.tlsClientTrustCertPath)) {
                            sslContextBuilder.trustManager(File(TlsSystemConfig.tlsClientTrustCertPath))
                        }
                    }
                    sslContextBuilder.keyManager(
                        if (!isNullOrEmpty(tlsClientCertPath)) FileInputStream(tlsClientCertPath) else null,
                        if (!isNullOrEmpty(tlsClientKeyPath)) decryptionStrategy.decryptPrivateKey(
                            tlsClientKeyPath,
                            true
                        ) else null,
                        if (!isNullOrEmpty(tlsClientKeyPassword)) tlsClientKeyPassword else null
                    )
                        .build()
                }
            } else {
                if (tlsTestModeEnable) {
                    val selfSignedCertificate = SelfSignedCertificate()
                    SslContextBuilder
                        .forServer(selfSignedCertificate.certificate(), selfSignedCertificate.privateKey())
                        .sslProvider(SslProvider.JDK)
                        .clientAuth(ClientAuth.OPTIONAL)
                        .build()
                } else {
                    val sslContextBuilder = SslContextBuilder.forServer(
                        if (!isNullOrEmpty(tlsServerCertPath)) FileInputStream(tlsServerCertPath) else null,
                        if (!isNullOrEmpty(tlsServerKeyPath)) decryptionStrategy.decryptPrivateKey(
                            tlsServerKeyPath,
                            false
                        ) else null,
                        if (!isNullOrEmpty(tlsServerKeyPassword)) tlsServerKeyPassword else null
                    )
                        .sslProvider(provider)
                    if (!tlsServerAuthClient) {
                        sslContextBuilder.trustManager(InsecureTrustManagerFactory.INSTANCE)
                    } else {
                        if (!isNullOrEmpty(tlsServerTrustCertPath)) {
                            sslContextBuilder.trustManager(File(tlsServerTrustCertPath))
                        }
                    }
                    sslContextBuilder.clientAuth(parseClientAuthMode(tlsServerNeedClientAuth))
                    sslContextBuilder.build()
                }
            }
        }
    }

}