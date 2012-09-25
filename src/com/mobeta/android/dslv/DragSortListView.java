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
 *     http://www.apache.org/licenses/LICENSE-2.0
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
import android.graphics.Canvas;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.os.Environment;
import android.os.SystemClock;
import android.util.AttributeSet;
import android.util.Log;
import android.view.*;
import android.widget.*;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;


/**
 * ListView subclass that mediates drag and drop resorting of items.
 *
 * @author heycosmo
 */
public class DragSortListView extends ListView {

    // Drag states
    public final static int NO_DRAG = 0;
    public final static int SRC_EXP = 1;
    public final static int SRC_ABOVE = 2;
    public final static int SRC_BELOW = 3;

    /**
     * Interface for controlling
     * scroll speed as a function of touch position and time. Use
     * {@link com.mobeta.android.dslv.DragSortListView#setDragScrollProfile(com.mobeta.android.dslv.DragSortListView.DragScrollProfile)} to
     * set custom profile.
     *
     * @author heycosmo
     */
    public interface DragScrollProfile {
        /**
         * Return a scroll speed in pixels/millisecond. Always return a
         * positive number.
         *
         * @param relativePosition Normalized position in scroll region (i.e. w \in [0,1]).
         *          Small w typically means slow scrolling.
         * @param elapsedTime Time (in milliseconds) since start of scroll (handy if you
         *          want scroll acceleration).
         * @return Scroll speed at position w and time t in pixels/ms.
         */
        float getSpeed(float relativePosition, long elapsedTime);
    }

    public interface DragListener {
        void drag(int from, int to);
    }

    /**
     * Your implementation of this has to reorder your ListAdapter!
     * Make sure to call
     * {@link android.widget.BaseAdapter#notifyDataSetChanged()} or something like it
     * in your implementation.
     *
     * @author heycosmo
     */
    public interface DropListener {
        void drop(int from, int to);
    }

    /**
     * Make sure to call
     * {@link android.widget.BaseAdapter#notifyDataSetChanged()} or something like it
     * in your implementation.
     *
     * @author heycosmo
     */
    public interface RemoveListener {
        void remove(int which);
    }

    public DragSortListView(Context context, AttributeSet attrs) {
        super(context, attrs);
        mRemoveMode = FLING;
        mTouchSlop = ViewConfiguration.get(context).getScaledTouchSlop();

        if (attrs != null) {
            TypedArray a = getContext().obtainStyledAttributes(attrs,
                    R.styleable.DragSortListView, 0, 0);

            mItemHeightCollapsed = a.getDimensionPixelSize(
                    R.styleable.DragSortListView_collapsed_height, mItemHeightCollapsed);

            mTrackDragScroll = a.getBoolean(
                    R.styleable.DragSortListView_track_drag_scroll, false);

            mFloatBGColor = a.getColor(R.styleable.DragSortListView_float_background_color,
                    0x00000000);

            // alpha between 0 and 255, 0=transparent, 255=opaque
            mFloatAlpha = a.getFloat(R.styleable.DragSortListView_float_alpha, 1.0f);

            mRemoveMode = a.getInt(R.styleable.DragSortListView_remove_mode, -1);

            float frac = a.getFloat(R.styleable.DragSortListView_drag_scroll_start,
                    mDragUpScrollStartFrac);
            setDragScrollStart(frac);

            mMaxScrollSpeed = a.getFloat(
                    R.styleable.DragSortListView_max_drag_scroll_speed, mMaxScrollSpeed);

            a.recycle();
        }

        mDragScroller = new DragScroller();
        setOnScrollListener(mDragScroller);
    }

    @Override
    public void setAdapter(ListAdapter adapter) {
        mAdapterWrapper = new AdapterWrapper(null, null, adapter);

        super.setAdapter(mAdapterWrapper);

        dispatchTouchEvent(MotionEvent.obtain(0, 0, MotionEvent.ACTION_CANCEL, 0f, 0f, 0));
    }

