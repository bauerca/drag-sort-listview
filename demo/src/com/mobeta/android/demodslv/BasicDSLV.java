package com.mobeta.android.demodslv;

import java.util.Arrays;
import java.util.ArrayList;

import android.app.ListActivity;
import android.os.Bundle;
import android.widget.ArrayAdapter;
import com.mobeta.android.dslv.DragSortListView;


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

    protected int getLayout() {
        return R.layout.dslv_main;
    }
    
    protected int getItemLayout() {
        return R.layout.list_item1;
    }

    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        //setContentView(R.layout.main_fling);
        setContentView(getLayout());

        DragSortListView lv = (DragSortListView) getListView(); 
        lv.setDropListener(onDrop);
        lv.setRemoveListener(onRemove);

        array = getResources().getStringArray(R.array.jazz_artist_names);
        list = new ArrayList<String>(Arrays.asList(array));

        adapter = new ArrayAdapter<String>(this, getItemLayout(), R.id.text1, list);
        setListAdapter(adapter);

    }

}
