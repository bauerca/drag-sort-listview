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
     * The first expanded {@link ListView} position that represents the drop slot tracking the
     * {@linkplain #mDragShadow drag shadow}.
     */
    private int mFirstExpPos;

    /**
     * The second expanded {@link ListView} position that represents the drop slot tracking the
     * {@linkplain #mDragShadow drag shadow}. May equal {@link #mFirstExpPos} if there is no slide shuffle occurring,
     * otherwise it is equal to {@link #mFirstExpPos} + 1.
     */
    private int mSecondExpPos;

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
     * Last touch x.
     */
    private int mLastX;

    /**
     * Last touch y.
     */
    private int mLastY;

    /**
     * The touch y-coordinate that initiated the drag-sort.
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
     *
     * <br/>XML attribute: track_drag_sort
     */
    private boolean mTrackDragSort = false;

    /**
     * Debugging class.
     */
    private DragSortTracker mDragSortTracker;



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

            mSlideRegionFrac = Math.max(0.0f, Math.min(1.0f, 1.0f - a.getFloat(R.styleable.DragSortListView_slide_shuffle_speed, 0.75f)));
            mAnimateSlideShuffle = mSlideRegionFrac > 0.0f;

            mRemoveGesture = a.getInt(R.styleable.DragSortListView_remove_mode, -1);

            float frac = a.getFloat(R.styleable.DragSortListView_drag_scroll_start,
                    mDragUpScrollStartProportion);
            setDragScrollStart(frac);

            mMaxScrollSpeed = a.getFloat(
                    R.styleable.DragSortListView_max_drag_scroll_speed, mMaxScrollSpeed);

            a.recycle();
        }

        //Log.d("mobeta", "collapsed height=" + mItemCollapsedHeight);

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
     * @return The ListAdapter set as the argument of {@link #setAdapter(android.widget.ListAdapter)}
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
                              ArrayList<FixedViewInfo> footerViewInfos, ListAdapter adapter) {
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

            //Log.d("mobeta", "getView; position="+position);

            //Log.d("mobeta", "getView: position="+position+" convertView="+convertView);
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
                        new AbsListView.LayoutParams(ViewGroup.LayoutParams.FILL_PARENT,
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

                if (expPosition > mFromIndex) {
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

        if (mDragShadow != null) {
            // draw the divider over the expanded item
            if (mFirstExpPos != mFromIndex) {
                drawDivider(mFirstExpPos, canvas);
            }
            if (mSecondExpPos != mFirstExpPos && mSecondExpPos != mFromIndex) {
                drawDivider(mSecondExpPos, canvas);
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

            return measureItemAndGetHeight(v, ofChild);
        }

    }

    private void printPosData() {
        Log.d("mobeta", "mFromIndex=" + mFromIndex + " mFirstExpPos=" + mFirstExpPos + " mSecondExpPos=" + mSecondExpPos);
    }

    /**
     * Get the shuffle edge for item at position when top of
     * item is at y-coord top
     *
     * @param position
     * @param top
     *
     * @return Shuffle line between position-1 and position (for
     * the given view of the list; that is, for when top of item at
     * position has y-coord of given `top`). If
     * floating View (treated as horizontal line) is dropped
     * immediately above this line, it lands in position-1. If
     * dropped immediately below this line, it lands in position.
     */
    private int getShuffleEdge(int position, int top) {

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

        if (mSecondExpPos <= mFromIndex) {
            // items are expanded on and/or above the source position

            if (position <= mFirstExpPos) {
                edge = top + (mDragShadowHeight - divHeight - getItemHeight(position - 1)) / 2;
            } else if (position == mSecondExpPos) {
                if (position == mFromIndex) {
                    edge = top + getItemHeight(position) - (2*divHeight + getItemHeight(position - 1, true) + mDragShadowHeight) / 2;
                } else {
                    int blankHeight = getItemHeight(position) - getItemHeight(position, true) - divHeight;
                    edge = top + (blankHeight - getItemHeight(position - 1)) / 2;
                }
            } else if (position < mFromIndex) {
                int childHeight = getItemHeight(position - 1, true);
                edge = top - (childHeight + 2*divHeight + mDragShadowHeight) / 2;
            } else if (position == mFromIndex) {
                int childHeight = getItemHeight(position - 1, true);
                edge = top + mItemCollapsedHeight - (2*divHeight + childHeight + mDragShadowHeight) / 2;
            } else {
                edge = top + (getItemHeight(position) - mDragShadowHeight) / 2;
            }
        } else {
            // items are expanded on and/or below the source position

            if (position <= mFromIndex) {
                edge = top + (mDragShadowHeight - getItemHeight(position - 1) - divHeight) / 2;
            } else if (position <= mFirstExpPos) {
                edge = top + (getItemHeight(position, true) + divHeight + mDragShadowHeight) / 2;
                if (position - 1 == mFromIndex) {
                    edge -= mItemCollapsedHeight + divHeight;
                }
            } else if (position == mSecondExpPos) {
                int blankAbove;
                if (position - 1 == mFromIndex) {
                    blankAbove = getItemHeight(position - 1);
                } else {
                    blankAbove = getItemHeight(position - 1) - getItemHeight(position - 1, true) - divHeight;
                }
                edge = top - blankAbove - divHeight + (getItemHeight(position, true) + divHeight + mDragShadowHeight) / 2;
                //int height = getItemHeight(position);
                //int blankHeight = height - getItemHeight(position, true) - divHeight;
                //edge = top + (height - (mDragShadowHeight - blankHeight)) / 2;
            } else {
                edge = top + (getItemHeight(position) - mDragShadowHeight) / 2;
            }
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

        int edge = getShuffleEdge(startPos, startTop);
        int lastEdge = edge;

        //Log.d("mobeta", "float mid="+mDragShadowCenterY);

        int itemPos = startPos;
        int itemTop = startTop;
        if (mDragShadowCenterY < edge) {
            // scanning up for float position
            //Log.d("mobeta", "  edge="+edge);
            while (itemPos >= 0) {
                itemPos--;

                //if (itemPos <= 0)
                if (itemPos == 0) {
                    edge = itemTop - getItemHeight(itemPos);
                    //itemPos = 0;
                    break;
                }

                itemTop -= getItemHeight(itemPos);
                edge = getShuffleEdge(itemPos, itemTop);
                //Log.d("mobeta", "  edge="+edge);

                if (mDragShadowCenterY >= edge) {
                    break;
                }

                lastEdge = edge;
            }
        } else {
            // scanning down for float position
            //Log.d("mobeta", "  edge="+edge);
            final int count = getCount();
            while (itemPos < count) {
                if (itemPos == count - 1) {
                    edge = itemTop + getItemHeight(itemPos);
                    break;
                }

                itemTop += getItemHeight(itemPos);
                edge = getShuffleEdge(itemPos + 1, itemTop);
                //Log.d("mobeta", "  edge="+edge);

                // test for hit
                if (mDragShadowCenterY < edge) {
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
            //Log.d("mobeta", "edgeTop="+edgeTop+" edgeBot="+edgeBottom);

            int slideRgnHeight = (int) (mSlideRegionFrac * edgeToEdge);
            float slideRgnHeightF = (float) slideRgnHeight;
            int slideEdgeTop = edgeTop + slideRgnHeight;
            int slideEdgeBottom = edgeBottom - slideRgnHeight;


            // Three regions
            if (mDragShadowCenterY < slideEdgeTop) {
                mFirstExpPos = itemPos - 1;
                mSecondExpPos = itemPos;
                mSlideFrac = 0.5f * ((float) (slideEdgeTop - mDragShadowCenterY)) / slideRgnHeightF;
                //Log.d("mobeta", "firstExp="+mFirstExpPos+" secExp="+mSecondExpPos+" slideFrac="+mSlideFrac);
            } else if (mDragShadowCenterY < slideEdgeBottom) {
                mFirstExpPos = itemPos;
                mSecondExpPos = itemPos;
            } else {
                mFirstExpPos = itemPos;
                mSecondExpPos = itemPos + 1;
                mSlideFrac = 0.5f * (1.0f + ((float) (edgeBottom - mDragShadowCenterY)) / slideRgnHeightF);
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

        if (itemPos != mDragShadowIndex) {
            if (mOnDragListener != null) {
                mOnDragListener.drag(mDragShadowIndex - numHeaders, itemPos - numHeaders);
            }

            mDragShadowIndex = itemPos;
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
        if (mOnRemoveListener != null && mGestureDetector == null) {
            if (mRemoveGesture == RemoveGesture.FLING) {
                mGestureDetector = new GestureDetector(getContext(), new SimpleOnGestureListener() {
                    @Override
                    public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX,
                                           float velocityY) {
                        if (mDragShadow != null) {
                            if (velocityX > 1000) {
                                Rect r = mTempRect;
                                mDragShadow.getDrawingRect(r);
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
        if (mOnDragListener != null || mOnDropListener != null) {
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

                    mDragPoint = Pair.create(item.getLeft(), item.getTop());
                    final int rawX = (int) ev.getRawX();
                    final int rawY = (int) ev.getRawY();
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
                        //item.setDrawingCacheQuality(View.DRAWING_CACHE_QUALITY_HIGH);
                        item.setDrawingCacheEnabled(true);
                        // Create a copy of the drawing cache so that it does not get recycled
                        // by the framework when the list tries to clean up memory
                        Bitmap bitmap = Bitmap.createBitmap(item.getDrawingCache());
                        item.setDrawingCacheEnabled(false);

                        mDragShadowHeight = item.getHeight();
                        mDragShadowMiddleHeight = mDragShadowHeight / 2;

                        mFirstExpPos = itemnum;
                        mSecondExpPos = itemnum;
                        mFromIndex = itemnum;
                        mDragShadowIndex = itemnum;

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
            mDragDownScrollStartProportion = 0.5f;
        } else {
            mDragDownScrollStartProportion = lowerFrac;
        }

        if (upperFrac > 0.5f) {
            mDragUpScrollStartProportion = 0.5f;
        } else {
            mDragUpScrollStartProportion = upperFrac;
        }

        if (getHeight() != 0) {
            updateScrollStarts();
        }
    }

    private void updateScrollStarts() {
        final int padTop = getPaddingTop();
        final int listHeight = getHeight() - padTop - getPaddingBottom();
        float heightF = (float) listHeight;

        mUpScrollStartYF = padTop + mDragUpScrollStartProportion * heightF;
        mDownScrollStartYF = padTop + (1.0f - mDragDownScrollStartProportion) * heightF;

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
            if (mOnRemoveListener != null) {
                mOnRemoveListener.remove(mFromIndex - getHeaderViewsCount());
            }
        } else {
            if (mOnDropListener != null && mDragShadowIndex >= 0 && mDragShadowIndex < getCount()) {
                final int numHeaders = getHeaderViewsCount();
                mOnDropListener.drop(mFromIndex - numHeaders, mDragShadowIndex - numHeaders);
            }

            int oldSrcPos = mFromIndex;

            mFromIndex = -1;
            mFirstExpPos = -1;
            mSecondExpPos = -1;
            mDragShadowIndex = -1;

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
                //Log.d("mobeta", "top="+top+" fvh="+mDragShadowHeight);
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
        //	mDragSortTracker.appendState();
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

        boolean isSliding = mAnimateSlideShuffle && mFirstExpPos != mSecondExpPos;

        if (position == mFromIndex) {
            if (mFromIndex == mFirstExpPos) {
                if (isSliding) {
                    height = Math.max((int) (mSlideFrac * mDragShadowHeight), mItemCollapsedHeight);
                } else {
                    height = mDragShadowHeight;
                }
            } else if (mFromIndex == mSecondExpPos) {
                // if gets here, we know an item is sliding
                height = Math.max(mDragShadowHeight - (int) (mSlideFrac * mDragShadowHeight), mItemCollapsedHeight);
            } else {
                height = mItemCollapsedHeight;
            }
        } else if (position == mFirstExpPos || position == mSecondExpPos) {

            int childHeight;
            if (needsMeasure) {
                childHeight = measureItemAndGetHeight(v, true);
            } else {
                childHeight = ((ViewGroup) v).getChildAt(0).getHeight();
            }

            if (position == mFirstExpPos) {
                if (isSliding) {
                    int blankHeight = (int) (mSlideFrac * mDragShadowHeight);
                    height = childHeight + divHeight + blankHeight;
                } else {
                    height = childHeight + divHeight + mDragShadowHeight;
                }
            } else { //position=mSecondExpPos
                // we know an item is sliding (b/c 2ndPos != 1stPos)
                int blankHeight = mDragShadowHeight - (int) (mSlideFrac * mDragShadowHeight);
                height = childHeight + divHeight + blankHeight;
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
            if (position < mFromIndex) {
                ((RelativeLayout) v).setGravity(Gravity.BOTTOM);
            } else if (position > mFromIndex) {
                ((RelativeLayout) v).setGravity(Gravity.TOP);
            }
        }


        // Finally adjust item visibility

        int oldVis = v.getVisibility();
        int vis = View.VISIBLE;

        if (position == mFromIndex && mDragShadow != null) {
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

    private void doDragScroll(int oldFirstExpPos) {
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

        if (moveHeightBefore != moveHeightAfter) {
            // some item height must change above move position
            // for adjustment to be required
            if (movePos > oldFirstExpPos || movePos > mFirstExpPos) {
                top += moveHeightBefore - moveHeightAfter;
            }
        }

        setSelectionFromTop(movePos, top - padTop);

        mScrollY = 0;
    }

    @Override
    protected void layoutChildren() {

        if (mDragShadow != null) {
            //Log.d("mobeta", "layout children");
            int oldFirstExpPos = mFirstExpPos;

            mBlockLayoutRequests = true;

            if (updatePositions()) {
                adjustAllItems();
            }

            if (mScrollY != 0) {
                doDragScroll(oldFirstExpPos);
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
        if ((mOnDragListener != null || mOnDropListener != null) && mDragShadow != null) {
            int action = ev.getAction();

            final int x = (int) ev.getX();
            final int y = (int) ev.getY();

            switch (action & MotionEvent.ACTION_MASK) {
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    Rect r = mTempRect;
                    mDragShadow.getDrawingRect(r);

                    if (mRemoveGesture == RemoveGesture.SLIDE_OFF_SCREEN_RIGHT && ev.getX() > r.right * 3 / 4) {
                        dropFloatView(true);
                    } else if (mRemoveGesture == RemoveGesture.SLIDE_OFF_SCREEN_LEFT && ev.getX() < r.right * 1 / 4) {
                        dropFloatView(true);
                    } else {
                        dropFloatView(false);
                    }

                    break;

                case MotionEvent.ACTION_DOWN:
                    // Ignore action
                    break;
                case MotionEvent.ACTION_MOVE:

                    // make src item invisible on first move away from pickup
                    // point. Reduces flicker.
                    if (mLastY == mDownY) {
                        // should we be this careful?
                        final View item = getChildAt(mFromIndex - getFirstVisiblePosition());
                        if (item != null) {
                            item.setVisibility(INVISIBLE);
                        }
                    }

                    //Log.d("mobeta", "move");
                    dragView(x, y);

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
        mWindowParams.x = x - mDragPoint.first + mScreenOffset.first;
        mWindowParams.y = y - mDragPoint.second + mScreenOffset.second;

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
        mWindowParams.alpha = mDragShadowAlpha;

        Context context = getContext();
        ImageView v = new ImageView(context);
        v.setBackgroundColor(mDragShadowBackgroundColor);
        v.setPadding(0, 0, 0, 0);
        v.setImageBitmap(bm);
        mDragShadowBitmap = bm;

        mWindowManager = (WindowManager)context.getSystemService(Context.WINDOW_SERVICE);
        mWindowManager.addView(v, mWindowParams);
        mDragShadow = v;

        if (mTrackDragSort) {
            mDragSortTracker.startTracking();
        }
    }

    private void dragView(int x, int y) {
        //Log.d("mobeta", "float view pure x=" + x + " y=" + y);
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
        if (y - mDragPoint.second < topLimit) {
            mWindowParams.y = mScreenOffset.second + topLimit;
        } else if (y - mDragPoint.second + mDragShadowHeight > bottomLimit) {
            mWindowParams.y = mScreenOffset.second + bottomLimit - mDragShadowHeight;
        } else {
            mWindowParams.y = y - mDragPoint.second + mScreenOffset.second;
        }
        // get midpoint of floating view (constrained to ListView bounds)
        mDragShadowCenterY = mWindowParams.y + mDragShadowMiddleHeight - mScreenOffset.second;
        //Log.d("mobeta", "float view taint x=" + mWindowParams.x + " y=" + mWindowParams.y);
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


    private void removeFloatView() {

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

    public void setTrashcan(Drawable trash) {
        mTrashCan = trash;
        mRemoveGesture = RemoveGesture.TRASH;
    }

    public void setDragListener(OnDragListener l) {
        mOnDragListener = l;
    }

    /**
     * This better reorder your ListAdapter! DragSortListView does not do this
     * for you; doesn't make sense to. Make sure
     * {@link BaseAdapter#notifyDataSetChanged()} or something like it is
     * called in your implementation.
     *
     * @param l
     */
    public void setDropListener(OnDropListener l) {
        mOnDropListener = l;
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
    public void setRemoveListener(OnRemoveListener l) {
        mOnRemoveListener = l;
    }

    public interface OnDragListener {
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
    public interface OnDropListener {
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
    public interface OnRemoveListener {
        public void remove(int which);
    }

    public interface OnDragSortListenerOnOn extends OnDropListener, OnDragListener, OnRemoveListener {}

    public void setDragSortListener(OnDragSortListenerOnOn l) {
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

            mBuilder.append("  <FirstExpPos>").append(mFirstExpPos).append("</FirstExpPos>\n");
            mBuilder.append("  <FirstExpBlankHeight>")
                    .append(getItemHeight(mFirstExpPos, false) -
                            getItemHeight(mFirstExpPos, true))
                    .append("</FirstExpBlankHeight>\n");
            mBuilder.append("  <SecondExpPos>").append(mSecondExpPos).append("</SecondExpPos>\n");
            mBuilder.append("  <SecondExpBlankHeight>")
                    .append(getItemHeight(mSecondExpPos, false) -
                            getItemHeight(mSecondExpPos, true))
                    .append("</SecondExpBlankHeight>\n");
            mBuilder.append("  <SrcPos>").append(mFromIndex).append("</SrcPos>\n");
            mBuilder.append("  <SrcHeight>").append(mDragShadowHeight + getDividerHeight()).append("</SrcHeight>\n");
            mBuilder.append("  <ViewHeight>").append(getHeight()).append("</ViewHeight>\n");
            mBuilder.append("  <LastY>").append(mLastY).append("</LastY>\n");
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


    }



}
