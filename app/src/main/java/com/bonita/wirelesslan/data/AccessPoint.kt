package com.bonita.wirelesslan.data

import android.content.Context
import android.net.NetworkInfo
import android.net.wifi.*
import android.preference.Preference
import android.text.TextUtils
import com.bonita.wirelesslan.R
import com.bonita.wirelesslan.reflection.ReflectionDefine
import com.bonita.wirelesslan.reflection.ReflectionUtils
import com.selvashc.blazewlan.data.CommonDefine

/**
 * AccessPoint
 * - com.android 의 Settingslib.wifi.AccessPoint 클래스를 참조함.
 *
 * @author bonita
 * @date 2022.03.07
 */
class AccessPoint(a_context: Context) : Preference(a_context) {

    companion object {
        const val SIGNAL_LEVELS = 5

        /**
         * Lower bound on the 2.4 GHz (802.11b/g/n) WLAN channels
         */
        const val LOWER_FREQ_24GHZ = 2400

        /**
         * Upper bound on the 2.4 GHz (802.11b/g/n) WLAN channels
         */
        const val HIGHER_FREQ_24GHZ = 2500

        /**
         * Lower bound on the 5.0 GHz (802.11a/h/j/n/ac) WLAN channels
         */
        const val LOWER_FREQ_5GHZ = 4900

        /**
         * Upper bound on the 5.0 GHz (802.11a/h/j/n/ac) WLAN channels
         */
        const val HIGHER_FREQ_5GHZ = 5900

        private const val KEY_PREFIX_AP = "AP:"
        private const val KEY_PREFIX_FQDN = "FQDN:"

        /*
         * NOTE: These constants for security and PSK types are saved to the bundle in saveWifiState,
         * and sent across IPC. The numeric values should remain stable, otherwise the changes will need
         * to be synced with other unbundled users of this library.
         */
        const val SECURITY_NONE = 0
        const val SECURITY_WEP = 1 // WEP
        const val SECURITY_PSK = 2 // WPA/WPA2-Personal
        const val SECURITY_EAP = 3 // WPA/WPA2/WPA3-Enterprise
        const val SECURITY_OWE = 4 // Enhanced open
        const val SECURITY_SAE = 5 // WPA3-Personal
        const val SECURITY_EAP_SUITE_B = 6 // WPA3-Enterprise
        const val SECURITY_PSK_SAE_TRANSITION = 7
        const val SECURITY_OWE_TRANSITION = 8
        const val SECURITY_MAX_VAL = 9 // Has to be the last

        private const val PSK_UNKNOWN = 0
        private const val PSK_WPA = 1
        private const val PSK_WPA2 = 2
        private const val PSK_WPA_WPA2 = 3
        private const val PSK_SAE = 4

        private const val EAP_UNKNOWN = 0
        private const val EAP_WPA = 1 // WPA-EAP
        private const val EAP_WPA2_WPA3 = 2 // RSN-EAP

        private const val UNREACHABLE_RSSI = Int.MIN_VALUE

        /**
         * 무선랜 감도 (Signal Strength) 반환
         */
        fun getSensitivityStr(a_context: Context, a_rssi: Int): String? {
            val level = getLevel(a_rssi)
            return when (level) {
                0 -> a_context.getString(R.string.wlan_msg_signal_very_low)
                1 -> a_context.getString(R.string.wlan_msg_signal_low)
                2 -> a_context.getString(R.string.wlan_msg_signal_good)
                3 -> a_context.getString(R.string.wlan_msg_signal_very_good)
                4 -> a_context.getString(R.string.wlan_msg_signal_excellent)
                else -> ""
            }
        }

        fun getLevel(a_rssi: Int): Int = WifiManager.calculateSignalLevel(a_rssi, SIGNAL_LEVELS)

        /**
         * string 맨 처음/끝에 있는 " 제거
         */
        fun removeDoubleQuotes(string: String): String {
            val length = string.length
            return if (length > 1 && string[0] == '"' && string[length - 1] == '"') {
                string.substring(1, length - 1)
            } else string
        }

        /**
         * string 맨 처음/끝에 " 추가
         */
        fun convertToQuotedString(string: String): String = "\"" + string + "\""

        /**
         * Security 반환
         */
        fun getSecurity(a_config: WifiConfiguration): Int {
            // Reflection 을 통해 field 값 가져오기
            val keyMgmtClass = ReflectionUtils.getInnerClass(
                ReflectionDefine.WifiConfigRef.CLASS_NAME,
                ReflectionDefine.WifiConfigRef.CLASS_KEY_MGMT
            )

            keyMgmtClass?.run {
                if (isAllowConfigs(this, CommonDefine.AUTH_OWE, a_config) == true) {
                    return SECURITY_OWE
                }

                if (isAllowConfigs(this, ReflectionDefine.WifiConfigRef.FIELD_SAE, a_config) == true) {
                    return SECURITY_SAE
                }

                if (isAllowConfigs(this, ReflectionDefine.WifiConfigRef.FIELD_SUITE, a_config) == true) {
                    return SECURITY_EAP_SUITE_B
                }
            }

            a_config.allowedKeyManagement.run {
                if (get(WifiConfiguration.KeyMgmt.WPA_PSK) == true) {
                    return SECURITY_PSK
                }

                if (get(WifiConfiguration.KeyMgmt.WPA_EAP) == true || get(WifiConfiguration.KeyMgmt.IEEE8021X) == true) {
                    return SECURITY_EAP
                }
            }

            return if (a_config.wepKeys[0] != null) SECURITY_WEP else SECURITY_NONE
        }

        fun getSecurity(a_result: ScanResult): Int {
            return when {
                a_result.capabilities.contains(CommonDefine.AUTH_WEP) -> SECURITY_WEP
                a_result.capabilities.contains("PSK+SAE") -> SECURITY_PSK_SAE_TRANSITION
                a_result.capabilities.contains("SAE") -> SECURITY_SAE
                a_result.capabilities.contains("PSK") -> SECURITY_PSK
                a_result.capabilities.contains("EAP_SUITE_B_192") -> SECURITY_EAP_SUITE_B
                a_result.capabilities.contains("EAP") -> SECURITY_EAP
                a_result.capabilities.contains("OWE_TRANSITION") -> SECURITY_OWE_TRANSITION
                a_result.capabilities.contains(CommonDefine.AUTH_OWE) -> SECURITY_OWE
                else -> SECURITY_NONE
            }
        }

        /**
         * Returns the AccessPoint key for a WifiConfiguration.
         * This will return a special Passpoint key if the config is for Passpoint.
         */
        fun getKey(a_config: WifiConfiguration): String {
            return if (a_config.isPasspoint == true) {
                KEY_PREFIX_FQDN + a_config.FQDN
            } else {
                getKey(removeDoubleQuotes(a_config.SSID), a_config.BSSID, getSecurity(a_config))
            }
        }

        fun getKey(a_result: ScanResult): String = getKey(a_result.SSID, a_result.BSSID, getSecurity(a_result))

        /**
         * Returns the AccessPoint key for a normal non-Passpoint network by ssid/bssid and security.
         */
        private fun getKey(a_ssID: String?, a_bssID: String?, a_security: Int): String =
            StringBuilder(KEY_PREFIX_AP).apply {
                if (TextUtils.isEmpty(a_ssID) == true) {
                    append(a_bssID)
                } else {
                    append(a_ssID)
                }
                append(',')
                append(a_security)
            }.toString()

        /**
         * WifiConfiguration 이 해당 key 를 지원하는지 반환
         */
        private fun isAllowConfigs(a_class: Class<*>, a_field: String, a_config: WifiConfiguration): Boolean {
            return try {
                val fieldValue = ReflectionUtils.getStaticFieldValue(a_class, a_field) as Int
                a_config.allowedKeyManagement.get(fieldValue)
            } catch (e: Exception) {
                e.printStackTrace()
                false
            }
        }
    }

