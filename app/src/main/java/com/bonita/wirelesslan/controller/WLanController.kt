package com.bonita.wirelesslan.controller

import android.content.Context
import android.net.NetworkInfo
import android.net.wifi.ScanResult
import android.net.wifi.WifiConfiguration
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import android.util.ArrayMap
import androidx.fragment.app.FragmentActivity
import com.bonita.wirelesslan.reflection.ReflectionDefine
import com.bonita.wirelesslan.reflection.ReflectionUtils
import com.bonita.wirelesslan.data.AccessPoint
import com.bonita.wirelesslan.dialog.NetworkInfoDialog
import com.bonita.wirelesslan.dialog.NetworkManagerDialog
import com.bonita.wirelesslan.dialog.PasswordDialog
import com.bonita.wirelesslan.dialog.SavedNetworksDialog

/**
 * WLanFragment 에서 사용하는 Controller
 *
 * @author bonita
 * @date 2022-03-07
 */
class WLanController {

    val accessPoints = arrayListOf<AccessPoint>()

    // Wifi 에 연결 중인지 확인
    var isConnecting = false

    // 기능 수행 중인지 확인
    var isProcessing = false

    lateinit var wifiManager: WifiManager
    private lateinit var scanner: Scanner

    private var mConnectedAccessPoint: AccessPoint? = null

    /**
     * 초기 작업
     * - Wifi on
     * - scan
     */
    fun initialize(a_context: Context) {
        wifiManager = a_context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        wifiManager.run {
            scanner = Scanner(this)

            if (isWifiEnabled == true) {
                scanner.resume()
            } else {
                setWifiEnabled(true)
            }
        }
    }

    /**
     * AccessPoint List 구성
     * - WifiTracker updateAccessPoints 함수 참조
     */
    fun makeAccessPoints(
        a_context: Context,
        a_lastWifiInfo: WifiInfo?,
        a_lastNetworkInfo: NetworkInfo?
    ): ArrayList<AccessPoint> {
        return ArrayList<AccessPoint>().apply {
            val scanResults = wifiManager.scanResults
            if (scanResults.isEmpty()) {
                return this
            }

            // (key, WifiConfiguration) Configured Network map 구성
            val configs = wifiManager.configuredNetworks
            val configuredMap = ArrayMap<String, WifiConfiguration>()
            configs?.let {
                for (config in it) {
                    configuredMap[AccessPoint.getKey(config)] = config
                }
            }

            val connectionConfig = if (a_lastWifiInfo != null) {
                getWifiConfigurationForNetworkId(a_lastWifiInfo.networkId, configs)
            } else {
                null
            }

            // (key, scanResult) map array 구성
            val scanResultsByApKey = ArrayMap<String, ArrayList<ScanResult>>().apply {
                for (result in scanResults) {
                    // Ignore hidden and ad-hoc networks.
                    if (result.SSID == null ||
                        result.SSID.isEmpty() ||
                        result.capabilities.contains("[IBSS]") == true
                    ) {
                        continue
                    }

                    val apKey = AccessPoint.getKey(result)
                    val resultList: ArrayList<ScanResult>
                    if (contains(apKey) == true) {
                        resultList = get(apKey)!!
                    } else {
                        resultList = ArrayList()
                        put(apKey, resultList)
                    }

                    resultList.add(result)
                }
            }

            // 연결되어 있는 wifi 의 ssid
            val connectInfo = wifiManager.connectionInfo
            val connectInfoId = AccessPoint.removeDoubleQuotes(connectInfo.ssid)

            // Add AccessPoint
            for ((key, value) in scanResultsByApKey) {
                val accessPoint = AccessPoint(a_context, value, wifiManager)

                // Update the matching config if there is one, to populate saved network info
                accessPoint.update(configuredMap[key])
                if (connectionConfig != null && a_lastWifiInfo != null && a_lastNetworkInfo != null) {
                    accessPoint.update(connectionConfig, a_lastWifiInfo, a_lastNetworkInfo)
                }

                // Ignore access points that are out of range.
                if (accessPoint.getLevel() != -1) {
                    // 연결 여부 확인
                    if (accessPoint.ssid == connectInfoId && connectInfo.ipAddress != 0) {
                        accessPoint.isConnected = true
                    }
                    add(accessPoint)
                }
            }

            // Pre-sort accessPoints to speed preference insertion
            sort()
        }
    }