    @Override
    protected void dispatchDraw(Canvas canvas) {
        super.dispatchDraw(canvas);

        if (mFloatView != null && mDragState != NO_DRAG && mDragState != SRC_EXP) {
            // draw the divider over the expanded item 
            final Drawable divider = getDivider();
            final int dividerHeight = getDividerHeight();

            if (divider != null && dividerHeight != 0) {
                final View expItem = getChildAt(mExpDragPos - getFirstVisiblePosition());
                if (expItem != null) {
                    final int left = getPaddingLeft();
                    final int right = getWidth() - getPaddingRight();
                    final int top;
                    final int bottom;
                    if (mDragState == SRC_ABOVE) {
                        bottom = expItem.getBottom() - mFloatViewHeight;
                        top = bottom - dividerHeight;
                    } else {
                        top = expItem.getTop() + mFloatViewHeight;
                        bottom = top + dividerHeight;
                    }

                    divider.setBounds(left, top, right, bottom);
                    divider.draw(canvas);
                }
            }
        } 
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent event) {
        if (mRemoveListener != null && mGestureDetector == null) {
            if (mRemoveMode == FLING) {
                mGestureDetector = new GestureDetector(getContext(), new GestureDetector.SimpleOnGestureListener() {
                    @Override
                    public boolean onFling(MotionEvent event1, MotionEvent event2, float velocityX,
                                           float velocityY) {
                        if (mFloatView != null) {
                            if (velocityX > 1000) {
                                Rect rect = mTempRect;
                                mFloatView.getDrawingRect(rect);
                                if (event2.getX() > rect.right * 2 / 3) {
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
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    int x = (int) event.getX();
                    int y = (int) event.getY();
                    mLastX = x;
                    mLastY = y;
                    mDownY = y;
                    int itemNum = pointToPosition(x, y); //includes headers/footers

                    final int numHeaders = getHeaderViewsCount();
                    final int numFooters = getFooterViewsCount();

                    if (itemNum == AdapterView.INVALID_POSITION || itemNum < numHeaders ||
                            itemNum >= getCount() - numFooters)
                        break;

                    ViewGroup item = (ViewGroup) getChildAt(itemNum - getFirstVisiblePosition());

                    mDragPointX = x - item.getLeft();
                    mDragPointY = y - item.getTop();
                    final int rawX = (int) event.getRawX();
                    final int rawY = (int) event.getRawY();
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
                        item.setDrawingCacheEnabled(true);
                        // Create a copy of the drawing cache so that it does not get recycled
                        // by the framework when the list tries to clean up memory
                        Bitmap bitmap = Bitmap.createBitmap(item.getDrawingCache());
                        item.setDrawingCacheEnabled(false);

                        mFloatViewHeight = item.getHeight();
                        mFloatViewHeightHalf = mFloatViewHeight / 2;

                        mExpDragPos = itemNum;
                        mSrcDragPos = itemNum;

                        startDragging(bitmap, x, y);

                        // cancel ListView fling
                        MotionEvent ev2 = MotionEvent.obtain(event);
                        ev2.setAction(MotionEvent.ACTION_CANCEL);
                        super.onInterceptTouchEvent(ev2);

                        return true;
                    }
                    removeFloatView();
                    break;
            }
        }
        return super.onInterceptTouchEvent(event);
    }

    @Override
    protected void onSizeChanged(int width, int height, int oldWidth, int oldHeight) {
        super.onSizeChanged(width, height, oldWidth, oldHeight);
        updateScrollStarts();
    }

    @Override
    public void addHeaderView(View view, Object data, boolean isSelectable) {
        super.addHeaderView(view, data, isSelectable);

        mHeaderHeights.add(getViewHeight(view));

        mHeadersTotalHeight += mHeaderHeights.get(mHeaderHeights.size() - 1);
    }

    @Override
    public void addFooterView(View view, Object data, boolean isSelectable) {
        super.addFooterView(view, data, isSelectable);

        mFooterHeights.add(getViewHeight(view));

        mFootersTotalHeight += mFooterHeights.get(mFooterHeights.size() - 1);
    }

    @Override
    protected void layoutChildren() {
        // we need to control calls to layoutChildren while
        // dragging to prevent things happening out of order
        if (mFloatView == null)
            super.layoutChildren();
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (mGestureDetector != null)
            mGestureDetector.onTouchEvent(event);

        if ((mDragListener != null || mDropListener != null) && mFloatView != null) {
            int action = event.getAction();

            final int x = (int) event.getX();
            final int y = (int) event.getY();

            switch (action & MotionEvent.ACTION_MASK) {
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    Rect rect = mTempRect;
                    mFloatView.getDrawingRect(rect);

                    if (mRemoveMode == SLIDE && event.getX() > rect.right * 3 / 4)
                        dropFloatView(true);
                    else if (mRemoveMode == SLIDELEFT && event.getX() < rect.right * 1 / 4)
                        dropFloatView(true);
                    else
                        dropFloatView(false);

                    break;
                case MotionEvent.ACTION_DOWN:
                    break;
                case MotionEvent.ACTION_MOVE:
                    // make src item invisible on first move away from pickup
                    // point. Reduces flicker.
                    if (mLastY == mDownY) {
                        // should we be this careful?
                        final View item = getChildAt(mSrcDragPos - getFirstVisiblePosition());
                        if (item != null)
                            item.setVisibility(INVISIBLE);
                    }

                    dragView(x, y);

                    if (!mDragScroller.isScrolling()) {
                        final int first = getFirstVisiblePosition();
                        final View startView = getChildAt(mExpDragPos - first);
                        int startPos;
                        int startTop;
                        if (startView == null) {
                            startPos = first + getChildCount() / 2;
                            startTop = getChildAt(startPos - first).getTop();
                            Log.d("DragSortListView", "startView was null");
                        } else {
                            startPos = mExpDragPos;
                            startTop = startView.getTop();
                        }

                        boolean shuffled = shuffleItems(getFloatPosition(y, startPos, startTop));

                        if (shuffled)
                            super.layoutChildren();
                    }

                    // get the current scroll direction
                    int currentScrollDir = mDragScroller.getScrollDir();

                    if (y > mLastY && y > mDownScrollStartY && currentScrollDir != DragScroller.DOWN) {
                        // dragged down, it is below the down scroll start and it is not scrolling up
                        if (currentScrollDir != DragScroller.STOP)
                            // moved directly from up scroll to down scroll
                            mDragScroller.stopScrolling(true);

                        // start scrolling down
                        mDragScroller.startScrolling(DragScroller.DOWN);
                    } else if (y < mLastY && y < mUpScrollStartY && currentScrollDir != DragScroller.UP) {
                        // dragged up, it is above the up scroll start and it is not scrolling up
                        if (currentScrollDir != DragScroller.STOP)
                            // moved directly from down scroll to up scroll
                            mDragScroller.stopScrolling(true);

                        // start scrolling up
                        mDragScroller.startScrolling(DragScroller.UP);
                    } else if (y >= mUpScrollStartY && y <= mDownScrollStartY && mDragScroller.isScrolling())
                        // not in the upper nor in the lower drag-scroll regions but it is still scrolling
                        mDragScroller.stopScrolling(true);

                    break;
            }

            mLastX = x;
            mLastY = y;

            return true;
        }
        return super.onTouchEvent(event);
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

    /**
     * As opposed to {@link android.widget.ListView#getAdapter()}, which returns
     * a heavily wrapped ListAdapter (DragSortListView wraps the
     * input ListAdapter {\emph and} ListView wraps the wrapped one).
     *
     * @return The ListAdapter set as the argument of {@link #setAdapter(android.widget.ListAdapter)}
     */
    public ListAdapter getInputAdapter() {
        return mAdapterWrapper == null? null : mAdapterWrapper.getAdapter();
    }

    /**
     * Set the width of each drag scroll region by specifying
     * a fraction of the ListView height.
     *
     * @param heightFraction Fraction of ListView height. Capped at
     *                       0.5f.
     */
    public void setDragScrollStart(float heightFraction) {
        setDragScrollStarts(heightFraction, heightFraction);
    }

    /**
     * Set the width of each drag scroll region by specifying
     * a fraction of the ListView height.
     *
     * @param upperFrac Fraction of ListView height for up-scroll bound.
     *                  Capped at 0.5f.
     * @param lowerFrac Fraction of ListView height for down-scroll bound.
     *                  Capped at 0.5f.
     */
    public void setDragScrollStarts(float upperFrac, float lowerFrac) {
        if (lowerFrac > 0.5f)
            mDragDownScrollStartFrac = 0.5f;
        else
            mDragDownScrollStartFrac = lowerFrac;

        if (upperFrac > 0.5f)
            mDragUpScrollStartFrac = 0.5f;
        else
            mDragUpScrollStartFrac = upperFrac;

        if (getHeight() != 0)
            updateScrollStarts();
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
     * {@link android.widget.BaseAdapter#notifyDataSetChanged()} or something like it is
     * called in your implementation.
     *
     * @param dropListener {@link DropListener} is expected to handle the ListAdapter reordering logic
     */
    public void setDropListener(DropListener dropListener) {
        mDropListener = dropListener;
    }

    /**
     * Probably a no-brainer, but make sure that your remove listener
     * calls {@link android.widget.BaseAdapter#notifyDataSetChanged()} or something like it.
     * When an item removal occurs, DragSortListView
     * relies on a redraw of all the items to recover invisible views
     * and such. Strictly speaking, if you remove something, your dataset
     * has changed...
     *
     * @param removeListener
     */
    public void setRemoveListener(RemoveListener removeListener) {
        mRemoveListener = removeListener;
    }

    /**
     * Completely custom scroll speed profile. Default increases linearly
     * with position and is constant in time. Create your own by implementing
     * {@link com.mobeta.android.dslv.DragSortListView.DragScrollProfile}.
     *
     * @param scrollSpeedProfile
     */
    public void setDragScrollProfile(DragScrollProfile scrollSpeedProfile) {
        if (scrollSpeedProfile != null)
            mScrollProfile = scrollSpeedProfile;
    }

    private int getItemHeight(int position) { 
        final int first = getFirstVisiblePosition();
        final int last = getLastVisiblePosition();

        if (position >= first && position <= last)
            return getChildAt(position - first).getHeight();
        else {
            final ListAdapter adapter = getAdapter();
            int type = adapter.getItemViewType(position);

            // There might be a better place for checking for the following
            final int typeCount = adapter.getViewTypeCount();
            if (typeCount != mSampleViewTypes.length)
                mSampleViewTypes = new View[typeCount];
            
            View view;
            if (type >= 0) {
                if (mSampleViewTypes[type] == null) {
                    view = adapter.getView(position, null, this);
                    mSampleViewTypes[type] = view;
                } else
                    view = adapter.getView(position, mSampleViewTypes[type], this);
            } else
                // type is HEADER_OR_FOOTER or IGNORE
                view = adapter.getView(position, null, this);
                
            ViewGroup.LayoutParams lp = view.getLayoutParams();
            final int height = lp == null ? 0 : lp.height;
            if (height > 0)
                return height;
            else {
                int spec = MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED);
                view.measure(spec, spec);
                return view.getMeasuredHeight();
            } 
        }
    }

    private int getVisualItemHeight(int position) {
        final int divHeight = getDividerHeight();

        if (position == mExpDragPos)
            return mFloatViewHeight + divHeight;

        int height;

        switch (mDragState) {
            case SRC_ABOVE:
                if (position >= mSrcDragPos && position < mExpDragPos) {
                    height = getItemHeight(position + 1);
                    if (position == mSrcDragPos)
                        height += mItemHeightCollapsed + divHeight;
                    
                    if (position == mExpDragPos - 1)
                        height -= mFloatViewHeight;
                    
                    return height + divHeight;
                }
                break;
            case SRC_BELOW:
                if (position <= mSrcDragPos && position > mExpDragPos) {
                    height = getItemHeight(position - 1);
                    if (position == mSrcDragPos)
                        height += mItemHeightCollapsed + divHeight;
                    
                    if (position == mExpDragPos + 1) 
                        height -= mFloatViewHeight;
                    
                    return height + divHeight;
                }
                break;
        }

        return getItemHeight(position) + getDividerHeight();
    }

    // position is visual position
    private int getDragEdge(int visualPosition, int visualTop) {
        if (visualPosition == 0)
            return visualTop;

        int edge;
        if (visualPosition <= mExpDragPos) {
            edge = visualTop + (mFloatViewHeight - getVisualItemHeight(visualPosition - 1)) / 2;
            if (mDragState == SRC_EXP) 
                edge -= mItemHeightCollapsed;
        } else {
            edge = visualTop + (getVisualItemHeight(visualPosition) - mFloatViewHeight) / 2;
            if (mDragState == SRC_EXP) 
                edge += mItemHeightCollapsed;
        }

        return edge;
    }

    /**
     * Get the position of the floating item for any view of the list
     * in its current drag state. Result is restricted to positions
     * between header and footer items.
     *
     * @param y        y-coord of dragging finger
     * @param position ListView position
     * @param top      y-coord of top of item at given position
     */
    private int getFloatPosition(int y, int position, int top) {
        // get midpoint of floating view (constrained to ListView bounds)
        final int floatViewMidY = Math.max(mFloatViewHeightHalf + getPaddingTop(),
                Math.min(getHeight() - getPaddingBottom() - mFloatViewHeightHalf,
                        y - mDragPointY + mFloatViewHeightHalf));

        // get closest visual item top and position
        int visItemTop;
        int visItemPos;
        final int divHeight = getDividerHeight();
        switch (mDragState) {
            case SRC_ABOVE:
                visItemTop = top;
                if (position == mSrcDragPos + 1)
                    visItemTop -= mItemHeightCollapsed + divHeight;

                if (position > mSrcDragPos && position <= mExpDragPos)
                    visItemPos = position - 1;
                else
                    visItemPos = position;
                break;
            case SRC_BELOW:
                visItemTop = top;
                if (position == mSrcDragPos) {
                    if (position == getCount() - 1) {
                        int aboveHeight = getItemHeight(position - 1);
                        if (position - 1 == mExpDragPos)
                            visItemTop -= aboveHeight - mFloatViewHeight;
                        else
                            visItemTop -= aboveHeight + divHeight;
                        
                        visItemPos = position;
                        break;
                    }
                    visItemTop += mItemHeightCollapsed + divHeight;
                }

                if (position <= mSrcDragPos && position > mExpDragPos)
                    visItemPos = position + 1;
                else
                    visItemPos = position;
                
                break;
            default:
                visItemTop = top;
                visItemPos = position;
        }

        int edge = getDragEdge(visItemPos, visItemTop);

        if (floatViewMidY < edge) {
            // scanning up for float position
            while (visItemPos >= 0) {
                visItemPos--;

                if (visItemPos <= 0) {
                    visItemPos = 0;
                    break;
                }

                visItemTop -= getVisualItemHeight(visItemPos);
                edge = getDragEdge(visItemPos, visItemTop);

                if (floatViewMidY >= edge)
                    break;
            }
        } else {
            // scanning down for float position
            final int count = getCount();
            while (visItemPos < count) {
                if (visItemPos == count - 1)
                    break;
                
                visItemTop += getVisualItemHeight(visItemPos);
                edge = getDragEdge(visItemPos + 1, visItemTop);

                // test for hit
                if (floatViewMidY < edge)
                    break;
                
                visItemPos++;
            }
        }

        final int numHeaders = getHeaderViewsCount();
        final int numFooters = getFooterViewsCount();

        if (visItemPos < numHeaders)
            return numHeaders;
        else if (visItemPos >= getCount() - numFooters)
            return getCount() - numFooters - 1;
        
        return visItemPos;
    }
    
    private void updateScrollStarts() {
        final int paddingTop = getPaddingTop();
        final int listHeight = getHeight() - paddingTop - getPaddingBottom();
        float heightF = (float) listHeight;

        mUpScrollStartYF = paddingTop + mDragUpScrollStartFrac * heightF;
        mDownScrollStartYF = paddingTop + (1.0f - mDragDownScrollStartFrac) * heightF;

        mUpScrollStartY = (int) mUpScrollStartYF;
        mDownScrollStartY = (int) mDownScrollStartYF;
        Log.d("DragSortListView", "up start=" + mUpScrollStartY);
        Log.d("DragSortListView", "down start=" + mDownScrollStartY);

        mDragUpScrollHeight = mUpScrollStartYF - paddingTop;
        mDragDownScrollHeight = paddingTop + listHeight - mDownScrollStartYF;
    }

    private int getViewHeight(View view) {
        // measure to get height of header item
        ViewGroup.LayoutParams lp = view.getLayoutParams();
        final int height = lp == null ? 0 : lp.height;
        if (height > 0)
            return height;
        else {
            int spec = MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED);
            view.measure(spec, spec);
            return view.getMeasuredHeight();
        }

    }

    private void dropFloatView(boolean removeSrcItem) {

        mDragScroller.stopScrolling(true);

        if (removeSrcItem) {
            if (mRemoveListener != null)
                mRemoveListener.remove(mSrcDragPos - getHeaderViewsCount());
        } else {
            if (mDropListener != null && mExpDragPos >= 0 && mExpDragPos < getCount()) {
                final int numHeaders = getHeaderViewsCount();
                mDropListener.drop(mSrcDragPos - numHeaders, mExpDragPos - numHeaders);
            }

            int firstPos = getFirstVisiblePosition();

            View expView = getChildAt(mExpDragPos - firstPos);
            if (expView != null) {
                ViewGroup.LayoutParams lp = expView.getLayoutParams();
                lp.height = ViewGroup.LayoutParams.WRAP_CONTENT;
                expView.requestLayout();
            }

            if (mSrcDragPos < firstPos) {
                // collapsed src item is off screen, no need to expand it; but, we
                // must adjust the scroll accordingly
                View view = getChildAt(0);
                int top = 0;
                if (view != null)
                    top = view.getTop();

                setSelectionFromTop(firstPos - 1, top - getPaddingTop());
            } else if (mSrcDragPos <= getLastVisiblePosition()) {
                // collapsed src item is in view, expand it
                View srcView = getChildAt(mSrcDragPos - firstPos);
                ViewGroup.LayoutParams lp = srcView.getLayoutParams();
                lp.height = ViewGroup.LayoutParams.WRAP_CONTENT;
                srcView.requestLayout();

                srcView.setVisibility(View.VISIBLE);
            }
        }

        removeFloatView();

        mDragState = NO_DRAG;
    }

    /**
     * Call this when list items are shuffled while dragging.
     */
    private void updateListState() {
        if (mFloatView == null)
            mDragState = NO_DRAG;
        else if (mExpDragPos == mSrcDragPos)
            mDragState = SRC_EXP;
        else if (mSrcDragPos < mExpDragPos)
            mDragState = SRC_ABOVE;
        else
            mDragState = SRC_BELOW;
    }

    /**
     * Smart item expansion.
     */
    private void expandItem(int position) {
        final int first = getFirstVisiblePosition();
        RelativeLayout view = (RelativeLayout) getChildAt(position - first);
        if (view != null && mFloatView != null) {
            ViewGroup.LayoutParams lp = view.getLayoutParams();

            int oldHeight = lp.height;
            if (lp.height == mItemHeightCollapsed && position == mSrcDragPos)
                // expanding collapsed src item
                lp.height = ViewGroup.LayoutParams.WRAP_CONTENT;
            else if (lp.height == ViewGroup.LayoutParams.WRAP_CONTENT && position != mExpDragPos) {
                // expanding normal item
                lp.height = view.getHeight() + mFloatViewHeight + getDividerHeight();

                // must set gravity in this case
                if (position > mSrcDragPos)
                    view.setGravity(Gravity.TOP);
                else
                    view.setGravity(Gravity.BOTTOM);

                // what if expanding first position?
            } else
                Log.d("DragSortListView", "expand item skipped");

            if (lp.height != oldHeight)
                view.requestLayout();
        }

    }

    /**
     * Smart item collapstion. Usually (Always?)
     * called with mExpDragPos as arg.
     */
    private void collapseItem(int position) {
        View view = getChildAt(position - getFirstVisiblePosition());
        if (view != null) {
            ViewGroup.LayoutParams lp = view.getLayoutParams();

            // collapsing normal item
            int oldHeight = lp.height;
            if (position == mSrcDragPos)
                // collapsing source item
                lp.height = mItemHeightCollapsed;
            else if (position == mExpDragPos)
                // to save time, assume collapsing an expanded item
                lp.height = ViewGroup.LayoutParams.WRAP_CONTENT;
            else
                Log.d("DragSortListView", "collapse ignored, pos=" + position);

            if (lp.height != oldHeight)
                view.requestLayout();
        }

    }

    /**
     * Shuffle list items given new float position.
     *
     * @param floatPos
     */
    private boolean shuffleItems(int floatPos) {
        if (floatPos != mExpDragPos) {
            // if we get here, the ListView is inconsistent with the
            // floating view

            collapseItem(mExpDragPos);
            expandItem(floatPos);

            // callback
            if (mDragListener != null) {
                final int numHeaders = getHeaderViewsCount();
                mDragListener.drag(mExpDragPos - numHeaders, floatPos - numHeaders);
            }

            // update state
            mExpDragPos = floatPos;
            updateListState();

            return true;
        }

        return false;
    }

    private void startDragging(Bitmap bm, int x, int y) {
        if (getParent() != null)
            getParent().requestDisallowInterceptTouchEvent(true);

        mWindowParams = new WindowManager.LayoutParams();
        mWindowParams.gravity = Gravity.TOP | Gravity.LEFT;
        mWindowParams.x = x - mDragPointX + mXOffset;
        mWindowParams.y = y - mDragPointY + mYOffset;

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
        ImageView view = new ImageView(context);
        view.setBackgroundColor(mFloatBGColor);
        view.setPadding(0, 0, 0, 0);
        view.setImageBitmap(bm);
        mDragBitmap = bm;

        mWindowManager = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
        mWindowManager.addView(view, mWindowParams);
        mFloatView = view;

        mDragState = SRC_EXP;
    }

    private void dragView(int x, int y) {
        if (mRemoveMode == SLIDE) {
            float alpha = mFloatAlpha;
            int width = mFloatView.getWidth();
            if (x > width / 2)
                alpha = mFloatAlpha * (((float) (width - x)) / (width / 2));
            
            mWindowParams.alpha = alpha;
        }

        if (mRemoveMode == SLIDELEFT) {
            float alpha = mFloatAlpha;
            int width = mFloatView.getWidth();
            if (x < width / 2)
                alpha = mFloatAlpha * (((float) (x)) / (width / 2));
            
            mWindowParams.alpha = alpha;
        }

        if (mRemoveMode == FLING || mRemoveMode == TRASH)
            mWindowParams.x = x - mDragPointX + mXOffset;
        else
            mWindowParams.x = mXOffset + getPaddingLeft();
        
        // keep floating view from going past bottom of last header view
        final int numHeaders = getHeaderViewsCount();
        final int numFooters = getFooterViewsCount();
        final int firstPos = getFirstVisiblePosition();
        final int lastPos = getLastVisiblePosition();
        int limit = getPaddingTop();
        if (firstPos < numHeaders)
            limit = getChildAt(numHeaders - firstPos - 1).getBottom();
        
        int footerLimit = getHeight() - getPaddingBottom();
        if (lastPos >= getCount() - numFooters)
            // get top-most footer view
            footerLimit = getChildAt(getCount() - numFooters - firstPos).getTop();

        if (y - mDragPointY < limit) 
            mWindowParams.y = mYOffset + limit;
        else if (y - mDragPointY + mFloatViewHeight > footerLimit) 
            mWindowParams.y = mYOffset + footerLimit - mFloatViewHeight;
        else 
            mWindowParams.y = y - mDragPointY + mYOffset;
        
        mWindowManager.updateViewLayout(mFloatView, mWindowParams);

        if (mTrashcan != null) {
            int width = mFloatView.getWidth();
            if (y > getHeight() * 3 / 4)
                mTrashcan.setLevel(2);
            else if (width > 0 && x > width / 4)
                mTrashcan.setLevel(1);
            else 
                mTrashcan.setLevel(0);
        }
    }

    private void removeFloatView() {
        if (mFloatView != null) {
            mFloatView.setVisibility(GONE);
            WindowManager wm = (WindowManager) getContext().getSystemService(Context.WINDOW_SERVICE);
            wm.removeView(mFloatView);
            mFloatView.setImageDrawable(null);
            mFloatView = null;
        }
        if (mDragBitmap != null) {
            mDragBitmap.recycle();
            mDragBitmap = null;
        }
        if (mTrashcan != null)
            mTrashcan.setLevel(0);
    }

    private int mDragState = NO_DRAG;

    private ImageView mFloatView;
    private int mFloatBGColor;
    private WindowManager mWindowManager;
    private WindowManager.LayoutParams mWindowParams;

    /**
     * Top of ListView in absolute (full, virtual list) y-coords.
     */
    private int mSrcTop;
    private int mSrcBottom;
    private int mExpTop;
    private int mExpBottom;

    /**
     * At which position is the item currently being dragged. All drag positions
     * are absolute list view positions; e.g. if there is one header view, and
     * mDragPos = 1, then mDragPos points to the first list item after the header.
     */
    private int mExpDragPos;
    private float mFloatAlpha;
    /**
     * At which position was the item being dragged originally
     */
    private int mSrcDragPos;
    private int mDragPointX;    // at what x offset inside the item did the user grab it
    private int mDragPointY;    // at what y offset inside the item did the user grab it
    private int mXOffset;  // the difference between screen coordinates and coordinates in this view
    private int mYOffset;  // the difference between screen coordinates and coordinates in this view
    private DragListener mDragListener;
    private DropListener mDropListener;
    private RemoveListener mRemoveListener;
    private int mUpScrollStartY;
    private int mDownScrollStartY;
    private float mDownScrollStartYF;
    private float mUpScrollStartYF;
    private GestureDetector mGestureDetector;
    private static final int FLING = 0;
    private static final int SLIDE = 1;
    private static final int SLIDELEFT = 2;
    private static final int TRASH = 3;
    private int mRemoveMode = -1;
    private Rect mTempRect = new Rect();
    private int[] mTempLoc = new int[2];
    private Bitmap mDragBitmap;
    private final int mTouchSlop;
    private int mItemHeightCollapsed = 1;
    private int mExpandedChildHeight; //state var
    private int mFloatViewHeight;
    private int mFloatViewHeightHalf;
    private Drawable mTrashcan;

    private View[] mSampleViewTypes = new View[1];

    private ArrayList<Integer> mHeaderHeights = new ArrayList<Integer>();
    private ArrayList<Integer> mFooterHeights = new ArrayList<Integer>();
    private int mHeadersTotalHeight = 0;
    private int mFootersTotalHeight = 0;

    private DragScroller mDragScroller;
    private float mDragUpScrollStartFrac = 1.0f / 3.0f;
    private float mDragDownScrollStartFrac = 1.0f / 3.0f;
    private float mDragUpScrollHeight;
    private float mDragDownScrollHeight;

    private float mMaxScrollSpeed = 0.3f; // pixels per millisec

    private boolean mTrackDragScroll = false;

    private DragScrollProfile mScrollProfile = new DragScrollProfile() {
        @Override
        public float getSpeed(float relativePosition, long elapsedTime) {
            return mMaxScrollSpeed * relativePosition;
        }
    };

    private int mLastX;
    private int mLastY;
    private int mDownY;

    private AdapterWrapper mAdapterWrapper;

    private class AdapterWrapper extends HeaderViewListAdapter {
        private ListAdapter mAdapter;

        public AdapterWrapper(ArrayList<FixedViewInfo> headerViewInfos,
                              ArrayList<FixedViewInfo> footerViewInfos, ListAdapter adapter) {
            super(headerViewInfos, footerViewInfos, adapter);
            mAdapter = adapter;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            RelativeLayout view;
            View child;

            if (convertView != null) {
                view = (RelativeLayout) convertView;
                View oldChild = view.getChildAt(0);

                child = mAdapter.getView(position, oldChild, view);
                if (child != oldChild) {
                    // shouldn't get here if user is reusing convertViews properly
                    view.removeViewAt(0);
                    view.addView(child);
                    // check that tags are equal too?
                    view.setTag(child.findViewById(R.id.drag));
                }
            } else {
                LayoutParams params =
                        new LayoutParams(ViewGroup.LayoutParams.FILL_PARENT,
                                ViewGroup.LayoutParams.WRAP_CONTENT);
                view = new RelativeLayout(getContext());
                view.setLayoutParams(params);
                child = mAdapter.getView(position, null, view);
                view.addView(child);

                view.setTag(child.findViewById(R.id.drag));
            }

            ViewGroup.LayoutParams lp = view.getLayoutParams();
            final int numHeaders = getHeaderViewsCount();

            final int srcAdapter = mSrcDragPos - numHeaders;
            final int expAdapter = mExpDragPos - numHeaders;

            boolean itemIsNormal = position != srcAdapter && position != expAdapter;
            boolean listHasExpPos = mDragState == SRC_ABOVE || mDragState == SRC_BELOW;
            boolean itemNeedsWC = itemIsNormal || !listHasExpPos;

            int oldHeight = lp.height;
            if (itemNeedsWC && lp.height != ViewGroup.LayoutParams.WRAP_CONTENT)
                // items that have a user-provided height
                lp.height = ViewGroup.LayoutParams.WRAP_CONTENT;
            else if (listHasExpPos) {
                if (position == srcAdapter && lp.height != mItemHeightCollapsed)
                    // collapsed items
                    lp.height = mItemHeightCollapsed;
                else if (position == expAdapter) {
                    // what if a previously-expanded wrapper view is used
                    // as a convertView for a different expanded item? 
                    // Always measure child
                    int spec = MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED);
                    child.measure(spec, spec);

                    mExpandedChildHeight = child.getMeasuredHeight();
                    int height = mExpandedChildHeight + mFloatViewHeight + getDividerHeight();
                    if (lp.height != height)
                        lp.height = height;

                    // set gravity
                    if (mDragState == SRC_ABOVE)
                        view.setGravity(Gravity.TOP);
                    else
                        view.setGravity(Gravity.BOTTOM);
                }
            }

            if (lp.height != oldHeight)
                view.setLayoutParams(lp);

            int oldViewVisibility = view.getVisibility();
            int newViewVisibility = oldViewVisibility;

            if (position == srcAdapter && mDragState != NO_DRAG) {
                if (newViewVisibility == View.VISIBLE)
                    newViewVisibility = View.INVISIBLE;
            } else if (newViewVisibility == View.INVISIBLE)
                newViewVisibility = View.VISIBLE;


            if (newViewVisibility != oldViewVisibility)
                view.setVisibility(newViewVisibility);

            return view;
        }

        public ListAdapter getAdapter() {
            return mAdapter;
        }
    }

    private class DragScroller implements Runnable, OnScrollListener {
        public final static int STOP = -1;
        public final static int UP = 0;
        public final static int DOWN = 1;

        public DragScroller() {
            if (mTrackDragScroll) {
                Log.d("DragSortListView", "state tracker created");
                mStateTracker = new StateTracker();
            }
        }

        @Override
        public void run() {
            if (mAbort) {
                mScrolling = false;
                return;
            }

            if (mTrackDragScroll) {
                mStateTracker.appendState();
            }

            if (scrollDir == UP)
                mScrollSpeed = mScrollProfile.getSpeed((mUpScrollStartYF - mLastY) / mDragUpScrollHeight, mPrevTime);
            else
                mScrollSpeed = -mScrollProfile.getSpeed((mLastY - mDownScrollStartYF) / mDragDownScrollHeight,
                        mPrevTime);

            dt = SystemClock.uptimeMillis() - mPrevTime;
            // dy is change in View position of a list item; i.e. positive dy
            // means user is scrolling up (list item moves down the screen, remember
            // y=0 is at top of View).
            dy = Math.round(mScrollSpeed * dt);

            // don't bail if dy=0, touch might be at edge of scroll region; but,
            // don't do any work 
            if (dy == 0) {
                mPrevTime += dt;
                post(this);
                return;
            }

            final int first = getFirstVisiblePosition();
            final int last = getLastVisiblePosition();

            final int count = getCount();

            final int padTop = getPaddingTop();
            final int listHeight = getHeight() - padTop - getPaddingBottom();

            int movePosition;
            if (dy > 0) {
                View v = getChildAt(0);
                if ((v == null) || (first == 0 && v.getTop() == padTop)) {
                    mScrolling = false;
                    return;
                }
                movePosition = first;
                dy = Math.min(listHeight, dy);
            } else {
                if (last == count - 1 &&
                        getChildAt(last - first).getBottom() <= listHeight + padTop) {
                    mScrolling = false;
                    return;
                }
                movePosition = last;
                dy = Math.max(-listHeight, dy);
            }
            // what if movePosition is a position to be expanded/collapsed?

            final int oldTop = getChildAt(movePosition - first).getTop();
            int newTop = oldTop + dy;

            // Where will floating view end up given current list state?
            // newFloatPos is a visual position
            int newFloatPos = getFloatPosition(mLastY, movePosition, newTop);

            if (newFloatPos != mExpDragPos) {
                // scroll induces shuffle; adjust scroll for smoothness

                if (scrollDir == DOWN && newFloatPos == movePosition)
                    newTop -= mFloatViewHeight + getDividerHeight();
                else if (newFloatPos < movePosition) {
                    if (scrollDir == UP || (scrollDir == DOWN && movePosition == mExpDragPos))
                        newTop += mFloatViewHeight + getDividerHeight();
                }
            }

            // Schedule expand/collapse where needed and update list state.
            // Important that this goes before the following underscroll move.
            shuffleItems(newFloatPos);

            // Do underscroll (assumes/uses new list state)
            setSelectionFromTop(movePosition, newTop - getPaddingTop());

            DragSortListView.super.layoutChildren();

            mPrevTime += dt;

            post(this);
        }

        @Override
        public void onScroll(AbsListView view, int firstVisibleItem,
                             int visibleItemCount, int totalItemCount) {
            if (mScrolling && visibleItemCount != 0) {
                // Keep floating view from overlapping header and footer
                // items during scrolling
                if (firstVisibleItem <= mLastHeader) {
                    int dragViewTop = mLastY - mDragPointY;
                    int lastHeaderBottom = getChildAt(mLastHeader - firstVisibleItem).getBottom();
                    if (dragViewTop < lastHeaderBottom) {
                        mWindowParams.y = mYOffset + lastHeaderBottom;
                        mWindowManager.updateViewLayout(mFloatView, mWindowParams);
                    }
                } else if (firstVisibleItem + visibleItemCount > mFirstFooter) {
                    int dragViewBottom = mLastY - mDragPointY + mFloatViewHeight;
                    int firstFooterTop = getChildAt(mFirstFooter - firstVisibleItem).getTop();
                    if (dragViewBottom > firstFooterTop) {
                        mWindowParams.y = mYOffset + firstFooterTop - mFloatViewHeight;
                        mWindowManager.updateViewLayout(mFloatView, mWindowParams);
                    }
                }
            }
        }

        @Override
        public void onScrollStateChanged(AbsListView view, int scrollState) {
        }
        
        public boolean isScrolling() {
            return mScrolling;
        }

        public int getScrollDir() {
            return mScrolling ? scrollDir : STOP;
        }

        public void startScrolling(int dir) {
            if (!mScrolling) {
                if (mTrackDragScroll) {
                    mStateTracker.startTracking();
                    Log.d("DragSortListView", "scroll tracking started");
                }

                mAbort = false;
                mScrolling = true;
                tStart = SystemClock.uptimeMillis();
                mPrevTime = tStart;
                mLastHeader = getHeaderViewsCount() - 1;
                mFirstFooter = getCount() - getFooterViewsCount();
                scrollDir = dir;
                post(this);
            }
        }

        public void stopScrolling(boolean now) {
            if (now) {
                DragSortListView.this.removeCallbacks(this);
                mScrolling = false;
            } else
                mAbort = true;

            if (mTrackDragScroll)
                mStateTracker.stopTracking();
        }

        private boolean mAbort;

        private long mPrevTime;

        private int dy;
        private float dt;
        private long tStart;
        private int scrollDir;

        private float mScrollSpeed; // pixels per ms

        private boolean mScrolling = false;

        private int mLastHeader;
        private int mFirstFooter;

        private StateTracker mStateTracker;
    }

    private class StateTracker {
        StringBuilder mBuilder = new StringBuilder();
        File mFile;

        public StateTracker() {
            File root = Environment.getExternalStorageDirectory();
            mFile = new File(root, "dslv_state.txt");

            if (!mFile.exists()) {
                try {
                    mFile.createNewFile();
                    Log.d("DragSortListView", "file created");
                } catch (IOException e) {
                    Log.w("DragSortListView", "Could not create dslv_state.txt");
                    Log.d("DragSortListView", e.getMessage());
                }
            }

        }

        public void startTracking() {
            mBuilder.append("<DSLVStates>\n");
            mNumFlushes = 0;
            mTracking = true;
        }

        public void putInt(String name, int val) {
            mInts.put(name, val);
        }

        public void appendState() {
            if (!mTracking) {
                return;
            }

            mBuilder.append("<DSLVState>\n");
            final int children = getChildCount();
            final int first = getFirstVisiblePosition();
            mBuilder.append("  <Positions>");
            for (int i = 0; i < children; ++i)
                mBuilder.append(first + i).append(",");

            mBuilder.append("</Positions>\n");

            mBuilder.append("  <Tops>");
            for (int i = 0; i < children; ++i)
                mBuilder.append(getChildAt(i).getTop()).append(",");

            mBuilder.append("</Tops>\n");
            mBuilder.append("  <Bottoms>");
            for (int i = 0; i < children; ++i)
                mBuilder.append(getChildAt(i).getBottom()).append(",");

            mBuilder.append("</Bottoms>\n");

            mBuilder.append("  <ExpPos>").append(mExpDragPos).append("</ExpPos>\n");
            mBuilder.append("  <SrcPos>").append(mSrcDragPos).append("</SrcPos>\n");
            mBuilder.append("  <DragState>").append(mDragState).append("</DragState>\n");
            mBuilder.append("  <SrcHeight>").append(mFloatViewHeight + getDividerHeight()).append("</SrcHeight>\n");
            mBuilder.append("  <ViewHeight>").append(getHeight()).append("</ViewHeight>\n");
            mBuilder.append("  <LastY>").append(mLastY).append("</LastY>\n");

            mBuilder.append("</DSLVState>\n");
            mNumInBuffer++;

            if (mNumInBuffer > 1000) {
                flush();
                mNumInBuffer = 0;
            }
        }

        public void flush() {
            if (!mTracking)
                return;

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

        private int mNumInBuffer = 0;
        private int mNumFlushes = 0;

        private boolean mTracking = false;

        private HashMap<String, Integer> mInts = new HashMap<String, Integer>();
    }
}
