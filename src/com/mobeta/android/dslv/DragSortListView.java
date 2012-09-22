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
	
	// Drag states
	public final static int NO_DRAG = 0;
	public final static int SRC_EXP = 1;
	public final static int SRC_ABOVE = 2;
	public final static int SRC_BELOW = 3;
	public final static int EXPANDED_SRC = 4;
	public final static int EXPANDED_NEAR = 5;
	public final static int EXPANDED_FAR = 6;
	public final static int SLIDING_NEAR = 7;
	public final static int SLIDING_FAR = 8;
	
	private int mDragState = NO_DRAG;
	
	private boolean mInvertDragState = false;
	
	private ImageView mFloatView;
	private int mFloatViewY;
  private int mFloatBGColor;
	private float mFloatAlpha;
	private int mFloatPos;
	private WindowManager mWindowManager;
	private WindowManager.LayoutParams mWindowParams;
	
	/**
	 * At which position is the item currently being dragged. All drag positions
	 * are absolute list view positions; e.g. if there is one header view, and
	 * mDragPos = 1, then mDragPos points to the first list item after the header.
	 */
	private int mFirstExpPos;
	private int mSecondExpPos;

	private int mMovePos;
	private int mMoveTop;

	private boolean mAnimate = false;

	/**
	 * At which position was the item being dragged originally
	 */
	private int mSrcPos;
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
  private int mFloatViewHeight;
  private int mFloatViewHeightHalf;
	private Drawable mTrashcan;

	private View[] mSampleViewTypes = new View[1];

	private DragScroller mDragScroller;
	private float mDragUpScrollStartFrac = 1.0f / 3.0f;
	private float mDragDownScrollStartFrac = 1.0f / 3.0f;
  private float mDragUpScrollHeight;
	private float mDragDownScrollHeight;
	
	private float mMaxScrollSpeed = 0.3f; // pixels per millisec

  private boolean mTrackDragSort = false;
	
	private DragScrollProfile mScrollProfile = new DragScrollProfile() {
		@Override
		public float getSpeed(float w, long t) {
			return mMaxScrollSpeed * w;
		}
	};
	
	private int mLastX;
	private int mLastY;
	private int mDownY;

	private float mSlideRegionFrac = 0.2f;

	/**
	 * Number between 0 and 1 indicating the location of
	 * an item during a slide (only used if drag-sort animations
	 * are turned on). Nearly 1 means the item is 
	 * at the top of the slide region (nearly full blank item
	 * is directly below).
	 */
	private float mSlideFrac = 0.0f;
	
	private AdapterWrapper mAdapterWrapper;

	private DragSortTracker mDragSortTracker;

	public DragSortListView(Context context, AttributeSet attrs) {
		super(context, attrs);
		mRemoveMode = FLING;
		mTouchSlop = ViewConfiguration.get(context).getScaledTouchSlop();

    if (attrs != null) {
      TypedArray a = getContext().obtainStyledAttributes(attrs,
        R.styleable.DragSortListView, 0, 0);

      mItemHeightCollapsed = a.getDimensionPixelSize(
        R.styleable.DragSortListView_collapsed_height, mItemHeightCollapsed);

      mTrackDragSort = a.getBoolean(
        R.styleable.DragSortListView_track_drag_sort, false);
			if (mTrackDragSort) {
				mDragSortTracker = new DragSortTracker();
			}

      mFloatBGColor = a.getColor(R.styleable.DragSortListView_float_background_color,
        0x00000000);

      // alpha between 0 and 255, 0=transparent, 255=opaque
      mFloatAlpha = a.getFloat(R.styleable.DragSortListView_float_alpha, 1.0f);

			mAnimate = a.getBoolean(
        R.styleable.DragSortListView_animate, false);

      mRemoveMode = a.getInt(R.styleable.DragSortListView_remove_mode, -1);

      float frac = a.getFloat(R.styleable.DragSortListView_drag_scroll_start,
          mDragUpScrollStartFrac);
      setDragScrollStart(frac);

			mMaxScrollSpeed = a.getFloat(
        R.styleable.DragSortListView_max_drag_scroll_speed, mMaxScrollSpeed);

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

		dispatchTouchEvent(MotionEvent.obtain(0, 0, MotionEvent.ACTION_CANCEL, 0f, 0f, 0));
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

      //Log.d("mobeta", "getView: position="+position+" convertView="+convertView);
      if (convertView != null) {

        v = (RelativeLayout) convertView;
        View oldChild = v.getChildAt(0);

        //child = super.getView(position, oldChild, v);
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

		if (mFloatView != null && mDragState != NO_DRAG) {
			// draw the divider over the expanded item
			if (mFirstExpPos != mSrcPos) {
				drawDivider(mFirstExpPos, canvas);
			}
			if (mSecondExpPos != mFirstExpPos && mSecondExpPos != mSrcPos) {
				drawDivider(mSecondExpPos, canvas);
			}
		}
	}

	private int measureItemAndGetHeight(View item, boolean ofChild) {
		ViewGroup.LayoutParams lp = item.getLayoutParams();

		final int height = lp == null ? 0 : lp.height;
		if (height > 0) {
		  return height;
		} else {
		  int spec = MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED);
		  item.measure(spec, spec);
			if (ofChild) {
				return ((ViewGroup) item).getChildAt(0).getMeasuredHeight();
			} else {
		  	return item.getMeasuredHeight();
			}
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
		Log.d("mobeta", "mSrcPos="+mSrcPos+" mFirstExpPos="+mFirstExpPos+" mSecondExpPos="+mSecondExpPos);
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

/*
		int posAbove;
		int posBelow;
		int srcPos;
		int firstExpPos;
		int secondExpPos;

		if (mInvertDragState) {
			posAbove = position;
			position--;
			posBelow = position - 1;
			srcPos = 2 * position - mSrcPos;
			firstExpPos = 2 * position - mFirstExpPos;
			secondExpPos = 2 * position - mSecondExpPos;

			//top += getItemHeight(position);
		} else {
			posAbove = position - 1;
			posBelow = position + 1;
			srcPos = mSrcPos;
			firstExpPos = mFirstExpPos;
			secondExpPos = mSecondExpPos;
		}
*/

		int edge;

		if (mSecondExpPos <= mSrcPos) {
			// items are expanded on and/or above the source position

			if (position <= mFirstExpPos) {
				edge = top + (mFloatViewHeight - divHeight - getItemHeight(position - 1)) / 2;
			} else if (position == mSecondExpPos) {
				if (position == mSrcPos) {
					edge = top + getItemHeight(position) - (2*divHeight + getItemHeight(position - 1, true) + mFloatViewHeight) / 2;
				} else {
					int blankHeight = getItemHeight(position) - getItemHeight(position, true) - divHeight;
					edge = top + (blankHeight - getItemHeight(position - 1)) / 2;
				}
			} else if (position < mSrcPos) {
				int childHeight = getItemHeight(position - 1, true);
				edge = top - (childHeight + 2*divHeight + mFloatViewHeight) / 2;
			} else if (position == mSrcPos) {
				int childHeight = getItemHeight(position - 1, true);
				edge = top + mItemHeightCollapsed - (2*divHeight + childHeight + mFloatViewHeight) / 2;
			} else {
				edge = top + (getItemHeight(position) - mFloatViewHeight) / 2;
			}
		} else {
			// items are expanded on and/or below the source position

			if (position <= mSrcPos) {
				edge = top + (mFloatViewHeight - getItemHeight(position - 1) - divHeight) / 2;
			} else if (position <= mFirstExpPos) {
				edge = top + (getItemHeight(position, true) + divHeight + mFloatViewHeight) / 2;
				if (position - 1 == mSrcPos) {
					edge -= mItemHeightCollapsed + divHeight;
				}
			} else if (position == mSecondExpPos) {
				int blankAbove;
				if (position - 1 == mSrcPos) {
					blankAbove = getItemHeight(position - 1);
				} else {
					blankAbove = getItemHeight(position - 1) - getItemHeight(position - 1, true) - divHeight;
				}
				edge = top - blankAbove - divHeight + (getItemHeight(position, true) + divHeight + mFloatViewHeight) / 2;
				//int height = getItemHeight(position);
				//int blankHeight = height - getItemHeight(position, true) - divHeight;
				//edge = top + (height - (mFloatViewHeight - blankHeight)) / 2;
			} else {
				edge = top + (getItemHeight(position) - mFloatViewHeight) / 2;
			}
		}

		return edge;

	}


  private boolean updatePositions() {


		//Log.d("mobeta", "mMovePos="+mMovePos+" mMoveTop="+mMoveTop);
		int edge = getShuffleEdge(mMovePos, mMoveTop);
		int lastEdge = edge;

		//Log.d("mobeta", "float mid="+mFloatViewY);
		
		int itemPos = mMovePos;
		int itemTop = mMoveTop;
		if (mFloatViewY < edge) {
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
				
				if (mFloatViewY >= edge) {
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
		
		if (itemPos < numHeaders) {
			itemPos = numHeaders;
			mFirstExpPos = itemPos;
			mSecondExpPos = itemPos;
		} else if (itemPos >= getCount() - numFooters) {
			itemPos = getCount() - numFooters - 1;
			mFirstExpPos = itemPos;
			mSecondExpPos = itemPos;
		} else if (mAnimate) {
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

			int slideRgnHeight = (int) (mSlideRegionFrac * edgeToEdge);
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

		if (mFirstExpPos != oldFirstExpPos || mSecondExpPos != oldSecondExpPos || mSlideFrac != oldSlideFrac) {
			updated = true;
		}

		if (itemPos != mFloatPos) {
			if (mDragListener != null) {
				mDragListener.drag(mFloatPos - numHeaders, itemPos - numHeaders);
			}

			mFloatPos = itemPos;

			updated = true;

			mInvertDragState = mSecondExpPos > mSrcPos;
			
			if (mFloatPos == mSrcPos) {
				mDragState = SRC_EXP;
			} else if (mFloatPos < mSrcPos) {
				mDragState = SRC_BELOW;
			} else {
				mDragState = SRC_ABOVE;
			}
		}

		return updated;
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

			mDragState = NO_DRAG;
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
				setSelectionFromTop(firstPos, top - getPaddingTop() + mFloatViewHeight);
			}

			removeFloatView();
		}

		if (mTrackDragSort) {
			mDragSortTracker.stopTracking();
		}
	}


	private void adjustAllItems() {
		final int firstPos = getFirstVisiblePosition();
		final int numChildren = getChildCount();
		for (int i = 0; i < numChildren; ++i) {
			View v = getChildAt(i);
			if (v != null) {
				adjustItem(firstPos + i, v, false);
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

		boolean isSliding = mAnimate && mFirstExpPos != mSecondExpPos;

		if (position == mSrcPos) {
			if (mSrcPos == mFirstExpPos) {
				if (isSliding) {
					height = Math.max((int) (mSlideFrac * mFloatViewHeight), mItemHeightCollapsed);
				} else {
					height = mFloatViewHeight;
				}
			} else if (mSrcPos == mSecondExpPos) {
				// if gets here, we know an item is sliding
				height = Math.max(mFloatViewHeight - (int) (mSlideFrac * mFloatViewHeight), mItemHeightCollapsed);
			} else {
				height = mItemHeightCollapsed;
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
					int blankHeight = (int) (mSlideFrac * mFloatViewHeight);
					height = childHeight + divHeight + blankHeight;
				} else {
					height = childHeight + divHeight + mFloatViewHeight;
				}
			} else { //position=mSecondExpPos
				// we know an item is sliding (b/c 2ndPos != 1stPos)
				int blankHeight = mFloatViewHeight - (int) (mSlideFrac * mFloatViewHeight);
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
			if (position < mSrcPos) {
				((RelativeLayout) v).setGravity(Gravity.BOTTOM);
			} else if (position > mSrcPos) {
				((RelativeLayout) v).setGravity(Gravity.TOP);
			}
		}

		
		// Finally adjust item visibility

		int oldVis = v.getVisibility();
		int vis = oldVis;

		if (position == mSrcPos && mDragState != NO_DRAG) {
			if (vis == View.VISIBLE) {
				vis = View.INVISIBLE;
			}
		} else if (vis == View.INVISIBLE) {
			vis = View.VISIBLE;
		}

		if (vis != oldVis) {
			v.setVisibility(vis);
		}
	}

/*

		// cases:
		//   1. normal item
		//   2. src item, fully collapsed
		//   3. src item, partially collapsed, above sliding item
		//   4. src item, partially collapsed, above sliding item
		//   5. fully expanded item (no slide happening)
		//   6. partially expanded item, provides blank above sliding item
		//   7. partially expanded item, provides blank below sliding item

		// some useful pre conditions
		boolean isSliding = mAnimate && mShuffleEdge != -1;

		// conditions for 1.
		//   a. any drag state AND position != src,exp1,exp2
		//   b. drag state w/out collapsed/expanded items
		boolean allNormal = (mDragState != SRC_ABOVE && mDragState != SRC_BELOW) && !isSliding;
		boolean posIsNormal = !posIsSrc && !posIsFirstExp && !posIsSecondExp;
		boolean normal = allNormal || posIsNormal;
		if (((mDragState != SRC_ABOVE && mDragState != SRC_BELOW) && !isSliding) || !posIsSrc && !posIsFirstExp && !posIsSecondExp;


		// conditions for 2.
		boolean srcIsCollapsed = (mDragState == SRC_ABOVE || mDragState == SRC_BELOW) && mFirstExpPos != mSrcPos && mSecondExpPos != mSrcPos;
		boolean srcCollapsed = position == mSrcPos && srcIsCollapsed;


		// conditions for 3.
		boolean srcPartialAbove = position == mSrcPos && isSliding && srcIsFirst;

		// conditions for 4.
		boolean srcPartialBelow = position == mSrcPos && isSliding && srcIsSecond;

		// conditions for 5.
		boolean expFull = position == mFirstExpPos && !isSliding;

		// conditions for 6.
		boolean expPartialAbove = position == mFirstExpPos && isSliding;

		// conditions for 7.
		boolean expPartialBelow = position == mSecondExpPos && isSliding;

		if (normal) {
			height = ViewGroup.LayoutParams.WRAP_CONTENT;
		} else if (srcCollapsed) {
			height = mItemHeightCollapsed;
		} else if (srcPartialAbove) {
			height = (int) (mSlideFrac * mFloatItemHeight);
		} else if (srcPartialBelow) {
			height = mFloatItemHeight - (int) (mSlideFrac * mFloatItemHeight);
		} else if (expFull) {
			height = getItemHeight(position, true) + divHeight + mFloatViewHeight;
		} else if (expPartialAbove) {

		}
			

		boolean posIsSrc = mSrcPos == position;
		boolean posIsFirstExp = mFirstExpPos == position;
		boolean posIsSecondExp = mSecondExpPos == position;

		boolean srcIsFirst = mSrcPos == mFirstExpPos;
		boolean srcIsSecond = mSrcPos == mSecondExpPos;

		boolean isSliding = mAnimate && mShuffleEdge != -1;

		boolean allNormal = (mDragState != SRC_ABOVE && mDragState != SRC_BELOW) && !isSliding;
		boolean posIsNormal = !posIsSrc && !posIsFirstExp && !posIsSecondExp;

		boolean srcIsCollapsed = (mDragState == SRC_ABOVE || mDragState == SRC_BELOW) && mFirstExpPos != mSrcPos && mSecondExpPos != mSrcPos;
		boolean srcIsPartial = isSliding && (srcIsFirst || srcIsSecond);
		boolean srcIsFull = 

		int blankHeightAbove = 0;
		int blankHeightBelow = 0;
		if (isSliding) {
			int blankHeightAbove = (int) (mSlideFrac * mFloatItemHeight);
			int blankHeightBelow = mFloatItemHeight - blankHeightAbove;
		}





		}

		if (isSliding && posIsFirst) {

		} else if (isSliding && posIsSecond) {

		}

		if (allNormal || posIsNormal) {
			height = ViewGroup.LayoutParams.WRAP_CONTENT;
		} else if (posIsSrc && srcIsCollapsed) {
			height = mItemHeightCollapsed;
		} else if (posIsSrc && isSliding && ) {
				height = 


		}
		} else if (srcIsCollapsed && posIsSrc) {
			height = mItemHeightCollapsed;
		} else if (
		
		else if (!isSliding) {
			if (position == mSrcPos) {
				if (mDragState == SRC_EXP) {
					height = mFloatViewHeight;
				} else {
					height = mItemHeightCollapsed;
				}
			} else if (position == mExpPos) {
				height = getItemHeight(position, true) + divHeight + mFloatViewHeight;
			}
		} else {
			// we are sliding and dealing with a non-standard item

			

			boolean srcExp = mSecondExpPos == mSrcPos || mFirstExpPos == mSrcPos;

			if (!srcExp && position == mSrcPos) {
				height = mItemHeightCollapsed;
			}
			boolean srcExp = (mDragState == SRC_BELOW && mSecondExpPos == mSrcPos) || (mDragState == SRC_ABOVE && 

			switch (mDragState) {
				case SRC_BELOW: {
					if (position == mFirstExpPos) {
						int blankHeight = (int) (mSlideFrac * mFloatItemHeight);
						height = getItemHeight(position, true) + divHeight + blankHeight;
						break; //height set
					} else if (position == mSecondExpPos) {
						int blankHeight = (int) ((1.0f - mSlideFrac) * mFloatItemHeight);
						if (position == mSrcPos) {
							height = blankHeight;
						} else { //pos < mSrcPos-1
							height = getItemHeight(position, true) + divHeight + blankHeight;
						}
						break; //height set
					}

					break;
				}
				case SRC_ABOVE: {
					if (mAnimate && mShuffleEdge != -1) {
						if (position == mFirstExpPos) {
							int blankHeight = (int) (mSlideFrac * mFloatItemHeight);
							if (position == mSrcPos) {
								height = blankHeight;
							} else { //pos > mSrcPos
								height = getItemHeight(position, true) + divHeight + blankHeight;
							}
							break; //height set
						} else if (position == mSecondExpPos) {
							int blankHeight = (int) ((1.0f - mSlideFrac) * mFloatItemHeight);
							height = getItemHeight(position, true) + divHeight + blankHeight;
							break; //height set
						}
					}


					break;
				}

			}
		}


		if (height != oldHeight) {
			lp.height = height;

			v.setLayoutParams(lp);
		}

*/

	
	
  @Override
  protected void layoutChildren() {
	
		// we need to control calls to layoutChildren while
		// dragging to prevent things happening out of order
		if (mFloatView == null) {
			super.layoutChildren();
		}

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
				
				if (mTrackDragSort) {
					mDragSortTracker.appendState();
				}
				dragView(x, y);
				if (mTrackDragSort) {
					mDragSortTracker.appendState();
				}

				if (!mDragScroller.isScrolling()) {
					final int first = getFirstVisiblePosition();
					final View expView = getChildAt(mFirstExpPos - first);
					if (expView == null) {
						mMovePos = first + getChildCount() / 2;
						mMoveTop = getChildAt(mMovePos - first).getTop();
						Log.d("mobeta", "startView was null");
					} else {
						mMovePos = mFirstExpPos;
						mMoveTop = expView.getTop();
					}

					//printPosData();
					//boolean updated = updatePositions();
					//printPosData();

					if (updatePositions()) {
						adjustAllItems();

						super.layoutChildren();
					}
				}

				// get the current scroll direction
				int currentScrollDir = mDragScroller.getScrollDir();

				//if (y > mLastY && y > mDownScrollStartY && currentScrollDir != DragScroller.DOWN) {
				if (y > mLastY && y > mDownScrollStartY && mLastY <= mDownScrollStartY) {
					// dragged down, it is below the down scroll start and it is not scrolling up

					if (currentScrollDir != DragScroller.STOP) {
						// moved directly from up scroll to down scroll
						mDragScroller.stopScrolling(true);
					}

					// start scrolling down
					mDragScroller.startScrolling(DragScroller.DOWN);
				}
				//else if (y < mLastY && y < mUpScrollStartY && currentScrollDir != DragScroller.UP) {
				else if (y < mLastY && y < mUpScrollStartY && mLastY >= mUpScrollStartY) {
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
		
		mDragState = SRC_EXP;

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
		int limit = getPaddingTop();
		if (firstPos < numHeaders) {
			limit = getChildAt(numHeaders - firstPos - 1).getBottom();
		}
		int footerLimit = getHeight() - getPaddingBottom();
		if (lastPos >= getCount() - numFooters) {
			// get top-most footer view
      footerLimit = getChildAt(getCount() - numFooters - firstPos).getTop();
			//footerLimit = getChildAt(getChildCount() - 1 - lastPos + getCount() - numFooters).getTop();
		}
		
		//Log.d("mobeta", "dragView top=" + (y - mDragPointY));
		//Log.d("mobeta", "limit=" + limit);
    //Log.d("mobeta", "mDragPointY=" + mDragPointY);
		if (y - mDragPointY < limit) {
			mWindowParams.y = mYOffset + limit;
		} else if (y - mDragPointY + mFloatViewHeight > footerLimit) {
			mWindowParams.y = mYOffset + footerLimit - mFloatViewHeight;
		} else {
			mWindowParams.y = y - mDragPointY + mYOffset;
		}
		// get midpoint of floating view (constrained to ListView bounds)
		mFloatViewY = mWindowParams.y + mFloatViewHeightHalf - mYOffset;
    //Log.d("mobeta", "float view taint x=" + mWindowParams.x + " y=" + mWindowParams.y);
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
		void drag(int from, int to);
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
		void drop(int from, int to);
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
		void remove(int which);
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

      //Log.d("mobeta", "scrolling by " + dy);
      // don't bail if dy=0, touch might be at edge of scroll region; but,
      // don't do any work 
      if (dy == 0) {
				mPrevTime += dt;
        post(this);
        return;
      } else if (dy > 0) {
        mMovePos = first;
        dy = Math.min(listHeight, dy);
			} else {
        mMovePos = last;
        dy = Math.max(-listHeight, dy);
      }

      final int oldTop = getChildAt(mMovePos - first).getTop();
      //int newTop = oldTop + dy;
			mMoveTop = oldTop + dy;
      //Log.d("mobeta", "movePos="+movePosition+" newTop="+newTop+" oldTop="+(newTop-dy)+" lvheight="+getHeight()+" fvheight="+mFloatViewHeight+" oldBottom="+getChildAt(movePosition-first).getBottom());

			int oldFloatPos = mFloatPos;
			int oldFirstExpPos = mFirstExpPos;

			if (updatePositions()) { //based on mMovePos and mMoveTop
				adjustAllItems();

				if (mSecondExpPos == mMovePos) {
					int oldSecondExpHeight = getItemHeight(mSecondExpPos);
					int secondExpHeight = measureItemAndGetHeight(getChildAt(mSecondExpPos - first), false);
					if (scrollDir == DOWN) {
						mMoveTop -= secondExpHeight - oldSecondExpHeight;
					} else {
						mMoveTop += oldSecondExpHeight - secondExpHeight;
					}
				}
			}

/*
			if (mFloatPos != oldFloatPos) {
				// scroll induces shuffle; adjust scroll for smoothness

				if (scrollDir == DOWN && mFloatPos == mMovePos) {
					mMoveTop -= mFloatViewHeight + getDividerHeight();
				} else if (mFloatPos < mMovePos) {
					if (scrollDir == UP || (scrollDir == DOWN && mMovePos == oldFirstExpPos)) {
						mMoveTop += mFloatViewHeight + getDividerHeight();
					}
				}

			}
*/
			
			// Schedule expand/collapse where needed and update list state.
			// Important that this goes before the following underscroll move.
			//shuffleItems(newFloatPos);
			
			// Do underscroll (assumes/uses new list state)
			//Log.d("mobeta", "viewTop=" + viewTop + " pos=" + pos + " itemTop=" + itemTop);
      //Log.d("mobeta", "dy="+(newTop - oldTop));

			setSelectionFromTop(mMovePos, mMoveTop - getPaddingTop());

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
			mBuilder.append("  <SrcPos>").append(mSrcPos).append("</SrcPos>\n");
			mBuilder.append("  <DragState>").append(mDragState).append("</DragState>\n");
			mBuilder.append("  <SrcHeight>").append(mFloatViewHeight + getDividerHeight()).append("</SrcHeight>\n");
			mBuilder.append("  <ViewHeight>").append(getHeight()).append("</ViewHeight>\n");
			mBuilder.append("  <LastY>").append(mLastY).append("</LastY>\n");
			mBuilder.append("  <FloatY>").append(mFloatViewY).append("</FloatY>\n");
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
