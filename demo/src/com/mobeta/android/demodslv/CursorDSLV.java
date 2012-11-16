package com.mobeta.android.demodslv;

import java.util.List;
import java.util.ArrayList;

import com.mobeta.android.dslv.DragSortListView;
import com.mobeta.android.dslv.SimpleDragSortCursorAdapter;
import com.mobeta.android.dslv.SimpleFloatViewManager;

import android.content.Context;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.os.Bundle;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.LoaderManager.LoaderCallbacks;
import android.support.v4.content.Loader;
import android.widget.ArrayAdapter;
import android.widget.TextView;
import android.view.View;
import android.view.ViewGroup;


public class CursorDSLV extends FragmentActivity implements LoaderCallbacks<Cursor> {

    private SimpleDragSortCursorAdapter adapter;

    private SQLiteOpenHelper mOpenHelper;

    private class OpenHelper extends SQLiteOpenHelper {

        private static final int DATABASE_VERSION = 1;
        private static final String DATABASE_NAME = "jazz";
        public static final String TABLE_NAME = "jazz_artists";
        private static final String ARTIST_NAME = "name";
        private static final String TABLE_CREATE =
                "CREATE TABLE " + TABLE_NAME + " (" +
                "_id PRIMARY_KEY, " +
                ARTIST_NAME + " TEXT);";

        private Context mContext;

        OpenHelper(Context context) {
            super(context, DATABASE_NAME, null, DATABASE_VERSION);
            mContext = context;
        }

        @Override
        public void onCreate(SQLiteDatabase db) {
            db.execSQL(TABLE_CREATE);

            // get jazz names
            String[] artistNames = mContext.getResources().getStringArray(R.array.jazz_artist_names);

            ContentValues v = new ContentValues();
            int id = 0;
            for (String artist : artistNames) {
                v.put("_id", id);
                v.put(ARTIST_NAME, artist);
                db.insert(TABLE_NAME, null, v);
                v.clear();
                id++;
            }
        }

        @Override
        public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
            // do nothing
        }
    }


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

        mOpenHelper = new OpenHelper(this);
        getSupportLoaderManager().initLoader(0, null, this);
    }

    //Loader callbacks
    @Override
    public Loader<Cursor> onCreateLoader(int loaderId, Bundle args) {
        Loader<Cursor> loader = new SQLiteCursorLoader(this, mOpenHelper,
                "SELECT _id, name "
                + "FROM " + OpenHelper.TABLE_NAME
                + " ORDER BY name", null);
        return loader;
    }

    @Override
    public void onLoadFinished(Loader<Cursor> loader, Cursor cursor) {
        adapter.changeCursor(cursor);
    }

    @Override
    public void onLoaderReset(Loader<Cursor> loader) {
        adapter.changeCursor(null);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        mOpenHelper.close();
    }
}
