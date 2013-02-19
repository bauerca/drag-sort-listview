package com.mobeta.android.dslv;

import android.database.DataSetObserver;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ListAdapter;
import android.widget.AdapterView;

public class HideAdapter extends BaseAdapter {

    private ListAdapter mAdapter;

    private int mHiddenPos = AdapterView.INVALID_POSITION;

    public HideAdapter(ListAdapter adapter) {
        mAdapter = adapter;
    }

    public void hide(int position) {
        if (mHiddenPos == AdapterView.INVALID_POSITION) {
            if (position >= 0 && position < getCount()) {
                mHiddenPos = position;
                notifyDataSetChanged();
            }
        }
    }

    public void reveal() {
        if (mHiddenPos != AdapterView.INVALID_POSITION) {
            mHiddenPos = AdapterView.INVALID_POSITION;
            notifyDataSetChanged();
        }
    }

    public boolean isHiding() {
        return mHiddenPos != AdapterView.INVALID_POSITION;
    }

    public boolean isEmpty() {
        return mAdapter == null || mAdapter.isEmpty();
    }

    public int getCount() {
        if (mAdapter != null) {
            if (mHiddenPos != AdapterView.INVALID_POSITION) {
                return mAdapter.getCount() - 1;
            } else {
                return mAdapter.getCount();
            }
        } else {
            return 0;
        }
    }

    public boolean areAllItemsEnabled() {
        if (mAdapter != null) {
            return mAdapter.areAllItemsEnabled();
        } else {
            return true;
        }
    }

    private int wrappedPosition(int position) {
        if (mHiddenPos == AdapterView.INVALID_POSITION || position < mHiddenPos) {
            return position;
        } else {
            return position + 1;
        }
    }

    public boolean isEnabled(int position) {
        if (mAdapter != null) {
            return mAdapter.isEnabled(wrappedPosition(position));
        } else {
            return false;
        }
    }

    public Object getItem(int position) {
        if (mAdapter != null) {
            return mAdapter.getItem(wrappedPosition(position));
        } else {
            return null;
        }
    }

    public long getItemId(int position) {
        if (mAdapter != null) {
            return mAdapter.getItemId(wrappedPosition(position));
        }
        return -1;
    }

    public boolean hasStableIds() {
        if (mAdapter != null) {
            return mAdapter.hasStableIds();
        }
        return false;
    }

    public View getView(int position, View convertView, ViewGroup parent) {
        if (mAdapter != null) {
            return mAdapter.getView(wrappedPosition(position), convertView, parent);
        }
        return null;
    }

    public int getItemViewType(int position) {
        if (mAdapter != null) {
            return mAdapter.getItemViewType(wrappedPosition(position));
        }
        return AdapterView.ITEM_VIEW_TYPE_HEADER_OR_FOOTER;
    }

    public int getViewTypeCount() {
        if (mAdapter != null) {
            return mAdapter.getViewTypeCount();
        }
        return 1;
    }

    public void registerDataSetObserver(DataSetObserver observer) {
        super.registerDataSetObserver(observer);
        if (mAdapter != null) {
            mAdapter.registerDataSetObserver(observer);
        }
    }

    public void unregisterDataSetObserver(DataSetObserver observer) {
        super.unregisterDataSetObserver(observer);
        if (mAdapter != null) {
            mAdapter.unregisterDataSetObserver(observer);
        }
    }
    
    public ListAdapter getAdapter() {
        return mAdapter;
    }
}

