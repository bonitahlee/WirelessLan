package com.bonita.wirelesslan.dialog

import android.app.Dialog
import android.content.Context
import android.net.wifi.WifiManager
import android.os.Bundle
import android.text.format.Formatter
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.DialogFragment
import com.bonita.wirelesslan.R
import com.bonita.wirelesslan.data.AccessPoint
import com.bonita.wirelesslan.data.BlazeSpinnerListItem
import com.bonita.wirelesslan.databinding.DialogNetworkInfoBinding
import com.bonita.wirelesslan.widget.BlazeSpinnerListAdapter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.net.*
import java.util.*
import kotlin.collections.ArrayList

/**
 * 프로파일 정보 대화상자
 *
 * @author bonita
 * @date 2022-06-21
 */
class NetworkInfoDialog(
    private val accessPoint: AccessPoint,
    private val dismissCallback: () -> Unit
) : DialogFragment() {

    companion object {
        const val TAG = "NetworkInfoDialog"

        fun newInstance(a_accessPoint: AccessPoint, a_dismissCallback: () -> Unit) = NetworkInfoDialog(a_accessPoint, a_dismissCallback)
    }

    private val infoDialog by lazy { Dialog(requireContext()) }

    private var _viewBinding: DialogNetworkInfoBinding? = null
    private val viewBinding get() = _viewBinding!!

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        infoDialog.setTitle(R.string.wlan_msg_profile_info)
        return infoDialog
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _viewBinding = DialogNetworkInfoBinding.inflate(inflater, container, false)
        return viewBinding.root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _viewBinding = null
        dismissCallback()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        bindListView()
    }

    /**
     * ListView 설정
     */
    private fun bindListView() {
        viewBinding.spLvInfo.run {
            val wifiManager = context.getSystemService(Context.WIFI_SERVICE) as WifiManager
            val intoItems = ArrayList<BlazeSpinnerListItem>().apply {

                CoroutineScope(Dispatchers.Default).launch {
                    val connectedInfo = wifiManager.connectionInfo
                    val dhcpInfo = wifiManager.dhcpInfo
                    val networkInterface = getWlanInterface()

                    // ssid (read only)
                    add(BlazeSpinnerListItem(getString(R.string.wlan_label_network_name), arrayOf(accessPoint.ssid), 0))

                    // signal strength
                    add(BlazeSpinnerListItem(getString(R.string.wlan_msg_signal), arrayOf(AccessPoint.getSensitivityStr(requireContext(), accessPoint.rssi)), 0))

                    // Frequency
                    add(BlazeSpinnerListItem(getString(R.string.wlan_label_frequency), arrayOf(getFrequencyBand(connectedInfo.frequency)), 0))

                    // security type
                    add(BlazeSpinnerListItem(getString(R.string.wlan_label_security), arrayOf(accessPoint.getSecurityString(false)), 0))

                    // mac address
                    add(BlazeSpinnerListItem(getString(R.string.wlan_label_mac_address), arrayOf(getMacAddress(networkInterface?.hardwareAddress)), 0))

                    // ip address
                    add(BlazeSpinnerListItem(getString(R.string.wlan_label_ip_address), arrayOf(Formatter.formatIpAddress(connectedInfo.ipAddress).toString()), 0))

                    // gateway
                    add(BlazeSpinnerListItem(getString(R.string.wlan_label_gateway), arrayOf(Formatter.formatIpAddress(dhcpInfo.gateway).toString()), 0))

                    // subnet mask
                    add(BlazeSpinnerListItem(getString(R.string.wlan_label_subnet_mask), arrayOf(getNetMask(networkInterface?.interfaceAddresses?.get(1)?.networkPrefixLength)), 0))

                    // DNS
                    add(BlazeSpinnerListItem(getString(R.string.wlan_label_dns1), arrayOf(Formatter.formatIpAddress(dhcpInfo.dns1).toString()), 0))
                    add(BlazeSpinnerListItem(getString(R.string.wlan_label_dns2), arrayOf(Formatter.formatIpAddress(dhcpInfo.dns2).toString()), 0))

                    // link speed
                    add(BlazeSpinnerListItem(getString(R.string.wlan_label_link_speed), arrayOf(context.getString(R.string.wlan_link_speed, connectedInfo.linkSpeed)), 0))

                    // IPv6 addresses
                    add(BlazeSpinnerListItem(getString(R.string.wlan_label_ipv6_address), arrayOf(getIpv6Address(networkInterface?.inetAddresses)), 0))

                    CoroutineScope(Dispatchers.Main).launch {
                        (adapter as BlazeSpinnerListAdapter).notifyDataSetChanged()
                        setSelection(0)
                    }
                }
            }

            adapter = BlazeSpinnerListAdapter(context, intoItems)
        }
    }

    /**
     * 무선랜 NetworkInterface 반환
     * - android.permission.INTERNET 권한 필요
     */
    private fun getWlanInterface(): NetworkInterface? {
        val interfaces = Collections.list(NetworkInterface.getNetworkInterfaces())
        for (networkInterface in interfaces) {
            if (networkInterface.name.equals("wlan0", true)) {
                return networkInterface
            }
        }
        return null
    }

    /**
     * 주파수 대역 반환
     * - 2.4 GHz or 5 GHz
     */
    private fun getFrequencyBand(a_frequency: Int): String {
        return if (a_frequency >= AccessPoint.LOWER_FREQ_24GHZ && a_frequency < AccessPoint.HIGHER_FREQ_24GHZ) {
            requireContext().getString(R.string.wlan_band_24ghz)
        } else if (a_frequency >= AccessPoint.LOWER_FREQ_5GHZ && a_frequency < AccessPoint.HIGHER_FREQ_5GHZ) {
            requireContext().getString(R.string.wlan_band_5ghz)
        } else {
            ""
        }
    }

    /**
     * 맥주소 반환
     */
    private fun getMacAddress(mac: ByteArray?): String {
        if (mac == null) {
            return ""
        }

        try {
            val buf = StringBuilder()
            for (b in mac) {
                buf.append(String.format("%02X:", b))
            }

            if (buf.isNotEmpty()) {
                buf.deleteCharAt(buf.length - 1)  // 마지막 : 제거
            }

            return buf.toString().lowercase(Locale.getDefault())

        } catch (ex: Exception) {
            ex.printStackTrace()
        }

        return ""
    }

    /**
     * dhcpInfo.netmask 는 항상 0 으로 return 된다 -> android issue
     * 즉, Ipv4Address 의 prefixLength 를 통해 subnet mask 를 변환해야 함
     */
    private fun getNetMask(a_length: Short?): String {
        if (a_length == null) return "0"

        val shft: Int = -0x1 shl 32 - a_length
        val oct1: Int = ((shft and -0x1000000) shr 24) and 0xff
        val oct2: Int = ((shft and 0x00ff0000) shr 16) and 0xff
        val oct3: Int = ((shft and 0x0000ff00) shr 8) and 0xff
        val oct4: Int = (shft and 0x000000ff) and 0xff
        return "$oct1.$oct2.$oct3.$oct4"
    }

    /**a
     * Ipv6 주소 반환
     */
    private fun getIpv6Address(inetAddresses: Enumeration<InetAddress>?): String {
        if (inetAddresses == null) {
            return ""
        }

        while (inetAddresses.hasMoreElements()) {
            inetAddresses.nextElement()
                .takeIf { it.isLoopbackAddress == false }
                ?.takeIf { it is Inet6Address }
                ?.run {
                    return@getIpv6Address hostAddress
                        .substringAfter("/")
                        .replace("%wlan0", "")
                }
        }

        return ""
    }
}