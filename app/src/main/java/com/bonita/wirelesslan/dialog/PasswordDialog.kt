package com.bonita.wirelesslan.dialog

import android.app.Dialog
import android.content.Context
import android.net.wifi.WifiConfiguration
import android.net.wifi.WifiManager
import android.view.KeyEvent
import android.widget.EditText
import com.bonita.wirelesslan.R
import com.bonita.wirelesslan.controller.CheckConnection
import com.bonita.wirelesslan.data.AccessPoint
import com.bonita.wirelesslan.reflection.ReflectionDefine
import com.bonita.wirelesslan.reflection.ReflectionUtils

/**
 * 비밀번호 입력 대화상자
 *
 * @author bonita
 * @date 2022-03-15
 */
class PasswordDialog(
    a_context: Context,
    private val wifiManager: WifiManager,
    private val connectedAp: AccessPoint?,
    private val connectCallback: (a_isConnecting: Boolean) -> Unit
) : Dialog(a_context) {

    // 비밀번호 입력창
    private lateinit var etPassword: EditText

    // 연결 중인지 기억하여 키입력 방지
    private var isConnecting = false

    override fun show() {
        setContentView(R.layout.dialog_password)
        setTitle(context.getString(R.string.wlan_label_network_key).replace(":", ""))

        etPassword = findViewById<EditText>(R.id.et_password).apply {
            setOnKeyListener { _, keyCode, event ->
                if (keyCode == KeyEvent.KEYCODE_ESCAPE && event.action == KeyEvent.ACTION_UP) {
                    dismiss()
                    true
                } else if (keyCode == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_UP) {
                    if (isConnecting == false) {
                        connect()
                    }
                    true
                } else {
                    false
                }
            }
        }

        super.show()
    }

    /**
     * AccessPoint 에 연결
     */
    private fun connect() {
        val password = etPassword.text.toString().trim()

        // 비밀번호 입력하지 않았을 때 처리
        password.takeIf { it.isEmpty() }?.apply { return }

        // 입력한 비밀번호로 연결 시도
        isConnecting = true
        connectCallback(true)

        val ssid = connectedAp?.ssid ?: ""

        val connectedConfig = getApConfig(password)
        connectedConfig?.run {
            val networkId = wifiManager.addNetwork(this)
            wifiManager.enableNetwork(networkId, true)
            wifiManager.saveConfiguration()

            // connect 시도 -> hidden api 사용
            val actionListener = ReflectionUtils.getInnerClass(
                ReflectionDefine.WifiManagerRef.CLASS_NAME,
                ReflectionDefine.WifiManagerRef.CLASS_ACTION_LISTENER
            )
            val method = wifiManager.javaClass.getMethod("connect", WifiConfiguration::class.java, actionListener)
            method.invoke(wifiManager, this, null)

            // connect 여부 확인
            CheckConnection(wifiManager, connectedAp!!.ssid) {
                isConnecting = false
                connectCallback(false)
                dismiss()
            }.start()
        }
    }

    private fun getApConfig(a_password: String): WifiConfiguration? {
        connectedAp?.takeIf { it.networkId != -1 }?.apply { return null }

        return WifiConfiguration().apply {
            SSID = connectedAp?.ssid ?: ""
            SSID = AccessPoint.convertToQuotedString(SSID)

            val security = connectedAp?.security ?: AccessPoint.SECURITY_NONE
            when (security) {
                AccessPoint.SECURITY_NONE -> allowedKeyManagement.set(WifiConfiguration.KeyMgmt.NONE)

                AccessPoint.SECURITY_WEP -> {
                    allowedKeyManagement.set(WifiConfiguration.KeyMgmt.NONE)
                    allowedAuthAlgorithms.set(WifiConfiguration.AuthAlgorithm.OPEN)
                    allowedAuthAlgorithms.set(WifiConfiguration.AuthAlgorithm.SHARED)

                    a_password
                        .takeIf { it.isNotEmpty() }
                        ?.let {
                            val length = it.length
                            // WEP-40, WEP-104, and 256-bit WEP (WEP-232?)
                            wepKeys[0] = if ((length == 10 || length == 26 || length == 58) &&
                                it.matches("[0-9A-Fa-f]*".toRegex())
                            ) {
                                it
                            } else {
                                "\"$it\""
                            }
                        }
                }

                AccessPoint.SECURITY_PSK,
                AccessPoint.SECURITY_PSK_SAE_TRANSITION -> {
                    // [21.03.08][JANGKH][BRAILLESB-512] PSK-SAE 도 psk 방식으로 처리되도록 수정.
                    allowedKeyManagement.set(WifiConfiguration.KeyMgmt.WPA_PSK)
                    a_password
                        .takeIf { it.isNotEmpty() }
                        ?.let {
                            preSharedKey = if (it.matches("[0-9A-Fa-f]{64}".toRegex())) {
                                it
                            } else {
                                "\"$it\""
                            }
                        }

                }

                AccessPoint.SECURITY_EAP -> {
                }

                else -> return null
            }
        }
    }
}