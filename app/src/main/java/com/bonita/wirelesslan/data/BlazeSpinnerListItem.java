package com.bonita.wirelesslan.data;

/**
 * <pre>
 *     Blaze Spinner ListItem
 * </pre>
 *
 * @author parkho79
 * @date 2021-11-04
 */
public class BlazeSpinnerListItem {

    private String mKey;
    private String mLabel;
    private String[] mValues;
    private int mSelectedIndex;

    public BlazeSpinnerListItem(String label, String[] values, int index) {
        init("", label, values, index);
    }

    public void init(String key, String label, String[] values, int index) {
        mKey = key;
        mLabel = label;
        if (mLabel.contains(":")) {
            mLabel = mLabel.replace(":", "");
        }
        if (mLabel.contains("?")) {
            mLabel = mLabel.replace("?", "");
        }
        mValues = values;
        mSelectedIndex = index;
    }

    public String getKey() {
        return mKey;
    }

    public String getLabel() {
        return mLabel;
    }

    public String[] getValues() {
        return mValues;
    }

    public void setValues(String[] values) {
        mValues = values;
    }

    public int getSelectedIndex() {
        return mSelectedIndex;
    }

    public void setSelectedIndex(int index) {
        mSelectedIndex = index;
    }

    public String getOutputDataKey() {
        return ("(" + mKey + ")");
    }
}


