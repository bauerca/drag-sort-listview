package com.mobeta.android.demodslv;

import java.util.List;
import java.util.ArrayList;

import com.mobeta.android.dslv.DragSortListView;
import com.mobeta.android.dslv.DragSortController;

import android.app.ListActivity;
import android.os.Bundle;
import android.widget.ArrayAdapter;
import android.widget.TextView;
import android.widget.ListView;
import android.view.View;
import android.view.ViewGroup;


public class ArbItemSizeDSLV extends ListActivity {

    private JazzAdapter adapter;

    private ArrayList<JazzArtist> mArtists;

    private String[] mArtistNames;
    private String[] mArtistAlbums;

    private DragSortListView.DropListener onDrop =
        new DragSortListView.DropListener() {
            @Override
            public void drop(int from, int to) {
                JazzArtist item = adapter.getItem(from);

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
        setContentView(R.layout.hetero_main);

        DragSortListView lv = (DragSortListView) getListView(); 

        lv.setDropListener(onDrop);
        lv.setRemoveListener(onRemove);

        mArtistNames = getResources().getStringArray(R.array.jazz_artist_names);
        mArtistAlbums = getResources().getStringArray(R.array.jazz_artist_albums);

        mArtists = new ArrayList<JazzArtist>();
        JazzArtist ja;
        for (int i = 0; i < mArtistNames.length; ++i) {
          ja = new JazzArtist();
          ja.name = mArtistNames[i];
          if (i < mArtistAlbums.length) {
            ja.albums = mArtistAlbums[i];
          } else {
            ja.albums = "No albums listed";
          }
          mArtists.add(ja);
        }

        adapter = new JazzAdapter(mArtists);
        
        setListAdapter(adapter);

    }

    private class JazzArtist {
      public String name;
      public String albums;

      @Override
      public String toString() {
        return name;
      }
    }

    private class ViewHolder {
      public TextView albumsView;
    }

    private class JazzAdapter extends ArrayAdapter<JazzArtist> {
      
      public JazzAdapter(List<JazzArtist> artists) {
        super(ArbItemSizeDSLV.this, R.layout.jazz_artist_list_item,
          R.id.artist_name_textview, artists);
      }

      public View getView(int position, View convertView, ViewGroup parent) {
        View v = super.getView(position, convertView, parent);

        if (v != convertView && v != null) {
          ViewHolder holder = new ViewHolder();

          TextView tv = (TextView) v.findViewById(R.id.artist_albums_textview);
          holder.albumsView = tv;

          v.setTag(holder);
        }

        ViewHolder holder = (ViewHolder) v.getTag();
        String albums = getItem(position).albums;

        holder.albumsView.setText(albums);

        return v;
      }
    }

}
