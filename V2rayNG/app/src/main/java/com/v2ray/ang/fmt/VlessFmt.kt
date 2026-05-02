package com.v2ray.ang.fmt

import com.v2ray.ang.AppConfig
import com.v2ray.ang.dto.ProfileItem
import com.v2ray.ang.dto.V2rayConfig.OutboundBean
import com.v2ray.ang.dto.V2rayConfig.OutboundBean.OutSettingsBean.VirtualNetworkBean
import com.v2ray.ang.enums.EConfigType
import com.v2ray.ang.extension.idnHost
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.handler.V2rayConfigManager
import com.v2ray.ang.util.Utils
import java.net.URI

object VlessFmt : FmtBase() {

    /**
     * Parses a Vless URI string into a ProfileItem object.
     *
     * @param str the Vless URI string to parse
     * @return the parsed ProfileItem object, or null if parsing fails
     */
    fun parse(str: String): ProfileItem? {
        var allowInsecure = MmkvManager.decodeSettingsBool(AppConfig.PREF_ALLOW_INSECURE, false)
        val config = ProfileItem.create(EConfigType.VLESS)

        val uri = URI(Utils.fixIllegalUrl(str))
        if (uri.rawQuery.isNullOrEmpty()) return null
        val queryParam = getQueryParam(uri)

        config.remarks = Utils.decodeURIComponent(uri.fragment.orEmpty()).let { it.ifEmpty { "none" } }
        config.server = uri.idnHost
        config.serverPort = uri.port.toString()
        config.password = uri.userInfo
        config.method = queryParam["encryption"] ?: "none"

        getItemFormQuery(config, queryParam, allowInsecure)

        return config
    }

    /**
     * Converts a ProfileItem object to a URI string.
     *
     * @param config the ProfileItem object to convert
     * @return the converted URI string
     */
    fun toUri(config: ProfileItem): String {
        val dicQuery = getQueryDic(config)
        dicQuery["encryption"] = config.method ?: "none"

        return toUri(config, config.password, dicQuery)
    }

    /**
     * Converts a ProfileItem object to an OutboundBean object.
     *
     * @param profileItem the ProfileItem object to convert
     * @return the converted OutboundBean object, or null if conversion fails
     */
    fun toOutbound(profileItem: ProfileItem): OutboundBean? {
        val outboundBean = V2rayConfigManager.createInitOutbound(EConfigType.VLESS)

        outboundBean?.settings?.vnext?.first()?.let { vnext ->
            vnext.address = getServerAddress(profileItem)
            vnext.port = profileItem.serverPort.orEmpty().toInt()
            vnext.users[0].id = profileItem.password.orEmpty()
            vnext.users[0].encryption = profileItem.method
            vnext.users[0].flow = profileItem.flow
        }

        // L3 virtualnet: mirror the inbound's panel-side virtualNetwork
        // block onto the outbound so xray-core's l3client kicks in with
        // the same subnet / pre-allocated IP. Gated on BOTH vnet=true
        // AND a non-blank vnetIp — same gate as
        // SettingsManager.getCurrentVnetProfile so all three sites
        // (parse / outbound / VpnService.Builder) agree. Without the
        // vnetIp guard a legacy "tun" inbound would still be added
        // (needTun=true because getCurrentVnetProfile returns null) and
        // race with l3client over the TUN file descriptor.
        if (profileItem.vnet == true && !profileItem.vnetIp.isNullOrBlank()) {
            outboundBean?.settings?.virtualNetwork = VirtualNetworkBean(
                enabled = true,
                subnet = profileItem.vnetSubnet?.ifBlank { null } ?: "10.0.0.0/24",
                vnetIp = profileItem.vnetIp,
                // defaultRoute is what tells l3client to pull all
                // traffic through the tunnel; on the panel side this
                // is forced to true, mirror that as the safe default.
                defaultRoute = profileItem.vnetDefaultRoute ?: true,
            )
        }

        val sni = outboundBean?.streamSettings?.let {
            V2rayConfigManager.populateTransportSettings(it, profileItem)
        }

        outboundBean?.streamSettings?.let {
            V2rayConfigManager.populateTlsSettings(it, profileItem, sni)
        }

        return outboundBean
    }
}