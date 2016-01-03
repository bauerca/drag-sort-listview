package com.mobeta.android.demodslv;

import java.util.Arrays;
import java.util.ArrayList;
import java.util.List;

import android.os.Bundle;
import android.content.Context;
import android.app.ListActivity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.MotionEvent;
import android.widget.BaseAdapter;
import android.widget.ListView;
import android.widget.TextView;
import android.graphics.drawable.Drawable;
import android.graphics.Point;
import android.util.Log;

import com.mobeta.android.dslv.DragSortListView;
import com.mobeta.android.dslv.DragSortController;

public class Sections extends ListActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.sections_main);

        DragSortListView dslv = (DragSortListView) getListView();

        // get jazz artist names and make adapter
        String[] array = getResources().getStringArray(R.array.jazz_artist_names);
        List<String> list = new ArrayList<String>(Arrays.asList(array));
        SectionAdapter adapter = new SectionAdapter(this, list);
        dslv.setDropListener(adapter);

        // make and set controller on dslv
        SectionController c = new SectionController(dslv, adapter);
        dslv.setFloatViewManager(c);
        dslv.setOnTouchListener(c);

        // pass it to the ListActivity
        setListAdapter(adapter);
    }

    private class SectionController extends DragSortController {

        private int mPos;
        private int mDivPos;

        private SectionAdapter mAdapter;

        DragSortListView mDslv;

        public SectionController(DragSortListView dslv, SectionAdapter adapter) {
            super(dslv, R.id.text, DragSortController.ON_DOWN, 0);
            setRemoveEnabled(false);
            mDslv = dslv;
            mAdapter = adapter;
            mDivPos = adapter.getDivPosition();
        }

        @Override
        public int startDragPosition(MotionEvent ev) {
            int res = super.dragHandleHitPosition(ev);
            if (res == mDivPos) {
                return DragSortController.MISS;
            }

            int width = mDslv.getWidth();

            if ((int) ev.getX() < width / 3) {
                return res;
            } else {
                return DragSortController.MISS;
            }
        }

        @Override
        public View onCreateFloatView(int position) {
            mPos = position;

            View v = mAdapter.getView(position, null, mDslv);
            if (position < mDivPos) {
                v.setBackgroundDrawable(getResources().getDrawable(R.drawable.bg_handle_section1));
            } else {
                v.setBackgroundDrawable(getResources().getDrawable(R.drawable.bg_handle_section2));
            }
            v.getBackground().setLevel(10000);
            return v;
        }

        private int origHeight = -1;

        @Override
        public void onDragFloatView(View floatView, Point floatPoint, Point touchPoint) {
            final int first = mDslv.getFirstVisiblePosition();
            final int lvDivHeight = mDslv.getDividerHeight();

            if (origHeight == -1) {
                origHeight = floatView.getHeight();
            }

            View div = mDslv.getChildAt(mDivPos - first);

            if (touchPoint.x > mDslv.getWidth() / 2) {
                float scale = touchPoint.x - mDslv.getWidth() / 2;
                scale /= (float) (mDslv.getWidth() / 5);
                ViewGroup.LayoutParams lp = floatView.getLayoutParams();
                lp.height = Math.max(origHeight, (int) (scale * origHeight));
                Log.d("mobeta", "setting height "+lp.height);
                floatView.setLayoutParams(lp);
            }

            if (div != null) {
                if (mPos > mDivPos) {
                    // don't allow floating View to go above
                    // section divider
                    final int limit = div.getBottom() + lvDivHeight;
                    if (floatPoint.y < limit) {
                        floatPoint.y = limit;
                    }
                } else {
                    // don't allow floating View to go below
                    // section divider
                    final int limit = div.getTop() - lvDivHeight - floatView.getHeight();
                    if (floatPoint.y > limit) {
                        floatPoint.y = limit;
                    }
                }
            }
        }

        @Override
        public void onDestroyFloatView(View floatView) {
            //do nothing; block super from crashing
        }

    }

    private class SectionAdapter extends BaseAdapter implements DragSortListView.DropListener {
        
        private final static int SECTION_DIV = 0;
        private final static int SECTION_ONE = 1;
        private final static int SECTION_TWO = 2;

        private List<String> mData;

        private int mDivPos;

        private LayoutInflater mInflater;

        public SectionAdapter(Context context, List<String> names) {
            super();
            mInflater = (LayoutInflater) context.getSystemService(LAYOUT_INFLATER_SERVICE);
            mData = names;
            mDivPos = names.size() / 2;
        }

        @Override
        public void drop(int from, int to) {
            if (from != to) {
                String data = mData.remove(dataPosition(from));
                mData.add(dataPosition(to), data);
                notifyDataSetChanged();
            }
        }

        @Override
        public int getCount() {
            return mData.size() + 1;
        }

        @Override
        public boolean areAllItemsEnabled() {
            return false;
        }

        @Override
        public boolean isEnabled(int position) {
            return position != mDivPos;
        }

        public int getDivPosition() {
            return mDivPos;
        }

        @Override
        public int getViewTypeCount() {
            return 3;
        }

        @Override
        public String getItem(int position) {
            if (position == mDivPos) {
                return "Something";
            } else {
                return mData.get(dataPosition(position));
            }
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public int getItemViewType(int position) {
            if (position == mDivPos) {
                return SECTION_DIV;
            } else if (position < mDivPos) {
                return SECTION_ONE;
            } else {
                return SECTION_TWO;
            }
        }

        private int dataPosition(int position) {
            return position > mDivPos ? position - 1 : position;
        }

        public Drawable getBGDrawable(int type) {
            Drawable d;
            if (type == SECTION_ONE) {
                d = getResources().getDrawable(R.drawable.bg_handle_section1_selector);
            } else {
                d = getResources().getDrawable(R.drawable.bg_handle_section2_selector);
            }
            d.setLevel(3000);
            return d;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            final int type = getItemViewType(position);

            View v = null;
            if (convertView != null) {
                Log.d("mobeta", "using convertView");
                v = convertView;
            } else if (type != SECTION_DIV) {
                Log.d("mobeta", "inflating normal item");
                v = mInflater.inflate(R.layout.list_item_bg_handle, parent, false);
                v.setBackgroundDrawable(getBGDrawable(type));
            } else {
                Log.d("mobeta", "inflating section divider");
                v = mInflater.inflate(R.layout.section_div, parent, false);
            }

            if (type != SECTION_DIV) {
                // bind data
                ((TextView) v).setText(mData.get(dataPosition(position)));
            }

            return v;
        }
    }
}
