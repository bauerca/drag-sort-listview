package com.mobeta.android.demodslv;

import com.mobeta.android.dslv.DragSortListView;
import com.mobeta.android.dslv.SimpleDragSortCursorAdapter;

import android.database.MatrixCursor;
import android.os.Bundle;
import android.support.v4.app.FragmentActivity;


public class CursorDSLV extends FragmentActivity {

    private SimpleDragSortCursorAdapter adapter;

    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.cursor_main);

        String[] cols = {"name"};
        int[] ids = {R.id.text};
        adapter = new SimpleDragSortCursorAdapter(this,
                R.layout.list_item_click_remove, null, cols, ids, 0);

        DragSortListView dslv = (DragSortListView) findViewById(android.R.id.list);
        dslv.setAdapter(adapter);

        // build a cursor from the String array
        MatrixCursor cursor = new MatrixCursor(new String[] {"_id", "name"});
        String[] artistNames = getResources().getStringArray(R.array.jazz_artist_names);
        for (int i = 0; i < artistNames.length; i++) {
            cursor.newRow()
                    .add(i)
                    .add(artistNames[i]);
        }
        adapter.changeCursor(cursor);
    }
}
