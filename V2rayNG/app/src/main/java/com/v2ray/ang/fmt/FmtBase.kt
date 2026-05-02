package com.v2ray.ang.fmt

import com.v2ray.ang.AppConfig
import com.v2ray.ang.dto.ProfileItem
import com.v2ray.ang.enums.NetworkType
import com.v2ray.ang.extension.nullIfBlank
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.util.HttpUtil
import com.v2ray.ang.util.Utils
import java.net.URI

open class FmtBase {
    /**
     * Converts a ProfileItem object to a URI string.
     *
     * @param config the ProfileItem object to convert
     * @param userInfo the user information to include in the URI
     * @param dicQuery the query parameters to include in the URI
     * @return the converted URI string
     */
    fun toUri(config: ProfileItem, userInfo: String?, dicQuery: HashMap<String, String>?): String {
        val query = if (dicQuery != null)
            "?" + dicQuery.toList().joinToString(
                separator = "&",
                transform = { it.first + "=" + Utils.encodeURIComponent(it.second) })
        else ""

        val url = String.format(
            "%s@%s:%s",
            Utils.encodeURIComponent(userInfo ?: ""),
            Utils.getIpv6Address(HttpUtil.toIdnDomain(config.server.orEmpty())),
            config.serverPort
        )

        return "${url}${query}#${Utils.encodeURIComponent(config.remarks)}"
    }

    /**
     * Extracts query parameters from a URI.
     *
     * @param uri the URI to extract query parameters from
     * @return a map of query parameters
     */
    fun getQueryParam(uri: URI): Map<String, String> {
        return uri.rawQuery.split("&")
            .associate { it.split("=").let { (k, v) -> k to Utils.decodeURIComponent(v) } }
    }

    /**
     * Populates a ProfileItem object with values from query parameters.
     *
     * @param config the ProfileItem object to populate
     * @param queryParam the query parameters to use for populating the ProfileItem
     * @param allowInsecure whether to allow insecure connections
     */
    fun getItemFormQuery(config: ProfileItem, queryParam: Map<String, String>, allowInsecure: Boolean) {
        config.network = queryParam["type"] ?: NetworkType.TCP.type
        config.headerType = queryParam["headerType"]
        config.host = queryParam["host"]
        config.path = queryParam["path"]

        config.seed = queryParam["seed"]
        config.kcpMtu = queryParam["mtu"]?.toIntOrNull()
        config.kcpTti = queryParam["tti"]?.toIntOrNull()
        config.quicSecurity = queryParam["quicSecurity"]
        config.quicKey = queryParam["key"]
        config.mode = queryParam["mode"]
        config.serviceName = queryParam["serviceName"]
        config.authority = queryParam["authority"]
        config.xhttpMode = queryParam["mode"]
        config.xhttpExtra = queryParam["extra"]
        config.finalMask = queryParam["fm"]

        config.security = queryParam["security"]
        if (config.security != AppConfig.TLS && config.security != AppConfig.REALITY) {
            config.security = null
        }
        // Support multiple possible query keys for allowInsecure like the C# implementation
        val allowInsecureKeys = arrayOf("insecure", "allowInsecure", "allow_insecure")
        config.insecure = when {
            allowInsecureKeys.any { queryParam[it] == "1" } -> true
            allowInsecureKeys.any { queryParam[it] == "0" } -> false
            else -> allowInsecure
        }
        config.sni = queryParam["sni"]
        config.fingerPrint = queryParam["fp"]
        config.alpn = queryParam["alpn"]
        config.echConfigList = queryParam["ech"]
        config.pinnedCA256 = queryParam["pcs"]
        config.publicKey = queryParam["pbk"]
        config.shortId = queryParam["sid"]
        config.spiderX = queryParam["spx"]
        config.mldsa65Verify = queryParam["pqv"]
        config.flow = queryParam["flow"]

        // L3 virtual-network hints from the 3x-ui fork. Only meaningful on
        // VLESS profiles backed by the xray-core fork's l3client outbound.
        config.vnet = when (queryParam["vnet"]) {
            "1" -> true
            "0" -> false
            else -> null
        }
        // Validate at parse time so VpnService.Builder.addAddress /
        // addRoute downstream never sees garbage from a malicious or
        // typo'd link. A bad value silently becomes null, which makes
        // the runtime fall back to the legacy address pool.
        // The xray-core fork's l3client subnet is IPv4-only; the panel
        // never hands out IPv6 vnetIps. Restrict both to IPv4 so a
        // family-mismatch can't slip through parse-time and blow up
        // VpnService.Builder.addRoute later (Builder rejects an IPv4
        // route on an IPv6 address pair and vice versa).
        config.vnetSubnet = queryParam["vnetSubnet"]?.ifBlank { null }
            ?.takeIf { isValidIpv4Cidr(it) }
        config.vnetIp = queryParam["vnetIp"]?.ifBlank { null }
            ?.takeIf { isIpv4Literal(it) }
        config.vnetDefaultRoute = when (queryParam["vnetDefaultRoute"]) {
            "1" -> true
            "0" -> false
            else -> null
        }
    }

