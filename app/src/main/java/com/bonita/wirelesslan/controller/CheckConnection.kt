package com.bonita.wirelesslan.controller

import android.net.wifi.SupplicantState
import android.net.wifi.WifiManager
import com.bonita.wirelesslan.data.AccessPoint

/**
 * Wifi 연결 확인
 *
 * @author bonita
 * @date 2022-03-14
 */
class CheckConnection(
    private val wifiManager: WifiManager,
    private val ssid: String,
    private val callback: (a_isConnected: Boolean) -> Unit
) : Thread() {

    override fun run() {
        for (i in 0..10) {
            try {
                sleep(1000)
            } catch (e: InterruptedException) {
                currentThread().interrupt()
            }

            if (ssid == "0x" || i == 10) break

            wifiManager.connectionInfo?.let {
                if (ssid == AccessPoint.removeDoubleQuotes(it.ssid)) {
                    if (it.ipAddress != 0 && it.supplicantState == SupplicantState.COMPLETED) {
                        // 연결 성공 알림
                        callback(true)
                        return@run
                    }
                }
            }
        }

        // 연결 실패 알림
        callback(false)
    }
}