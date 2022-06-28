package com.selvashc.blazewlan.data

/**
 * Static 변수를 모아놓은 클래스
 *
 * @author bonita
 * @date 2022-03-07
 */
object CommonDefine {

    const val AUTH_WPA = "WPA"
    const val AUTH_ENHANCED_OPEN = "Enhanced Open"
    const val AUTH_WEP = "WEP"
    const val AUTH_WPA2_PERSONAL = "WPA/WPA2-Personal"
    const val AUTH_WPA3_PERSONAL = "WPA3-Personal"
    const val AUTH_WPA_ENTERPRISE = "WPA/WPA2/WPA3-Enterprise"
    const val AUTH_WPA_PERSONAL_PSK = "WPA-Personal"
    const val AUTH_SAE_WPA3 = "WPA3"
    const val AUTH_OWE = "OWE"

    const val EAP_PEAP = "PEAP"
    const val EAP_TLS = "TLS"
    const val EAP_TTLS = "TTLS"
    const val EAP_PWD = "PWD"
    const val EAP_SUITE = "Suite-B-192"
    const val EAP_WPA = "WPA-EAP"
    const val EAP_WPA_ENTERPRISE = "WPA-Enterprise"
    const val EAP_RSN = "RSN-EAP"
    const val EAP_WPA3_ENTERPRISE = "WPA2/WPA3-Enterprise"
    const val EAP_802 = "802.1x"

    const val PSK_WPA2 = "WPA2"
    const val PSK_WPA_WPA2 = "WPA/WPA2"
    const val PSK_WPA2_PERSONAL = "WPA2-Personal"
    const val PSK_SAE_WPA = "WPA2/WPA3"
    const val PSK_SAE_WPA_PERSONAL = "WPA2/WPA3-Personal"

    const val PHASE_MSCHAPV2 = "MSCHAPV2"
    const val PHASE_MSCHAP = "MSCHAP"
    const val PHASE_GTC = "GTC"
    const val PHASE_SIM = "SIM"
    const val PHASE_AKA = "AKA"
    const val PHASE_AKA_PRIME = "AKA'"
    const val PHASE_PAP = "PAP"
}