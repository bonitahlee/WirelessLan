package com.bonita.wirelesslan.dialog

import android.app.Dialog
import android.net.wifi.WifiConfiguration
import android.net.wifi.WifiEnterpriseConfig
import android.os.Bundle
import android.text.TextUtils
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Spinner
import androidx.fragment.app.DialogFragment
import com.bonita.wirelesslan.R
import com.bonita.wirelesslan.data.AccessPoint
import com.bonita.wirelesslan.databinding.DialogNetworkManagerBinding
import com.bonita.wirelesslan.reflection.ReflectionDefine.*
import com.bonita.wirelesslan.reflection.ReflectionUtils.*
import com.selvashc.blazewlan.data.CommonDefine
import java.util.*

/**
 * 네트워크 관리 대화상자
 *
 * - Password 입력창은 Auth 가 None, Enhanced Open, WAPI Certificate 아닐 때에만 visible,
 *                   Auth 가 Enterprise 이고, EAP Method 가 TLS, SIM, AKA, AKA' 아닐 때에만 visible
 *
 * @author bonita
 * @date 2022-05-13
 */
class NetworkManagerDialog(
    private val accessPoint: AccessPoint?,
    private val mode: Mode,
    private val dismissCallback: () -> Unit
) : DialogFragment() {

    companion object {
        const val TAG = "NetworkManagerDialog"

        private const val SYSTEM_CA_STORE_PATH = "/system/etc/security/cacerts"

        /* These values come from "wifi_peap_phase2_entries" resource array */
        private const val WIFI_PEAP_PHASE2_MSCHAPV2 = 0
        private const val WIFI_PEAP_PHASE2_GTC = 1
        private const val WIFI_PEAP_PHASE2_SIM = 2
        private const val WIFI_PEAP_PHASE2_AKA = 3
        private const val WIFI_PEAP_PHASE2_AKA_PRIME = 4

        private const val SSID_ASCII_MAX_LENGTH = 32

        fun newInstance(a_accessPoint: AccessPoint?, a_mode: Mode, a_dismissCallback: () -> Unit) = NetworkManagerDialog(a_accessPoint, a_mode, a_dismissCallback)
    }

    private val networkDialog by lazy { Dialog(requireContext()) }

    private var _viewBinding: DialogNetworkManagerBinding? = null
    private val viewBinding get() = _viewBinding!!

    // Network security
    private val securityInfoList = arrayListOf<SecurityInfo>()
    private var accessPointSecurity = 0

    private lateinit var phase2PEAPAdapter: ArrayAdapter<String>
    private lateinit var phase2TTLSAdapter: ArrayAdapter<String>

    private val mUnspecifiedCertString by lazy { getString(R.string.wlan_item_please_select) }
    private val mMultipleCertSetString by lazy { getString(R.string.wlan_item_multiple_certs_added) }
    private val mUseSystemCertsString by lazy { getString(R.string.wlan_item_use_system_cert) }
    private val mDoNotProvideEapUserCertString by lazy { getString(R.string.wlan_item_not_provide) }
    private val mDoNotValidateEapServerString by lazy { getString(R.string.wlan_item_not_validate) }

    // 연결 중 키 입력 방지 위함
    private var isConnecting = false

    // EAP 네트워크 설정 시 사용하는 변수
    var eapSSID = ""

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        // Dialog mode 에 따른 대화상자 타이틀 설정
        val title = when (mode) {
            // 네트워크 추가
            Mode.ADD -> getString(R.string.wlan_menu_add)

            // 네트워크 속성
            Mode.PROPERTIES -> getString(R.string.wlan_menu_properties)

            // EAP 연결
            else -> getString(R.string.wlan_label_network_key).replace(":", "")
        }
        networkDialog.setTitle(title)
        return networkDialog
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _viewBinding = DialogNetworkManagerBinding.inflate(inflater, container, false)
        return viewBinding.root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _viewBinding = null
        isConnecting = false
        dismissCallback()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        networkDialog.setOnKeyListener { dialog, keyCode, event ->
            if (isConnecting == true || networkDialog.currentFocus == null) {
                return@setOnKeyListener false
            }
            when (keyCode) {
                // profile 저장
                KeyEvent.KEYCODE_ENTER -> {
                    saveProfile()
                }

                // 대화상자 종료
                KeyEvent.KEYCODE_ESCAPE -> {
                    dismiss()
                }

                else -> return@setOnKeyListener false
            }
            true
        }

        // Network Name Field
        bindNetworkName()

        // Security field
        bindSecuritySpinner()
        bindSecurityEapMethod()
        bindSecurityPhase()
        bindSecurityCertificate()

        if (mode == Mode.PROPERTIES) {
            setEditModeView()
        }

        // [18.02.01][EVA][SWTEAM-273] 802.1.X 접근 화면 Setting
        // Dialog 가 빨리 뜨도록 하기 위해 post runnable 사용
        if (TextUtils.isEmpty(eapSSID) == false && accessPoint == null) {
            viewBinding.etNetworkName.setText(eapSSID)
            viewBinding.spNetworkSecurity.post { viewBinding.spNetworkSecurity.setSelection(5, false) }
        }
    }

    /**
     * Save 가능한지, 입력한 값들의 유효성 체크
     */
    private fun isSubmittable(): Boolean {
        viewBinding.etNetworkName.run {
            val ssid = viewBinding.etNetworkName.text.toString()

            // Network name 입력 여부 확인
            if (ssid.isEmpty() == true) {
                onError(this, R.string.wlan_err_no_network_name)
                return false
            }

            // Network name 길이 확인
            if (ssid.toByteArray().size > SSID_ASCII_MAX_LENGTH) {
                onError(this, R.string.wlan_err_network_name_length)
                return false
            }
        }

        // security 에 따라 password 입력 여부 및 유효성 확인
        viewBinding.etPassword.run {
            if (mode != Mode.PROPERTIES && isShown == true && checkPassword() == false) {
                onError(this, R.string.wlan_err_invalid_password)
                return false
            }
        }

        // check ca certificate
        val isEap = (accessPointSecurity == AccessPoint.SECURITY_EAP || accessPointSecurity == AccessPoint.SECURITY_EAP_SUITE_B)
        viewBinding.spCaCertificate
            .takeIf { isEap == true && it.isShown == true }
            ?.run {
                val caCertSelection = selectedItem as String
                if (caCertSelection == mUnspecifiedCertString == true) {
                    // Disallow submit if the user has not selected a CA certificate for an EAP network
                    // configuration.
                    onError(this, R.string.wlan_item_please_select)
                    return false
                }

                viewBinding.etDomain.let {
                    if (caCertSelection == mUseSystemCertsString == true && it.isShown == true && TextUtils.isEmpty(it.text.toString()) == true) {
                        // Disallow submit if the user chooses to use system certificates for EAP server
                        // validation, but does not provide a domain.
                        onError(it, R.string.wlan_err_no_domain)
                        return false
                    }
                }
            }

        // check user certificate
        viewBinding.spUserCertificate
            .takeIf { isEap == true && it.isShown == true && it.selectedItem == mUnspecifiedCertString }
            ?.run {
                // Disallow submit if the user has not selected a user certificate for an EAP network
                // configuration.
                onError(this, R.string.wlan_item_please_select)
                return false
            }

        return true
    }

    /**
     * 네트워크 추가/수정 또는 eap 네트워크 구성
     */
    private fun saveProfile() {
        if (isSubmittable() == true) {
            try {
                val connectConfig = getConfig()
                connectConfig?.let {
                    // fixme [20.10.19][bonita] 와이파이 저장은 system app 에서 해야함

                }
                dismiss()

            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    /**
     * Wifi Configuration 반환
     */
    private fun getConfig(): WifiConfiguration? {
        // 새로 추가하려는 network 가 이미 존재하는지 반환
        val isWifiExists = mode != Mode.PROPERTIES && accessPoint != null && accessPoint.networkId != -1
        if (isWifiExists == true) {
            return null
        }

        // 반환할 WifiConfiguration
        return WifiConfiguration().apply {
            // SSID, NetworkID 설정
            SSID = when {
                (accessPoint == null) ->
                    AccessPoint.convertToQuotedString(viewBinding.etNetworkName.text.toString())

                (accessPoint.isSaved() == false) ->
                    AccessPoint.convertToQuotedString(accessPoint.ssid)

                else -> {
                    networkId = accessPoint.configuration?.networkId ?: -1
                    AccessPoint.convertToQuotedString(accessPoint.ssid)
                }
            }

            // security type 설정
            val keyMgmtClass = getInnerClass(WifiConfigRef.CLASS_NAME, WifiConfigRef.CLASS_KEY_MGMT)
            when (accessPointSecurity) {
                AccessPoint.SECURITY_NONE ->
                    allowedKeyManagement.set(WifiConfiguration.KeyMgmt.NONE)

                AccessPoint.SECURITY_WEP -> {
                    allowedKeyManagement.set(WifiConfiguration.KeyMgmt.NONE)
                    allowedAuthAlgorithms.set(WifiConfiguration.AuthAlgorithm.OPEN)
                    allowedAuthAlgorithms.set(WifiConfiguration.AuthAlgorithm.SHARED)
                    val length = viewBinding.etPassword.length()
                    if (length != 0) {
                        val password = viewBinding.etPassword.text.toString()
                        // WEP-40, WEP-104, and 256-bit WEP (WEP-232?)
                        wepKeys[0] = if ((length == 10 || length == 26 || length == 58) && password.matches("[0-9A-Fa-f]*".toRegex()) == true) {
                            password
                        } else {
                            "\"$password\""
                        }
                    }
                }

                AccessPoint.SECURITY_PSK -> {
                    allowedKeyManagement.set(WifiConfiguration.KeyMgmt.WPA_PSK)
                    if (viewBinding.etPassword.length() != 0) {
                        val password = viewBinding.etPassword.text.toString()
                        preSharedKey = if (password.matches("[0-9A-Fa-f]{64}".toRegex()) == true) {
                            password
                        } else {
                            "\"$password\""
                        }
                    }
                }

                AccessPoint.SECURITY_EAP,
                AccessPoint.SECURITY_EAP_SUITE_B ->
                    setEapConfig(this)

                AccessPoint.SECURITY_SAE -> {
                    val sae = getStaticFieldValue(keyMgmtClass, WifiConfigRef.FIELD_SAE) as Int
                    allowedKeyManagement.set(sae)
                    setPublicFieldValue(this, WifiConfigRef.FIELD_REQUIRE_PMF, true)
                    if (viewBinding.etPassword.length() != 0) {
                        val password = viewBinding.etPassword.text.toString()
                        preSharedKey = "\"$password\""
                    }
                }

                AccessPoint.SECURITY_OWE -> {
                    val owe = getStaticFieldValue(keyMgmtClass, CommonDefine.AUTH_OWE) as Int
                    allowedKeyManagement.set(owe)
                    setPublicFieldValue(this, WifiConfigRef.FIELD_REQUIRE_PMF, true)
                }
            }
        }
    }

    /**
     * Security 가 EAP 일 때의 WifiConfiguration 값 setting
     */
    private fun setEapConfig(a_config: WifiConfiguration) {
        a_config.allowedKeyManagement.apply {
            set(WifiConfiguration.KeyMgmt.WPA_EAP)
            set(WifiConfiguration.KeyMgmt.IEEE8021X)
        }

        a_config.enterpriseConfig.apply {

            // 1. eap method
            val phase2Position = viewBinding.spPhase2Auth.selectedItemPosition
            phase2Method = if (viewBinding.spEapMethod.selectedItemPosition == WifiEnterpriseConfig.Eap.PEAP) {
                // PEAP supports limited phase2 values
                // Map the index from the PHASE2_PEAP_ADAPTER to the one used
                // by the API which has the full list of PEAP methods.
                when (phase2Position) {
                    WIFI_PEAP_PHASE2_MSCHAPV2 -> WifiEnterpriseConfig.Phase2.MSCHAPV2
                    WIFI_PEAP_PHASE2_GTC -> WifiEnterpriseConfig.Phase2.GTC
                    WIFI_PEAP_PHASE2_SIM -> WifiEnterpriseConfig.Phase2.SIM
                    WIFI_PEAP_PHASE2_AKA -> WifiEnterpriseConfig.Phase2.AKA
                    WIFI_PEAP_PHASE2_AKA_PRIME -> WifiEnterpriseConfig.Phase2.AKA_PRIME
                    else -> WifiEnterpriseConfig.Phase2.NONE
                }
            } else {
                // The default index from PHASE2_FULL_ADAPTER maps to the API
                phase2Position
            }

            // 2. certificates
            setCaCertificateAliases(a_config, null)
            setCaPath(a_config, null)

            domainSuffixMatch = viewBinding.etDomain.text.toString()

            val caCert = viewBinding.spCaCertificate.selectedItem as String
            when (caCert) {
                mUnspecifiedCertString, mDoNotValidateEapServerString -> {
                    // ca_cert already set to null, so do nothing.
                }

                mUseSystemCertsString -> setCaPath(a_config, SYSTEM_CA_STORE_PATH)

                mMultipleCertSetString -> {
                    if (accessPoint != null) {
                        val caAliases = getCaCertificateAliases(accessPoint.configuration!!.enterpriseConfig)
                        setCaCertificateAliases(a_config, caAliases)
                    }
                }

                else -> setCaCertificateAliases(a_config, arrayOf(caCert))
            }

            var clientCert = viewBinding.spUserCertificate.selectedItem as String
            if (clientCert == mUnspecifiedCertString || clientCert == mDoNotProvideEapUserCertString) {
                clientCert = ""
            }

            javaClass
                .getMethod(WifiEnterpriseConfigRef.METHOD_SET_CLIENT_ALIASES, String::class.java)
                .invoke(this, clientCert)

            // 3. identity
            val eapMethod = viewBinding.spEapMethod.selectedItemPosition
            when (eapMethod) {
                WifiEnterpriseConfig.Eap.SIM,
                WifiEnterpriseConfig.Eap.AKA,
                WifiEnterpriseConfig.Eap.AKA_PRIME -> {
                    identity = ""
                    anonymousIdentity = ""
                }

                WifiEnterpriseConfig.Eap.PWD -> {
                    identity = viewBinding.etIdentity.text.toString()
                    anonymousIdentity = ""
                }

                else -> {
                    identity = viewBinding.etIdentity.text.toString()
                    anonymousIdentity = viewBinding.etAnonymousIdentity.text.toString()
                }
            }

            // 4. set password
            // For security reasons, a previous password is not displayed to user.
            // edit mode 에서 비밀번호 변경 불가능
            if (mode != Mode.PROPERTIES && viewBinding.etPassword.isShown && viewBinding.etPassword.length() > 0) {
                password = viewBinding.etPassword.text.toString()
            }
        }
    }

    /**
     * Network name 관련 view 설정
     */
    private fun bindNetworkName() {
        if (mode != Mode.ADD) {
            viewBinding.etNetworkName.isEnabled = false
        }
    }

    /**
     * Network security spinner 설정
     */
    private fun bindSecuritySpinner() {
        // Security spinner
        viewBinding.spNetworkSecurity.run {
            securityInfoList.run {
                add(SecurityInfo(AccessPoint.SECURITY_NONE, getString(R.string.wlan_item_security_none)))
                add(SecurityInfo(AccessPoint.SECURITY_OWE, CommonDefine.AUTH_ENHANCED_OPEN))
                add(SecurityInfo(AccessPoint.SECURITY_WEP, CommonDefine.AUTH_WEP))
                add(SecurityInfo(AccessPoint.SECURITY_PSK, CommonDefine.AUTH_WPA2_PERSONAL))
                add(SecurityInfo(AccessPoint.SECURITY_SAE, CommonDefine.AUTH_WPA3_PERSONAL))
                add(SecurityInfo(AccessPoint.SECURITY_EAP, CommonDefine.AUTH_WPA_ENTERPRISE))
            }

            val securityList = arrayListOf<String>()
            securityInfoList.forEach { securityList.add(it.description) }
            adapter = ArrayAdapter<String>(context, android.R.layout.simple_spinner_item, securityList)

            onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                    accessPointSecurity = securityInfoList[position].type

                    viewBinding.run {
                        when (accessPointSecurity) {
                            AccessPoint.SECURITY_WEP,
                            AccessPoint.SECURITY_PSK,
                            AccessPoint.SECURITY_SAE -> {
                                eapField.visibility = View.GONE
                                passwordField.visibility = View.VISIBLE
                            }

                            AccessPoint.SECURITY_EAP -> {
                                eapField.visibility = View.VISIBLE
                                passwordField.visibility = View.GONE
                            }

                            else -> {
                                eapField.visibility = View.GONE
                                passwordField.visibility = View.GONE
                            }
                        }
                    }
                }

                override fun onNothingSelected(parent: AdapterView<*>?) {}
            }

            // T90 에서는 security 정보창 gone 처리 되어있음
            if (mode != Mode.ADD) {
                viewBinding.networkAuthField.visibility = View.GONE
            }
        }
    }

    /**
     * Network Security 모드 -> EAP Method 초기화
     * - Security 가  WPA/WPA2/WPA3-Enterprise 일 경우 visible
     */
    private fun bindSecurityEapMethod() {
        viewBinding.spEapMethod.run {
            val eapMethods = listOf(
                CommonDefine.EAP_PEAP,
                CommonDefine.EAP_TLS,
                CommonDefine.EAP_TTLS,
                CommonDefine.EAP_PWD,
                CommonDefine.PHASE_SIM,
                CommonDefine.PHASE_AKA,
                CommonDefine.PHASE_AKA_PRIME
            )

            adapter = ArrayAdapter<String>(context, android.R.layout.simple_spinner_item, eapMethods)
            onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                    when (position) {
                        WifiEnterpriseConfig.Eap.PEAP -> showPhase(phase2PEAPAdapter)

                        WifiEnterpriseConfig.Eap.TLS -> {
                            viewBinding.run {
                                phase2Field.visibility = View.GONE
                                caCertificateField.visibility = View.VISIBLE
                                domainField.visibility = View.VISIBLE
                                userCertificateField.visibility = View.VISIBLE
                                identityField.visibility = View.VISIBLE
                                anonymousIdentityField.visibility = View.GONE
                                passwordField.visibility = View.GONE
                            }
                        }

                        WifiEnterpriseConfig.Eap.TTLS -> showPhase(phase2TTLSAdapter)

                        WifiEnterpriseConfig.Eap.PWD -> {
                            viewBinding.run {
                                phase2Field.visibility = View.GONE
                                caCertificateField.visibility = View.GONE
                                domainField.visibility = View.GONE
                                userCertificateField.visibility = View.GONE
                                identityField.visibility = View.VISIBLE
                                anonymousIdentityField.visibility = View.GONE
                                passwordField.visibility = View.VISIBLE
                            }
                        }

                        // sim, aka, aka'
                        else -> {
                            viewBinding.run {
                                phase2Field.visibility = View.GONE
                                caCertificateField.visibility = View.GONE
                                domainField.visibility = View.GONE
                                userCertificateField.visibility = View.GONE
                                identityField.visibility = View.GONE
                                anonymousIdentityField.visibility = View.GONE
                                passwordField.visibility = View.GONE
                            }
                        }
                    }
                }

                override fun onNothingSelected(parent: AdapterView<*>?) {}

                private fun showPhase(a_adapter: ArrayAdapter<String>) {
                    viewBinding.run {
                        phase2Field.visibility = View.VISIBLE
                        caCertificateField.visibility = View.VISIBLE
                        domainField.visibility = View.VISIBLE
                        userCertificateField.visibility = View.GONE
                        identityField.visibility = View.VISIBLE
                        anonymousIdentityField.visibility = View.VISIBLE
                        passwordField.visibility = View.VISIBLE
                    }

                    // Adapter 가 다를 때에만 갱신하도록 함
                    viewBinding.spPhase2Auth.let {
                        if (it.adapter != a_adapter) {
                            it.adapter = a_adapter
                            it.setSelection(0)
                        }
                    }
                }
            }
        }
    }

    /**
     * Network Security 모드 -> Phase-2 authentication 초기화
     * - Security 가 EAP 이고 EAP 가 PEAP, TTLS 일 때에만 visible 됨
     */
    private fun bindSecurityPhase() {
        viewBinding.spPhase2Auth.run {
            // EAP Method 가 PEAP 일 때의 adapter
            val phasePEAPItems = listOf(
                CommonDefine.PHASE_MSCHAPV2,
                CommonDefine.PHASE_GTC,
                CommonDefine.PHASE_SIM,
                CommonDefine.PHASE_AKA,
                CommonDefine.PHASE_AKA_PRIME
            )
            phase2PEAPAdapter = ArrayAdapter<String>(context, android.R.layout.simple_spinner_item, phasePEAPItems)

            // EAP Method 가 TTLS 일 때의 adapter
            val phaseTTLSItems = listOf(
                CommonDefine.PHASE_PAP,
                CommonDefine.PHASE_MSCHAP,
                CommonDefine.PHASE_MSCHAPV2,
                CommonDefine.PHASE_GTC
            )
            phase2TTLSAdapter = ArrayAdapter<String>(context, android.R.layout.simple_spinner_item, phaseTTLSItems)

            adapter = phase2PEAPAdapter
        }
    }

    /**
     * Network Security 모드 -> Certificates 초기화
     */
    private fun bindSecurityCertificate() {
        // CA certificate (Security 가 Enterprise 이고 EAP Method 가 PEAP, TLS, TTLS 일 때에만 visible)
        viewBinding.spCaCertificate.run {
            adapter = getCertificateAdapter("CACERT_", mDoNotValidateEapServerString, a_isShowMultipleCerts = false, a_isShowUserCertOption = true)
            setSelection(1) // T90 에서만
            onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                    if (selectedItem == mUseSystemCertsString) {
                        viewBinding.domainField.visibility = View.VISIBLE
                    } else {
                        viewBinding.domainField.visibility = View.GONE
                        viewBinding.etDomain.setText("")
                    }
                }

                override fun onNothingSelected(parent: AdapterView<*>?) {}
            }
        }

        // User certificate
        viewBinding.spUserCertificate.run {
            adapter = getCertificateAdapter("USRPKEY_", mDoNotProvideEapUserCertString, a_isShowMultipleCerts = false, a_isShowUserCertOption = false)
            setSelection(1) // T90 에서만
        }
    }

    /**
     * Edit mode 일 경우 view setting
     * - Dialog 가 빨리 뜨도록 하기 위해 selection 시, post runnable 사용
     */
    private fun setEditModeView() {
        val config = accessPoint!!.configuration!!
        // -> EditMode 에서는 accessPoint 가 null 이면 안되고,
        // 저장되어있는 네트워크를 수정하는 경우이기 때문에 configuration 도 null 일 수가 없음

        // 1. Network name setting
        viewBinding.etNetworkName.setText(accessPoint.ssid)

        // 2. Security mode 선택 후 안보이게 처리
        var position = 0
        for (i in 0 until securityInfoList.size) {
            if (securityInfoList[i].type == accessPointSecurity) {
                position = i
                break
            }
        }

        // T90 에서는 security 정보창 gone 처리 되어있음
        viewBinding.networkAuthField.visibility = View.GONE
        if (position > 0) viewBinding.spNetworkSecurity.setSelection(position)

        // 3. Security 하위 필드 구성
        val enterpriseConfig = config.enterpriseConfig
        when (accessPoint.security) {
            AccessPoint.SECURITY_WEP,
            AccessPoint.SECURITY_PSK,
            AccessPoint.SECURITY_SAE -> viewBinding.etPassword.setText(enterpriseConfig.password)

            AccessPoint.SECURITY_EAP -> {
                viewBinding.eapField.visibility = View.GONE
                val eapPosition = if (accessPoint.isCarrierAp) accessPoint.carrierApEapType else enterpriseConfig.eapMethod

                // PEAP, TTLS 일 때 adapter setting 한 이유:
                // 현재 선택한 phasePosition 이 eap method spinner 의 onItemSelected 에서
                // 초기화 되지 않도록 eap 에 따른 adapter setting
                if (accessPoint.isSaved() == true) {
                    when (eapPosition) {
                        WifiEnterpriseConfig.Eap.PEAP,
                        WifiEnterpriseConfig.Eap.TTLS -> {
                            // PEAP, TTLS -> phase, ca certificate, identity, anon-identity, password
                            setEditPhase(enterpriseConfig, eapPosition)
                            setEditCaCert(enterpriseConfig)
                            setEditIdentity(enterpriseConfig, true)
                        }

                        WifiEnterpriseConfig.Eap.TLS -> {
                            // TLS -> ca certificate, user certificate, identity
                            setEditCaCert(enterpriseConfig)
                            setEditUserCert(enterpriseConfig)
                            setEditIdentity(enterpriseConfig, false)
                        }
                    }
                }

                viewBinding.spEapMethod.run { post { setSelection(eapPosition) } }
            }
        }
    }

    /**
     * Certificate 에 대한 adapter 생성하여 반환
     */
    private fun getCertificateAdapter(
        a_prefix: String,
        a_certificateString: String,
        a_isShowMultipleCerts: Boolean,
        a_isShowUserCertOption: Boolean
    ): ArrayAdapter<String> {

        val certs = arrayListOf<String>().apply {
            add(mUnspecifiedCertString)

            if (a_isShowMultipleCerts == true) {
                add(mMultipleCertSetString)
            }
            if (a_isShowUserCertOption == true) {
                add(mUseSystemCertsString)
            }

            try {
                val uid = getStaticFieldValue(android.os.Process::class.java, "WIFI_UID") as Int
                val keyStoreClass = Class.forName(KeyStoreRef.CLASS_NAME)
                val keyStore = keyStoreClass.getMethod(KeyStoreRef.METHOD_GET_INSTANCE).invoke(null)
                val method = keyStore.javaClass.getMethod(KeyStoreRef.METHOD_LIST, String::class.java, Int::class.java)
                val list = method.invoke(keyStore, a_prefix, uid)
                list?.let { addAll(list as Array<String>) }
            } catch (e: Exception) {
                e.printStackTrace()
            }

            add(a_certificateString)
        }

        return ArrayAdapter<String>(context, android.R.layout.simple_spinner_item, certs).apply {
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }
    }

    /**
     * Edit Mode 에서 현재 AccessPoint 에 대한 Phase setting
     * - PEAP, TTLS 일 때 adapter setting 한 이유
     * -> 현재 선택한 phasePosition 이 eap method spinner 의 onItemSelected 에서 초기화 되면 안되므로
     */
    private fun setEditPhase(a_enterpriseConfig: WifiEnterpriseConfig, a_eap: Int) {
        viewBinding.spPhase2Auth.run {
            val phase2Method = a_enterpriseConfig.phase2Method
            val phasePosition = if (a_eap == WifiEnterpriseConfig.Eap.PEAP) {
                // PEAP
                viewBinding.spPhase2Auth.adapter = phase2PEAPAdapter

                when (phase2Method) {
                    WifiEnterpriseConfig.Phase2.MSCHAPV2 -> WIFI_PEAP_PHASE2_MSCHAPV2
                    WifiEnterpriseConfig.Phase2.GTC -> WIFI_PEAP_PHASE2_GTC
                    WifiEnterpriseConfig.Phase2.SIM -> WIFI_PEAP_PHASE2_SIM
                    WifiEnterpriseConfig.Phase2.AKA -> WIFI_PEAP_PHASE2_AKA
                    WifiEnterpriseConfig.Phase2.AKA_PRIME -> WIFI_PEAP_PHASE2_AKA_PRIME
                    else -> return
                }

            } else {
                // TTLS
                viewBinding.spPhase2Auth.adapter = phase2TTLSAdapter
                phase2Method
            }

            post { setSelection(phasePosition) }
        }
    }

    /**
     * Edit Mode 에서 현재 AccessPoint 에 대한 ca certificate setting
     */
    private fun setEditCaCert(a_enterpriseConfig: WifiEnterpriseConfig) {
        viewBinding.spCaCertificate.run {
            val caPath = getMethodValue(a_enterpriseConfig, WifiEnterpriseConfigRef.METHOD_GET_CA_PATH)
            if (caPath == null) {
                val caCerts = getCaCertificateAliases(a_enterpriseConfig)
                caCerts
                    ?.let {
                        val caCertsArray = caCerts as Array<String>
                        if (caCertsArray.size == 1) {
                            selectSpinner(this, caCertsArray[0])
                        } else {
                            // Reload the cert spinner with an extra "multiple certificates added" item.
                            adapter = getCertificateAdapter("CACERT_", mDoNotValidateEapServerString, a_isShowMultipleCerts = true, a_isShowUserCertOption = true)
                            selectSpinner(this, mMultipleCertSetString)
                        }
                    } ?: run {
                    selectSpinner(viewBinding.spCaCertificate, mDoNotValidateEapServerString)
                }

            } else {
                selectSpinner(this, mUseSystemCertsString)
            }
        }

        viewBinding.etDomain.setText(a_enterpriseConfig.domainSuffixMatch)
    }

    /**
     * Edit Mode 에서 현재 AccessPoint 에 대한 user certificate setting
     */
    private fun setEditUserCert(a_enterpriseConfig: WifiEnterpriseConfig) {
        viewBinding.spUserCertificate.run {
            val userCert = getMethodValue(a_enterpriseConfig, WifiEnterpriseConfigRef.METHOD_GET_CLIENT_ALIASES) as String?
            if (TextUtils.isEmpty(userCert) == true) {
                selectSpinner(this, mDoNotProvideEapUserCertString)
            } else {
                selectSpinner(this, userCert)
            }
        }
    }

    /**
     * Edit Mode 에서 현재 AccessPoint 에 대한 identity setting
     */
    private fun setEditIdentity(a_enterpriseConfig: WifiEnterpriseConfig, a_isNeedAnonymous: Boolean) {
        viewBinding.etIdentity.setText(a_enterpriseConfig.identity)
        if (a_isNeedAnonymous == true) {
            viewBinding.etAnonymousIdentity.setText(a_enterpriseConfig.anonymousIdentity)
        }
    }

    /**
     * Spinner 에서 a_value 을 찾아 select
     */
    private fun selectSpinner(a_spinner: Spinner, a_value: String?) {
        if (a_value == null) {
            return
        }

        val position: Int
        val adapter = a_spinner.adapter as ArrayAdapter<String>
        for (i in 0 until adapter.count) {
            if (adapter.getItem(i) == a_value) {
                position = i
                a_spinner.post { a_spinner.setSelection(position) }
                return
            }
        }
    }

    /**
     * password 유효성 검사
     */
    private fun checkPassword(): Boolean {
        val password = viewBinding.etPassword.text.toString()
        return when (accessPointSecurity) {
            AccessPoint.SECURITY_WEP -> (viewBinding.etPassword.length() != 0)

            AccessPoint.SECURITY_PSK -> (password.length == 64 &&
                    password.matches("[0-9A-Fa-f]{64}".toRegex()) == true) ||
                    (password.length in 8..63)

            AccessPoint.SECURITY_SAE -> (password.length in 1..63)

            else -> true
        }
    }

    /**
     * 오류 처리
     * - 오류 메시지 출력 후, a_selectView 에 focus 위치
     */
    private fun onError(a_selectView: View, a_resID: Int) {
        // select view 에 focus
        a_selectView.requestFocusFromTouch()
    }

    /**
     * Reflection -> WifiConfiguration.enterpriseConfig.getCaCertificateAliases()
     */
    private fun getCaCertificateAliases(a_enterpriseConfig: WifiEnterpriseConfig): Any? =
        getMethodValue(
            a_enterpriseConfig,
            WifiEnterpriseConfigRef.METHOD_GET_CA_ALIASES
        )

    /**
     * Reflection -> WifiConfiguration.enterpriseConfig.setCaCertificateAliases()
     */
    @Throws(Exception::class)
    private fun setCaCertificateAliases(a_config: WifiConfiguration, a_value: Any?) {
        a_config.enterpriseConfig.javaClass
            .getMethod(WifiEnterpriseConfigRef.METHOD_SET_CA_ALIASES, Array<String>::class.java)
            .invoke(a_config.enterpriseConfig, a_value)
    }

    /**
     * Reflection -> WifiConfiguration.enterpriseConfig.setCaPath()
     */
    @Throws(Exception::class)
    private fun setCaPath(a_config: WifiConfiguration, a_value: Any?) {
        a_config.enterpriseConfig.javaClass
            .getMethod(WifiEnterpriseConfigRef.METHOD_SET_CA_PATH, String::class.java)
            .invoke(a_config.enterpriseConfig, a_value)
    }

    /**
     * Network Manager 대화상자 mode
     * - 추가, 속성, EAP 연결
     */
    enum class Mode {
        ADD, PROPERTIES, CONNECT_EAP
    }

    /**
     * security type 과 그에 따른 string 을 저장 하는 클래스
     */
    inner class SecurityInfo(val type: Int, val description: String)
}