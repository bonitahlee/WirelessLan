package com.bonita.wirelesslan.widget;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Spinner;
import android.widget.TextView;

import com.bonita.wirelesslan.R;
import com.bonita.wirelesslan.data.BlazeSpinnerListItem;

import java.util.ArrayList;

/**
 * <pre>
 *     Blaze SpinnerList Adapter
 * </pre>
 *
 * @author parkho79
 * @date 2021-11-04
 */
public class BlazeSpinnerListAdapter extends BaseAdapter {

    protected Context mContext;
    protected ArrayList<BlazeSpinnerListItem> mItemList;

    public BlazeSpinnerListAdapter(Context a_context, ArrayList<BlazeSpinnerListItem> a_list) {
        mContext = a_context;
        mItemList = a_list;
    }

    @Override
    public View getView(int a_position, View a_convertView, ViewGroup a_parent) {
        BlazeSpinnerListItem spinnerListItem = mItemList.get(a_position);
        ViewHolder holder;

        if (a_convertView == null) {
            LayoutInflater inflater = (LayoutInflater) mContext.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            a_convertView = inflater.inflate(R.layout.spinner_list_item, null);

            holder = new ViewHolder();
            holder.mLabel = (TextView) a_convertView.findViewById(R.id.tv_setting_label);
            holder.mSpinner = (Spinner) a_convertView.findViewById(R.id.hs_setting_value);

            a_convertView.setTag(holder);
        } else {
            holder = (ViewHolder) a_convertView.getTag();
        }

        final String strLabel = spinnerListItem.getLabel();
        if (spinnerListItem.getKey().isEmpty()) {
            if (strLabel.isEmpty() == false && strLabel.charAt(strLabel.length() - 1) == '：') {
                holder.mLabel.setText(strLabel);
            } else {
                holder.mLabel.setText(strLabel + ":");
            }
        } else {
            holder.mLabel.setText(strLabel + " " + spinnerListItem.getOutputDataKey() + ":");
        }

        // 재사용 시 adapter 다시 setting 해줘야 함
        BlazeSpinnerItemAdapter adapter = new BlazeSpinnerItemAdapter(mContext, android.R.layout.simple_spinner_item, spinnerListItem.getValues());
        holder.mSpinner.setAdapter(adapter);
        holder.mSpinner.setSelection(spinnerListItem.getSelectedIndex());
        holder.mSpinner.setFocusable(false);

        return a_convertView;
    }

    @Override
    public int getCount() {
        return mItemList.size();
    }

    @Override
    public Object getItem(int position) {
        if (position == -1) {
            position = 0;
        }
        return mItemList.get(position);
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    public class ViewHolder {
        private TextView mLabel;
        private Spinner mSpinner;

        public TextView getLabel() {
            return mLabel;
        }

        public void setLabel(TextView mLabel) {
            this.mLabel = mLabel;
        }

        public Spinner getSpinner() {
            return mSpinner;
        }

        public void setSpinner(Spinner mSpinner) {
            this.mSpinner = mSpinner;
        }
    }
}
