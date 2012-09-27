/*
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
import android.graphics.*;
import android.graphics.drawable.Drawable;
import android.os.Environment;
import android.os.SystemClock;
import android.util.AttributeSet;
import android.util.Log;
import android.util.Pair;
import android.view.*;
import android.view.GestureDetector.SimpleOnGestureListener;
import android.widget.*;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;


/*
 * Implementation Notes:
 *
 * Some terminology:
 *
 *     index    - index of the items in the list
 *     position - location on the screen
 */

/**
 * {@link ListView} subclass that mediates drag and drop resorting of items visually, and defines a callback for the
 * developer to implement the item resorting logic for the underlying adapter.
 *
 * @author heycosmo
 *
 */
public class DragSortListView extends ListView {
    /** Enables log messages */
    private static boolean DEBUG = false;

    /**
     * Interface definition for a callback to be invoked when a drag is being dispatched.
     *
     * @author heycosmo
     */
    public interface OnDragListener {
        /**
         * Called when a drag event is dispatched.
         * @param from index where the dragged item is coming from
         * @param to index where the dragged item is going to
         */
        public void onDrag(int from, int to);
    }

    /**
     * Interface definition for a callback to be invoked when the drag shadow is dropped. This is where the
     * {@link ListAdapter} reordering logic is expected to be implemented. Make sure to notify the {@link ListView} of
     * the change with something like {@link BaseAdapter#notifyDataSetChanged()}.
     *
     * @author heycosmo
     */
    public interface OnDropListener {
        /**
         * Called when the drag shadow is dropped.
         * @param from index where the dragged item is coming from
         * @param to index where the dragged item was dropped
         */
        public void onDrop(int from, int to);
    }

    /**
     * Interface definition for a callback to be invoked when the remove gesture is executed. This is where the
     * {@link ListAdapter} item remove logic is expected to be implemented. Make sure to notify the {@link ListView} of
     * the change with something like {@link BaseAdapter#notifyDataSetChanged()}.
     *
     * @author heycosmo
     */
    public interface OnRemoveListener {
        /**
         * Called when the remove gesture is executed.
         * @param which index of the item to remove
         */
        public void onRemove(int which);
    }

    /**
     * Interface definition for a callback to be invoked when a drag event is dispatched, the drag shadow is dropped, or
     * the remove gesture is executed.
     *
     * @see {@link OnDragListener}
     * @see {@link OnDropListener}
     * @see {@link OnRemoveListener}
     */
    public interface OnDragDropRemoveListener extends OnDropListener, OnDragListener, OnRemoveListener {}

    /**
     * Interface to define the scroll speed as a function of relative touch position and duration of touch. Use
     * {@link DragSortListView#setDragScrollProfile(DragScrollProfile)} to set a custom profile.
     *
     * @author heycosmo
     */
    public interface DragScrollProfile {
        /**
         * Return a scroll speed (in pixels/millisecond). Speed is expected to always be positive.
         *
         * @param relativePosition Normalized position in scroll region (i.e. w \in [0,1]). Small `relativePosition`
         *                         typically means slow scrolling.
         * @param elapsedTime Time (in milliseconds) since start of scroll (handy if you want scroll acceleration).
         * @return Scroll speed at position `relativePosition` (in pixels) and time `elapsedTime` (in pixels/ms).
         */
        float getSpeed(float relativePosition, long elapsedTime);
    }
    
    public DragSortListView(Context context) {
        this(context, null);
    }
    
    public DragSortListView(Context context, AttributeSet attrs) {
        super(context, attrs);
        mRemoveGesture = RemoveGesture.FLING;

        if (attrs != null) {
            TypedArray a = getContext().obtainStyledAttributes(attrs,
                    R.styleable.DragSortListView, 0, 0);

            mItemCollapsedHeight = Math.max(1, a.getDimensionPixelSize(
                    R.styleable.DragSortListView_collapsed_height, 1));

            mTrackDragSort = a.getBoolean(
                    R.styleable.DragSortListView_track_drag_sort, false);
            if (mTrackDragSort) {
                mDragSortTracker = new DragSortTracker();
            }

            mDragShadowBackgroundColor = a.getColor(R.styleable.DragSortListView_float_background_color,
                    0x00000000);

            // alpha between 0 and 255, 0=transparent, 255=opaque
            mDragShadowAlpha = a.getFloat(R.styleable.DragSortListView_float_alpha, 1.0f);

            mSlideInitiationThreshold = Math.max(0.0f, Math.min(1.0f, 1.0f - a.getFloat(R.styleable.DragSortListView_slide_shuffle_speed, 0.75f)));
            mAnimateSlideShuffle = mSlideInitiationThreshold > 0.0f;

            mRemoveGesture = a.getInt(R.styleable.DragSortListView_remove_mode, -1);

            float proportion = a.getFloat(R.styleable.DragSortListView_drag_scroll_start,
                    mDragUpScrollStartProportion);
            setDragScrollStart(proportion);

            mMaxScrollSpeed = a.getFloat(
                    R.styleable.DragSortListView_max_drag_scroll_speed, mMaxScrollSpeed);

            a.recycle();
        }

        if (DEBUG) Log.d("DragSortListView", "collapsed height=" + mItemCollapsedHeight);

        mDragScroller = new DragScroller();
        setOnScrollListener(mDragScroller);
    }

    /**
     * Wraps the given {@link ListAdapter} in the {@link AdapterWrapper} and attaches it to the {@link ListView}.
     *
     * @param adapter the given data adapter
     * @see ListView#setAdapter(android.widget.ListAdapter)
     */
    @Override
    public void setAdapter(ListAdapter adapter) {
        mAdapterWrapper = new AdapterWrapper(null, null, adapter);

        super.setAdapter(mAdapterWrapper);
    }

    /**
     * Draws the divider over the expanded item.
     *
     * @param canvas the drawing canvas
     * @see {@link ListView#dispatchDraw(android.graphics.Canvas)}
     */
    @Override
    protected void dispatchDraw(Canvas canvas) {
        super.dispatchDraw(canvas);

        if (mDragShadow != null) {
            // draw the divider over the expanded item
            if (mFirstExpandedIndex != mFromIndex) {
                drawDivider(mFirstExpandedIndex, canvas);
            }
            if (mSecondExpandedIndex != mFirstExpandedIndex && mSecondExpandedIndex != mFromIndex) {
                drawDivider(mSecondExpandedIndex, canvas);
            }
        }
    }

