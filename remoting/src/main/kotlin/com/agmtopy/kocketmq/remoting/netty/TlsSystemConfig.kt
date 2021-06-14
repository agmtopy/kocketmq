package com.agmtopy.kocketmq.remoting.netty

import com.agmtopy.kocketmq.remoting.common.TlsMode
import io.netty.handler.ssl.SslContext
import java.lang.Boolean


object TlsSystemConfig {
    const val TLS_SERVER_MODE = "tls.server.mode"
    const val TLS_ENABLE = "tls.enable"
    const val TLS_CONFIG_FILE = "tls.config.file"
    const val TLS_TEST_MODE_ENABLE = "tls.test.mode.enable"
    const val TLS_SERVER_NEED_CLIENT_AUTH = "tls.server.need.client.auth"
    const val TLS_SERVER_KEYPATH = "tls.server.keyPath"
    const val TLS_SERVER_KEYPASSWORD = "tls.server.keyPassword"
    const val TLS_SERVER_CERTPATH = "tls.server.certPath"
    const val TLS_SERVER_AUTHCLIENT = "tls.server.authClient"
    const val TLS_SERVER_TRUSTCERTPATH = "tls.server.trustCertPath"
    const val TLS_CLIENT_KEYPATH = "tls.client.keyPath"
    const val TLS_CLIENT_KEYPASSWORD = "tls.client.keyPassword"
    const val TLS_CLIENT_CERTPATH = "tls.client.certPath"
    const val TLS_CLIENT_AUTHSERVER = "tls.client.authServer"
    const val TLS_CLIENT_TRUSTCERTPATH = "tls.client.trustCertPath"

    /**
     * To determine whether use SSL in client-side, include SDK client and BrokerOuterAPI
     */
    var tlsEnable = Boolean.parseBoolean(System.getProperty(TLS_ENABLE, "false"))

    /**
     * To determine whether use test mode when initialize TLS context
     */
    var tlsTestModeEnable = Boolean.parseBoolean(System.getProperty(TLS_TEST_MODE_ENABLE, "true"))

    /**
     * Indicates the state of the [javax.net.ssl.SSLEngine] with respect to client authentication.
     * This configuration item really only applies when building the server-side [SslContext],
     * and can be set to none, require or optional.
     */
    var tlsServerNeedClientAuth = System.getProperty(TLS_SERVER_NEED_CLIENT_AUTH, "none")

    /**
     * The store path of server-side private key
     */
    var tlsServerKeyPath = System.getProperty(TLS_SERVER_KEYPATH, null)

    /**
     * The  password of the server-side private key
     */
    var tlsServerKeyPassword = System.getProperty(TLS_SERVER_KEYPASSWORD, null)

    /**
     * The store path of server-side X.509 certificate chain in PEM format
     */
    var tlsServerCertPath = System.getProperty(TLS_SERVER_CERTPATH, null)

    /**
     * To determine whether verify the client endpoint's certificate strictly
     */
    var tlsServerAuthClient = Boolean.parseBoolean(System.getProperty(TLS_SERVER_AUTHCLIENT, "false"))

    /**
     * The store path of trusted certificates for verifying the client endpoint's certificate
     */
    var tlsServerTrustCertPath = System.getProperty(TLS_SERVER_TRUSTCERTPATH, null)

    /**
     * The store path of client-side private key
     */
    var tlsClientKeyPath = System.getProperty(TLS_CLIENT_KEYPATH, null)

    /**
     * The  password of the client-side private key
     */
    var tlsClientKeyPassword = System.getProperty(TLS_CLIENT_KEYPASSWORD, null)

    /**
     * The store path of client-side X.509 certificate chain in PEM format
     */
    var tlsClientCertPath = System.getProperty(TLS_CLIENT_CERTPATH, null)

    /**
     * To determine whether verify the server endpoint's certificate strictly
     */
    var tlsClientAuthServer = Boolean.parseBoolean(System.getProperty(TLS_CLIENT_AUTHSERVER, "false"))

    /**
     * The store path of trusted certificates for verifying the server endpoint's certificate
     */
    var tlsClientTrustCertPath = System.getProperty(TLS_CLIENT_TRUSTCERTPATH, null)

    /**
     * For server, three SSL modes are supported: disabled, permissive and enforcing.
     * For client, use [TlsSystemConfig.tlsEnable] to determine whether use SSL.
     *
     *  1. **disabled:** SSL is not supported; any incoming SSL handshake will be rejected, causing connection closed.
     *  1. **permissive:** SSL is optional, aka, server in this mode can serve client connections with or without SSL;
     *  1. **enforcing:** SSL is required, aka, non SSL connection will be rejected.
     *
     */
    var tlsMode: TlsMode = TlsMode.parse(System.getProperty(TLS_SERVER_MODE, "permissive"))

    /**
     * A config file to store the above TLS related configurations,
     * except [TlsSystemConfig.tlsMode] and [TlsSystemConfig.tlsEnable]
     */
    var tlsConfigFile = System.getProperty(TLS_CONFIG_FILE, "/etc/rocketmq/tls.properties")
}