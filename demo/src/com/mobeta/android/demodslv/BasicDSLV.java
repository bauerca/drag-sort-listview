package com.mobeta.android.demodslv;

import java.util.Arrays;
import java.util.ArrayList;

import android.app.ListActivity;
import android.os.Bundle;
import android.widget.ArrayAdapter;
import com.mobeta.android.dslv.DragSortListView;
import android.widget.TextView;
import android.util.Log;


public class BasicDSLV extends ListActivity {

    private ArrayAdapter<String> adapter;

    private String[] array;
    private ArrayList<String> list;

    private DragSortListView.DropListener onDrop =
      new DragSortListView.DropListener() {
        @Override
        public void drop(int from, int to) {
          String item = adapter.getItem(from);

          adapter.remove(item);
          adapter.insert(item, to);
        }
      };

    private DragSortListView.RemoveListener onRemove = 
      new DragSortListView.RemoveListener() {
        @Override
        public void remove(int which) {
          adapter.remove(adapter.getItem(which));
        }
      };


    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        //setContentView(R.layout.main_fling);
        setContentView(R.layout.dslv_main);

        DragSortListView lv = (DragSortListView) getListView(); 
        lv.setDropListener(onDrop);
        lv.setRemoveListener(onRemove);

        array = getResources().getStringArray(R.array.jazz_artist_names);
        list = new ArrayList<String>(Arrays.asList(array));

        adapter = new ArrayAdapter<String>(this, R.layout.list_item1, R.id.text1, list);
        setListAdapter(adapter);

    }

}
