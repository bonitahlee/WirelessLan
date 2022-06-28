package com.bonita.wirelesslan


import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.NetworkInfo
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.bonita.wirelesslan.controller.WLanController
import com.bonita.wirelesslan.databinding.FragmentWlanBinding
import com.bonita.wirelesslan.widget.AccessPointAdapter
import java.util.*

/**
 * Set WirelessLan Fragment
 *
 * @author bonita
 * @date 2022-03-04
 */
class WLanFragment : Fragment() {

    companion object {
        const val FRAGMENT_TAG = "WLanFragment"
    }

    // View binding
    private var _viewBinding: FragmentWlanBinding? = null
    private val viewBinding get() = _viewBinding!!

    private lateinit var adapterAccessPoint: AccessPointAdapter

    private val wlanController = WLanController()

    private var mLastWifiInfo: WifiInfo? = null
    private var mLastNetworkInfo: NetworkInfo? = null

    private var mIsRSSIScan = false

    private val wifiReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, a_intent: Intent) {
            if (wlanController.isProcessing) return
            val action = a_intent.action ?: return
            when (action) {
                WifiManager.WIFI_STATE_CHANGED_ACTION -> {
                    val state = a_intent.getIntExtra(WifiManager.EXTRA_WIFI_STATE, WifiManager.WIFI_STATE_UNKNOWN)
                    when (state) {
                        // 와이파이 연결 됐을 때
                        WifiManager.WIFI_STATE_ENABLED -> {
                            wlanController.resumeScan()
                        }

                        // 와이파이 연결 해제됐을 때
                        WifiManager.WIFI_STATE_DISABLED -> {
                            wlanController.pauseScan()
                            adapterAccessPoint.clear()
                        }
                    }

                    mLastWifiInfo = null
                    mLastNetworkInfo = null
                }

                WifiManager.SCAN_RESULTS_AVAILABLE_ACTION -> {
                    if (wlanController.isConnecting == false) {
                        updateAccessPoints()
                    }
                }

                WifiManager.RSSI_CHANGED_ACTION -> {
                    if (mIsRSSIScan == true) {
                        updateAccessPoints()
                        mIsRSSIScan = false
                    }

                    mLastWifiInfo = wlanController.getConnectionInfo(mLastWifiInfo)
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val filter = IntentFilter().apply {
            addAction(WifiManager.WIFI_STATE_CHANGED_ACTION)
            addAction(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION)
            addAction(WifiManager.RSSI_CHANGED_ACTION)
        }
        requireActivity().registerReceiver(wifiReceiver, filter)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _viewBinding = FragmentWlanBinding.inflate(inflater, container, false)
        return viewBinding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        bindWLanList()
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)

        wlanController.initialize(requireActivity())
    }

    override fun onResume() {
        super.onResume()

        wlanController.setProcessingMode(false)
    }

    override fun onPause() {
        super.onPause()

        wlanController.setProcessingMode(true)
    }

    override fun onDestroyView() {
        super.onDestroyView()

        _viewBinding = null
    }

    override fun onDestroy() {
        requireActivity().unregisterReceiver(wifiReceiver)

        super.onDestroy()
    }

    /**
     * 무선랜 목록 초기화
     */
    private fun bindWLanList() {
        viewBinding.lvAccessPoint.run {
            adapterAccessPoint = AccessPointAdapter(requireActivity(), android.R.layout.simple_list_item_1, wlanController.accessPoints)
            adapter = adapterAccessPoint
            setOnItemClickListener { _, _, _, _ -> connectToAp() }
        }
    }

    /**
     * 무선랜 목록 update
     */
    private fun updateAccessPoints() {
        when (wlanController.getWifiState()) {
            WifiManager.WIFI_STATE_ENABLED -> {
                /*
                 * 현재 선택된 AccessPoint 기억
                 * - list update 된 후 현재 선택된 accessPoint 에 select 유지하기 위함
                 */
                val prevAccessPoint = if (adapterAccessPoint.isEmpty == true || viewBinding.lvAccessPoint.selectedItemPosition == -1) {
                    null
                } else {
                    adapterAccessPoint.getItem(viewBinding.lvAccessPoint.selectedItemPosition)
                }

                // AccessPoint List 구성
                val list = wlanController.makeAccessPoints(requireActivity(), mLastWifiInfo, mLastNetworkInfo)

                // Update AccessPoint List
                wlanController.accessPoints.clear()
                wlanController.accessPoints.addAll(list)
                adapterAccessPoint.notifyDataSetChanged()

                // 이전에 선택된 accessPoint position 찾기
                prevAccessPoint?.run {
                    list.forEachIndexed { index, accessPoint ->
                        if (accessPoint.bssid == bssid) {
                            viewBinding.lvAccessPoint.setSelection(index)
                            return@run
                        }
                    }
                }

                viewBinding.lvAccessPoint.requestFocus()
            }

            WifiManager.WIFI_STATE_ENABLING -> wlanController.accessPoints.clear()
        }
    }

    /**
     * 프로파일에 연결
     */
    private fun connectToAp() {
        wlanController.connectAp(requireActivity(), viewBinding.lvAccessPoint.selectedItemPosition) {
            Handler(Looper.getMainLooper()).post { updateAccessPoints() }
        }
    }

    /**
     * 프로파일 정보보기
     */
    private fun callNetworkInfoDialog() {
        val position = viewBinding.lvAccessPoint.selectedItemPosition
        if (position == -1) {
            return
        }

        wlanController.callNetworkInfoDialog(requireActivity(), position)
    }
}