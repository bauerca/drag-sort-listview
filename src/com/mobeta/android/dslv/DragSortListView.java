/*
 * DragSortListView.
 *
 * A subclass of the Android ListView component that enables drag
 * and drop re-ordering of list items.
 *
 * Copyright 2012 Carl Bauer
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.mobeta.android.dslv;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Bitmap;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.graphics.Canvas;
import android.os.Environment;
import android.os.SystemClock;
import android.util.AttributeSet;
import android.util.Log;
import android.view.*;
import android.view.GestureDetector.SimpleOnGestureListener;
import android.widget.*;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;


/**
 * ListView subclass that mediates drag and drop resorting of items.
 * 
 * 
 * @author heycosmo
 *
 */
public class DragSortListView extends ListView {
    
    
    /**
     * The View that floats above the ListView and represents
     * the dragged item.
     */
    private ImageView mFloatView;

    /**
     * The middle (in the y-direction) of the floating View.
     */
    private int mFloatViewY;


    private int mFloatBGColor;

    /**
     * Transparency for the floating View (XML attribute).
     */
    private float mFloatAlpha;

    /**
     * While drag-sorting, the current position of the floating
     * View. If dropped, the dragged item will land in this position.
     */
    private int mFloatPos;

    /**
     * Manages the floating View.
     */
    private WindowManager mWindowManager;

    /**
     * LayoutParams for the floating View.
     */
    private WindowManager.LayoutParams mWindowParams;

    /**
     * The amount to scroll during the next layout pass. Used only
     * for drag-scrolling, not standard ListView scrolling.
     */
    private int mScrollY = 0;
    
    /**
     * The first expanded ListView position that helps represent
     * the drop slot tracking the floating View.
     */
    private int mFirstExpPos;

    /**
     * The second expanded ListView position that helps represent
     * the drop slot tracking the floating View. This can equal
     * mFirstExpPos if there is no slide shuffle occurring; otherwise
     * it is equal to mFirstExpPos + 1.
     */
    private int mSecondExpPos;

    /**
     * Flag set if slide shuffling is enabled.
     */
    private boolean mAnimate = false;

    /**
     * The user dragged from this position.
     */
    private int mSrcPos;

    /**
     * Offset (in x) within the dragged item at which the user
     * picked it up (or first touched down with the digitalis).
     */
    private int mDragPointX;

    /**
     * Offset (in y) within the dragged item at which the user
     * picked it up (or first touched down with the digitalis).
     */
    private int mDragPointY;


    /**
     * The difference (in x) between screen coordinates and coordinates
     * in this view.
     */
    private int mXOffset;

    /**
     * The difference (in y) between screen coordinates and coordinates
     * in this view.
     */
    private int mYOffset;

    /**
     * A listener that receives callbacks whenever the floating View
     * hovers over a new position.
     */
    private DragListener mDragListener;

    /**
     * A listener that receives a callback when the floating View
     * is dropped.
     */
    private DropListener mDropListener;

    /**
     * A listener that receives a callback when the floating View
     * (or more precisely the originally dragged item) is removed
     * by one of the provided gestures.
     */
    private RemoveListener mRemoveListener;

    /**
     * Used to detect a remove gesture.
     */
    private GestureDetector mGestureDetector;

    /**
     * Remove mode enum.
     */
    private static final int FLING = 0;
    private static final int SLIDE = 1;
    private static final int SLIDELEFT = 2;
    private static final int TRASH = 3;

    /**
     * The current remove mode.
     */
    private int mRemoveMode = -1;

    private Rect mTempRect = new Rect();
    private int[] mTempLoc = new int[2];
    private Bitmap mDragBitmap;
    private final int mTouchSlop;

    /**
     * Height in pixels to which the originally dragged item
     * is collapsed during a drag-sort. Currently, this value
     * must be greater than zero.
     */
    private int mItemHeightCollapsed = 1;

    /**
     * Height of the floating View. Stored for the purpose of
     * providing the tracking drop slot.
     */
    private int mFloatViewHeight;

    /**
     * Convenience member. See above.
     */
    private int mFloatViewHeightHalf;


    private Drawable mTrashcan;

    /**
     * Sample Views ultimately used for calculating the height
     * of ListView items that are off-screen.
     */
    private View[] mSampleViewTypes = new View[1];

    /**
     * Drag-scroll encapsulator!
     */
    private DragScroller mDragScroller;

    /**
     * Determines the start of the upward drag-scroll region
     * at the top of the ListView. Specified by a fraction
     * of the ListView height, thus screen resolution agnostic.
     */
    private float mDragUpScrollStartFrac = 1.0f / 3.0f;

    /**
     * Determines the start of the downward drag-scroll region
     * at the bottom of the ListView. Specified by a fraction
     * of the ListView height, thus screen resolution agnostic.
     */
    private float mDragDownScrollStartFrac = 1.0f / 3.0f;

    /**
     * The following are calculated from the above fracs.
     */
    private int mUpScrollStartY;
    private int mDownScrollStartY;
    private float mDownScrollStartYF;
    private float mUpScrollStartYF;

    /**
     * Calculated from above above and current ListView height.
     */
    private float mDragUpScrollHeight;

    /**
     * Calculated from above above and current ListView height.
     */
    private float mDragDownScrollHeight;


    /**
     * Maximum drag-scroll speed in pixels per ms. Only used with
     * default linear drag-scroll profile.
     */
    private float mMaxScrollSpeed = 0.3f;
    
    /**
     * Defines the scroll speed during a drag-scroll. User can
     * provide their own; this default is a simple linear profile
     * where scroll speed increases linearly as the floating View
     * nears the top/bottom of the ListView.
     */
    private DragScrollProfile mScrollProfile = new DragScrollProfile() {
        @Override
        public float getSpeed(float w, long t) {
            return mMaxScrollSpeed * w;
        }
    };
    
    /**
     * Last touch x.
     */
    private int mLastX;

    /**
     * Last touch y.
     */
    private int mLastY;

    /**
     * The touch y-coord that initiated the drag-sort.
     */
    private int mDownY;

    /**
     * Determines when a slide shuffle animation starts. That is,
     * defines how close to the edge of the drop slot the floating
     * View must be to initiate the slide.
     */
    private float mSlideRegionFrac = 0.25f;

    /**
     * Number between 0 and 1 indicating the location of
     * an item during a slide (only used if drag-sort animations
     * are turned on). Nearly 1 means the item is 
     * at the top of the slide region (nearly full blank item
     * is directly below).
     */
    private float mSlideFrac = 0.0f;
    
    /**
     * Wraps the user-provided ListAdapter. This is used to wrap each
     * item View given by the user inside another View (currenly
     * a RelativeLayout) which
     * expands and collapses to simulate the item shuffling.
     */
    private AdapterWrapper mAdapterWrapper;

    /**
     * Turn on custom debugger.
     */
    private boolean mTrackDragSort = false;

    /**
     * Debugging class.
     */
    private DragSortTracker mDragSortTracker;