    var isConnected = false

    var ssid = ""
    var bssid = ""
    var security = 0
    var networkId = -1

    var configuration: WifiConfiguration? = null
    private var wifiInfo: WifiInfo? = null
    private var networkInfo: NetworkInfo? = null

    var rssi = UNREACHABLE_RSSI
    private var pskType = PSK_UNKNOWN
    private var eapType = EAP_UNKNOWN

    var isCarrierAp = false
    var carrierApEapType = WifiEnterpriseConfig.Eap.NONE

    private lateinit var wifiManager: WifiManager

    /**
     * The key which identifies this AccessPoint grouping.
     */
    private var mKey = ""

    /**
     * The underlying set of scan results comprising this AccessPoint.
     */
    private val mScanResults = ArrayList<ScanResult>()

    /**
     * Synchronization lock for managing concurrency between main and worker threads.
     *
     *
     * This lock should be held for all modifications to [.mScanResults] and
     * [.mExtraScanResults].
     */
    private val mLock = Any()

    constructor(a_context: Context, a_scanResults: ArrayList<ScanResult>, a_wifiManager: WifiManager) : this(a_context) {
        wifiManager = a_wifiManager

        setScanResults(a_scanResults)
        updateKey()
    }

    /**
     * Creates an AccessPoint with only a WifiConfiguration. This is used for the saved networks
     * page.
     */
    constructor(a_context: Context, a_config: WifiConfiguration, a_wifiManager: WifiManager) : this(a_context) {
        wifiManager = a_wifiManager

        loadConfig(a_config)
        updateKey()
    }

