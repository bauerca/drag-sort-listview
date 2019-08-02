package com.mobeta.android.demodslv;

import android.view.View;
import android.widget.AdapterView;
import android.widget.ListView;
import android.os.Bundle;
import android.widget.Toast;

public class DSLVFragmentClicks extends DSLVFragment {

    public static DSLVFragmentClicks newInstance(int headers, int footers) {
        DSLVFragmentClicks f = new DSLVFragmentClicks();

        Bundle args = new Bundle();
        args.putInt("headers", headers);
        args.putInt("footers", footers);
        f.setArguments(args);

        return f;
    }

    AdapterView.OnItemLongClickListener mLongClickListener = 
            new AdapterView.OnItemLongClickListener() {
                @Override
                public boolean onItemLongClick(AdapterView<?> arg0, View arg1, int arg2,
                        long arg3) {
                    String message = String.format("Long-clicked item %d", arg2);
                    Toast.makeText(getActivity(), message, Toast.LENGTH_SHORT).show();
                    return true;
                }
            };

    @Override
    public void onActivityCreated(Bundle savedState) {
        super.onActivityCreated(savedState);

        ListView lv = getListView();
        lv.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> arg0, View arg1, int arg2,
                    long arg3) {
                String message = String.format("Clicked item %d", arg2);
                Toast.makeText(getActivity(), message, Toast.LENGTH_SHORT).show();
                
            }
        });
        lv.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
            @Override
            public boolean onItemLongClick(AdapterView<?> arg0, View arg1, int arg2,
                    long arg3) {
                String message = String.format("Long-clicked item %d", arg2);
                Toast.makeText(getActivity(), message, Toast.LENGTH_SHORT).show();
                return true;
            }
        });
    }
}