    public DragSortListView(Context context, AttributeSet attrs) {
        super(context, attrs);
        mRemoveMode = FLING;
        mTouchSlop = ViewConfiguration.get(context).getScaledTouchSlop();

        if (attrs != null) {
            TypedArray a = getContext().obtainStyledAttributes(attrs,
                    R.styleable.DragSortListView, 0, 0);

            mItemHeightCollapsed = Math.max(1, a.getDimensionPixelSize(
                    R.styleable.DragSortListView_collapsed_height, 1));

            mTrackDragSort = a.getBoolean(
                    R.styleable.DragSortListView_track_drag_sort, false);

            if (mTrackDragSort) {
                mDragSortTracker = new DragSortTracker();
            }

            mFloatBGColor = a.getColor(R.styleable.DragSortListView_float_background_color,
                0x00000000);

            // alpha between 0 and 255, 0=transparent, 255=opaque
            mFloatAlpha = a.getFloat(R.styleable.DragSortListView_float_alpha, 1.0f);

            mSlideRegionFrac = Math.max(0.0f,
                    Math.min(1.0f, 1.0f - a.getFloat(
                    R.styleable.DragSortListView_slide_shuffle_speed,
                    0.75f)));

            mAnimate = mSlideRegionFrac > 0.0f;

            mRemoveMode = a.getInt(R.styleable.DragSortListView_remove_mode, -1);

            float frac = a.getFloat(
                    R.styleable.DragSortListView_drag_scroll_start,
                    mDragUpScrollStartFrac);

            setDragScrollStart(frac);

            mMaxScrollSpeed = a.getFloat(
                    R.styleable.DragSortListView_max_drag_scroll_speed,
                    mMaxScrollSpeed);

            a.recycle();
        }

        //Log.d("mobeta", "collapsed height=" + mItemHeightCollapsed);

        mDragScroller = new DragScroller();
        setOnScrollListener(mDragScroller);
    }
    
    /**
     * Set maximum drag scroll speed in positions/second. Only applies
     * if using default ScrollSpeedProfile.
     * 
     * @param max Maximum scroll speed.
     */
    public void setMaxScrollSpeed(float max) {
        mMaxScrollSpeed = max;
    }
    
    @Override
    public void setAdapter(ListAdapter adapter) {
        //mAdapterWrapper = new AdapterWrapper(adapter);
        mAdapterWrapper = new AdapterWrapper(null, null, adapter);
        
        super.setAdapter(mAdapterWrapper);
    }
    
    /**
     * As opposed to {@link ListView#getAdapter()}, which returns
     * a heavily wrapped ListAdapter (DragSortListView wraps the
     * input ListAdapter {\emph and} ListView wraps the wrapped one).
     *
     * @return The ListAdapter set as the argument of {@link setAdapter()}
     */
    public ListAdapter getInputAdapter() {
        if (mAdapterWrapper == null) {
            return null;
        } else {
            return mAdapterWrapper.getAdapter();
        }
    }

    
    private class AdapterWrapper extends HeaderViewListAdapter {
        private ListAdapter mAdapter;
        
        public AdapterWrapper(ArrayList<FixedViewInfo> headerViewInfos,
                ArrayList<FixedViewInfo> footerViewInfos,
                ListAdapter adapter) {
            super(headerViewInfos, footerViewInfos, adapter);
            mAdapter = adapter;
        }
        
        public ListAdapter getAdapter() {
            return mAdapter;
        }

        
        @Override
        public View getView(int position, View convertView, ViewGroup parent) {

            RelativeLayout v;
            View child;

            //Log.d("mobeta", "getView: position="+position+" convertView="+convertView);
            Log.d("mobeta", "getView: position="+position+" convertView="+convertView);
            if (convertView != null) {

                v = (RelativeLayout) convertView;
                View oldChild = v.getChildAt(0);

                child = mAdapter.getView(position, oldChild, v);
                if (child != oldChild) {
                    // shouldn't get here if user is reusing convertViews properly
                    v.removeViewAt(0);
                    v.addView(child);
                    // check that tags are equal too?
                    v.setTag(child.findViewById(R.id.drag));
                }
            } else {
                AbsListView.LayoutParams params =
                    new AbsListView.LayoutParams(
                            ViewGroup.LayoutParams.FILL_PARENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT);
                v = new RelativeLayout(getContext());
                v.setLayoutParams(params);
                child = mAdapter.getView(position, null, v);
                v.addView(child);

                v.setTag(child.findViewById(R.id.drag));
            }

            // Set the correct item height given drag state; passed
            // View needs to be measured if measurement is required.
            adjustItem(position + getHeaderViewsCount(), v, true);

            return v;
        }
        
    }

    private void drawDivider(int expPosition, Canvas canvas) {
        
        final Drawable divider = getDivider();
        final int dividerHeight = getDividerHeight();
        
        if (divider != null && dividerHeight != 0) {
            final ViewGroup expItem = (ViewGroup) getChildAt(expPosition - getFirstVisiblePosition());
            if (expItem != null) {
                final int l = getPaddingLeft();
                final int r = getWidth() - getPaddingRight();
                final int t;
                final int b;

                final int childHeight = expItem.getChildAt(0).getHeight();

                if (expPosition > mSrcPos) {
                    t = expItem.getTop() + childHeight;
                    b = t + dividerHeight;
                } else {
                    b = expItem.getBottom() - childHeight;
                    t = b - dividerHeight;
                }

                divider.setBounds(l, t, r, b);
                divider.draw(canvas);
            }
        }
    }

    @Override
    protected void dispatchDraw(Canvas canvas) {
        super.dispatchDraw(canvas);

        if (mFloatView != null) {
            // draw the divider over the expanded item
            if (mFirstExpPos != mSrcPos) {
                drawDivider(mFirstExpPos, canvas);
            }
            if (mSecondExpPos != mFirstExpPos && mSecondExpPos != mSrcPos) {
                drawDivider(mSecondExpPos, canvas);
            }
        }
    }

    private class ItemHeights {
        int item;
        int child;
    }

    private void measureItemAndGetHeights(int position, View item, ItemHeights heights) {
        ViewGroup.LayoutParams lp = item.getLayoutParams();

        boolean isHeadFoot = position < getHeaderViewsCount() || position >= getCount() - getFooterViewsCount();

        int height = lp == null ? 0 : lp.height;
        if (height > 0) {
            heights.item = height;

            // get height of child, measure if we have to
            if (isHeadFoot) {
                heights.child = heights.item;
            } else if (position == mSrcPos) {
                heights.child = 0;
            } else {
                View child = ((ViewGroup) item).getChildAt(0);
                lp = child.getLayoutParams();
                height = lp == null ? 0 : lp.height;
                if (height > 0) {
                    heights.child = height;
                } else {
                    // we have to measure child
                    int hspec = MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED);
                    int wspec = ViewGroup.getChildMeasureSpec(mWidthMeasureSpec, getListPaddingLeft() + getListPaddingRight(), lp.width);
                    Log.d("mobeta", "measure child");
                    child.measure(wspec, hspec);
                    heights.child = child.getMeasuredHeight();
                }
            }
        } else {
            // do measure on item
            int hspec = MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED);
            int wspec = ViewGroup.getChildMeasureSpec(mWidthMeasureSpec,
                    getListPaddingLeft() + getListPaddingRight(),
                    lp == null ? ViewGroup.LayoutParams.FILL_PARENT : lp.width);
            Log.d("mobeta", "measure item");
            item.measure(wspec, hspec);