    /**
     * 저장된 AccessPoint List 구성
     */
    fun makeSavedAccessPoints(a_context: Context): List<AccessPoint> {
        // Settingslib.wifi.WifiSavedConfigUtils.getAllConfigs(a_context, a_wifiManager) 참조
        val savedConfigs: MutableList<AccessPoint> = java.util.ArrayList()
        val savedNetworks = wifiManager.configuredNetworks
        for (network in savedNetworks) {
            // Configuration for Passpoint network is configured temporary by WifiService for
            // connection attempt only.  The underlying configuration is saved as Passpoint
            // configuration, which will be retrieved with WifiManager#getPasspointConfiguration
            // call below.
            if (network.isPasspoint == true) {
                continue
            }

            savedConfigs.add(AccessPoint(a_context, network, wifiManager))
        }

        // 저장된 네트워크가 다 나오지 않는다면 아래 주석 풀고 구현 필요
//        try {
//            final Class wifiManager = a_wifiManager.getClass();
//            final Method method = wifiManager.getMethod("getPasspointConfigurations", null);
//            final List<Object> savedPasspointConfigs = (List<Object>) method.invoke(a_wifiManager);
//
//            for (Object config : savedPasspointConfigs) {
//               // savedConfigs.add(new Access632Point(a_context, config));
//            }
//        } catch (Exception e) {
//            // Passpoint not supported.
//        }
        return savedConfigs
    }

    /**
     * 현재 선택된 AccessPoint 에 연결
     */
    fun connectAp(a_activity: FragmentActivity, a_selectedPosition: Int, a_callback: (a_isSuccess: Boolean) -> Unit) {
        // 와이파이 꺼져있을 때 return
        if (getWifiState() == WifiManager.WIFI_STATE_DISABLED) return

        pauseScan()

        // 현재 선택한 AccessPoint
        val ap = accessPoints[a_selectedPosition]

        // 이미 연결된 AccessPoint 일 경우 처리
        wifiManager.connectionInfo
            ?.takeIf { it.ipAddress != 0 }
            ?.takeIf { AccessPoint.removeDoubleQuotes(it.bssid) == AccessPoint.removeDoubleQuotes(ap.bssid) }
            ?.apply {
                a_callback(false)
                resumeScan()
                return
            }

        // 현재 선택한 AccessPoint 기억 (?)
        mConnectedAccessPoint = ap

        val networkId = getNetworkId(ap)
        val ssid = AccessPoint.removeDoubleQuotes(ap.ssid)

        if (networkId == -1) {
            // 저장되지 않은 wifi 에 연결
            when (ap.security) {
                AccessPoint.SECURITY_NONE -> {
                    isConnecting = true
                    /* Bypass dialog for unsecured networks */
                    ap.generateOpenNetworkConfig()
                    connectNone(a_activity, ap.configuration, ssid, a_callback)
                }

                AccessPoint.SECURITY_EAP,
                AccessPoint.SECURITY_EAP_SUITE_B ->
                    callEapNetworkDialog(a_activity, ap.ssid)

                else -> callPasswordDialog(a_activity, a_callback)
            }
        } else {
            // 저장된 wifi 에 연결
            isConnecting = true
            connectConfiguredNetwork(a_activity, networkId, ssid, a_callback)
        }
    }

    /**
     * 저장된 wifi 에 연결
     * - NetworkID 필요
     */
    fun connectConfiguredNetwork(a_context: Context, a_networkId: Int, a_ssid: String, a_callback: (a_isSuccess: Boolean) -> Unit) {
        // bonita. H632B 에서 wifi 연결 되지않아 추가
        wifiManager.enableNetwork(a_networkId, true)

        // connect 시도 -> hidden api 사용
        val actionListener = ReflectionUtils.getInnerClass(
            ReflectionDefine.WifiManagerRef.CLASS_NAME,
            ReflectionDefine.WifiManagerRef.CLASS_ACTION_LISTENER
        )
        val method = wifiManager.javaClass.getMethod("connect", Int::class.java, actionListener)
        method.invoke(wifiManager, a_networkId, null)

        checkConnection(a_context, a_ssid, a_callback)
    }

    /**
     * 프로파일 정보보기
     */
    fun callNetworkInfoDialog(a_activity: FragmentActivity, a_position: Int) {
        setProcessingMode(true)

        val accessPoint = accessPoints[a_position]
        NetworkInfoDialog.newInstance(accessPoint) {
            setProcessingMode(false)
        }.show(a_activity.supportFragmentManager, NetworkInfoDialog.TAG)
    }