    /**
     * If {@linkplain #mDragSortTracker drag sort tracking} is enabled then the
     * {@linkplain com.keepandshare.ui.DragSortListView.DragSortTracker#appendState() state is appended}.
     *
     * @param canvas the drawing canvas
     * @see {@link ListView#onDraw(android.graphics.Canvas)}
     */
    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        if (mTrackDragSort) mDragSortTracker.appendState();
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent event) {
        if (mOnRemoveListener != null && mGestureDetector == null) {
            if (mRemoveGesture == RemoveGesture.FLING) {
                mGestureDetector = new GestureDetector(getContext(), new SimpleOnGestureListener() {
                    @Override
                    public boolean onFling(MotionEvent event1, MotionEvent event2, float velocityX,
                                           float velocityY) {
                        if (mDragShadow != null) {
                            if (velocityX > 1000) {
                                Rect rect = mTempRect;
                                mDragShadow.getDrawingRect(rect);
                                if ( event2.getX() > rect.right * 2 / 3) {
                                    // fast fling right with release near the right edge of the screen
                                    dropDragShadow(true);
                                }
                            }
                            // flinging while dragging should have no effect thus the gesture should not pass on to
                            // other onTouch handlers. Gobble...
                            return true;
                        }
                        return false;
                    }
                });
            }
        }
        if (mOnDragListener != null || mOnDropListener != null) {
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    if (DEBUG) Log.d("DragSortListView", "action down!");
                    int x = (int) event.getX();
                    int y = (int) event.getY();
                    mLastTouchPoint = Pair.create(x, y);
                    mDragInitiationPositionY = y;
                    int itemNum = pointToPosition(x, y); //includes headers/footers

                    final int numHeaders = getHeaderViewsCount();
                    final int numFooters = getFooterViewsCount();

                    if (DEBUG) Log.d("DragSortListView", "touch down on position " + itemNum);
                    if (itemNum == AdapterView.INVALID_POSITION || itemNum < numHeaders ||
                            itemNum >= getCount() - numFooters) {
                        break;
                    }
                    ViewGroup item = (ViewGroup) getChildAt(itemNum - getFirstVisiblePosition());

                    mDragPoint = Pair.create(item.getLeft(), item.getTop());
                    final int rawX = (int) event.getRawX();
                    final int rawY = (int) event.getRawY();
                    mScreenOffset = Pair.create(rawX - x, rawY - y);


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

                        mDragShadowHeight = item.getHeight();
                        mDragShadowMiddleHeight = mDragShadowHeight / 2;

                        mFirstExpandedIndex = itemNum;
                        mSecondExpandedIndex = itemNum;
                        mFromIndex = itemNum;
                        mDragShadowIndex = itemNum;

                        if (DEBUG) Log.d("DragSortListView", "getCount() = " + getCount());
                        if (DEBUG) Log.d("DragSortListView", "headers = " + getHeaderViewsCount());

                        startDragging(bitmap, x, y);

                        // cancel ListView fling
                        MotionEvent event2 = MotionEvent.obtain(event);
                        event2.setAction(MotionEvent.ACTION_CANCEL);
                        super.onInterceptTouchEvent(event2);

                        return true;
                    }
                    removeDragShadow();
                    break;
            }
        }
        return super.onInterceptTouchEvent(event);
    }

    /**
     * Triggers {@link #updateScrollStarts()}.
     *
     * @param width new width
     * @param height new height
     * @param oldWidth old width
     * @param oldHeight old height
     * @see {@link ListView#onSizeChanged(int, int, int, int)}
     */
    @Override
    protected void onSizeChanged(int width, int height, int oldWidth, int oldHeight) {
        super.onSizeChanged(width, height, oldWidth, oldHeight);
        updateScrollStarts();
    }

    /**
     * Allows {@link android.widget.ListView#requestLayout()}s to be ignored through {@link #mBlockLayoutRequests}'s
     * value.
     *
     * @see {@link android.widget.ListView#requestLayout()}
     */
    @Override
    public void requestLayout() {
        if (!mBlockLayoutRequests) {
            super.requestLayout();
        }
    }

    /**
     * If there is a {@link #mDragShadow} then {@link #adjustAllItems()} and {@link #doDragScroll(int)} are performed
     * as needed. Makes sure to {@linkplain #mBlockLayoutRequests block layout requests} while performing these methods.
     *
     * @see {@link android.widget.ListView#layoutChildren()}
     */
    @Override
    protected void layoutChildren() {

        if (mDragShadow != null) {
            if (DEBUG) Log.d("DragSortListView", "layout children");
            int oldFirstExpandedIndex = mFirstExpandedIndex;

            // Block layout requests will adjusting items and performing the drag scroll
            mBlockLayoutRequests = true;

            if (updateItemIndices()) {
                adjustAllItems();
            }

            if (mScrollY != 0) {
                doDragScroll(oldFirstExpandedIndex);
            }
            // Re-enable layout requests
            mBlockLayoutRequests = false;
        }

        super.layoutChildren();
    }

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        if (mGestureDetector != null) {
            mGestureDetector.onTouchEvent(ev);
        }
        if ((mOnDragListener != null || mOnDropListener != null) && mDragShadow != null) {
            int action = ev.getAction();

            final int x = (int) ev.getX();
            final int y = (int) ev.getY();

            switch (action & MotionEvent.ACTION_MASK) {
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    Rect rect = mTempRect;
                    mDragShadow.getDrawingRect(rect);

                    if (mRemoveGesture == RemoveGesture.SLIDE_OFF_SCREEN_RIGHT && ev.getX() > rect.right * 3/4) {
                        dropDragShadow(true);
                    } else if (mRemoveGesture == RemoveGesture.SLIDE_OFF_SCREEN_LEFT && ev.getX() < rect.right * 1/4) {
                        dropDragShadow(true);
                    } else {
                        dropDragShadow(false);
                    }

                    break;

                case MotionEvent.ACTION_DOWN:
                    // Ignore action
                    break;
                case MotionEvent.ACTION_MOVE:
                    // make src item invisible on first move away from pickup
                    // point. Reduces flicker.
                    if (mLastTouchPoint.second == mDragInitiationPositionY) {
                        // should we be this careful?
                        final View item = getChildAt(mFromIndex - getFirstVisiblePosition());
                        if (item != null) {
                            item.setVisibility(INVISIBLE);
                        }
                    }

                    if (DEBUG) Log.d("DragSortListView", "move");
                    dragDropShadow(x, y);

                    requestLayout();

                    // get the current scroll direction
                    int currentScrollDirection = mDragScroller.getScrollDirection();

                    if (y > mLastTouchPoint.second && y > mDownScrollStartY &&
                            currentScrollDirection != DragScroller.DOWN) {
                        // dragged down, it is below the down scroll start and it is not scrolling up
                        if (currentScrollDirection != DragScroller.STOP) {
                            // moved directly from up scroll to down scroll
                            mDragScroller.stopScrolling(true);
                        }

                        // start scrolling down
                        mDragScroller.startScrolling(DragScroller.DOWN);
                    }
                    else if (y < mLastTouchPoint.second && y < mUpScrollStartY &&
                            currentScrollDirection != DragScroller.UP) {
                        // dragged up, it is above the up scroll start and it is not scrolling up
                        if (currentScrollDirection != DragScroller.STOP) {
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

            mLastTouchPoint = Pair.create(x, y);

            return true;
        }
        return super.onTouchEvent(ev);
    }

    /**
     * Set maximum drag scroll speed in positions/second. Only applies if using default {@link #mScrollProfile}.
     *
     * @param max Maximum scroll speed.
     */
    public void setMaxScrollSpeed(float max) {
        mMaxScrollSpeed = max;
    }

    /**
     * Returns the {@link ListAdapter} that was originally pass into {@link #setAdapter(android.widget.ListAdapter)}.
     * Unlike {@link ListView#getAdapter()}, which will return a {@link ListAdapter} that is wrapped in an
     * {@link AdapterWrapper} (by {@link DragSortListView}) {\emph and} wrapped again by {@link ListView}.
     *
     * @return The {@link ListAdapter} that was a parameter of {@link #setAdapter(android.widget.ListAdapter)}
     */
    public ListAdapter getWrappedAdapter() {
        return mAdapterWrapper == null? null : mAdapterWrapper.getAdapter();
    }

    /**
     * Set the threshold of each drag scroll region by specifying it as a proportion of the {@link ListView}'s height.
     *
     * @param dragScrollStartProportion Fraction of {@link ListView}'s height. Capped at 0.5f.
     *
     */
    public void setDragScrollStart(float dragScrollStartProportion) {
        setDragScrollStarts(dragScrollStartProportion, dragScrollStartProportion);
    }

    /**
     * Set the threshold of each drag scroll region by specifying it as a proportion of the {@link ListView}'s height.
     *
     * @param dragUpScrollStartProportion Fraction of ListView height for up-scroll bound. Capped at 0.5f.
     * @param dragDownScrollStartProportion Fraction of ListView height for down-scroll bound. Capped at 0.5f.
     *
     */
    public void setDragScrollStarts(float dragUpScrollStartProportion, float dragDownScrollStartProportion) {
        mDragUpScrollStartProportion = Math.min(dragUpScrollStartProportion, 0.5f);
        mDragDownScrollStartProportion = Math.min(dragDownScrollStartProportion, 0.5f);

        if (getHeight() != 0) {
            updateScrollStarts();
        }
    }

    /**
     * Sets the {@link Drawable} for the {@linkplain RemoveGesture#TRASH trash gesture} and sets the trash gesture as
     * the {@linkplain #mRemoveGesture item removal gesture}.
     * @param trashCan the trash can {@link Drawable}
     */
    public void setTrashcan(Drawable trashCan) {
        mTrashCan = trashCan;
        mRemoveGesture = RemoveGesture.TRASH;
    }

    /**
     * Attaches the {@link OnDragListener} that is assumed to perform the {@link ListAdapter} reordering logic.
     * {\emph DragSortListView does not do this for you}. Make sure to notify the {@link ListView} of the change with
     * something like {@link BaseAdapter#notifyDataSetChanged()}.
     *
     * @param onDropListener receives the {@link OnDropListener#onDrop(int, int)} event
     */
    public void setOnDropListener(OnDropListener onDropListener) {
        mOnDropListener = onDropListener;
    }

    /**
     * Attaches the {@link OnRemoveListener} that is assumed to perform the {@link ListAdapter} item removal logic.
     * Make sure to notify the {@link ListView} of the change with something like
     * {@link BaseAdapter#notifyDataSetChanged()}. When an item removal occurs, {@link DragSortListView} relies on a
     * redraw of all the items to recover invisible views and such. Strictly speaking, if you remove an item, your
     * dataset has changed...
     *
     * @param onRemoveListener receives the {@link OnRemoveListener#onRemove(int)} event
     */
    public void setOnRemoveListener(OnRemoveListener onRemoveListener) {
        mOnRemoveListener = onRemoveListener;
    }

    /**
     * Attaches the {@link OnDragListener}.
     * @param onDragListener receives the {@link OnDragListener#onDrag} event
     */
    public void setOnDragListener(OnDragListener onDragListener) {
        mOnDragListener = onDragListener;
    }

    /**
     * Attaches the {@link OnRemoveListener}.
     * @param onDragDropRemoveListener receives the {@link OnDragListener#onDrag(int, int)},
     *                                 {@link OnDropListener#onDrop(int, int)}, and the
     *                                 {@link OnRemoveListener#onRemove(int)} events
     * @see {@link #setOnDragListener(OnDragListener)}
     * @see {@link #setOnDropListener(OnDropListener)}
     * @see {@link #setOnRemoveListener(OnRemoveListener)}
     */
    public void setOnDragDropRemoveListener(OnDragDropRemoveListener onDragDropRemoveListener) {
        setOnDropListener(onDragDropRemoveListener);
        setOnDragListener(onDragDropRemoveListener);
        setOnRemoveListener(onDragDropRemoveListener);
    }

    /**
     * Sets a custom {@linkplain DragScrollProfile scroll speed profile}. The default scroll speed profile increases
     * linearly with the relative position.
     *
     * @param scrollSpeedProfile
     */
    public void setDragScrollProfile(DragScrollProfile scrollSpeedProfile) {
        if (scrollSpeedProfile != null) {
            mScrollProfile = scrollSpeedProfile;
        }
    }

    private void drawDivider(int expandedIndex, Canvas canvas) {
        final Drawable divider = getDivider();
        final int dividerHeight = getDividerHeight();

        if (divider != null && dividerHeight != 0) {
            final ViewGroup expandedItem = (ViewGroup) getChildAt(expandedIndex - getFirstVisiblePosition());
            if (expandedItem != null) {
                final int left = getPaddingLeft();
                final int right = getWidth() - getPaddingRight();
                final int top;
                final int bottom;

                final int childHeight = expandedItem.getChildAt(0).getHeight();

                if (expandedIndex > mFromIndex) {
                    top = expandedItem.getTop() + childHeight;
                    bottom = top + dividerHeight;
                } else {
                    bottom = expandedItem.getBottom() - childHeight;
                    top = bottom - dividerHeight;
                }

                divider.setBounds(left, top, right, bottom);
                divider.draw(canvas);
            }
        }
    }

    private int measureItemAndGetHeight(View item, boolean ofChild) {
        ViewGroup.LayoutParams lp;
        if (ofChild) {
            item = ((ViewGroup) item).getChildAt(0);
            lp = item.getLayoutParams();
        } else {
            lp = item.getLayoutParams();
        }

        final int height = lp == null ? 0 : lp.height;
        if (height > 0) {
            return height;
        } else {
            int spec = MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED);
            item.measure(spec, spec);
            return item.getMeasuredHeight();
        }
    }

    private int getItemHeight(int position) {
        return getItemHeight(position, false);
    }

    private int getItemHeight(int position, boolean ofChild) {
        final int first = getFirstVisiblePosition();
        final int last = getLastVisiblePosition();

        if (position >= first && position <= last) {
            if (ofChild) {
                ViewGroup item = (ViewGroup) getChildAt(position - first);
                return item.getChildAt(0).getHeight();
            } else {
                return getChildAt(position - first).getHeight();
            }
        } else {
            if (DEBUG) Log.d("DragSortListView", "getView for height");

            final ListAdapter adapter = getAdapter();
            int type = adapter.getItemViewType(position);

            // There might be a better place for checking for the following
            final int typeCount = adapter.getViewTypeCount();
            if (typeCount != mSampleViewTypes.length) {
                mSampleViewTypes = new View[typeCount];
            }

            View view;
            if (type >= 0) {
                if (mSampleViewTypes[type] == null) {
                    view = adapter.getView(position, null, this);
                    mSampleViewTypes[type] = view;
                } else {
                    view = adapter.getView(position, mSampleViewTypes[type], this);
                }
            } else {
                // type is HEADER_OR_FOOTER or IGNORE
                view = adapter.getView(position, null, this);
            }

            return measureItemAndGetHeight(view, ofChild);
        }
    }

    private void logIndexData() {
        Log.d("DragSortListView", "mFromIndex=" + mFromIndex + " mFirstExpandedIndex=" + mFirstExpandedIndex + " mSecondExpandedIndex=" + mSecondExpandedIndex);
    }

    /**
     * Get the shuffle edge for the item at index when its top is at y-coordinate top.
     *
     * @param index
     * @param top
     *
     * @return Shuffle line between `index - 1` and `index` (for the given view of the list. That is, when the item's
     * top has y-coordinate of given `top`). If the {@link #mDragShadow drag shadow} (treated as horizontal line) is
     * dropped immediately above this line, it lands in `index - 1`, otherwise it lands in `index`.
     */
    private int getShuffleEdge(int index, int top) {

        final int numHeaders = getHeaderViewsCount();
        final int numFooters = getFooterViewsCount();

        // shuffle edges are defined between items that can be dragged; there are N-1 of them if there are N draggable
        // items.
        if (index <= numHeaders || (index >= getCount() - numFooters)) {
            return top;
        }

        int dividerHeight = getDividerHeight();

        int edge;

        if (mSecondExpandedIndex <= mFromIndex) {
            // items are expanded on and/or above the source position
            final int childHeight = getItemHeight(index - 1, true);
            if (index <= mFirstExpandedIndex) {
                edge = top + (mDragShadowHeight - dividerHeight - childHeight) / 2;
            } else if (index == mSecondExpandedIndex) {
                edge = index == mFromIndex?
                       top + getItemHeight(index) - (2 * dividerHeight + childHeight + mDragShadowHeight) / 2 :
                       top + (getItemHeight(index) - getItemHeight(index, true) - dividerHeight
                                - getItemHeight(index - 1)) / 2;
            } else if (index < mFromIndex) {
                edge = top - (childHeight + 2 * dividerHeight + mDragShadowHeight) / 2;
            } else if (index == mFromIndex) {
                edge = top + mItemCollapsedHeight - (2 * dividerHeight + childHeight + mDragShadowHeight) / 2;
            } else {
                edge = top + (getItemHeight(index) - mDragShadowHeight) / 2;
            }
        } else {
            // items are expanded on and/or below the source position
            if (index <= mFromIndex) {
                edge = top + (mDragShadowHeight - getItemHeight(index - 1) - dividerHeight) / 2;
            } else if (index <= mFirstExpandedIndex) {
                edge = index - 1 == mFromIndex?
                       top + (getItemHeight(index, true) + dividerHeight + mDragShadowHeight) / 2 :
                       top + (getItemHeight(index, true) + dividerHeight + mDragShadowHeight) / 2
                                - mItemCollapsedHeight + dividerHeight;
            } else if (index == mSecondExpandedIndex) {
                int blankAbove = index - 1 == mFromIndex?
                                 getItemHeight(index - 1) :
                                 getItemHeight(index - 1) - getItemHeight(index - 1, true) - dividerHeight;
                edge = top - blankAbove - dividerHeight + (getItemHeight(index, true) + dividerHeight
                                  + mDragShadowHeight) / 2;
            } else {
                edge = top + (getItemHeight(index) - mDragShadowHeight) / 2;
            }
        }

        return edge;
    }

    private boolean updateItemIndices() {
        final int first = getFirstVisiblePosition();
        int startIndex = mFirstExpandedIndex;
        View startView = getChildAt(startIndex - first);

        if (startView == null) {
            startIndex = first + getChildCount() / 2;
            startView = getChildAt(startIndex - first);
        }
        int startTop = startView.getTop() + mScrollY;

        int edge = getShuffleEdge(startIndex, startTop);
        int lastEdge = edge;

        if (DEBUG) Log.d("DragSortListView", "drag shadow center=" + mDragShadowCenterY);

        int itemIndex = startIndex;
        int itemTop = startTop;
        if (mDragShadowCenterY < edge) {
            // scanning up for drag shadow position
            if (DEBUG) Log.d("DragSortListView", "  edge=" + edge);
            while (itemIndex >= 0) {
                itemIndex--;

                if (itemIndex == 0) {
                    edge = itemTop - getItemHeight(itemIndex);
                    break;
                }

                itemTop -= getItemHeight(itemIndex);
                edge = getShuffleEdge(itemIndex, itemTop);
                if (DEBUG) Log.d("DragSortListView", "  edge=" + edge);

                if (mDragShadowCenterY >= edge) {
                    break;
                }

                lastEdge = edge;
            }
        } else {
            // scanning down for drag shadow position
            if (DEBUG) Log.d("DragSortListView", "  edge=" + edge);
            final int count = getCount();
            while (itemIndex < count) {
                if (itemIndex == count - 1) {
                    edge = itemTop + getItemHeight(itemIndex);
                    break;
                }

                itemTop += getItemHeight(itemIndex);
                edge = getShuffleEdge(itemIndex + 1, itemTop);
                if (DEBUG) Log.d("DragSortListView", "  edge=" + edge);

                // test for hit
                if (mDragShadowCenterY < edge) {
                    break;
                }

                lastEdge = edge;
                itemIndex++;
            }
        }

        final int numHeaders = getHeaderViewsCount();
        final int numFooters = getFooterViewsCount();

        boolean updated = false;

        final int oldFirstExpandedIndex = mFirstExpandedIndex;
        final int oldSecondExpandedIndex = mSecondExpandedIndex;
        float oldSlideAnimationItemPosition = mSlideAnimationItemPosition;

        if (mAnimateSlideShuffle) {
            int edgeToEdge = Math.abs(edge - lastEdge);

            int edgeTop, edgeBottom;
            if (mDragShadowCenterY < edge) {
                edgeBottom = edge;
                edgeTop = lastEdge;
            } else {
                edgeTop = edge;
                edgeBottom = lastEdge;
            }
            if (DEBUG) Log.d("DragSortListView", "edgeTop=" + edgeTop + " edgeBot=" + edgeBottom);

            int slideRgnHeight = (int) (0.5f * mSlideInitiationThreshold * edgeToEdge);
            float slideRgnHeightF = (float) slideRgnHeight;
            int slideEdgeTop = edgeTop + slideRgnHeight;
            int slideEdgeBottom = edgeBottom - slideRgnHeight;


            // Three regions
            if (mDragShadowCenterY < slideEdgeTop) {
                mFirstExpandedIndex = itemIndex - 1;
                mSecondExpandedIndex = itemIndex;
                mSlideAnimationItemPosition = 0.5f * ((float) (slideEdgeTop - mDragShadowCenterY)) / slideRgnHeightF;
                if (DEBUG) Log.d("mobeta", "firstExp="+mFirstExpandedIndex+" secExp="+mSecondExpandedIndex+" slideFrac="+mSlideAnimationItemPosition);
            } else if (mDragShadowCenterY < slideEdgeBottom) {
                mFirstExpandedIndex = itemIndex;
                mSecondExpandedIndex = itemIndex;
            } else {
                mFirstExpandedIndex = itemIndex;
                mSecondExpandedIndex = itemIndex + 1;
                mSlideAnimationItemPosition = 0.5f * (1.0f + (edgeBottom - mDragShadowCenterY) / slideRgnHeightF);
                if (DEBUG) Log.d("mobeta", "firstExp="+mFirstExpandedIndex+" secExp="+mSecondExpandedIndex+" slideFrac="+mSlideAnimationItemPosition);
            }
        } else {
            mFirstExpandedIndex = itemIndex;
            mSecondExpandedIndex = itemIndex;
        }

        // correct for headers and footers
        if (mFirstExpandedIndex < numHeaders) {
            itemIndex = numHeaders;
            mFirstExpandedIndex = itemIndex;
            mSecondExpandedIndex = itemIndex;
        } else if (mSecondExpandedIndex >= getCount() - numFooters) {
            itemIndex = getCount() - numFooters - 1;
            mFirstExpandedIndex = itemIndex;
            mSecondExpandedIndex = itemIndex;
        }

        updated = (mFirstExpandedIndex != oldFirstExpandedIndex || mSecondExpandedIndex != oldSecondExpandedIndex ||
                   mSlideAnimationItemPosition != oldSlideAnimationItemPosition);

        if (itemIndex != mDragShadowIndex) {
            if (mOnDragListener != null) {
                mOnDragListener.onDrag(mDragShadowIndex - numHeaders, itemIndex - numHeaders);
            }

            mDragShadowIndex = itemIndex;
            updated = true;
        }

        return updated;
    }

    private void updateScrollStarts() {
        final int paddingTop = getPaddingTop();
        final int listHeight = getHeight() - paddingTop - getPaddingBottom();
        final float heightF = (float) listHeight;

        mUpScrollStartYF = paddingTop + mDragUpScrollStartProportion * heightF;
        mDownScrollStartYF = paddingTop + (1.0f - mDragDownScrollStartProportion) * heightF;

        mUpScrollStartY = (int) mUpScrollStartYF;
        mDownScrollStartY = (int) mDownScrollStartYF;
        if (DEBUG) Log.d("DragSortListView", "up start=" + mUpScrollStartY);
        if (DEBUG) Log.d("DragSortListView", "down start=" + mDownScrollStartY);

        mDragUpScrollHeight = mUpScrollStartYF - paddingTop;
        mDragDownScrollHeight = paddingTop + listHeight - mDownScrollStartYF;
    }

    private void dropDragShadow(boolean removeDraggedItem) {
        mDragScroller.stopScrolling(true);

        if (removeDraggedItem && mOnRemoveListener != null) {
                mOnRemoveListener.onRemove(mFromIndex - getHeaderViewsCount());
        } else if (!removeDraggedItem) {
            if (mOnDropListener != null && mDragShadowIndex >= 0 && mDragShadowIndex < getCount()) {
                final int numHeaders = getHeaderViewsCount();
                mOnDropListener.onDrop(mFromIndex - numHeaders, mDragShadowIndex - numHeaders);
            }

            int oldDraggedItemIndex = mFromIndex;

            mFromIndex = -1;
            mFirstExpandedIndex = -1;
            mSecondExpandedIndex = -1;
            mDragShadowIndex = -1;


            int firstVisibleIndex = getFirstVisiblePosition();
            if (oldDraggedItemIndex < firstVisibleIndex) {
                // collapsed src item is off screen;
                // adjust the scroll after item heights have been fixed
                View view = getChildAt(0);
                int top = 0;
                if (view != null) {
                    top = view.getTop();
                }
                if (DEBUG) Log.d("DragSortListView", "top=" + top + " drag shadow height=" + mDragShadowHeight);
                setSelectionFromTop(firstVisibleIndex - 1, top - getPaddingTop());
            }

            removeDragShadow();
        }

        if (mTrackDragSort) mDragSortTracker.stopTracking();
    }

    private void adjustAllItems() {
        final int first = getFirstVisiblePosition();
        final int last = getLastVisiblePosition();

        int begin = Math.max(0, getHeaderViewsCount() - first);
        int end = Math.min(last - first, getCount() - 1 - getFooterViewsCount() - first);

        for (int index = begin; index <= end; ++index) {
            View view = getChildAt(index);
            if (view != null) {
                adjustItem(first + index, view, false);
            }
        }
    }

    private void adjustItem(int index) {
        View view = getChildAt(index - getFirstVisiblePosition());

        if (view != null) {
            adjustItem(index, view, false);
        }
    }

    private void adjustItem(int index, View view, boolean needsMeasure) {
        // Adjust item height
        ViewGroup.LayoutParams lp = view.getLayoutParams();
        int oldHeight = lp.height;
        int height = oldHeight;

        int dividerHeight = getDividerHeight();

        boolean isSliding = mAnimateSlideShuffle && mFirstExpandedIndex != mSecondExpandedIndex;

        if (index == mFromIndex) {
            if (mFromIndex == mFirstExpandedIndex) {
                height = isSliding?
                         Math.max((int) (mSlideAnimationItemPosition * mDragShadowHeight), mItemCollapsedHeight) :
                         mDragShadowHeight;
            } else if (mFromIndex == mSecondExpandedIndex) {
                // if gets here, we know an item is sliding
                height = Math.max(mDragShadowHeight - (int) (mSlideAnimationItemPosition * mDragShadowHeight),
                                  mItemCollapsedHeight);
            } else {
                height = mItemCollapsedHeight;
            }
        } else if (index == mFirstExpandedIndex || index == mSecondExpandedIndex) {
            int childHeight = needsMeasure?
                              measureItemAndGetHeight(view, true) :
                              ((ViewGroup) view).getChildAt(0).getHeight();

            height = index == mFirstExpandedIndex?
                        (isSliding?
                         childHeight + dividerHeight + (int) mSlideAnimationItemPosition * mDragShadowHeight :
                         childHeight + dividerHeight + mDragShadowHeight) :
                     childHeight + dividerHeight + mDragShadowHeight
                             - (int)(mSlideAnimationItemPosition * mDragShadowHeight);
        } else {
            height = ViewGroup.LayoutParams.WRAP_CONTENT;
        }

        if (height != oldHeight) {
            lp.height = height;

            view.setLayoutParams(lp);
        }

        // Adjust item gravity
        if (index == mFirstExpandedIndex || index == mSecondExpandedIndex) {
            if (index < mFromIndex) {
                ((RelativeLayout) view).setGravity(Gravity.BOTTOM);
            } else if (index > mFromIndex) {
                ((RelativeLayout) view).setGravity(Gravity.TOP);
            }
        }


        // Finally adjust item visibility
        final int oldVisibility = view.getVisibility();
        int visible = View.VISIBLE;

        if (index == mFromIndex && mDragShadow != null) {
            visible = View.INVISIBLE;
        }

        if (visible != oldVisibility) {
            view.setVisibility(visible);
        }
    }

    private void doDragScroll(int oldFirstExpandedIndex) {
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

        int moveHeightBefore = moveItem.getHeight();
        int moveHeightAfter = measureItemAndGetHeight(moveItem, false);

        if (moveHeightBefore != moveHeightAfter && (movePos > oldFirstExpandedIndex || movePos > mFirstExpandedIndex)) {
            // some item height must change above move position for adjustment to be required
            top += moveHeightBefore - moveHeightAfter;
        }

        setSelectionFromTop(movePos, top - padTop);

        mScrollY = 0;
    }

    private void startDragging(Bitmap dragShadowImage, int x, int y) {
        if (getParent() != null) {
            getParent().requestDisallowInterceptTouchEvent(true);
        }

        mWindowParams = new WindowManager.LayoutParams();
        mWindowParams.gravity = Gravity.TOP | Gravity.LEFT;
        mWindowParams.x = x - mDragPoint.first + mScreenOffset.first;
        mWindowParams.y = y - mDragPoint.second + mScreenOffset.second;

        if (DEBUG) Log.d("DragShadowListView", "drag shadow x=" + mWindowParams.x + " y=" + mWindowParams.y);

        mWindowParams.height = WindowManager.LayoutParams.WRAP_CONTENT;
        mWindowParams.width = WindowManager.LayoutParams.WRAP_CONTENT;
        mWindowParams.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                | WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
                | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS;
        mWindowParams.format = PixelFormat.TRANSLUCENT;
        mWindowParams.windowAnimations = 0;
        mWindowParams.alpha = mDragShadowAlpha;

        final Context context = getContext();
        final ImageView dragShadowView = new ImageView(context);
        dragShadowView.setBackgroundColor(mDragShadowBackgroundColor);
        dragShadowView.setPadding(0, 0, 0, 0);
        dragShadowView.setImageBitmap(dragShadowImage);
        mDragShadowBitmap = dragShadowImage;

        mWindowManager = (WindowManager)context.getSystemService(Context.WINDOW_SERVICE);
        mWindowManager.addView(dragShadowView, mWindowParams);
        mDragShadow = dragShadowView;

        if (mTrackDragSort) mDragSortTracker.startTracking();
}

    private void dragDropShadow(int x, int y) {
        if (DEBUG) Log.d("DragSortListView", "drag shadow pure x=" + x + " y=" + y);
        if (mRemoveGesture == RemoveGesture.SLIDE_OFF_SCREEN_RIGHT) {
            float alpha = mDragShadowAlpha;
            int width = mDragShadow.getWidth();
            if (x > width / 2) {
                alpha = mDragShadowAlpha * (((float)(width - x)) / (width / 2));
            }
            mWindowParams.alpha = alpha;
        }

        if (mRemoveGesture == RemoveGesture.SLIDE_OFF_SCREEN_LEFT) {
            float alpha = mDragShadowAlpha;
            int width = mDragShadow.getWidth();
            if (x < width / 2) {
                alpha = mDragShadowAlpha * (((float) (x)) / (width / 2));
            }
            mWindowParams.alpha = alpha;
        }

        if (mRemoveGesture == RemoveGesture.FLING || mRemoveGesture == RemoveGesture.TRASH) {
            mWindowParams.x = x - mDragPoint.first + mScreenOffset.first;
            mWindowParams.x = x - mDragPoint.first + mScreenOffset.first;
        } else {
            mWindowParams.x = mScreenOffset.first + getPaddingLeft();
        }


        // keep drag shadow from going past bottom of last header view
        final int numHeaders = getHeaderViewsCount();
        final int numFooters = getFooterViewsCount();
        final int firstIndex = getFirstVisiblePosition();
        final int lastPos = getLastVisiblePosition();

        if (DEBUG) Log.d("DragSortListView", "nHead="+numHeaders+" nFoot="+numFooters+" first="+firstIndex+" last="+lastPos);
        int topLimit = firstIndex < numHeaders? getChildAt(numHeaders - firstIndex - 1).getBottom() : getPaddingTop();

        // bottom limit is top of first footer View or
        // bottom of last item in list
        int bottomLimit = lastPos >= getCount() - numFooters - 1?
                          getChildAt(getCount() - numFooters - 1 - firstIndex).getBottom() :
                          getHeight() - getPaddingBottom();

        if (DEBUG) Log.d("DragSortListView", "dragDropShadow top=" + (y - mDragPoint.second));
        if (DEBUG) Log.d("DragSortListView", "mDragPointY=" + mDragPoint.second);
        if (y - mDragPoint.second < topLimit) {
            mWindowParams.y = mScreenOffset.second + topLimit;
        } else if (y - mDragPoint.second + mDragShadowHeight > bottomLimit) {
            mWindowParams.y = mScreenOffset.second + bottomLimit - mDragShadowHeight;
        } else {
            mWindowParams.y = y - mDragPoint.second + mScreenOffset.second;
        }
        // get midpoint of floating view (constrained to ListView bounds)
        mDragShadowCenterY = mWindowParams.y + mDragShadowMiddleHeight - mScreenOffset.second;
        if (DEBUG) Log.d("DragShadowListView", "drag shadow taint x=" + mWindowParams.x + " y=" + mWindowParams.y);
        mWindowManager.updateViewLayout(mDragShadow, mWindowParams);

        if (mTrashCan != null) {
            int width = mDragShadow.getWidth();
            if (y > getHeight() * 3 / 4) {
                mTrashCan.setLevel(2);
            } else if (width > 0 && x > width / 4) {
                mTrashCan.setLevel(1);
            } else {
                mTrashCan.setLevel(0);
            }
        }
    }

    private void removeDragShadow() {
        if (mDragShadow != null) {
            mDragShadow.setVisibility(GONE);
            WindowManager wm = (WindowManager)getContext().getSystemService(Context.WINDOW_SERVICE);
            wm.removeView(mDragShadow);
            mDragShadow.setImageDrawable(null);
            mDragShadow = null;
        }
        if (mDragShadowBitmap != null) {
            mDragShadowBitmap.recycle();
            mDragShadowBitmap = null;
        }
        if (mTrashCan != null) {
            mTrashCan.setLevel(0);
        }
    }

    private class AdapterWrapper extends HeaderViewListAdapter {
        
        public AdapterWrapper(ArrayList<FixedViewInfo> headerViewInfos,
                              ArrayList<FixedViewInfo> footerViewInfos, ListAdapter adapter) {
            super(headerViewInfos, footerViewInfos, adapter);
            mAdapter = adapter;
        }

        @Override
        public View getView(int index, View convertView, ViewGroup parent) {
            RelativeLayout relativeLayout;
            View child;

            if (DEBUG) Log.d("DragSortListView", "getView; index=" + index);

            if (DEBUG) Log.d("DragSortListView", "getView: index=" + index + " convertView=" + convertView);
            if (convertView != null) {

                relativeLayout = (RelativeLayout) convertView;
                View oldChild = relativeLayout.getChildAt(0);

                child = mAdapter.getView(index, oldChild, relativeLayout);
                if (child != oldChild) {
                    // shouldn't get here if user is reusing convertViews properly
                    relativeLayout.removeViewAt(0);
                    relativeLayout.addView(child);
                    // check that tags are equal too?
                    relativeLayout.setTag(child.findViewById(R.id.drag));
                }
            } else {
                AbsListView.LayoutParams params =
                        new AbsListView.LayoutParams(ViewGroup.LayoutParams.FILL_PARENT,
                                ViewGroup.LayoutParams.WRAP_CONTENT);
                relativeLayout = new RelativeLayout(getContext());
                relativeLayout.setLayoutParams(params);
                child = mAdapter.getView(index, null, relativeLayout);
                relativeLayout.addView(child);

                relativeLayout.setTag(child.findViewById(R.id.drag));
            }

            // Set the correct item height given drag state; passed
            // View needs to be measured if measurement is required.
            adjustItem(index + getHeaderViewsCount(), relativeLayout, true);

            return relativeLayout;
        }

        public ListAdapter getAdapter() {
            return mAdapter;
        }
        
        private ListAdapter mAdapter;
    }

    private class DragScroller implements Runnable, AbsListView.OnScrollListener {
        public final static int STOP = -1;
        public final static int UP = 0;
        public final static int DOWN = 1;

        public DragScroller() {}

        @Override
        public void run() {
            if (mAbort) {
                mScrolling = false;
                return;
            }

            if (DEBUG) Log.d("DragSortListView", "scroll");

            final int first = getFirstVisiblePosition();
            final int last = getLastVisiblePosition();
            final int count = getCount();
            final int paddingTop = getPaddingTop();
            final int listHeight = getHeight() - paddingTop - getPaddingBottom();

            if (scrollDirection == UP) {
                View view = getChildAt(0);
                if (DEBUG) Log.d("DragSortListView", "view top=" + view.getTop()+" padding top=" + paddingTop);
                if (view == null) {
                    mScrolling = false;
                    return;
                } else {
                    if (first == 0 && view.getTop() == paddingTop) {
                        mScrolling = false;
                        return;
                    }
                }
                mScrollSpeed = mScrollProfile.getSpeed((mUpScrollStartYF - mLastTouchPoint.second)/ mDragUpScrollHeight,
                        mPrevTime);
            } else {
                View view = getChildAt(last - first);
                if (view == null) {
                    mScrolling = false;
                    return;
                } else {
                    if (last == count - 1 && view.getBottom() <= listHeight + paddingTop) {
                        mScrolling = false;
                        return;
                    }
                }
                mScrollSpeed = -mScrollProfile.getSpeed((mLastTouchPoint.second - mDownScrollStartYF)
                        / mDragDownScrollHeight, mPrevTime);
            }

            dt = SystemClock.uptimeMillis() - mPrevTime;
            // dy is change in View position of a list item; i.e. positive dy
            // means user is scrolling up (list item moves down the screen, remember
            // y=0 is at top of View).
            dy = Math.round(mScrollSpeed * dt);
            mScrollY += dy;

            requestLayout();

            mPrevTime += dt;

            post(this);
        }

        @Override
        public void onScroll(AbsListView listView, int firstVisibleItem, int visibleItemCount, int totalItemCount) {
            if (mScrolling && visibleItemCount != 0) {
                // Keep floating view from overlapping header and footer
                // items during scrolling
                int firstFooter = getCount() - getFooterViewsCount();
                int lastHeader = getHeaderViewsCount() - 1;

                if (firstVisibleItem <= lastHeader) {
                    int floatViewTop = mDragShadowCenterY - mDragShadowMiddleHeight;
                    int lastHeaderBottom = getChildAt(lastHeader - firstVisibleItem).getBottom();
                    if (floatViewTop < lastHeaderBottom) {
                        mWindowParams.y = mScreenOffset.second + lastHeaderBottom;
                        mDragShadowCenterY = mWindowParams.y + mDragShadowMiddleHeight - mScreenOffset.second;
                        mWindowManager.updateViewLayout(mDragShadow, mWindowParams);
                    }
                } else if (firstVisibleItem + visibleItemCount > firstFooter) {
                    int floatViewBottom = mDragShadowCenterY + mDragShadowMiddleHeight;
                    int firstFooterTop = getChildAt(firstFooter - firstVisibleItem).getTop();
                    if (floatViewBottom > firstFooterTop) {
                        mWindowParams.y = mScreenOffset.second + firstFooterTop - mDragShadowHeight;
                        mDragShadowCenterY = mWindowParams.y + mDragShadowMiddleHeight - mScreenOffset.second;
                        mWindowManager.updateViewLayout(mDragShadow, mWindowParams);
                    }
                }
            }
        }

        @Override
        public void onScrollStateChanged(AbsListView view, int scrollState) {}

        public boolean isScrolling() {
            return mScrolling;
        }

        public int getScrollDirection() {
            return mScrolling ? scrollDirection : STOP;
        }

        public void startScrolling(int direction) {
            if (!mScrolling) {
                mAbort = false;
                mScrolling = true;
                tStart = SystemClock.uptimeMillis();
                mPrevTime = tStart;
                scrollDirection = direction;
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
        }
        
        private boolean mAbort;

        private long mPrevTime;

        private int dy;
        private float dt;
        private long tStart;
        private int scrollDirection;

        private float mScrollSpeed; // pixels per ms

        private boolean mScrolling = false;
    }

    private class DragSortTracker {
        StringBuilder mBuilder = new StringBuilder();

        File mFile;
        
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
            mBuilder.append("  <Positions>");
            for (int i = 0; i < children; ++i) {
                mBuilder.append(first + i).append(",");
            }
            mBuilder.append("</Positions>\n");

            mBuilder.append("  <Tops>");
            for (int i = 0; i < children; ++i) {
                mBuilder.append(getChildAt(i).getTop()).append(",");
            }
            mBuilder.append("</Tops>\n");
            mBuilder.append("  <Bottoms>");
            for (int i = 0; i < children; ++i) {
                mBuilder.append(getChildAt(i).getBottom()).append(",");
            }
            mBuilder.append("</Bottoms>\n");

            mBuilder.append("  <FirstExpPos>").append(mFirstExpandedIndex).append("</FirstExpPos>\n");
            mBuilder.append("  <FirstExpBlankHeight>")
                    .append(getItemHeight(mFirstExpandedIndex, false) -
                            getItemHeight(mFirstExpandedIndex, true))
                    .append("</FirstExpBlankHeight>\n");
            mBuilder.append("  <SecondExpPos>").append(mSecondExpandedIndex).append("</SecondExpPos>\n");
            mBuilder.append("  <SecondExpBlankHeight>")
                    .append(getItemHeight(mSecondExpandedIndex, false) -
                            getItemHeight(mSecondExpandedIndex, true))
                    .append("</SecondExpBlankHeight>\n");
            mBuilder.append("  <SrcPos>").append(mFromIndex).append("</SrcPos>\n");
            mBuilder.append("  <SrcHeight>").append(mDragShadowHeight + getDividerHeight()).append("</SrcHeight>\n");
            mBuilder.append("  <ViewHeight>").append(getHeight()).append("</ViewHeight>\n");
            mBuilder.append("  <LastY>").append(mLastTouchPoint.second).append("</LastY>\n");
            mBuilder.append("  <FloatY>").append(mDragShadowCenterY).append("</FloatY>\n");
            mBuilder.append("  <ShuffleEdges>");
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

        private int mNumInBuffer = 0;
        private int mNumFlushes = 0;

        private boolean mTracking = false;
    }

    /**
     * Corresponds to the gesture required to trigger the removal of the item being dragged.
     *
     * <ul>
     *     <li>{@link RemoveGesture#NONE}: removal of the item being dragged is not enabled</li>
     *     <li>{@link RemoveGesture#FLING}: removal will be triggered with the fling gesture</li>
     *     <li>{@link RemoveGesture#SLIDE_OFF_SCREEN_RIGHT}: removal will be triggered with a swipe to the right</li>
     *     <li>{@link RemoveGesture#SLIDE_OFF_SCREEN_LEFT}: removal will be triggered with a swipe to the left</li>
     *     <li>{@link RemoveGesture#TRASH}: removal will be triggered with the trash gesture</li>
     * </ul>
     */
    private static enum RemoveGesture {
        NONE, FLING, SLIDE_OFF_SCREEN_RIGHT, SLIDE_OFF_SCREEN_LEFT, TRASH
    }

    /**
     * The image that floats above the {@link ListView} and representing the dragged item.
     */
    private ImageView mDragShadow;

    /**
     * The middle (on the y-axis) of the {@linkplain #mDragShadow drag shadow}.
     */
    private int mDragShadowCenterY;

    /**
     * The color set on the {@linkplain #mDragShadow drag shadow}'s background.
     *
     * <br/>XML attribute: float_background_color
     */
    private int mDragShadowBackgroundColor;

    /**
     * Transparency applied to {@linkplain #mDragShadow drag shadow}.
     *
     * <br/>XML attribute: float_alpha
     */
    private float mDragShadowAlpha;

    /**
     * Tracks the current position of {@linkplain #mDragShadow drag shadow} within the {@link ListView} while dragging,
     * and if it is dropped, then dragged item will land in this position.
     */
    private int mDragShadowIndex;

    /**
     * Manages the {@linkplain #mDragShadow drag shadow}.
     */
    private WindowManager mWindowManager;

    /**
     * LayoutParams for the {@linkplain #mDragShadow drag shadow}.
     */
    private WindowManager.LayoutParams mWindowParams;

    /**
     * The amount to scroll during the next layout pass. Used only during drag-scrolling, not during the standard
     * {@link ListView} scrolling.
     */
    private int mScrollY = 0;

    /**
     * The first expanded {@link ListView} index that represents the drop slot tracking the
     * {@linkplain #mDragShadow drag shadow}.
     */
    private int mFirstExpandedIndex;

    /**
     * The second expanded {@link ListView} index that represents the drop slot tracking the
     * {@linkplain #mDragShadow drag shadow}. May equal {@link #mFirstExpandedIndex} if there is no slide shuffle 
     * occurring, otherwise it is equal to {@link #mFirstExpandedIndex} + 1.
     */
    private int mSecondExpandedIndex;

    /**
     * Flag set if slide shuffling is enabled.
     *
     * <br/>XML attribute: slide_shuffle_speed (1 = false, otherwise true)
     */
    private boolean mAnimateSlideShuffle = false;

    /**
     * The original index of the item being dragged.
     */
    private int mFromIndex;

    /**
     * The offset within the {@link #mDragShadow} where the drag was initiated.
     */
    private Pair<Integer, Integer> mDragPoint;

    /**
     * The offset of the {@link ListView} within the screen, used to convert between absolute coordinates and
     * coordinates relative to the {@link ListView}
     */
    private Pair<Integer,Integer> mScreenOffset;

    /**
     * The {@link OnDragListener} that receives callbacks when the {@link #mDragShadow drag shadow} intersects a new
     * index.
     */
    private OnDragListener mOnDragListener;

    /**
     * The {@link OnDropListener} that receives callbacks when the {@link #mDragShadow drag shadow} is dropped.
     */
    private OnDropListener mOnDropListener;

    /**
     * The {@link OnRemoveListener} that receives callbacks when one of the {@link RemoveGesture}s is performed on the
     * {@link #mDragShadow drag shadow} indicating that the item being dragged should be removed from the underlying
     * {@link ListAdapter}.
     */
    private OnRemoveListener mOnRemoveListener;

    /**
     * Used to detect a remove gesture.
     */
    private GestureDetector mGestureDetector;

    /**
     * The current remove mode.
     *
     * <br/>XML attribute: remove_mode
     */
    private RemoveGesture mRemoveGesture = RemoveGesture.NONE;

    /**
     * Reusable {@link Rect} to reduce object allocation.
     */
    private Rect mTempRect = new Rect();

    /**
     * Reusable int[] for holding a pair of coordinates to reduce object allocation.
     */
    private int[] mTempLoc = new int[2];

    /**
     * The generated image used the {@link #mDragShadow drag shadow} is set to display.
     */
    private Bitmap mDragShadowBitmap;

    /**
     * The height (in pixels) that the dragged item's original view in the {@link ListView} is collapsed to during a
     * drag. <b>NOTE:</b> this value must be greater than zero.
     *
     * <br/>XML attribute: collapsed_height (must be > 0)
     */
    private int mItemCollapsedHeight = 1;

    /**
     * The {@link #mDragShadow drag shadow}'s height. Stored for the purpose of providing the tracking drop slot.
     */
    private int mDragShadowHeight;

    /**
     * The middle of the {@link #mDragShadow drag shadow}'s height. Stored for the purpose of providing the tracking
     * drop slot.
     */
    private int mDragShadowMiddleHeight;

    /**
     * The image that is used to represent the trash can for the
     * {@linkplain RemoveGesture#TRASH trash can removal gesture}
     */
    private Drawable mTrashCan;

    /**
     * Sample {@link View}s used to calculate the height of {@link ListView} items that are off-screen.
     */
    private View[] mSampleViewTypes = new View[1];

    /**
     * Encapsulates the drag scroll operation.
     */
    private DragScroller mDragScroller;

    /**
     * Determines the start of the upward drag-scroll region at the top of the {@link ListView}. Specified by a
     * proportion of the {@link ListView}'s height to be screen resolution agnostic.
     *
     * <br/>XML attribute: drag_scroll_start
     */
    private float mDragUpScrollStartProportion = 1.0f / 3.0f;

    /**
     * Determines the start of the downward drag-scroll region at the bottom of the {@link ListView}. Specified by a
     * proportion of the {@link ListView}'s height to be screen resolution agnostic. Set to be identical to the
     * {@link #mDragUpScrollStartProportion}
     *
     * <br/>XML attribute: drag_scroll_start
     */
    private float mDragDownScrollStartProportion = mDragUpScrollStartProportion;

    /*
     * The following are calculated from mDragUpScrollStartProportion and mDragDownScrollStartProportion.
     */
    private int mUpScrollStartY;
    private int mDownScrollStartY;
    private float mDownScrollStartYF;
    private float mUpScrollStartYF;

    /**
     * Calculated using {@link #mUpScrollStartYF} and {@link ListView}'s height.
     */
    private float mDragUpScrollHeight;

    /**
     * Calculated using {@link #mDownScrollStartYF} and {@link ListView}'s height.
     */
    private float mDragDownScrollHeight;


    /**
     * Maximum drag-scroll speed (in pixels/ms). Only used with the default linear drag-scroll profile.
     *
     * <br/>XML attribute: max_drag_scroll_speed
     */
    private float mMaxScrollSpeed = 0.3f;

    /**
     * Defines the scroll speed during a drag-scroll. This default {@link DragScrollProfile} uses a simple linear
     * profile where scroll speed increases linearly as the {@link #mDragShadow drag shadow} approaches the top or
     * bottom of the {@link ListView}.
     */
    private DragScrollProfile mScrollProfile = new DragScrollProfile() {
        @Override
        public float getSpeed(float relativePosition, long elapsedTime) {
            return mMaxScrollSpeed * relativePosition;
        }
    };

    /**
     * The previously observed touch point.
     */
    private Pair<Integer, Integer> mLastTouchPoint;

    /**
     * The touch y-coordinate that initiated the drag-sort.
     */
    private int mDragInitiationPositionY;

    /**
     * Determines when to start a slide shuffle animation. That is, it defines how close to the edge of the drop slot 
     * the {@link #mDragShadow drag shadow} must be to initiate the slide.
     */
    private float mSlideInitiationThreshold = 0.25f;

    /**
     * Number between 0 and 1 (inclusive) specifying the position of an item during a slide (only used if 
     * {@linkplain #mAnimateSlideShuffle slide animations} are enabled). Values close to 1 mean the item is
     * at the top of the {@link #mSlideInitiationThreshold} (nearly full blank item is directly below).
     */
    private float mSlideAnimationItemPosition = 0.0f;

    /**
     * Wraps the provided {@link ListAdapter}. This is used to wrap the {@link View}s returned by 
     * {@link ListAdapter#getView(int, android.view.View, android.view.ViewGroup)} with a {@link RelativeLayout} that
     * expands and collapses to simulate the item shuffling.
     */
    private AdapterWrapper mAdapterWrapper;

    /**
     * Turn on custom debugger.
     *
     * <br/>XML attribute: track_drag_sort
     */
    private boolean mTrackDragSort = false;

    /**
     * Debugging class.
     */
    private DragSortTracker mDragSortTracker;

    /**
     * Enables/Disables {@link android.widget.ListView#requestLayout()}s.
     */
    private boolean mBlockLayoutRequests = false;
}