    override fun compareTo(other: Preference): Int {
        val otherAP = other as AccessPoint

        // Active one goes first.
        if (isActive() == true && otherAP.isActive() == false) {
            return -1
        }
        if (isActive() == false && otherAP.isActive() == true) {
            return 1
        }

        // Reachable one goes before unreachable one.
        if (isReachable() == true && otherAP.isReachable() == false) {
            return -1
        }
        if (isReachable() == false && otherAP.isReachable() == true) {
            return 1
        }

        // Configured one goes before unconfigured one.
        if (isSaved() == true && otherAP.isSaved() == false) {
            return -1
        }
        if (isSaved() == false && otherAP.isSaved() == true) {
            return 1
        }

        // Sort by signal strength, bucketed by level
        var difference = (WifiManager.calculateSignalLevel(otherAP.rssi, SIGNAL_LEVELS)
                - WifiManager.calculateSignalLevel(rssi, SIGNAL_LEVELS))
        if (difference != 0) {
            return difference
        }

        // Sort by title.
        difference = title.compareTo(otherAP.getTitle(), true)
        return if (difference != 0) {
            difference
        } else {
            // Do a case sensitive comparison to distinguish SSIDs that differ in case only
            ssid.compareTo(otherAP.ssid)
        }
    }

    /**
     * Returns the display title for the AccessPoint, such as for an AccessPointPreference's title.
     */
    override fun getTitle(): String = if (isPassPoint() == true) {
        configuration?.providerFriendlyName ?: ""
    } else {
        ssid
    }

    override fun equals(other: Any?): Boolean = if (other is AccessPoint) {
        compareTo(other) == 0
    } else {
        false
    }

    override fun hashCode(): Int {
        var result = 0
        wifiInfo?.let {
            result += 13 * it.hashCode()
        }
        result += 19 * rssi
        result += 23 * networkId
        result += 29 * ssid.hashCode()
        return result
    }

    /**
     * 화면에 보여줄 문자열 반환
     *
     * @return id, security, strength
     */
    fun getDisplayNetworkInfoStr(): String = StringBuilder().apply {
        // ssid
        append(ssid).append(", ")

        // security
        append(context.getString(R.string.wlan_msg_encryption)).append(": ")
        val sSecurity = getSecurityString(true)
        if (sSecurity.isEmpty() == true) {
            append(context.getString(R.string.wlan_item_security_none)).append(", ")
        } else {
            append(sSecurity).append(", ")
        }

        // sensitivity
        append(context.getString(R.string.wlan_msg_signal)).append(": ")
        append(getSensitivityStr(context, rssi))
    }.toString()

