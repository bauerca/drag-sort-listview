package com.mobeta.android.demodslv;

import java.util.ArrayList;
import java.util.Arrays;

import android.app.ListActivity;
import android.os.Bundle;
import android.widget.ArrayAdapter;

import com.mobeta.android.dslv.DragSortListView;


public class MultipleChoiceListView extends ListActivity
{
    private ArrayAdapter<String> adapter;

    private DragSortListView.DropListener onDrop =
        new DragSortListView.DropListener() {
            @Override
            public void drop(int from, int to) {
                if (from != to) {
                    DragSortListView list = getListView();
                    String item = adapter.getItem(from);
                    adapter.remove(item);
                    adapter.insert(item, to);
                    list.moveCheckState(from, to);
                }
            }
        };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.checkable_main);
        
        String[] array = getResources().getStringArray(R.array.jazz_artist_names);
        ArrayList<String> arrayList = new ArrayList<String>(Arrays.asList(array));

        adapter = new ArrayAdapter<String>(this, R.layout.list_item_checkable, R.id.text, arrayList);
        
        setListAdapter(adapter);
        
        DragSortListView list = getListView();
        list.setDropListener(onDrop);
   }

    @Override
    public DragSortListView getListView() {
        return (DragSortListView) super.getListView();
    }
    
}
