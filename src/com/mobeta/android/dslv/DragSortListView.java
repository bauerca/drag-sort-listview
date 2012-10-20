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
import android.database.DataSetObserver;
import android.graphics.Bitmap;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.graphics.Canvas;
import android.graphics.Point;
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
    private View mFloatView;

    /**
     * A proposed float View location based on touch location
     * and given deltaX and deltaY.
     */
    private Point mFloatLoc = new Point();

    /**
     * The middle (in the y-direction) of the floating View.
     */
    private int mFloatViewMid;

    /**
     * Left edge of floating View.
     */
    private int mFloatViewLeft;

    /**
     * Top edge of floating View.
     */
    private int mFloatViewTop;

    /**
     * Watch the Adapter for data changes. Cancel a drag if
     * coincident with a change.
     */ 
    private DataSetObserver mObserver;

    /**
     * Transparency for the floating View (XML attribute).
     */
    private float mFloatAlpha = 1.0f;
    private float mCurrFloatAlpha = 1.0f;

    /**
     * While drag-sorting, the current position of the floating
     * View. If dropped, the dragged item will land in this position.
     */
    private int mFloatPos;

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
    private int mDragDeltaX;

    /**
     * Offset (in y) within the dragged item at which the user
     * picked it up (or first touched down with the digitalis).
     */
    private int mDragDeltaY;


    /**
     * The difference (in x) between screen coordinates and coordinates
     * in this view.
     */
    private int mOffsetX;

    /**
     * The difference (in y) between screen coordinates and coordinates
     * in this view.
     */
    private int mOffsetY;

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
     * Enable/Disable item dragging
     */
    private boolean mDragEnabled = true;

    /**
     * Drag state enum.
     */
    private final static int IDLE = 0;
    private final static int STOPPED = 1;
    private final static int DRAGGING = 2;
    
    private int mDragState = IDLE;
    
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

    /**
     * Save the given width spec for use in measuring children
     */
    private int mWidthMeasureSpec = 0;

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
    private float mMaxScrollSpeed = 0.5f;
    
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
     * Current touch x.
     */
    private int mX;

    /**
     * Current touch y.
     */
    private int mY;
    
    /**
     * Last touch x.
     */
    private int mLastX;

    /**
     * Last touch y.
     */
    private int mLastY;

    /**
     * The touch y-coord at which drag started
     */
    private int mDragStartY;

    /**
     * Drag flag bit. Floating View can move in the positive
     * x direction.
     */
    public final static int DRAG_POS_X = 0x1;

    /**
     * Drag flag bit. Floating View can move in the negative
     * x direction.
     */
    public final static int DRAG_NEG_X = 0x2;

    /**
     * Drag flag bit. Floating View can move in the positive
     * y direction. This is subtle. What this actually means is
     * that, if enabled, the floating View can be dragged below its starting
     * position. Remove in favor of upper-bounding item position?
     */
    public final static int DRAG_POS_Y = 0x4;

    /**
     * Drag flag bit. Floating View can move in the negative
     * y direction. This is subtle. What this actually means is
     * that the floating View can be dragged above its starting
     * position. Remove in favor of lower-bounding item position?
     */
    public final static int DRAG_NEG_Y = 0x8;

    /**
     * Flags that determine limits on the motion of the
     * floating View. See flags above.
     */
    private int mDragFlags = 0;

    /**
     * Last call to an on*TouchEvent was a call to
     * onInterceptTouchEvent.
     */
    private boolean mLastCallWasIntercept = false;

    /**
     * A touch event is in progress.
     */
    private boolean mInTouchEvent = false;

    /**
     * Let the user customize the floating View.
     */
    private FloatViewManager mFloatViewManager = null;

    /**
     * Given to ListView to cancel its action when a drag-sort
     * begins.
     */
    private MotionEvent mCancelEvent;

    /**
     * Enum telling where to cancel the ListView action when a
     * drag-sort begins
     */
    private static final int NO_CANCEL = 0;
    private static final int ON_TOUCH_EVENT = 1;
    private static final int ON_INTERCEPT_TOUCH_EVENT = 2;

    /**
     * Where to cancel the ListView action when a
     * drag-sort begins
     */ 
    private int mCancelMethod = NO_CANCEL;

    /**
     * Determines when a slide shuffle animation starts. That is,
     * defines how close to the edge of the drop slot the floating
     * View must be to initiate the slide.
     */
    private float mSlideRegionFrac = 0.25f;

    /**
     * Number between 0 and 1 indicating the relative location of
     * a sliding item (only used if drag-sort animations
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

    /**
     * Needed for adjusting item heights from within layoutChildren
     */
    private boolean mBlockLayoutRequests = false;

    public DragSortListView(Context context, AttributeSet attrs) {
        super(context, attrs);

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

            // alpha between 0 and 255, 0=transparent, 255=opaque
            mFloatAlpha = a.getFloat(R.styleable.DragSortListView_float_alpha, mFloatAlpha);
            mCurrFloatAlpha = mFloatAlpha;

            mDragEnabled = a.getBoolean(R.styleable.DragSortListView_drag_enabled, mDragEnabled);

            mSlideRegionFrac = Math.max(0.0f,
                    Math.min(1.0f, 1.0f - a.getFloat(
                    R.styleable.DragSortListView_slide_shuffle_speed,
                    0.75f)));

            mAnimate = mSlideRegionFrac > 0.0f;

            float frac = a.getFloat(
                    R.styleable.DragSortListView_drag_scroll_start,
                    mDragUpScrollStartFrac);

            setDragScrollStart(frac);

            mMaxScrollSpeed = a.getFloat(
                    R.styleable.DragSortListView_max_drag_scroll_speed,
                    mMaxScrollSpeed);

            boolean useDefault = a.getBoolean(
                    R.styleable.DragSortListView_use_default_controller,
                    true);

            if (useDefault) {
                boolean removeEnabled = a.getBoolean(
                        R.styleable.DragSortListView_remove_enabled,
                        false);
                int removeMode = a.getInt(
                        R.styleable.DragSortListView_remove_mode,
                        DragSortController.FLING_RIGHT_REMOVE);
                boolean sortEnabled = a.getBoolean(
                        R.styleable.DragSortListView_sort_enabled,
                        false);
                int dragInitMode = a.getInt(
                        R.styleable.DragSortListView_drag_start_mode,
                        DragSortController.ON_DOWN);
                int dragHandleId = a.getResourceId(
                        R.styleable.DragSortListView_drag_handle_id,
                        0);
                
                DragSortController controller = new DragSortController(
                        this, dragHandleId, dragInitMode, removeMode);
                controller.setRemoveEnabled(removeEnabled);
                controller.setSortEnabled(sortEnabled);

                mFloatViewManager = controller;
                setOnTouchListener(controller);
            }

            a.recycle();
        }

        mDragScroller = new DragScroller();
        setOnScrollListener(mDragScroller);

        mCancelEvent = MotionEvent.obtain(0,0,MotionEvent.ACTION_CANCEL,0f,0f,0f,0f,0,0f,0f,0,0);

        // construct the dataset observer
        mObserver = new DataSetObserver() {
                    private void cancel() {
                        if (mDragState == DRAGGING) {
                            stopDrag(false);
                        }
                    }

                    @Override
                    public void onChanged() {
                        cancel();
                    }

                    @Override
                    public void onInvalidated() {
                        cancel();
                    }
                };
    }

    /**
     * Usually called from a FloatViewManager. The float alpha
     * will be reset to the xml-defined value every time a drag
     * is stopped.
     */
    public void setFloatAlpha(float alpha) {
        mCurrFloatAlpha = alpha;
    }

    public float getFloatAlpha() {
        return mCurrFloatAlpha;
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
        mAdapterWrapper = new AdapterWrapper(adapter);
        adapter.registerDataSetObserver(mObserver);
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
        
        public AdapterWrapper(ListAdapter adapter) {
            super(null, null, adapter);
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
            if (convertView != null) {

                v = (RelativeLayout) convertView;
                View oldChild = v.getChildAt(0);

                child = mAdapter.getView(position, oldChild, v);
                if (child != oldChild) {
                    // shouldn't get here if user is reusing convertViews properly
                    v.removeViewAt(0);
                    v.addView(child);
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

            // draw the float view over everything
            final int w = mFloatView.getWidth();
            final int h = mFloatView.getHeight();
            final int alpha = (int) (255f * mCurrFloatAlpha);

            canvas.save();
            //Log.d("mobeta", "clip rect bounds: " + canvas.getClipBounds());
            canvas.translate(mFloatViewLeft, mFloatViewTop);
            canvas.clipRect(0, 0, w, h);

            //Log.d("mobeta", "clip rect bounds: " + canvas.getClipBounds());
            canvas.saveLayerAlpha(0, 0, w, h, alpha, Canvas.ALL_SAVE_FLAG);
            mFloatView.draw(canvas);
            canvas.restore();
            canvas.restore();
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
                    //Log.d("mobeta", "measure child");
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
            //Log.d("mobeta", "measure item");
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

        //Log.d("mobeta", "float mid="+mFloatViewMid);

        int itemPos = startPos;
        int itemTop = startTop;
        if (mFloatViewMid < edge) {
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
                
                if (mFloatViewMid >= edge) {
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
                if (mFloatViewMid < edge) {
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
            if (mFloatViewMid < edge) {
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
            if (mFloatViewMid < slideEdgeTop) {
                mFirstExpPos = itemPos - 1;
                mSecondExpPos = itemPos;
                mSlideFrac = 0.5f * ((float) (slideEdgeTop - mFloatViewMid)) / slideRgnHeightF;
                //Log.d("mobeta", "firstExp="+mFirstExpPos+" secExp="+mSecondExpPos+" slideFrac="+mSlideFrac);
            } else if (mFloatViewMid < slideEdgeBottom) {
                mFirstExpPos = itemPos;
                mSecondExpPos = itemPos;
            } else {
                mFirstExpPos = itemPos;
                mSecondExpPos = itemPos + 1;
                mSlideFrac = 0.5f * (1.0f + ((float) (edgeBottom - mFloatViewMid)) / slideRgnHeightF);
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

    /**
     * Stop a drag in progress. Pass <code>true</code> if you would
     * like to remove the dragged item from the list.
     *
     * @param remove Remove the dragged item from the list. Calls
     * a registered DropListener, if one exists.
     *
     * @return True if the stop was successful.
     */
    public boolean stopDrag(boolean remove) {
        if (mFloatView != null) {
            mDragState = STOPPED;

            // stop the drag
            dropFloatView(remove);

            return true;
        } else {
            // stop failed
            return false;
        }
    }


    @Override
    public boolean onTouchEvent(MotionEvent ev) {

        if (!mDragEnabled) {
            return super.onTouchEvent(ev);
        }

        boolean more = false;

        boolean lastCallWasIntercept = mLastCallWasIntercept;
        mLastCallWasIntercept = false;

        if (!lastCallWasIntercept) {
            saveTouchCoords(ev);
        }

        if (mFloatView != null) {
            onDragTouchEvent(ev);
            more = true; //give us more!
        } else {
            // what if float view is null b/c we dropped in middle
            // of drag touch event?

            if (mDragState != STOPPED) {
                if (super.onTouchEvent(ev)) {
                    more = true;
                }
            }

            int action = ev.getAction() & MotionEvent.ACTION_MASK;
            switch (action) {
            case MotionEvent.ACTION_CANCEL:
            case MotionEvent.ACTION_UP:
                doActionUpOrCancel();
                break;
            default:
                if (more) {
                    mCancelMethod = ON_TOUCH_EVENT;
                }
            }
        }

        return more;

    }

    private void doActionUpOrCancel() {
        mCancelMethod = NO_CANCEL;
        mInTouchEvent = false;
        mDragState = IDLE;
        mCurrFloatAlpha = mFloatAlpha;
    }

    private void saveTouchCoords(MotionEvent ev) {
        int action = ev.getAction() & MotionEvent.ACTION_MASK;
        if (action != MotionEvent.ACTION_DOWN) {
            mLastX = mX;
            mLastY = mY;
        }
        mX = (int) ev.getX();
        mY = (int) ev.getY();
        if (action == MotionEvent.ACTION_DOWN) {
            mLastX = mX;
            mLastY = mY;
        }
        mOffsetX = (int) ev.getRawX() - mX;
        mOffsetY = (int) ev.getRawY() - mY;
    }
    
    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        if (!mDragEnabled) {
            return super.onInterceptTouchEvent(ev);
        }

        saveTouchCoords(ev);
        mLastCallWasIntercept = true;

        boolean intercept = false;

        int action = ev.getAction() & MotionEvent.ACTION_MASK;

        if (action == MotionEvent.ACTION_DOWN) {
            mInTouchEvent = true;
        }
        
        // the following deals with calls to super.onInterceptTouchEvent
        if (mFloatView != null) {
            // super's touch event canceled in startDrag
            intercept = true;
        } else {
            if (super.onInterceptTouchEvent(ev)) {
                intercept = true;
            }

            switch (action) {
            case MotionEvent.ACTION_CANCEL:
            case MotionEvent.ACTION_UP:
                doActionUpOrCancel();
                break;
            default:
                if (intercept) {
                    mCancelMethod = ON_TOUCH_EVENT;
                } else {
                    mCancelMethod = ON_INTERCEPT_TOUCH_EVENT;
                }
            }
        }

        // check for startDragging

        if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
            mInTouchEvent = false;
        }

        return intercept;
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

    private void continueDrag(int x, int y) {

        //Log.d("mobeta", "move");
        dragView(x, y);

        //if (mTrackDragSort) {
        //    mDragSortTracker.appendState();
        //}

        requestLayout();

        int minY = Math.min(y, mFloatViewMid + mFloatViewHeightHalf);
        int maxY = Math.max(y, mFloatViewMid - mFloatViewHeightHalf);

        // get the current scroll direction
        int currentScrollDir = mDragScroller.getScrollDir();

        if (minY > mLastY && minY > mDownScrollStartY && currentScrollDir != DragScroller.DOWN) {
            // dragged down, it is below the down scroll start and it is not scrolling up

            if (currentScrollDir != DragScroller.STOP) {
                // moved directly from up scroll to down scroll
                mDragScroller.stopScrolling(true);
            }

            // start scrolling down
            mDragScroller.startScrolling(DragScroller.DOWN);
        } else if (maxY < mLastY && maxY < mUpScrollStartY && currentScrollDir != DragScroller.UP) {
            // dragged up, it is above the up scroll start and it is not scrolling up

            if (currentScrollDir != DragScroller.STOP) {
                // moved directly from down scroll to up scroll
                mDragScroller.stopScrolling(true);
            }
            
            // start scrolling up
            mDragScroller.startScrolling(DragScroller.UP);
        }
        else if (maxY >= mUpScrollStartY && minY <= mDownScrollStartY && mDragScroller.isScrolling()) {
            // not in the upper nor in the lower drag-scroll regions but it is still scrolling

            mDragScroller.stopScrolling(true);
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

            //adjustAllItems();

            int firstPos = getFirstVisiblePosition();
            if (mSrcPos < firstPos) {
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
        }

        mSrcPos = -1;
        mFirstExpPos = -1;
        mSecondExpPos = -1;
        mFloatPos = -1;

        removeFloatView();

        adjustAllItems();

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

    private void measureFloatView() {
        if (mFloatView != null) {
            ViewGroup.LayoutParams lp = mFloatView.getLayoutParams();
            if (lp == null) {
                lp = new ViewGroup.LayoutParams(ViewGroup.LayoutParams.FILL_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            }
            int wspec = ViewGroup.getChildMeasureSpec(mWidthMeasureSpec, getListPaddingLeft() + getListPaddingRight(), lp.width);
            int hspec;
            if (lp.height > 0) {
                hspec = MeasureSpec.makeMeasureSpec(lp.height, MeasureSpec.EXACTLY);
            } else {
                hspec = MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED);
            }
            mFloatView.measure(wspec, hspec);
            mFloatViewHeight = mFloatView.getMeasuredHeight();
            mFloatViewHeightHalf = mFloatViewHeight / 2;
        }
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        if (mFloatView != null) {
            if (mFloatView.isLayoutRequested()) {
                measureFloatView();
            }
        }
        mWidthMeasureSpec = widthMeasureSpec;
    }
    
    @Override
    protected void layoutChildren() {

        if (mFloatView != null) {
            mFloatView.layout(0, 0, mFloatView.getMeasuredWidth(), mFloatView.getMeasuredHeight());

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

    protected boolean onDragTouchEvent(MotionEvent ev) {
        // we are in a drag
        int action = ev.getAction() & MotionEvent.ACTION_MASK;

        switch (ev.getAction() & MotionEvent.ACTION_MASK) {
        case MotionEvent.ACTION_CANCEL:
        case MotionEvent.ACTION_UP:
            stopDrag(false);
            doActionUpOrCancel();
            break;
        case MotionEvent.ACTION_MOVE:
            continueDrag((int) ev.getX(), (int) ev.getY());
            break;
        }

        return true;
    }
    
    private boolean mFloatViewInvalidated = false;

    private void invalidateFloatView() {
        mFloatViewInvalidated = true;
    }

    /**
     * Start a drag of item at <code>position</code> using the
     * registered FloatViewManager. Calls through
     * to {@link #startDrag(int,View,int,int,int)} after obtaining
     * the floating View from the FloatViewManager.
     *
     * @param position Item to drag.
     * @param dragFlags Flags that restrict some movements of the
     * floating View. For example, set <code>dragFlags |= 
     * ~{@link #DRAG_NEG_X}</code> to allow dragging the floating
     * View in all directions except off the screen to the left.
     * @param deltaX Offset in x of the touch coordinate from the
     * left edge of the floating View (i.e. touch-x minus float View
     * left).
     * @param deltaY Offset in y of the touch coordinate from the
     * top edge of the floating View (i.e. touch-y minus float View
     * top).
     *
     * @return True if the drag was started, false otherwise. This
     * <code>startDrag</code> will fail if we are not currently in
     * a touch event, there is no registered FloatViewManager,
     * or the FloatViewManager returns a null View.
     */
    public boolean startDrag(int position, int dragFlags, int deltaX, int deltaY) {
        if (!mInTouchEvent || mFloatViewManager == null) {
            return false;
        }

        View v = mFloatViewManager.onCreateFloatView(position);

        if (v == null) {
            return false;
        } else {
            return startDrag(position, v, dragFlags, deltaX, deltaY);
        }

    }

    /**
     * Start a drag of item at <code>position</code> without using
     * a FloatViewManager.
     *
     * @param position Item to drag.
     * @param floatView Floating View.
     * @param dragFlags Flags that restrict some movements of the
     * floating View. For example, set <code>dragFlags |= 
     * ~{@link #DRAG_NEG_X}</code> to allow dragging the floating
     * View in all directions except off the screen to the left.
     * @param deltaX Offset in x of the touch coordinate from the
     * left edge of the floating View (i.e. touch-x minus float View
     * left).
     * @param deltaY Offset in y of the touch coordinate from the
     * top edge of the floating View (i.e. touch-y minus float View
     * top).
     *
     * @return True if the drag was started, false otherwise. This
     * <code>startDrag</code> will fail if we are not currently in
     * a touch event, <code>floatView</code> is null, or there is
     * a drag in progress.
     */
    public boolean startDrag(int position, View floatView, int dragFlags, int deltaX, int deltaY) {
        if (!mInTouchEvent || mFloatView != null || floatView == null) {
            return false;
        }

        if (getParent() != null) {
            getParent().requestDisallowInterceptTouchEvent(true);
        }

        int pos = position + getHeaderViewsCount();
        mFirstExpPos = pos;
        mSecondExpPos = pos;
        mSrcPos = pos;
        mFloatPos = pos;

        //mDragState = dragType;
        mDragState = DRAGGING;
        mDragFlags = 0;
        mDragFlags |= dragFlags;

        mFloatView = floatView;
        measureFloatView(); //sets mFloatViewHeight

        mDragDeltaX = deltaX;
        mDragDeltaY = deltaY;
        mDragStartY = mY;

        updateFloatView(mX - mDragDeltaX, mY - mDragDeltaY);

        // set src item invisible
        final View srcItem = getChildAt(mSrcPos - getFirstVisiblePosition());
        if (srcItem != null) {
            srcItem.setVisibility(View.INVISIBLE);
        }

        if (mTrackDragSort) {
            mDragSortTracker.startTracking();
        }

        // once float view is created, events are no longer passed
        // to ListView
        switch (mCancelMethod) {
        case ON_TOUCH_EVENT:
            super.onTouchEvent(mCancelEvent);
            break;
        case ON_INTERCEPT_TOUCH_EVENT:
            super.onInterceptTouchEvent(mCancelEvent);
            break;
        }

        requestLayout();

        return true;
    }

    /**
     * Sets float View location based on suggested values and
     * constraints set in mDragFlags.
     */
    private void updateFloatView(int floatX, int floatY) {

        // restrict x motion
        int padLeft = getPaddingLeft();
        if ((mDragFlags & DRAG_POS_X) == 0 && floatX > padLeft) {
            mFloatViewLeft = padLeft;
        } else if ((mDragFlags & DRAG_NEG_X) == 0 && floatX < padLeft) {
            mFloatViewLeft = padLeft;
        } else {
            mFloatViewLeft = floatX;
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
        if ((mDragFlags & DRAG_NEG_Y) == 0) {
            if (firstPos <= mSrcPos) {
                topLimit = Math.max(getChildAt(mSrcPos - firstPos).getTop(), topLimit);
            }
        }
        // bottom limit is top of first footer View or
        // bottom of last item in list
        int bottomLimit = getHeight() - getPaddingBottom();
        if (lastPos >= getCount() - numFooters - 1) {
            bottomLimit = getChildAt(getCount() - numFooters - 1 - firstPos).getBottom();
        }
        if ((mDragFlags & DRAG_POS_Y) == 0) {
            if (lastPos >= mSrcPos) {
                bottomLimit = Math.min(getChildAt(mSrcPos - firstPos).getBottom(), bottomLimit);
            }
        }
        
        //Log.d("mobeta", "dragView top=" + (y - mDragDeltaY));
        //Log.d("mobeta", "limit=" + limit);
        //Log.d("mobeta", "mDragDeltaY=" + mDragDeltaY);
        
        if (floatY < topLimit) {
            mFloatViewTop = topLimit;
        } else if (floatY + mFloatViewHeight > bottomLimit) {
            mFloatViewTop = bottomLimit - mFloatViewHeight;
        } else {
            mFloatViewTop = floatY;
        }

        // get y-midpoint of floating view (constrained to ListView bounds)
        mFloatViewMid = mFloatViewTop + mFloatViewHeightHalf;
    }

    private void dragView(int x, int y) {
        //Log.d("mobeta", "float view pure x=" + x + " y=" + y);

        // proposed position
        mFloatLoc.x = x - mDragDeltaX;
        mFloatLoc.y = y - mDragDeltaY;

        Point touch = new Point(x, y);

        // let manager adjust proposed position first
        if (mFloatViewManager != null) {
            mFloatViewManager.onDragFloatView(mFloatView, mFloatLoc, touch);
        }

        // then we override if manager gives an unsatisfactory
        // position (e.g. over a header/footer view). Also,
        // dragFlags override manager adjustments.
        updateFloatView(mFloatLoc.x, mFloatLoc.y);
    }

    
    private void removeFloatView() {
        if (mFloatView != null) {
            mFloatView.setVisibility(GONE);
            if (mFloatViewManager != null) {
                mFloatViewManager.onDestroyFloatView(mFloatView);
            }
            mFloatView = null;
        }
    }

    /**
     * Interface for customization of the floating View appearance
     * and dragging behavior. Implement
     * your own and pass it to {@link #setFloatViewManager}. If
     * your own is not passed, the default {@link SimpleFloatViewManager}
     * implementation is used.
     */
    public interface FloatViewManager {
        /**
         * Return the floating View for item at <code>position</code>.
         * DragSortListView will measure and layout this View for you,
         * so feel free to just inflate it. You can help DSLV by
         * setting some {@link ViewGroup.LayoutParams} on this View;
         * otherwise it will set some for you (with a width of FILL_PARENT
         * and a height of WRAP_CONTENT).
         *
         * @param position Position of item to drag (NOTE:
         * <code>position</code> excludes header Views; thus, if you
         * want to call {@link ListView#getChildAt(int)}, you will need
         * to add {@link ListView#getHeaderViewsCount()} to the index).
         *
         * @return The View you wish to display as the floating View.
         */
        public View onCreateFloatView(int position);

        /**
         * Called whenever the floating View is dragged. Float View
         * properties can be changed here. Also, the upcoming location
         * of the float View can be altered by setting
         * <code>location.x</code> and <code>location.y</code>.
         *
         * @param floatView The floating View.
         * @param location The location (top-left; relative to DSLV
         * top-left) at which the float
         * View would like to appear, given the current touch location
         * and the offset provided in {@link DragSortListView#startDrag}.
         * @param touch The current touch location (relative to DSLV
         * top-left).
         */
        public void onDragFloatView(View floatView, Point location, Point touch);

        /**
         * Called when the float View is dropped; lets you perform
         * any necessary cleanup. The internal DSLV floating View
         * reference is set to null immediately after this is called.
         *
         * @param floatView The floating View passed to
         * {@link #onCreateFloatView(int)}.
         */
        public void onDestroyFloatView(View floatView);
    }

    public void setFloatViewManager(FloatViewManager manager) {
        mFloatViewManager = manager;
    }

    public void setDragListener(DragListener l) {
        mDragListener = l;
    }

    /**
     * Allows for easy toggling between a DragSortListView
     * and a regular old ListView. If enabled, items are
     * draggable, where the drag init mode determines how
     * items are lifted (see {@link setDragInitMode(int)}).
     * If disabled, items cannot be dragged.
     *
     * @param enabled Set <code>true</code> to enable list
     * item dragging
     */
    public void setDragEnabled(boolean enabled) {
        mDragEnabled = enabled;
    }

    public boolean isDragEnabled() {
        return mDragEnabled;
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

            int minY = Math.min(mY, mFloatViewMid + mFloatViewHeightHalf);
            int maxY = Math.max(mY, mFloatViewMid - mFloatViewHeightHalf);

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
                mScrollSpeed = mScrollProfile.getSpeed((mUpScrollStartYF - maxY) / mDragUpScrollHeight, mPrevTime);
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
                mScrollSpeed = -mScrollProfile.getSpeed((minY - mDownScrollStartYF) / mDragDownScrollHeight, mPrevTime);
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
                dragView(mX, mY);
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
            mBuilder.append("    <FloatY>").append(mFloatViewMid).append("</FloatY>\n");
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