    fun update(a_config: WifiConfiguration?) {
        configuration = a_config
        configuration?.let {
            ssid = removeDoubleQuotes(it.SSID)
            networkId = it.networkId
        } ?: run {
            networkId = -1
        }
    }

    fun update(a_config: WifiConfiguration?, a_info: WifiInfo, a_networkInfo: NetworkInfo): Boolean {
        val updated: Boolean
        when {
            (isInfoForThisAccessPoint(a_info) == true) -> {
                if (isPassPoint() == false && configuration != a_config) {
                    // We do not set updated = true as we do not want to increase the amount of sorting
                    // and copying performed in WifiTracker at this time. If issues involving refresh
                    // are still seen, we will investigate further.
                    update(a_config) // Notifies the AccessPointListener of the change
                }

                updated = if (rssi != a_info.rssi && a_info.rssi != -127) {
                    rssi = a_info.rssi
                    true
                } else if (networkInfo != null && networkInfo!!.detailedState != a_networkInfo.detailedState) {
                    true
                } else {
                    (wifiInfo == null)
                }

                wifiInfo = a_info
                networkInfo = a_networkInfo
            }

            (wifiInfo != null) -> {
                updated = true
                wifiInfo = null
                networkInfo = null
            }

            else -> updated = false
        }

        return updated
    }

    /**
     * Return whether this is the active connection.
     * For ephemeral connections (networkId is invalid), this returns false if the network is
     * disconnected.
     */
    private fun isActive(): Boolean = networkInfo != null && (networkId != -1 || networkInfo!!.state != NetworkInfo.State.DISCONNECTED)