    /**
     * Returns true when the input is an IPv4 CIDR like "10.0.0.0/24".
     *
     * Used to gate vnetSubnet so we never feed a malformed value to
     * VpnService.Builder.addRoute(). IPv4-only by design: the panel
     * subnet for the xray-core l3client is always IPv4, and Builder
     * rejects family-mismatched address/route pairs at runtime.
     */
    private fun isValidIpv4Cidr(value: String): Boolean {
        val parts = value.split('/')
        if (parts.size != 2) return false
        val prefix = parts[1].toIntOrNull() ?: return false
        if (prefix !in 0..32) return false
        return isIpv4Literal(parts[0])
    }

    /**
     * Returns true when the input is a dotted-quad IPv4 literal.
     *
     * Utils.isPureIpAddress accepts both IPv4 and IPv6, which lets a
     * crafted vnetIp like "::1" slip past parse-time validation. We
     * want IPv4-only here.
     */
    private fun isIpv4Literal(value: String): Boolean {
        val parts = value.split('.')
        if (parts.size != 4) return false
        return parts.all {
            val n = it.toIntOrNull() ?: return false
            n in 0..255 && it == n.toString()
        }
    }

    /**
     * Creates a map of query parameters from a ProfileItem object.
     *
     * @param config the ProfileItem object to create query parameters from
     * @return a map of query parameters
     */
    fun getQueryDic(config: ProfileItem): HashMap<String, String> {
        val dicQuery = HashMap<String, String>()
        dicQuery["security"] = config.security?.ifEmpty { "none" }.orEmpty()
        config.sni?.nullIfBlank()?.let { dicQuery["sni"] = it }
        config.alpn?.nullIfBlank()?.let { dicQuery["alpn"] = it }
        config.echConfigList?.nullIfBlank()?.let { dicQuery["ech"] = it }
        config.pinnedCA256?.nullIfBlank()?.let { dicQuery["pcs"] = it }
        config.fingerPrint?.nullIfBlank()?.let { dicQuery["fp"] = it }
        config.publicKey?.nullIfBlank()?.let { dicQuery["pbk"] = it }
        config.shortId?.nullIfBlank()?.let { dicQuery["sid"] = it }
        config.spiderX?.nullIfBlank()?.let { dicQuery["spx"] = it }
        config.mldsa65Verify?.nullIfBlank()?.let { dicQuery["pqv"] = it }
        config.flow?.nullIfBlank()?.let { dicQuery["flow"] = it }
        config.finalMask?.nullIfBlank()?.let { dicQuery["fm"] = it }
        // Round-trip the L3 virtualnet hints. Only emitted when vnet is on
        // so legacy clients see clean VLESS share-links.
        if (config.vnet == true) {
            dicQuery["vnet"] = "1"
            config.vnetSubnet?.nullIfBlank()?.let { dicQuery["vnetSubnet"] = it }
            config.vnetIp?.nullIfBlank()?.let { dicQuery["vnetIp"] = it }
            if (config.vnetDefaultRoute == true) {
                dicQuery["vnetDefaultRoute"] = "1"
            } else if (config.vnetDefaultRoute == false) {
                dicQuery["vnetDefaultRoute"] = "0"
            }
        }
        config.kcpMtu?.let { dicQuery["mtu"] = it.toString() }
        config.kcpTti?.let { dicQuery["tti"] = it.toString() }
        // Add two keys for compatibility: "insecure" and "allowInsecure"
        if (config.security == AppConfig.TLS) {
            val insecureFlag = if (config.insecure == true) "1" else "0"
            dicQuery["insecure"] = insecureFlag
            dicQuery["allowInsecure"] = insecureFlag
        }

        val networkType = NetworkType.fromString(config.network)
        dicQuery["type"] = networkType.type

        when (networkType) {
            NetworkType.TCP -> {
                dicQuery["headerType"] = config.headerType?.ifEmpty { "none" }.orEmpty()
                config.host?.nullIfBlank()?.let { dicQuery["host"] = it }
            }

            NetworkType.KCP -> {
                dicQuery["headerType"] = config.headerType?.ifEmpty { "none" }.orEmpty()
                config.seed?.nullIfBlank()?.let { dicQuery["seed"] = it }
            }

            NetworkType.WS, NetworkType.HTTP_UPGRADE -> {
                config.host?.nullIfBlank()?.let { dicQuery["host"] = it }
                config.path?.nullIfBlank()?.let { dicQuery["path"] = it }
            }

            NetworkType.XHTTP -> {
                config.host?.nullIfBlank()?.let { dicQuery["host"] = it }
                config.path?.nullIfBlank()?.let { dicQuery["path"] = it }
                config.xhttpMode?.nullIfBlank()?.let { dicQuery["mode"] = it }
                config.xhttpExtra?.nullIfBlank()?.let { dicQuery["extra"] = it }
            }

            NetworkType.HTTP, NetworkType.H2 -> {
                dicQuery["type"] = "http"
                config.host?.nullIfBlank()?.let { dicQuery["host"] = it }
                config.path?.nullIfBlank()?.let { dicQuery["path"] = it }
            }

//            NetworkType.QUIC -> {
//                dicQuery["headerType"] = config.headerType?.ifEmpty { "none" }.orEmpty()
//                config.quicSecurity?.nullIfBlank()?.let { dicQuery["quicSecurity"] = it }
//                config.quicKey?.nullIfBlank()?.let { dicQuery["key"] = it }
//            }

            NetworkType.GRPC -> {
                config.mode?.nullIfBlank()?.let { dicQuery["mode"] = it }
                config.authority?.nullIfBlank()?.let { dicQuery["authority"] = it }
                config.serviceName?.nullIfBlank()?.let { dicQuery["serviceName"] = it }
            }

            else -> {}
        }

        return dicQuery
    }

    fun getServerAddress(profileItem: ProfileItem): String {
        if (Utils.isPureIpAddress(profileItem.server.orEmpty())) {
            return profileItem.server.orEmpty()
        }

        val domain = HttpUtil.toIdnDomain(profileItem.server.orEmpty())
        if (MmkvManager.decodeSettingsString(AppConfig.PREF_OUTBOUND_DOMAIN_RESOLVE_METHOD, "1") != "2") {
            return domain
        }
        //Resolve and replace domain
        val resolvedIps = HttpUtil.resolveHostToIP(domain, MmkvManager.decodeSettingsBool(AppConfig.PREF_PREFER_IPV6))
        if (resolvedIps.isNullOrEmpty()) {
            return domain
        }
        return resolvedIps.first()
    }
}
