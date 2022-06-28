package com.bonita.wirelesslan.controller

import android.net.wifi.WifiManager
import android.os.Handler
import android.os.Message

/**
 * Wifi Scan Handler
 *
 * @author bonita
 * @date 2022-03-07
 */
class Scanner(private val wifiManager: WifiManager) : Handler() {

    companion object {
        const val WIFI_RESCAN_INTERVAL_MS = 10 * 2000
    }

    private var retryCount = 0
    private var isPause = false

    override fun handleMessage(msg: Message?) {
        if (isPause == true) {
            return
        }

        if (wifiManager.startScan() == true) {
            retryCount = 0
        }

        if (++retryCount >= 3) {
            retryCount = 0
        }

        sendEmptyMessageDelayed(0, WIFI_RESCAN_INTERVAL_MS.toLong())
    }

    fun resume() {
        isPause = false

        if (hasMessages(0) == false) {
            sendEmptyMessage(0)
        }
    }

    fun pause() {
        retryCount = 0
        isPause = true

        removeMessages(0)
    }
}