    fun generateOpenNetworkConfig() {
        check(security == SECURITY_NONE || security == SECURITY_OWE || security == SECURITY_OWE_TRANSITION)

        configuration ?: run {
            configuration = WifiConfiguration().apply {
                SSID = convertToQuotedString(ssid)
                if (security == SECURITY_NONE ||
                    ReflectionUtils.getMethodValue(
                        wifiManager,
                        ReflectionDefine.WifiManagerRef.METHOD_IS_CONNECT_SUPPORT
                    ) as Boolean == false
                ) {
                    allowedKeyManagement.set(WifiConfiguration.KeyMgmt.NONE)
                } else {
                    // Reflection 을 통해 OWE 로 setting
                    try {
                        val keyMgmtClass = ReflectionUtils.getInnerClass(
                            ReflectionDefine.WifiConfigRef.CLASS_NAME,
                            ReflectionDefine.WifiConfigRef.CLASS_KEY_MGMT
                        )
                        val value = ReflectionUtils.getStaticFieldValue(
                            keyMgmtClass,
                            CommonDefine.AUTH_OWE
                        ) as Int

                        allowedKeyManagement.set(value)
                        ReflectionUtils.setPublicFieldValue(this, ReflectionDefine.WifiConfigRef.FIELD_REQUIRE_PMF, true)

                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }
        }
    }

    /**
     * Security 반환
     */
    fun getSecurityString(a_isConcise: Boolean): String {
        return when (security) {
            SECURITY_WEP -> CommonDefine.AUTH_WEP // WEP

            SECURITY_PSK -> when (pskType) {
                PSK_WPA -> {
                    if (a_isConcise) CommonDefine.AUTH_WPA // WPA
                    else CommonDefine.AUTH_WPA_PERSONAL_PSK // WPA-Personal
                }
                PSK_WPA2 -> {
                    if (a_isConcise) CommonDefine.PSK_WPA2 // WPA2
                    else CommonDefine.PSK_WPA2_PERSONAL // WPA2-Personal
                }
                else -> {
                    if (a_isConcise) CommonDefine.PSK_WPA_WPA2  // WPA/WPA2
                    else CommonDefine.AUTH_WPA2_PERSONAL  // WPA/WPA2-Personal
                }
            }

            SECURITY_EAP -> getEAPString(a_isConcise)

            SECURITY_OWE -> {
                if (a_isConcise) CommonDefine.AUTH_OWE // OWE
                else CommonDefine.AUTH_ENHANCED_OPEN // Enhanced Open
            }

            SECURITY_SAE, SECURITY_PSK_SAE_TRANSITION -> {
                if (pskType == PSK_SAE) {
                    if (a_isConcise) CommonDefine.PSK_SAE_WPA // WPA2/WPA3
                    else CommonDefine.PSK_SAE_WPA_PERSONAL // WPA2/WPA3-Personal
                } else {
                    if (a_isConcise) CommonDefine.AUTH_SAE_WPA3  // WPA3
                    else CommonDefine.AUTH_WPA3_PERSONAL // WPA3-Personal
                }
            }

            SECURITY_EAP_SUITE_B -> {
                if (a_isConcise) CommonDefine.EAP_SUITE  // Suite-B-192
                else CommonDefine.AUTH_WPA_ENTERPRISE // WPA3-Enterprise 192-bit
            }

            SECURITY_OWE_TRANSITION -> {
                if (isSaved() && getSecurity(configuration!!) == SECURITY_OWE) {
                    if (a_isConcise) CommonDefine.AUTH_OWE else CommonDefine.AUTH_ENHANCED_OPEN
                } else {
                    if (a_isConcise) "" else context.getString(R.string.wlan_item_security_none)
                }
            }

            else -> if (a_isConcise) "" else context.getString(R.string.wlan_item_security_none)
        }
    }

    private fun getEAPString(a_isConcise: Boolean): String {
        return when (eapType) {
            EAP_WPA -> if (a_isConcise) CommonDefine.EAP_WPA else  // WPA-EAP
                CommonDefine.EAP_WPA_ENTERPRISE // WPA-Enterprise

            EAP_WPA2_WPA3 -> if (a_isConcise) CommonDefine.EAP_RSN else  // RSN-EAP
                CommonDefine.EAP_WPA3_ENTERPRISE // WPA2/WPA3-Enterprise

            EAP_UNKNOWN -> if (a_isConcise) CommonDefine.EAP_802 else  // 802.1x
                CommonDefine.AUTH_WPA_ENTERPRISE // WPA/WPA2/WPA3-Enterprise

            else -> if (a_isConcise) CommonDefine.EAP_802 else CommonDefine.AUTH_WPA_ENTERPRISE
        }
    }

    /**
     * Return whether the given [WifiInfo] is for this access point.
     * If the current AP does not have a network Id then the config is used to
     * match based on SSID and security.
     */
    private fun isInfoForThisAccessPoint(a_info: WifiInfo?): Boolean {
        val isPassPointAp = ReflectionUtils.getMethodValue(a_info, ReflectionDefine.WifiInfoRef.METHOD_IS_PASS_POINT) as Boolean

        return if (isPassPointAp == true || isPassPoint() == true) {
            isPassPointAp == true &&
                    isPassPoint() == true &&
                    TextUtils.equals(ReflectionUtils.getMethodValue(a_info, "getPasspointFqdn") as String, configuration!!.FQDN)
        } else if (networkId != -1) {
            networkId == a_info!!.networkId
        } else {
            // Might be an ephemeral connection with no WifiConfiguration. Try matching on SSID.
            // (Note that we only do this if the WifiConfiguration explicitly equals INVALID).
            // Handle hex string SSIDs.
            ssid == removeDoubleQuotes(a_info!!.ssid)
        }
    }

    /**
     * Return true if this AccessPoint represents a Passpoint AP.
     */
    private fun isPassPoint(): Boolean {
        return isSaved() && configuration!!.isPasspoint
    }

    private fun setScanResults(a_scanResults: List<ScanResult>?) {
        a_scanResults?.takeIf { it.isEmpty() == false } ?: run { return }

        mScanResults.clear()
        mScanResults.addAll(a_scanResults)

        if (isActive() == true) {
            return
        }

        // Set RSSI
        var bestResult: ScanResult? = null
        var bestRssi = UNREACHABLE_RSSI
        synchronized(mLock) {
            for (result in a_scanResults) {
                if (result.level > bestRssi) {
                    bestRssi = result.level
                    bestResult = result
                }
            }
        }

        // Set the rssi to the average of the current rssi and the previous rssi.
        rssi = if (bestRssi != UNREACHABLE_RSSI && rssi != UNREACHABLE_RSSI) {
            (rssi + bestRssi) / 2
        } else {
            bestRssi
        }

        bestResult?.run {
            ssid = SSID
            bssid = BSSID
            security = getSecurity(this)
            if (security == SECURITY_PSK || security == SECURITY_SAE || security == SECURITY_PSK_SAE_TRANSITION) {
                pskType = getPskType(this)
            } else if (security == SECURITY_EAP) {
                eapType = getEapType(this)
            }

            try {
                isCarrierAp = ReflectionUtils.getPublicFieldValue(
                    bestResult,
                    ReflectionDefine.ScanResultRef.FIELD_IS_CARRIER
                ) as Boolean
                carrierApEapType = ReflectionUtils.getPublicFieldValue(
                    bestResult,
                    ReflectionDefine.ScanResultRef.FIELD_CARRIER_TYPE
                ) as Int
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        // Update the config SSID of a Passpoint network to that of the best RSSI
        if (isPassPoint() == true) {
            configuration?.SSID = convertToQuotedString(ssid)
        }
    }

    private fun getPskType(a_result: ScanResult): Int {
        val wpa = a_result.capabilities.contains("WPA-PSK")
        val wpa2 = a_result.capabilities.contains("RSN-PSK")
        val wpa3TransitionMode = a_result.capabilities.contains("PSK+SAE")

        return if (wpa3TransitionMode == true) {
            PSK_SAE
        } else if (wpa2 == true && wpa == true) {
            PSK_WPA_WPA2
        } else if (wpa2 == true) {
            PSK_WPA2
        } else if (wpa == true) {
            PSK_WPA
        } else {
            PSK_UNKNOWN
        }
    }

    private fun getEapType(a_result: ScanResult): Int {
        return when {
            // WPA2-Enterprise and WPA3-Enterprise (non 192-bit) advertise RSN-EAP-CCMP
            a_result.capabilities.contains(CommonDefine.EAP_RSN) -> {
                EAP_WPA2_WPA3
            }

            // WPA-Enterprise advertises WPA-EAP-TKIP
            a_result.capabilities.contains(CommonDefine.EAP_WPA) -> {
                EAP_WPA
            }

            else -> EAP_UNKNOWN
        }
    }

    /**
     * Updates [.mKey] and should only called upon object creation/initialization.
     */
    private fun updateKey() {
        mKey = if (isPassPoint()) {
            getKey(configuration!!)
        } else {
            getKey(ssid, bssid, security)
        }
    }

    private fun loadConfig(a_config: WifiConfiguration) {
        ssid = if (a_config.SSID == null) "" else removeDoubleQuotes(a_config.SSID)
        bssid = if (a_config.BSSID == null) "" else removeDoubleQuotes(a_config.BSSID)
        security = getSecurity(a_config)
        networkId = a_config.networkId
        configuration = a_config
    }

    /**
     * Return true if the current RSSI is reachable, and false otherwise.
     */
    private fun isReachable(): Boolean = rssi != UNREACHABLE_RSSI

    fun isSaved(): Boolean = configuration != null

    fun getLevel(): Int = WifiManager.calculateSignalLevel(rssi, SIGNAL_LEVELS)
}