    /**
     * 저장된 네트워크 대화상자 열기
     */
    fun callSavedNetworksDialog(a_activity: FragmentActivity) {
        setProcessingMode(true)

        SavedNetworksDialog.newInstance(this) {
            setProcessingMode(false)
        }.show(a_activity.supportFragmentManager, SavedNetworksDialog.TAG)
    }

    /**
     * 비밀번호 입력 대화상자 열기
     */
    private fun callPasswordDialog(a_context: Context, a_callback: (a_isSuccess: Boolean) -> Unit) {
        setProcessingMode(true)

        PasswordDialog(a_context, wifiManager, mConnectedAccessPoint) {
            // connecting 중인지 setting
            isConnecting = it
        }.apply {
            setOnDismissListener {
                setProcessingMode(false)
                a_callback(true)
            }
        }.show()
    }

    /**
     * EAP 네트워크 연결 대화상자 열기
     */
    private fun callEapNetworkDialog(a_activity: FragmentActivity, a_ssid: String) {
        setProcessingMode(true)

        NetworkManagerDialog.newInstance(null, NetworkManagerDialog.Mode.CONNECT_EAP) {
            setProcessingMode(false)
        }.run {
            eapSSID = a_ssid
            show(a_activity.supportFragmentManager, NetworkManagerDialog.TAG)
        }
    }

    /**
     * 기능 수행 여부에 따라 mode setting
     */
    fun setProcessingMode(a_isProcessing: Boolean) {
        isProcessing = a_isProcessing
        if (isProcessing) pauseScan() else resumeScan()
    }

    /**
     * AccessPoint scan 재개
     */
    fun resumeScan() {
        scanner.resume()
    }

    /**
     * AccessPoint scan 중지
     */
    fun pauseScan() {
        scanner.pause()
    }

    /**
     * 연결된 WifiInfo 반환
     */
    fun getConnectionInfo(a_wifiInfo: WifiInfo?): WifiInfo? =
        if (wifiManager.isWifiEnabled == true) {
            wifiManager.connectionInfo
        } else {
            a_wifiInfo
        }

    /**
     * Wifi 연결 상태 반환
     */
    fun getWifiState(): Int = wifiManager.wifiState

    private fun getWifiConfigurationForNetworkId(a_networkId: Int, a_configs: List<WifiConfiguration>?): WifiConfiguration? {
        a_configs?.let {
            for (config in it) {
                config.run {
                    if (networkId == a_networkId) {
                        val selfAdded = ReflectionUtils.getPublicFieldValue(this, ReflectionDefine.WifiConfigRef.FIELD_SELF_ADDED) as Boolean
                        val numAssociation = ReflectionUtils.getPublicFieldValue(this, ReflectionDefine.WifiConfigRef.FIELD_NUM_ASSOCIATION) as Int
                        if (selfAdded == false || numAssociation != 0) {
                            return this
                        }
                    }
                }
            }
        }

        return null
    }

    /**
     * AccessPoint 의 Network ID 반환
     */
    private fun getNetworkId(a_ap: AccessPoint): Int {
        wifiManager.configuredNetworks?.let {
            for (config in it) {
                if (AccessPoint.removeDoubleQuotes(config.SSID) == a_ap.ssid) {
                    return config.networkId
                }
            }
        }

        return -1
    }

    /**
     * Security 가 none 인 wifi 에 연결
     * - WifiConfiguration 필요
     */
    private fun connectNone(a_context: Context, a_config: WifiConfiguration?, a_ssid: String, a_callback: (a_isSuccess: Boolean) -> Unit) {
        // [21.03.15][bonita]security 가 none 인 와이파이 연결은 system app 에서 해야함
    }

    /**
     * Connect 여부 확인하는 thread 수행
     * - Connection 되지 않을 때, 일정 시간 기다린 후 fail 처리 해주기 위해 thread 사용
     */
    private fun checkConnection(a_context: Context, a_ssid: String, a_callback: (a_isSuccess: Boolean) -> Unit) {
        CheckConnection(wifiManager, a_ssid) { isConnected ->
            if (isConnected) {
                a_callback(true)
            } else {
                a_callback(false)
            }

            isConnecting = false
            resumeScan()
        }.start()
    }
}