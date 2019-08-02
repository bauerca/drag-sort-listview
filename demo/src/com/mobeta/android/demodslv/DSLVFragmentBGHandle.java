package com.mobeta.android.demodslv;

import java.util.List;
import java.util.ArrayList;
import java.util.Arrays;

import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;
import android.os.Bundle;
import android.widget.Toast;
import android.view.MotionEvent;
import android.util.Log;

import com.mobeta.android.dslv.DragSortListView;
import com.mobeta.android.dslv.DragSortController;

public class DSLVFragmentBGHandle extends DSLVFragment {

    @Override
    public int getItemLayout() {
        return R.layout.list_item_bg_handle;
    }

    @Override
    public void setListAdapter() {
        String[] array = getResources().getStringArray(R.array.jazz_artist_names);
        ArrayList<String> list = new ArrayList<String>(Arrays.asList(array));
        adapter = new MyAdapter(list);
        setListAdapter(adapter);
    }

    @Override
    public DragSortController buildController(DragSortListView dslv) {
        MyDSController c = new MyDSController(dslv);
        return c;
    }


    private class MyAdapter extends ArrayAdapter<String> {
      
      public MyAdapter(List<String> artists) {
        super(getActivity(), getItemLayout(), R.id.text, artists);
      }

      public View getView(int position, View convertView, ViewGroup parent) {
        View v = super.getView(position, convertView, parent);
        v.getBackground().setLevel(3000);
        return v;
      }
    }

    private class MyDSController extends DragSortController {

        DragSortListView mDslv;

        public MyDSController(DragSortListView dslv) {
            super(dslv);
            setDragHandleId(R.id.text);
            mDslv = dslv;
        }

        @Override
        public View onCreateFloatView(int position) {
            View v = adapter.getView(position, null, mDslv);
            v.getBackground().setLevel(10000);
            return v;
        }

        @Override
        public void onDestroyFloatView(View floatView) {
            //do nothing; block super from crashing
        }

        @Override
        public int startDragPosition(MotionEvent ev) {
            int res = super.dragHandleHitPosition(ev);
            int width = mDslv.getWidth();

            if ((int) ev.getX() < width / 3) {
                return res;
            } else {
                return DragSortController.MISS;
            }
        }
    }



}
