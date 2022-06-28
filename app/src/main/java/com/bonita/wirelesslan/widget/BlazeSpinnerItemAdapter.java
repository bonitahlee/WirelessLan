package com.bonita.wirelesslan.widget;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;

import java.util.List;

/**
 * <pre>
 *     Blaze Spinner Item Adapter
 * </pre>
 *
 * @author parkho79
 * @date 2021-11-04
 */
public class BlazeSpinnerItemAdapter extends ArrayAdapter<String> {

    private int mResourceId;
    protected String[] mArray;
    private List<String> mList;
    private boolean mIsListMode;

    public BlazeSpinnerItemAdapter(Context a_context, int a_resource, String[] a_array) {
        super(a_context, a_resource, a_array);

        mResourceId = a_resource;
        mArray = a_array;
        mIsListMode = false;
    }

    public BlazeSpinnerItemAdapter(Context a_context, int a_resource, List<String> a_list) {
        super(a_context, a_resource, a_list);

        mResourceId = a_resource;
        mList = a_list;
        mIsListMode = true;
    }

    @Override
    public View getView(int a_position, View a_convertView, ViewGroup a_parent) {
        final TextView textView;
        if (a_convertView == null) {
            LayoutInflater inflater = (LayoutInflater) getContext().getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            a_convertView = inflater.inflate(mResourceId, null);
            textView = (TextView) a_convertView.findViewById(android.R.id.text1);
            a_convertView.setTag(textView);
        } else {
            textView = (TextView) a_convertView.getTag();
        }

        String text = "";
        int size = 0;
        if (mIsListMode) {
            text = mList.get(a_position);
            size = mList.size();
        } else {
            text = mArray[a_position];
            size = mArray.length;
        }

        text += " ";
        text += a_position + 1;
        text += "/";
        text += size;

        textView.setText(text);
        textView.setContentDescription(text);

        return a_convertView;
    }
}