            heights.item = item.getMeasuredHeight();
            // child gets measured in the process
            if (isHeadFoot) {
                heights.child = heights.item;
            } else if (position == mSrcPos) {
                heights.child = 0;
            } else {
                heights.child = ((ViewGroup) item).getChildAt(0).getMeasuredHeight();
            }
        }
    }


    /**
     * Get the height of the given wrapped item and its child.
     *
     * @param position Position from which item was obtained.
     * @param item List item (usually obtained from {@link ListView#getChildAt()}).
     * @param heights Object to fill with heights of item.
     */
    private void getItemHeights(int position, View item, ItemHeights heights) {
        boolean isHeadFoot = position < getHeaderViewsCount() || position >= getCount() - getFooterViewsCount();
        
        heights.item = item.getHeight();

        if (isHeadFoot) {
            heights.child = heights.item;
        } else if (position == mSrcPos) {
            heights.child = 0;
        } else {
            heights.child = ((ViewGroup) item).getChildAt(0).getHeight();
        }
    }


    /**
     * This function works for arbitrary positions (could be
     * off-screen). If requested position is off-screen, this
     * function calls <code>getView</code> to get height information.
     *
     * @param position ListView position.
     * @param heights Object to fill with heights of item at
     * <code>position</code>.
     */
    private void getItemHeights(int position, ItemHeights heights) {

        final int first = getFirstVisiblePosition();
        final int last = getLastVisiblePosition();

        if (position >= first && position <= last) {
            getItemHeights(position, getChildAt(position - first), heights);
        } else {
            //Log.d("mobeta", "getView for height");

            final ListAdapter adapter = getAdapter();
            int type = adapter.getItemViewType(position);
            
            // There might be a better place for checking for the following
            final int typeCount = adapter.getViewTypeCount();
            if (typeCount != mSampleViewTypes.length) {
                mSampleViewTypes = new View[typeCount];
            }

            View v;
            if (type >= 0) {
                if (mSampleViewTypes[type] == null) {
                    v = adapter.getView(position, null, this);
                    mSampleViewTypes[type] = v;
                } else {
                    v = adapter.getView(position, mSampleViewTypes[type], this);
                }
            } else {
                // type is HEADER_OR_FOOTER or IGNORE
                v = adapter.getView(position, null, this);
            }

            measureItemAndGetHeights(position, v, heights);
        }

    }

    private void printPosData() {
        Log.d("mobeta", "mSrcPos="+mSrcPos+" mFirstExpPos="+mFirstExpPos+" mSecondExpPos="+mSecondExpPos);
    }

    private int getShuffleEdge(int position, int top) {
        return getShuffleEdge(position, top, null);
    }

    /**
     * Get the shuffle edge for item at position when top of
     * item is at y-coord top
     *
     * @param position 
     * @param top
     * @param height Height of item at position. If -1, this function
     * calculates this height.
     *
     * @return Shuffle line between position-1 and position (for
     * the given view of the list; that is, for when top of item at
     * position has y-coord of given `top`). If
     * floating View (treated as horizontal line) is dropped
     * immediately above this line, it lands in position-1. If
     * dropped immediately below this line, it lands in position.
     */
    private int getShuffleEdge(int position, int top, ItemHeights heights) {

        final int numHeaders = getHeaderViewsCount();
        final int numFooters = getFooterViewsCount();

        // shuffle edges are defined between items that can be
        // dragged; there are N-1 of them if there are N draggable
        // items.

        if (position <= numHeaders || (position >= getCount() - numFooters)) {
            return top;
        }

        int divHeight = getDividerHeight();

        int edge;

        int maxBlankHeight = mFloatViewHeight - mItemHeightCollapsed;

        if (heights == null) {
            heights = new ItemHeights();
            getItemHeights(position, heights);
        }

        // first calculate top of item given that floating View is
        // centered over src position
        int otop = top;
        if (mSecondExpPos <= mSrcPos) {
            // items are expanded on and/or above the source position

            if (position == mSecondExpPos && mFirstExpPos != mSecondExpPos) {
                if (position == mSrcPos) {
                    otop = top + heights.item - mFloatViewHeight;
                } else {
                    int blankHeight = heights.item - heights.child;
                    otop = top + blankHeight - maxBlankHeight;
                }
            } else if (position > mSecondExpPos && position <= mSrcPos) {
                otop = top - maxBlankHeight;
            }

        } else {
            // items are expanded on and/or below the source position

            if (position > mSrcPos && position <= mFirstExpPos) {
                otop = top + maxBlankHeight;
            } else if (position == mSecondExpPos && mFirstExpPos != mSecondExpPos) {
                int blankHeight = heights.item - heights.child;
                otop = top + blankHeight;
            }
        }

        // otop is set
        if (position <= mSrcPos) {
            ItemHeights tmpHeights = new ItemHeights();
            getItemHeights(position - 1, tmpHeights);
            edge = otop + (mFloatViewHeight - divHeight - tmpHeights.child) / 2;
        } else {
            edge = otop + (heights.child - divHeight - mFloatViewHeight) / 2;
        }

        return edge;
    }


    private boolean updatePositions() {

        final int first = getFirstVisiblePosition();
        int startPos = mFirstExpPos;
        View startView = getChildAt(startPos - first);

        if (startView == null) {
            startPos = first + getChildCount() / 2;
            startView = getChildAt(startPos - first);
        }
        int startTop = startView.getTop() + mScrollY;

        ItemHeights itemHeights = new ItemHeights();
        getItemHeights(startPos, startView, itemHeights);

        int edge = getShuffleEdge(startPos, startTop, itemHeights);
        int lastEdge = edge;

        int divHeight = getDividerHeight();

        //Log.d("mobeta", "float mid="+mFloatViewY);

        int itemPos = startPos;
        int itemTop = startTop;
        if (mFloatViewY < edge) {
            // scanning up for float position
            //Log.d("mobeta", "    edge="+edge);
            while (itemPos >= 0) {
                itemPos--;
                getItemHeights(itemPos, itemHeights);

                //if (itemPos <= 0)
                if (itemPos == 0) {
                    edge = itemTop - divHeight - itemHeights.item;
                    //itemPos = 0;
                    break;
                }

                itemTop -= itemHeights.item + divHeight;
                edge = getShuffleEdge(itemPos, itemTop, itemHeights);
                //Log.d("mobeta", "    edge="+edge);
                
                if (mFloatViewY >= edge) {
                    break;
                }

                lastEdge = edge;
            }
        } else {
            // scanning down for float position
            //Log.d("mobeta", "    edge="+edge);
            final int count = getCount();
            while (itemPos < count) {
                if (itemPos == count - 1) {
                    edge = itemTop + divHeight + itemHeights.item;
                    break;
                }

                itemTop += divHeight + itemHeights.item;
                getItemHeights(itemPos + 1, itemHeights);
                edge = getShuffleEdge(itemPos + 1, itemTop, itemHeights);
                //Log.d("mobeta", "    edge="+edge);

                // test for hit
                if (mFloatViewY < edge) {
                    break;
                }

                lastEdge = edge;
                itemPos++;
            }
        }

        final int numHeaders = getHeaderViewsCount();
        final int numFooters = getFooterViewsCount();

        boolean updated = false;

        int oldFirstExpPos = mFirstExpPos;
        int oldSecondExpPos = mSecondExpPos;
        float oldSlideFrac = mSlideFrac;
        
        if (mAnimate) {
            int edgeToEdge = Math.abs(edge - lastEdge);

            int edgeTop, edgeBottom;
            if (mFloatViewY < edge) {
                edgeBottom = edge;
                edgeTop = lastEdge;
            } else {
                edgeTop = edge;
                edgeBottom = lastEdge;
            }
            //Log.d("mobeta", "edgeTop="+edgeTop+" edgeBot="+edgeBottom);

            int slideRgnHeight = (int) (0.5f * mSlideRegionFrac * edgeToEdge);
            float slideRgnHeightF = (float) slideRgnHeight;
            int slideEdgeTop = edgeTop + slideRgnHeight;
            int slideEdgeBottom = edgeBottom - slideRgnHeight;

            // Three regions
            if (mFloatViewY < slideEdgeTop) {
                mFirstExpPos = itemPos - 1;
                mSecondExpPos = itemPos;
                mSlideFrac = 0.5f * ((float) (slideEdgeTop - mFloatViewY)) / slideRgnHeightF;
                //Log.d("mobeta", "firstExp="+mFirstExpPos+" secExp="+mSecondExpPos+" slideFrac="+mSlideFrac);
            } else if (mFloatViewY < slideEdgeBottom) {
                mFirstExpPos = itemPos;
                mSecondExpPos = itemPos;
            } else {
                mFirstExpPos = itemPos;
                mSecondExpPos = itemPos + 1;
                mSlideFrac = 0.5f * (1.0f + ((float) (edgeBottom - mFloatViewY)) / slideRgnHeightF);
                //Log.d("mobeta", "firstExp="+mFirstExpPos+" secExp="+mSecondExpPos+" slideFrac="+mSlideFrac);
            }

        } else {
            mFirstExpPos = itemPos;
            mSecondExpPos = itemPos;
        }

        // correct for headers and footers
        if (mFirstExpPos < numHeaders) {
            itemPos = numHeaders;
            mFirstExpPos = itemPos;
            mSecondExpPos = itemPos;
        } else if (mSecondExpPos >= getCount() - numFooters) {
            itemPos = getCount() - numFooters - 1;
            mFirstExpPos = itemPos;
            mSecondExpPos = itemPos;
        }

        if (mFirstExpPos != oldFirstExpPos || mSecondExpPos != oldSecondExpPos || mSlideFrac != oldSlideFrac) {
            updated = true;
        }

        if (itemPos != mFloatPos) {
            if (mDragListener != null) {
                mDragListener.drag(mFloatPos - numHeaders, itemPos - numHeaders);
            }

            mFloatPos = itemPos;
            updated = true;
        }

        return updated;
    }
    
    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        if (mTrackDragSort) {
            mDragSortTracker.appendState();
        }
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        if (mRemoveListener != null && mGestureDetector == null) {
            if (mRemoveMode == FLING) {
                mGestureDetector = new GestureDetector(getContext(), new SimpleOnGestureListener() {
                    @Override
                    public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX,
                            float velocityY) {
                        if (mFloatView != null) {
                            if (velocityX > 1000) {
                                Rect r = mTempRect;
                                mFloatView.getDrawingRect(r);
                                if ( e2.getX() > r.right * 2 / 3) {
                                    // fast fling right with release near the right edge of the screen
                                    dropFloatView(true);
                                }
                            }
                            // flinging while dragging should have no effect
                            // i.e. the gesture should not pass on to other
                            // onTouch handlers. Gobble...
                            return true;
                        }
                        return false;
                    }
                });
            }
        }
        if (mDragListener != null || mDropListener != null) {
            switch (ev.getAction()) {
            case MotionEvent.ACTION_DOWN:
                //Log.d("mobeta", "action down!");
                int x = (int) ev.getX();
                int y = (int) ev.getY();
                mLastX = x;
                mLastY = y;
                mDownY = y;
                int itemnum = pointToPosition(x, y); //includes headers/footers
                
                final int numHeaders = getHeaderViewsCount();
                final int numFooters = getFooterViewsCount();
                
                //Log.d("mobeta", "touch down on position " + itemnum);
                if (itemnum == AdapterView.INVALID_POSITION || itemnum < numHeaders || itemnum >= getCount() - numFooters) {
                    break;
                }
                ViewGroup item = (ViewGroup) getChildAt(itemnum - getFirstVisiblePosition());
                
                mDragPointX = x - item.getLeft();
                mDragPointY = y - item.getTop();
                final int rawX = (int) ev.getRawX();
                final int rawY = (int) ev.getRawY();
                mXOffset = rawX - x;
                mYOffset = rawY - y;


                View dragBox = (View) item.getTag();
                boolean dragHit = false;
                if (dragBox != null) {
                    dragBox.getLocationOnScreen(mTempLoc);
                    
                    dragHit = rawX > mTempLoc[0] && rawY > mTempLoc[1] &&
                                        rawX < mTempLoc[0] + dragBox.getWidth() &&
                                        rawY < mTempLoc[1] + dragBox.getHeight();
                }

                if (dragHit) {
                    //item.setDrawingCacheQuality(View.DRAWING_CACHE_QUALITY_HIGH);
                    item.setDrawingCacheEnabled(true);
                    // Create a copy of the drawing cache so that it does not get recycled
                    // by the framework when the list tries to clean up memory
                    Bitmap bitmap = Bitmap.createBitmap(item.getDrawingCache());
                    item.setDrawingCacheEnabled(false);

                    mFloatViewHeight = item.getHeight();
                    mFloatViewHeightHalf = mFloatViewHeight / 2;
                    
                    mFirstExpPos = itemnum;
                    mSecondExpPos = itemnum;
                    mSrcPos = itemnum;
                    mFloatPos = itemnum;
                    
                    //Log.d("mobeta", "getCount() = " + getCount());
                    //Log.d("mobeta", "headers = " + getHeaderViewsCount());
                    
                    startDragging(bitmap, x, y);

                    // cancel ListView fling
                    MotionEvent ev2 = MotionEvent.obtain(ev);
                    ev2.setAction(MotionEvent.ACTION_CANCEL);
                    super.onInterceptTouchEvent(ev2);

                    //return false;
                    return true;
                }
                removeFloatView();
                break;
            }
        }
        return super.onInterceptTouchEvent(ev);
    }
    
    /**
     * Set the width of each drag scroll region by specifying
     * a fraction of the ListView height.
     *
     * @param heightFraction Fraction of ListView height. Capped at
     * 0.5f.
     * 
     */
    public void setDragScrollStart(float heightFraction) {
        setDragScrollStarts(heightFraction, heightFraction);
    }
    
    
    /**
     * Set the width of each drag scroll region by specifying
     * a fraction of the ListView height.
     *
     * @param upperFrac Fraction of ListView height for up-scroll bound.
     * Capped at 0.5f.
     * @param lowerFrac Fraction of ListView height for down-scroll bound.
     * Capped at 0.5f.
     * 
     */
    public void setDragScrollStarts(float upperFrac, float lowerFrac) {
        if (lowerFrac > 0.5f) {
            mDragDownScrollStartFrac = 0.5f;
        } else {
            mDragDownScrollStartFrac = lowerFrac;
        }

        if (upperFrac > 0.5f) {
            mDragUpScrollStartFrac = 0.5f;
        } else {
            mDragUpScrollStartFrac = upperFrac;
        }

        if (getHeight() != 0) {
            updateScrollStarts();
        }
    }

    private void updateScrollStarts() {
        final int padTop = getPaddingTop();
        final int listHeight = getHeight() - padTop - getPaddingBottom();
        float heightF = (float) listHeight;
        
        mUpScrollStartYF = padTop + mDragUpScrollStartFrac * heightF;
        mDownScrollStartYF = padTop + (1.0f - mDragDownScrollStartFrac) * heightF;

        mUpScrollStartY = (int) mUpScrollStartYF;
        mDownScrollStartY = (int) mDownScrollStartYF;
        Log.d("mobeta", "up start="+mUpScrollStartY);
        Log.d("mobeta", "down start="+mDownScrollStartY);
        
        mDragUpScrollHeight = mUpScrollStartYF - padTop;
        mDragDownScrollHeight = padTop + listHeight - mDownScrollStartYF;
    }
    
    
    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        updateScrollStarts();
    }



    private void dropFloatView(boolean removeSrcItem) {

        mDragScroller.stopScrolling(true);
        
        if (removeSrcItem) {
            if (mRemoveListener != null) {
                mRemoveListener.remove(mSrcPos - getHeaderViewsCount());
            }
        } else {
            if (mDropListener != null && mFloatPos >= 0 && mFloatPos < getCount()) {
                final int numHeaders = getHeaderViewsCount();
                mDropListener.drop(mSrcPos - numHeaders, mFloatPos - numHeaders);
            }

            int oldSrcPos = mSrcPos;

            mSrcPos = -1;
            mFirstExpPos = -1;
            mSecondExpPos = -1;
            mFloatPos = -1;

            //adjustAllItems();

            int firstPos = getFirstVisiblePosition();
            if (oldSrcPos < firstPos) {
                // collapsed src item is off screen;
                // adjust the scroll after item heights have been fixed
                View v = getChildAt(0);
                int top = 0;
                if (v != null) {
                    top = v.getTop();
                }
                //Log.d("mobeta", "top="+top+" fvh="+mFloatViewHeight);
                setSelectionFromTop(firstPos - 1, top - getPaddingTop());
            }

            removeFloatView();
        }

        if (mTrackDragSort) {
            mDragSortTracker.stopTracking();
        }
    }


    private void adjustAllItems() {
        final int first = getFirstVisiblePosition();
        final int last = getLastVisiblePosition();

        int begin = Math.max(0, getHeaderViewsCount() - first);
        int end = Math.min(last - first, getCount() - 1 - getFooterViewsCount() - first);

        for (int i = begin; i <= end; ++i) {
            View v = getChildAt(i);
            if (v != null) {
                adjustItem(first + i, v, false);
            }
        }

        //if (mTrackDragSort) {
        //    mDragSortTracker.appendState();
        //}
    }

    private void adjustItem(int position) {
        View v = getChildAt(position - getFirstVisiblePosition());

        if (v != null) {
            adjustItem(position, v, false);
        }
    }

    private void adjustItem(int position, View v, boolean needsMeasure) {

        // Adjust item height

        ViewGroup.LayoutParams lp = v.getLayoutParams();
        int oldHeight = lp.height;
        int height = oldHeight;

        int divHeight = getDividerHeight();

        boolean isSliding = mAnimate && mFirstExpPos != mSecondExpPos;
        int maxNonSrcBlankHeight = mFloatViewHeight - mItemHeightCollapsed;
        int slideHeight = (int) (mSlideFrac * maxNonSrcBlankHeight);

        if (position == mSrcPos) {
            if (mSrcPos == mFirstExpPos) {
                if (isSliding) {
                    height = slideHeight + mItemHeightCollapsed;
                } else {
                    height = mFloatViewHeight;
                }
            } else if (mSrcPos == mSecondExpPos) {
                // if gets here, we know an item is sliding
                height = mFloatViewHeight - slideHeight;
            } else {
                height = mItemHeightCollapsed;
            }
        } else if (position == mFirstExpPos || position == mSecondExpPos) {
            // position is not src
        
            ItemHeights itemHeights = new ItemHeights();
            if (needsMeasure) {
                measureItemAndGetHeights(position, v, itemHeights);
            } else {
                getItemHeights(position, v, itemHeights);
            }

            if (position == mFirstExpPos) {
                if (isSliding) {
                    height = itemHeights.child + slideHeight;
                } else {
                    height = itemHeights.child + maxNonSrcBlankHeight;
                }
            } else { //position=mSecondExpPos
                // we know an item is sliding (b/c 2ndPos != 1stPos)
                height = itemHeights.child + maxNonSrcBlankHeight - slideHeight;
            }
        } else {
            height = ViewGroup.LayoutParams.WRAP_CONTENT;
        }

        if (height != oldHeight) {
            lp.height = height;

            v.setLayoutParams(lp);
        }


        // Adjust item gravity

        if (position == mFirstExpPos || position == mSecondExpPos) {
            if (position < mSrcPos) {
                ((RelativeLayout) v).setGravity(Gravity.BOTTOM);
            } else if (position > mSrcPos) {
                ((RelativeLayout) v).setGravity(Gravity.TOP);
            }
        }

        
        // Finally adjust item visibility

        int oldVis = v.getVisibility();
        int vis = View.VISIBLE;

        if (position == mSrcPos && mFloatView != null) {
            vis = View.INVISIBLE;
        }

        if (vis != oldVis) {
            v.setVisibility(vis);
        }
    }

    private boolean mBlockLayoutRequests = false;

    @Override
    public void requestLayout() {
        if (!mBlockLayoutRequests) {
            super.requestLayout();
        }
    }

    private void doDragScroll(int oldFirstExpPos, int oldSecondExpPos) {
        if (mScrollY == 0) {
            return;
        }

        final int padTop = getPaddingTop();
        final int listHeight = getHeight() - padTop - getPaddingBottom();
        final int first = getFirstVisiblePosition();
        final int last = getLastVisiblePosition();

        int movePos;

        if (mScrollY >= 0) {
            mScrollY = Math.min(listHeight, mScrollY);
            movePos = first;
        } else {
            mScrollY = Math.max(-listHeight, mScrollY);
            movePos = last;
        }

        final View moveItem = getChildAt(movePos - first);
        int top = moveItem.getTop() + mScrollY;

        if (movePos == 0 && top > padTop) {
            top = padTop;
        }

        ItemHeights itemHeightsBefore = new ItemHeights();
        getItemHeights(movePos, moveItem, itemHeightsBefore);
        int moveHeightBefore = itemHeightsBefore.item;
        int moveBlankBefore = moveHeightBefore - itemHeightsBefore.child;

        ItemHeights itemHeightsAfter = new ItemHeights();
        measureItemAndGetHeights(movePos, moveItem, itemHeightsAfter);
        int moveHeightAfter = itemHeightsAfter.item;
        int moveBlankAfter = moveHeightAfter - itemHeightsAfter.child;

        if (movePos <= oldFirstExpPos) {
            if (movePos > mFirstExpPos) {
                top += mFloatViewHeight - moveBlankAfter;
            }
        } else if (movePos == oldSecondExpPos) {
            if (movePos <= mFirstExpPos) {
                top += moveBlankBefore - mFloatViewHeight;
            } else if (movePos == mSecondExpPos) {
                top += moveHeightBefore - moveHeightAfter;
            } else {
                top += moveBlankBefore;
            }
        } else {
            if (movePos <= mFirstExpPos) {
                top -= mFloatViewHeight;
            } else if (movePos == mSecondExpPos) {
                top -= moveBlankAfter;
            }
        }

        setSelectionFromTop(movePos, top - padTop);

        mScrollY = 0;
    }

    private int mWidthMeasureSpec = 0;

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);

        mWidthMeasureSpec = widthMeasureSpec;
    }
    
    @Override
    protected void layoutChildren() {

        if (mFloatView != null) {
            //Log.d("mobeta", "layout children");
            int oldFirstExpPos = mFirstExpPos;
            int oldSecondExpPos = mSecondExpPos;

            mBlockLayoutRequests = true;

            if (updatePositions()) {
                adjustAllItems();
            }

            if (mScrollY != 0) {
                doDragScroll(oldFirstExpPos, oldSecondExpPos);
            }

            mBlockLayoutRequests = false;
        }

        super.layoutChildren();
    }

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        if (mGestureDetector != null) {
            mGestureDetector.onTouchEvent(ev);
        }
        if ((mDragListener != null || mDropListener != null) && mFloatView != null) {
            int action = ev.getAction();

            final int x = (int) ev.getX();
            final int y = (int) ev.getY();

            switch (action & MotionEvent.ACTION_MASK) {
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                Rect r = mTempRect;
                mFloatView.getDrawingRect(r);
                //mDragScroller.stopScrolling(true);
                
                if (mRemoveMode == SLIDE && ev.getX() > r.right * 3 / 4) {
                    dropFloatView(true);
                } else if (mRemoveMode == SLIDELEFT && ev.getX() < r.right * 1 / 4) {
                    dropFloatView(true);
                } else {
                    dropFloatView(false);
                }
                
                break;

            case MotionEvent.ACTION_DOWN:
                //doExpansion();
                break;
            case MotionEvent.ACTION_MOVE:

                // make src item invisible on first move away from pickup
                // point. Reduces flicker.
                if (mLastY == mDownY) {
                    // should we be this careful?
                    final View item = getChildAt(mSrcPos - getFirstVisiblePosition());
                    if (item != null) {
                        item.setVisibility(INVISIBLE);
                    }
                }
                
                //Log.d("mobeta", "move");
                dragView(x, y);

                //if (mTrackDragSort) {
                //    mDragSortTracker.appendState();
                //}

                requestLayout();

                // get the current scroll direction
                int currentScrollDir = mDragScroller.getScrollDir();

                if (y > mLastY && y > mDownScrollStartY && currentScrollDir != DragScroller.DOWN) {
                    // dragged down, it is below the down scroll start and it is not scrolling up

                    if (currentScrollDir != DragScroller.STOP) {
                        // moved directly from up scroll to down scroll
                        mDragScroller.stopScrolling(true);
                    }

                    // start scrolling down
                    mDragScroller.startScrolling(DragScroller.DOWN);
                }
                else if (y < mLastY && y < mUpScrollStartY && currentScrollDir != DragScroller.UP) {
                    // dragged up, it is above the up scroll start and it is not scrolling up

                    if (currentScrollDir != DragScroller.STOP) {
                        // moved directly from down scroll to up scroll
                        mDragScroller.stopScrolling(true);
                    }
                    
                    // start scrolling up
                    mDragScroller.startScrolling(DragScroller.UP);
                }
                else if (y >= mUpScrollStartY && y <= mDownScrollStartY && mDragScroller.isScrolling()) {
                    // not in the upper nor in the lower drag-scroll regions but it is still scrolling

                    mDragScroller.stopScrolling(true);
                }
                break;
            }

            mLastX = x;
            mLastY = y;

            return true;
        }
        return super.onTouchEvent(ev);
    }

    private void startDragging(Bitmap bm, int x, int y) {
        if (getParent() != null) {
            getParent().requestDisallowInterceptTouchEvent(true);
        }

        //removeFloatView();

        mWindowParams = new WindowManager.LayoutParams();
        mWindowParams.gravity = Gravity.TOP | Gravity.LEFT;
        mWindowParams.x = x - mDragPointX + mXOffset;
        mWindowParams.y = y - mDragPointY + mYOffset;

        //Log.d("mobeta", "float view x=" + mWindowParams.x + " y=" + mWindowParams.y);

        mWindowParams.height = WindowManager.LayoutParams.WRAP_CONTENT;
        mWindowParams.width = WindowManager.LayoutParams.WRAP_CONTENT;
        mWindowParams.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
        | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        | WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
        | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS;
        mWindowParams.format = PixelFormat.TRANSLUCENT;
        mWindowParams.windowAnimations = 0;
        mWindowParams.alpha = mFloatAlpha;

        Context context = getContext();
        ImageView v = new ImageView(context);
        //int backGroundColor = context.getResources().getColor(R.color.dragndrop_background);
        v.setBackgroundColor(mFloatBGColor);
        //v.setAlpha(mFloatAlpha);
        //v.setBackgroundResource(R.drawable.playlist_tile_drag);
        v.setPadding(0, 0, 0, 0);
        v.setImageBitmap(bm);
        mDragBitmap = bm;

        mWindowManager = (WindowManager)context.getSystemService(Context.WINDOW_SERVICE);
        mWindowManager.addView(v, mWindowParams);
        mFloatView = v;

        if (mTrackDragSort) {
            mDragSortTracker.startTracking();
        }
    }

    private void dragView(int x, int y) {
        //Log.d("mobeta", "float view pure x=" + x + " y=" + y);
        if (mRemoveMode == SLIDE) {
            float alpha = mFloatAlpha;
            int width = mFloatView.getWidth();
            if (x > width / 2) {
                alpha = mFloatAlpha * (((float)(width - x)) / (width / 2));
            }
            mWindowParams.alpha = alpha;
        }
        
        if (mRemoveMode == SLIDELEFT) {
            float alpha = mFloatAlpha;
            int width = mFloatView.getWidth();
            if (x < width / 2) {
                alpha = mFloatAlpha * (((float) (x)) / (width / 2));
            }
            mWindowParams.alpha = alpha;
        }

        if (mRemoveMode == FLING || mRemoveMode == TRASH) {
            mWindowParams.x = x - mDragPointX + mXOffset;
        } else {
            mWindowParams.x = mXOffset + getPaddingLeft();
        }
        
        
        // keep floating view from going past bottom of last header view
        final int numHeaders = getHeaderViewsCount();
        final int numFooters = getFooterViewsCount();
        final int firstPos = getFirstVisiblePosition();
        final int lastPos = getLastVisiblePosition();

        //Log.d("mobeta", "nHead="+numHeaders+" nFoot="+numFooters+" first="+firstPos+" last="+lastPos);
        int topLimit = getPaddingTop();
        if (firstPos < numHeaders) {
            topLimit = getChildAt(numHeaders - firstPos - 1).getBottom();
        }
        // bottom limit is top of first footer View or
        // bottom of last item in list
        int bottomLimit = getHeight() - getPaddingBottom();
        if (lastPos >= getCount() - numFooters - 1) {
            bottomLimit = getChildAt(getCount() - numFooters - 1 - firstPos).getBottom();
        }
        
        //Log.d("mobeta", "dragView top=" + (y - mDragPointY));
        //Log.d("mobeta", "limit=" + limit);
        //Log.d("mobeta", "mDragPointY=" + mDragPointY);
        if (y - mDragPointY < topLimit) {
            mWindowParams.y = mYOffset + topLimit;
        } else if (y - mDragPointY + mFloatViewHeight > bottomLimit) {
            mWindowParams.y = mYOffset + bottomLimit - mFloatViewHeight;
        } else {
            mWindowParams.y = y - mDragPointY + mYOffset;
        }
        // get midpoint of floating view (constrained to ListView bounds)
        mFloatViewY = mWindowParams.y + mFloatViewHeightHalf - mYOffset;

        mWindowManager.updateViewLayout(mFloatView, mWindowParams);

        if (mTrashcan != null) {
            int width = mFloatView.getWidth();
            if (y > getHeight() * 3 / 4) {
                mTrashcan.setLevel(2);
            } else if (width > 0 && x > width / 4) {
                mTrashcan.setLevel(1);
            } else {
                mTrashcan.setLevel(0);
            }
        }
    }

    
    private void removeFloatView() {
        
        if (mFloatView != null) {
            mFloatView.setVisibility(GONE);
            WindowManager wm = (WindowManager)getContext().getSystemService(Context.WINDOW_SERVICE);
            wm.removeView(mFloatView);
            mFloatView.setImageDrawable(null);
            mFloatView = null;
        }
        if (mDragBitmap != null) {
            mDragBitmap.recycle();
            mDragBitmap = null;
        }
        if (mTrashcan != null) {
            mTrashcan.setLevel(0);
        }
    }

    public void setTrashcan(Drawable trash) {
        mTrashcan = trash;
        mRemoveMode = TRASH;
    }

    public void setDragListener(DragListener l) {
        mDragListener = l;
    }

    /**
     * This better reorder your ListAdapter! DragSortListView does not do this
     * for you; doesn't make sense to. Make sure
     * {@link BaseAdapter#notifyDataSetChanged()} or something like it is
     * called in your implementation.
     * 
     * @param l
     */
    public void setDropListener(DropListener l) {
        mDropListener = l;
    }

    /**
     * Probably a no-brainer, but make sure that your remove listener
     * calls {@link BaseAdapter#notifyDataSetChanged()} or something like it.
     * When an item removal occurs, DragSortListView
     * relies on a redraw of all the items to recover invisible views
     * and such. Strictly speaking, if you remove something, your dataset
     * has changed...
     * 
     * @param l
     */
    public void setRemoveListener(RemoveListener l) {
        mRemoveListener = l;
    }

    public interface DragListener {
        public void drag(int from, int to);
    }
    
    /**
     * Your implementation of this has to reorder your ListAdapter! 
     * Make sure to call
     * {@link BaseAdapter#notifyDataSetChanged()} or something like it
     * in your implementation.
     * 
     * @author heycosmo
     *
     */
    public interface DropListener {
        public void drop(int from, int to);
    }
    
    /**
     * Make sure to call
     * {@link BaseAdapter#notifyDataSetChanged()} or something like it
     * in your implementation.
     * 
     * @author heycosmo
     *
     */
    public interface RemoveListener {
        public void remove(int which);
    }

    public interface DragSortListener extends DropListener, DragListener, RemoveListener {}

    public void setDragSortListener(DragSortListener l) {
        setDropListener(l);
        setDragListener(l);
        setRemoveListener(l);
    }
    
    /**
     * Completely custom scroll speed profile. Default increases linearly
     * with position and is constant in time. Create your own by implementing
     * {@link DragSortListView.DragScrollProfile}.
     * 
     * @param ssp
     */
    public void setDragScrollProfile(DragScrollProfile ssp) {
        if (ssp != null) {
            mScrollProfile = ssp;
        }
    }
    
    /**
     * Interface for controlling
     * scroll speed as a function of touch position and time. Use
     * {@link DragSortListView#setDragScrollProfile(DragScrollProfile)} to
     * set custom profile.
     * 
     * @author heycosmo
     *
     */
    public interface DragScrollProfile {
        /**
         * Return a scroll speed in pixels/millisecond. Always return a
         * positive number.
         * 
         * @param w Normalized position in scroll region (i.e. w \in [0,1]).
         * Small w typically means slow scrolling.
         * @param t Time (in milliseconds) since start of scroll (handy if you
         * want scroll acceleration).
         * @return Scroll speed at position w and time t in pixels/ms.
         */
        float getSpeed(float w, long t);
    }

    private class DragScroller implements Runnable, AbsListView.OnScrollListener {

        private boolean mAbort;
        
        private long mPrevTime;
        
        private int dy;
        private float dt;
        private long tStart;
        private int scrollDir;

        public final static int STOP = -1;
        public final static int UP = 0;
        public final static int DOWN = 1;
        
        private float mScrollSpeed; // pixels per ms
        
        private boolean mScrolling = false;
        
        private int mLastHeader;
        private int mFirstFooter;
        
        public boolean isScrolling() {
            return mScrolling;
        }

        public int getScrollDir() {
            return mScrolling ? scrollDir : STOP;
        }

        public DragScroller() {}
        
        public void startScrolling(int dir) {
            if (!mScrolling) {
                //Debug.startMethodTracing("dslv-scroll");
                mAbort = false;
                mScrolling = true;
                tStart = SystemClock.uptimeMillis();
                mPrevTime = tStart;
                scrollDir = dir;
                post(this);
            }
        }
        
        public void stopScrolling(boolean now) {
            if (now) {
                DragSortListView.this.removeCallbacks(this);
                mScrolling = false;
            } else {
                mAbort = true;
            }

            //Debug.stopMethodTracing();
        }
        
        
        @Override
        public void run() {
            if (mAbort) {
                mScrolling = false;
                return;
            }

            //Log.d("mobeta", "scroll");

            final int first = getFirstVisiblePosition();
            final int last = getLastVisiblePosition();
            final int count = getCount();
            final int padTop = getPaddingTop();
            final int listHeight = getHeight() - padTop - getPaddingBottom();

            if (scrollDir == UP) {
                View v = getChildAt(0);
                //Log.d("mobeta", "vtop="+v.getTop()+" padtop="+padTop);
                if (v == null) {
                    mScrolling = false;
                    return;
                } else {
                    if (first == 0 && v.getTop() == padTop) {
                        mScrolling = false;
                        return;
                    }
                }
                mScrollSpeed = mScrollProfile.getSpeed((mUpScrollStartYF - mLastY) / mDragUpScrollHeight, mPrevTime);
            } else {
                View v = getChildAt(last - first);
                if (v == null) {
                    mScrolling = false;
                    return;
                } else {
                    if (last == count - 1 && v.getBottom() <= listHeight + padTop) {
                        mScrolling = false;
                        return;
                    }
                }
                mScrollSpeed = -mScrollProfile.getSpeed((mLastY - mDownScrollStartYF) / mDragDownScrollHeight, mPrevTime);
            }
            
            dt = SystemClock.uptimeMillis() - mPrevTime;
            // dy is change in View position of a list item; i.e. positive dy
            // means user is scrolling up (list item moves down the screen, remember
            // y=0 is at top of View).
            dy = (int) Math.round(mScrollSpeed * dt);
            mScrollY += dy;

            requestLayout();

            mPrevTime += dt;
            
            post(this);
        }

        @Override
        public void onScroll(AbsListView view, int firstVisibleItem, int visibleItemCount, int totalItemCount) {

            if (mScrolling && visibleItemCount != 0) {
                // Keep floating view from overlapping header and footer
                // items during scrolling
                int firstFooter = getCount() - getFooterViewsCount();
                int lastHeader = getHeaderViewsCount() - 1;

                if (firstVisibleItem <= lastHeader) {
                    int floatViewTop = mFloatViewY - mFloatViewHeightHalf;
                    int lastHeaderBottom = getChildAt(lastHeader - firstVisibleItem).getBottom();
                    if (floatViewTop < lastHeaderBottom) {
                        mWindowParams.y = mYOffset + lastHeaderBottom;
                        mFloatViewY = mWindowParams.y + mFloatViewHeightHalf - mYOffset;
                        mWindowManager.updateViewLayout(mFloatView, mWindowParams);
                    }
                } else if (firstVisibleItem + visibleItemCount > firstFooter) {
                    int floatViewBottom = mFloatViewY + mFloatViewHeightHalf;
                    int firstFooterTop = getChildAt(firstFooter - firstVisibleItem).getTop();
                    if (floatViewBottom > firstFooterTop) {
                        mWindowParams.y = mYOffset + firstFooterTop - mFloatViewHeight;
                        mFloatViewY = mWindowParams.y + mFloatViewHeightHalf - mYOffset;
                        mWindowManager.updateViewLayout(mFloatView, mWindowParams);
                    }
                }
            }
        }

        @Override
        public void onScrollStateChanged(AbsListView view, int scrollState) {}

    }

    private class DragSortTracker {
        StringBuilder mBuilder = new StringBuilder();

        File mFile;
        
        private int mNumInBuffer = 0;
        private int mNumFlushes = 0;

        private boolean mTracking = false;

        public DragSortTracker() {
            File root = Environment.getExternalStorageDirectory();
            mFile = new File(root, "dslv_state.txt");

            if (!mFile.exists()) {
                try {
                    mFile.createNewFile();
                    Log.d("mobeta", "file created");
                } catch (IOException e) {
                    Log.w("mobeta", "Could not create dslv_state.txt");
                    Log.d("mobeta", e.getMessage());
                }
            }

        }
        
        public void startTracking() {
            mBuilder.append("<DSLVStates>\n");
            mNumFlushes = 0;
            mTracking = true;
        }

        public void appendState() {
            if (!mTracking) {
                return;
            }

            mBuilder.append("<DSLVState>\n");
            final int children = getChildCount();
            final int first = getFirstVisiblePosition();
            ItemHeights itemHeights = new ItemHeights();
            mBuilder.append("    <Positions>");
            for (int i = 0; i < children; ++i) {
                mBuilder.append(first + i).append(",");
            }
            mBuilder.append("</Positions>\n");
            
            mBuilder.append("    <Tops>");
            for (int i = 0; i < children; ++i) {
                mBuilder.append(getChildAt(i).getTop()).append(",");
            }
            mBuilder.append("</Tops>\n");
            mBuilder.append("    <Bottoms>");
            for (int i = 0; i < children; ++i) {
                mBuilder.append(getChildAt(i).getBottom()).append(",");
            }
            mBuilder.append("</Bottoms>\n");

            mBuilder.append("    <FirstExpPos>").append(mFirstExpPos).append("</FirstExpPos>\n");
            getItemHeights(mFirstExpPos, itemHeights);
            mBuilder.append("    <FirstExpBlankHeight>")
                            .append(itemHeights.item - itemHeights.child)
                            .append("</FirstExpBlankHeight>\n");
            mBuilder.append("    <SecondExpPos>").append(mSecondExpPos).append("</SecondExpPos>\n");
            getItemHeights(mSecondExpPos, itemHeights);
            mBuilder.append("    <SecondExpBlankHeight>")
                            .append(itemHeights.item - itemHeights.child)
                            .append("</SecondExpBlankHeight>\n");
            mBuilder.append("    <SrcPos>").append(mSrcPos).append("</SrcPos>\n");
            mBuilder.append("    <SrcHeight>").append(mFloatViewHeight + getDividerHeight()).append("</SrcHeight>\n");
            mBuilder.append("    <ViewHeight>").append(getHeight()).append("</ViewHeight>\n");
            mBuilder.append("    <LastY>").append(mLastY).append("</LastY>\n");
            mBuilder.append("    <FloatY>").append(mFloatViewY).append("</FloatY>\n");
            mBuilder.append("    <ShuffleEdges>");
            for (int i = 0; i < children; ++i) {
                mBuilder.append(getShuffleEdge(first + i, getChildAt(i).getTop())).append(",");
            }
            mBuilder.append("</ShuffleEdges>\n");
            
            mBuilder.append("</DSLVState>\n");
            mNumInBuffer++;

            if (mNumInBuffer > 1000) {
                flush();
                mNumInBuffer = 0;
            }
        }
        
        public void flush() {
            if (!mTracking) {
                return;
            }

            // save to file on sdcard
            try {
                boolean append = true;
                if (mNumFlushes == 0) {
                    append = false;
                }
                FileWriter writer = new FileWriter(mFile, append);

                writer.write(mBuilder.toString());
                mBuilder.delete(0, mBuilder.length());

                writer.flush();
                writer.close();

                mNumFlushes++;
            } catch (IOException e) {
                // do nothing
            }
        }

        public void stopTracking() {
            if (mTracking) {
                mBuilder.append("</DSLVStates>\n");
                flush();
                mTracking = false;
            }
        }
        

    }



}
