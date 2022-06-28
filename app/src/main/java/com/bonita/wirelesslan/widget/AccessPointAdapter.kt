package com.bonita.wirelesslan.widget

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.TextView
import com.bonita.wirelesslan.R
import com.bonita.wirelesslan.data.AccessPoint

/**
 * AccessPoint Adapter
 *
 * @author bonita
 * @date 2022-03-07
 */
class AccessPointAdapter(
    a_context: Context,
    private val resourceId: Int,
    a_accessPoints: ArrayList<AccessPoint>
) : ArrayAdapter<AccessPoint>(a_context, resourceId, a_accessPoints) {

    override fun getView(a_position: Int, a_convertView: View?, a_parent: ViewGroup): View {
        val convertView: View
        val itemTextView: TextView

        if (a_convertView == null) {
            convertView = (context.getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater).inflate(resourceId, null)
            itemTextView = convertView.findViewById(android.R.id.text1) as TextView
            convertView.tag = itemTextView
        } else {
            convertView = a_convertView
            itemTextView = convertView.tag as TextView
        }

        // 항목 정보 setting
        itemTextView.text = StringBuilder().apply {
            val accessPoint = getItem(a_position)!!
            append(accessPoint.getDisplayNetworkInfoStr())

            if (accessPoint.isConnected) {
                append(", ")
                append(context.getString(R.string.wlan_msg_connected))
            }
        }

        // Description setting
        convertView.contentDescription = StringBuilder().apply {
            append(itemTextView.text.toString())
            append(' ')
            append(a_position + 1)
            append('/')
            append(count)
        }

        return convertView
    }
}