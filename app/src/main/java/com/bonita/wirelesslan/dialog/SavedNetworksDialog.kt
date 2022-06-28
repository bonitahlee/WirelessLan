package com.bonita.wirelesslan.dialog

import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import androidx.fragment.app.DialogFragment
import com.bonita.wirelesslan.R
import com.bonita.wirelesslan.controller.WLanController
import com.bonita.wirelesslan.data.AccessPoint
import com.bonita.wirelesslan.databinding.DialogSavedNetworksBinding

/**
 * 저장된 네트워크 목록 대화상자
 *
 * @author bonita
 * @date 2022-05-13
 */
class SavedNetworksDialog(private val controller: WLanController, private val dismissCallback: () -> Unit) : DialogFragment() {

    companion object {
        const val TAG = "SavedNetworksDialog"

        fun newInstance(a_controller: WLanController, a_dismissCallback: () -> Unit) = SavedNetworksDialog(a_controller, a_dismissCallback)
    }

    private val networkDialog by lazy { Dialog(requireContext()) }

    private var _viewBinding: DialogSavedNetworksBinding? = null
    private val viewBinding get() = _viewBinding!!

    // 저장된 네트워크 목록
    private val savedAccessPoints = ArrayList<AccessPoint>()
    private val savedProfiles = ArrayList<String>()

    // Wifi 에 연결 중인지 확인
    private var isConnecting = false

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        networkDialog.setTitle(R.string.wlan_menu_saved_networks)
        return networkDialog
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _viewBinding = DialogSavedNetworksBinding.inflate(inflater, container, false)
        return viewBinding.root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _viewBinding = null
        dismissCallback()

        isConnecting = false
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        bindListView()
    }

    /**
     * 저장된 네트워크 목록 구성
     */
    private fun bindListView() {
        viewBinding.lvSavedNetworks.run {
            savedAccessPoints.addAll(controller.makeSavedAccessPoints(context))
            savedAccessPoints.forEachIndexed { _, accessPoint -> savedProfiles.add(accessPoint.ssid) }
            adapter = ArrayAdapter(context, android.R.layout.simple_list_item_1, savedProfiles)
            setOnItemClickListener { parent, view, position, id -> connectProfile() }
        }
    }

    /**
     * 선택한 프로파일에 연결
     */
    private fun connectProfile() {
        if (savedAccessPoints.isEmpty() == true) {
            return
        }

        val selectedAP = savedAccessPoints[viewBinding.lvSavedNetworks.selectedItemPosition]

        // 이미 연결된 AccessPoint 일 경우 처리
        controller.wifiManager.connectionInfo
            ?.takeIf { it.ipAddress != 0 }
            ?.takeIf { AccessPoint.removeDoubleQuotes(it.bssid) == AccessPoint.removeDoubleQuotes(selectedAP.bssid) }
            ?.apply {
                return
            }

        // 연결 시도
        isConnecting = true

        controller.connectConfiguredNetwork(requireContext(), selectedAP.configuration!!.networkId, selectedAP.ssid) {
            isConnecting = false
        }
    }
}