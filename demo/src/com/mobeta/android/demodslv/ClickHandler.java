package com.mobeta.android.demodslv;

import java.util.ArrayList;

import com.mobeta.android.dslv.DragSortListView;

import android.app.ListActivity;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Toast;

/** Demonstrate onItemClickListener and onItemLongClickListener.
 * 
 * Uses a list item layout where the whole layout is the drag handle, to demonstrate that tapping the child takes priority.
 * Note that with the whole layout being the drag handle, you can't drag to scroll the layout.
 */
public class ClickHandler extends ListActivity {
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

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.dslv_main);

        DragSortListView lv = (DragSortListView) getListView(); 
        lv.setDropListener(onDrop);

        int num = 5;

        array = getResources().getStringArray(R.array.jazz_artist_names);
        list = new ArrayList<String>();
        for (int i = 0; i < num; ++i) {
                list.add(array[i]);
        }
        adapter = new ArrayAdapter<String>(this, R.layout.list_item_no_handle, R.id.drag, list);
        
        setListAdapter(adapter);
        
        
        lv.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> arg0, View arg1, int arg2,
                    long arg3) {
                String message = String.format("Clicked item %d", arg2);
                Toast.makeText(ClickHandler.this, message, Toast.LENGTH_SHORT).show();
                
            }
        });
        lv.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
            @Override
            public boolean onItemLongClick(AdapterView<?> arg0, View arg1, int arg2,
                    long arg3) {
                String message = String.format("Long-clicked item %d", arg2);
                Toast.makeText(ClickHandler.this, message, Toast.LENGTH_SHORT).show();
                return true;
            }
        });
    }
}
