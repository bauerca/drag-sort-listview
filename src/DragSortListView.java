package com.mobeta.android.dslv;

import java.util.ArrayList;

import android.content.Context;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.database.DataSetObservable;
import android.database.DataSetObserver;
import android.graphics.Canvas;
import android.graphics.Bitmap;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.os.SystemClock;
import android.util.AttributeSet;
import android.util.Log;
import android.view.GestureDetector;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.GestureDetector.SimpleOnGestureListener;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.HeaderViewListAdapter;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.ListAdapter;
import android.widget.ListView;
import android.widget.AbsListView;

import android.os.Debug;


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
	private int mHeight;
	private float mHeightf;
	private GestureDetector mGestureDetector;
	private static final int FLING = 0;
	private static final int SLIDE = 1;
	private static final int TRASH = 2;
	private int mRemoveMode = -1;
	private Rect mTempRect = new Rect();
  private int[] mTempLoc = new int[2];
	private Bitmap mDragBitmap;
	private final int mTouchSlop;
	private int mItemHeightNormal = -1;
	private int mItemHeightExpanded;
	private int mItemHeightHalf;
	private int mItemHeightCollapsed;
	private Drawable mTrashcan;

  private ArrayList<Integer> mHeaderHeights = new ArrayList<Integer>();
  private ArrayList<Integer> mFooterHeights = new ArrayList<Integer>();
  private int mHeadersTotalHeight = 0;
  private int mFootersTotalHeight = 0;
	
	private DragScroller mDragScroller;
	private float mDragUpScrollBoundFrac;
	private float mDragDownScrollBoundFrac;
  private float mDragUpScrollHeight;
	private float mDragDownScrollHeight;
	
	private float mMaxScrollSpeed = 5.0f; // positions per sec

  /**
   * For converting from positions per second to pixels per ms
   */
  private float mPosSpeedToPixelSpeed;
	
	private DragScrollProfile mScrollProfile = new DragScrollProfile() {
		@Override
		public float getSpeed(float w, long t) {
			return mMaxScrollSpeed * w;
		}
	};
	
	private int mLastX;
	private int mLastY;
	private int mDownY;
	
	private AdapterWrapper mAdapterWrapper;

	public DragSortListView(Context context, AttributeSet attrs) {
		super(context, attrs);
		mRemoveMode = FLING;
		mTouchSlop = ViewConfiguration.get(context).getScaledTouchSlop();

    if (attrs != null) {
      TypedArray a = getContext().obtainStyledAttributes(attrs,
        R.styleable.DragSortListView, 0, 0);

      mItemHeightNormal = a.getDimensionPixelSize(
        R.styleable.DragSortListView_normal_height, 0); // required
      mItemHeightHalf = mItemHeightNormal / 2;
      mItemHeightExpanded = a.getDimensionPixelSize(
        R.styleable.DragSortListView_expanded_height, 2 * mItemHeightNormal);
      mItemHeightCollapsed = a.getDimensionPixelSize(
        R.styleable.DragSortListView_collapsed_height, 1);

      mFloatBGColor = a.getColor(R.styleable.DragSortListView_float_background,
        0x00000000);

      mRemoveMode = a.getInt(R.styleable.DragSortListView_remove_mode, -1);

      float frac = a.getFloat(R.styleable.DragSortListView_scroll_bound, 1.0f / 3.0f);
      setDragScrollBounds(frac);

	    mMaxScrollSpeed = a.getFloat(R.styleable.DragSortListView_max_scroll_speed, 5.0f); // positions per sec
      mPosSpeedToPixelSpeed = ((float) mItemHeightNormal) * 0.001f;

      a.recycle();
    }

    Log.d("mobeta", "normal height=" + mItemHeightNormal);
    Log.d("mobeta", "expanded height=" + mItemHeightExpanded);
    Log.d("mobeta", "collapsed height=" + mItemHeightCollapsed);

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

      //Log.d("mobeta", "getView: position="+position+" convertView="+convertView);

      if (convertView != null) {
        v = (RelativeLayout) convertView;
        View oldChild = v.getChildAt(0);

        View newChild = super.getView(position, oldChild, v);
        if (newChild != oldChild) {
          // shouldn't get here if user is reusing convertViews properly
          v.removeViewAt(0);
          v.addView(newChild);
          // check that tags are equal too?
          v.setTag(newChild.findViewById(R.id.drag));
        }

      } else {
        AbsListView.LayoutParams params =
          new AbsListView.LayoutParams(ViewGroup.LayoutParams.FILL_PARENT,
            mItemHeightNormal);
        v = new RelativeLayout(getContext());
        v.setLayoutParams(params);
        v.addView(super.getView(position, null, v));

        v.setTag(v.findViewById(R.id.drag));
      }

			// must be castable to LinearLayout (to use 'gravity' attribute)
			//LinearLayout v = (LinearLayout) super.getView(position, convertView, parent);


			//Log.d("mobeta", "getView position " + position);

			ViewGroup.LayoutParams params = v.getLayoutParams();
			final int numHeaders = getHeaderViewsCount();

			switch (mDragState) {
				case NO_DRAG:
					if (params.height != mItemHeightNormal) {
						resizeItem(v, mItemHeightNormal, Gravity.CENTER_VERTICAL, VISIBLE);
					} else if (v.getVisibility() == INVISIBLE) {
						v.setVisibility(VISIBLE);
					}
					break;
				case SRC_EXP:
					int vis = View.VISIBLE;
					if (position == mSrcDragPos - numHeaders) {
						vis = View.INVISIBLE;
					}
					v.setVisibility(vis);
					if (params.height != mItemHeightNormal) {
						resizeItem(v, mItemHeightNormal, Gravity.CENTER_VERTICAL, vis);
					}
					break;
				case SRC_ABOVE:
					if (position == mSrcDragPos - numHeaders) {
						resizeItem(v, mItemHeightCollapsed, Gravity.CENTER_VERTICAL, INVISIBLE);
					} else if (position == mExpDragPos - numHeaders) {
						resizeItem(v, mItemHeightExpanded, Gravity.TOP, VISIBLE);
					} else if (params.height != mItemHeightNormal) {
						resizeItem(v, mItemHeightNormal, Gravity.CENTER_VERTICAL, VISIBLE);
					}
					break;
				case SRC_BELOW:
					if (position == mSrcDragPos - numHeaders) {
						resizeItem(v, mItemHeightCollapsed, Gravity.CENTER_VERTICAL, INVISIBLE);
					} else if (position == mExpDragPos - numHeaders) {
						resizeItem(v, mItemHeightExpanded, Gravity.BOTTOM, VISIBLE);
					} else if (params.height != mItemHeightNormal) {
						resizeItem(v, mItemHeightNormal, Gravity.CENTER_VERTICAL, VISIBLE);
					}
					break;
			}

			return v;
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
					item.setDrawingCacheEnabled(true);
					// Create a copy of the drawing cache so that it does not get recycled
					// by the framework when the list tries to clean up memory
					Bitmap bitmap = Bitmap.createBitmap(item.getDrawingCache());
					item.setDrawingCacheEnabled(false);
					
					mExpDragPos = itemnum;
					mSrcDragPos = itemnum;
					
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
	public void setDragScrollBounds(float heightFraction) {
    setDragScrollBounds(heightFraction, heightFraction);
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
	public void setDragScrollBounds(float upperFrac, float lowerFrac) {
    if (lowerFrac > 0.5f) {
      mDragDownScrollBoundFrac = 0.5f;
    } else {
      mDragDownScrollBoundFrac = lowerFrac;
    }

    if (upperFrac > 0.5f) {
      mDragUpScrollBoundFrac = 0.5f;
    } else {
      mDragUpScrollBoundFrac = upperFrac;
    }

    if (getHeight() != 0) {
      updateScrollBounds();
    }
  }

  private void updateScrollBounds() {
		mHeight = getHeight();
    float heightF = (float) mHeight;
		
    mUpScrollStartYF = mDragUpScrollBoundFrac * heightF;
    mDownScrollStartYF = (1.0f - mDragDownScrollBoundFrac) * heightF;

		mUpScrollStartY = (int) mUpScrollStartYF;
		mDownScrollStartY = (int) mDownScrollStartYF;
		
		mDragUpScrollHeight = mUpScrollStartYF;
		mDragDownScrollHeight = mHeight - mDownScrollStartYF;
  }
	
	
  @Override
  protected void onSizeChanged(int w, int h, int oldw, int oldh) {
    super.onSizeChanged(w, h, oldw, oldh);
    updateScrollBounds();
  }

  @Override
  public void addHeaderView(View v, Object data, boolean isSelectable) {
    super.addHeaderView(v, data, isSelectable);

    // measure to get height of header item
    ViewGroup.LayoutParams lp = v.getLayoutParams();
    final int height = lp == null ? 0 : lp.height;
    if (height > 0) {
      mHeaderHeights.add(height);
    } else {
      int spec = MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED);
      v.measure(spec, spec);
      mHeaderHeights.add(v.getMeasuredHeight());
    }

    mHeadersTotalHeight += mHeaderHeights.get(mHeaderHeights.size() - 1);
  }

  @Override
  public void addFooterView(View v, Object data, boolean isSelectable) {
    super.addFooterView(v, data, isSelectable);

    // measure to get height of footer item
    ViewGroup.LayoutParams lp = v.getLayoutParams();
    final int height = lp == null ? 0 : lp.height;
    if (height > 0) {
      mFooterHeights.add(height);
    } else {
      int spec = MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED);
      v.measure(spec, spec);
      mFooterHeights.add(v.getMeasuredHeight());
    }

    mFootersTotalHeight += mFooterHeights.get(mFooterHeights.size() - 1);
  }
	
	
	private int viewYToListY(int viewY) {
		final int firstPos = getFirstVisiblePosition();
		final int topY = getChildAt(0).getTop();

		switch (mDragState) {
			case SRC_ABOVE:
				if (firstPos <= mSrcDragPos) {
					return firstPos * mItemHeightNormal - topY + viewY;
				} else if (firstPos <= mExpDragPos) {
					return (firstPos - 1) * mItemHeightNormal + mItemHeightCollapsed - topY + viewY;
				} else {
					// this code is probably never reached, but whatever
					return firstPos * mItemHeightNormal + mItemHeightCollapsed - topY + viewY;
				}
			case SRC_BELOW:
				if (firstPos <= mExpDragPos) {
					return firstPos * mItemHeightNormal - topY + viewY;
				} else if (firstPos <= mSrcDragPos) {
					return (firstPos + 1) * mItemHeightNormal - topY + viewY;
				} else {
					return firstPos * mItemHeightNormal + mItemHeightCollapsed - topY + viewY;
				}
			case NO_DRAG:
			case SRC_EXP:
			default:
				return firstPos * mItemHeightNormal - topY + viewY;
		}
	}
	
	/**
	 * 
	 * @param position List item position
	 * @return Absolute y-coord of top of given list item
	 */
	private int getItemTop(int position) {
		switch (mDragState) {
			case SRC_ABOVE:
				if (position <= mSrcDragPos) {
					return position * mItemHeightNormal;
				} else if (position <= mExpDragPos) {
					return (position - 1) * mItemHeightNormal + mItemHeightCollapsed;
				} else {
					return position * mItemHeightNormal + mItemHeightCollapsed;
				}
			case SRC_BELOW:
				if (position <= mExpDragPos) {
					return position * mItemHeightNormal;
				} else if (position <= mSrcDragPos) {
					return (position + 1) * mItemHeightNormal;
				} else {
					return position * mItemHeightNormal + mItemHeightCollapsed;
				}
			case NO_DRAG:
			case SRC_EXP:
			default:
				return position * mItemHeightNormal;
		}
	}
	
	private int getListHeight() {
		switch (mDragState) {
			case SRC_BELOW:
			case SRC_ABOVE:
				return getCount() * mItemHeightNormal + mItemHeightCollapsed;
			case NO_DRAG:
			case SRC_EXP:
			default:
				return getCount() * mItemHeightNormal;	
		}
	}
	
	/**
	 * Given an absolute y-coord (where 0 is top of full virtual list),
	 * return the corresponding
	 * ListView position. Accounts for effects of dragging, i.e.
	 * expanded/collapsed list items.
	 * 
	 * @param listY Absolute y-coord in virtual list
	 * @return ListView position
	 */
	private int listYToPosition(int listY) {
		// if listY is out of bounds, bring it back in
		if (listY < 0) {
			return 0;
		} else if (listY >= getListHeight()) {
			return getCount() - 1;
		}
		
		switch (mDragState) {
			
			case SRC_ABOVE:
				if (listY < mSrcTop) {
					// listY is above src item
					return listY / mItemHeightNormal;
				} else if (listY < mSrcBottom) {
					// listY is on src item
					return mSrcDragPos;
				} else if (listY < mExpTop) {
					// listY is between src item and expanded item
					return mSrcDragPos + 1 + (listY - mSrcTop - mItemHeightCollapsed) / mItemHeightNormal;
				} else if (listY < mExpBottom) {
					// listY is on expanded item
					return mExpDragPos;
				} else {
					// listY is below expanded item
					return mExpDragPos + 1 + (listY - (mExpDragPos + 1) * mItemHeightNormal - mItemHeightCollapsed) / mItemHeightNormal;
				}
			case SRC_BELOW:
				if (listY < mExpTop) {
					// listY is above expanded item
					return listY / mItemHeightNormal;
				} else if (listY < mExpBottom) {
					// listY is on expanded item
					return mExpDragPos;
				} else if (listY < mSrcTop) {
					// listY is between expanded item and src item
					return mExpDragPos + 1 + (listY - (mExpDragPos + 2) * mItemHeightNormal) / mItemHeightNormal;
				} else if (listY < mSrcBottom) {
					// listY is on src item
					return mSrcDragPos;
				} else {
					// listY is below src item
					return mSrcDragPos + 1 + (listY - (mSrcDragPos + 1) * mItemHeightNormal - mItemHeightCollapsed) / mItemHeightNormal;
				}
			case NO_DRAG:
			case SRC_EXP:
			default:
				return listY / mItemHeightNormal;
		}
	}
	
	
		
		
	/**
	 *  
	 * 
	 * @param xView x in ListView coords
	 * @param yView y in ListView coords
	 * @param viewTop Top of ListView in list coords.
	 * @return
	 */
	private int getFloatPosition(int xView, int yView, int viewTop) {
		
		// get midpoint of floating view (constrained to ListView bounds)
		final int dragViewMidY = Math.max(0, Math.min(mHeight, yView - mDragPointY + mItemHeightHalf));
		
		// get absolute position in list
		//int listY = viewYToListY(dragViewMidY);
		int listY = viewTop + dragViewMidY;
		//Log.d("mobeta", "mid drag = " + dragViewMidY + " listY = " + listY);
		
		
		// get ListView position
		int lvPos = listYToPosition(listY);
		
		// check if position
		final int numHeaders = getHeaderViewsCount();
		final int numFooters = getFooterViewsCount();
		
		if (lvPos < numHeaders) {
			return numHeaders;
		} else if (lvPos >= getCount() - numFooters) {
			return getCount() - numFooters - 1;
		}
		
		switch (mDragState) {
			case SRC_ABOVE:
				if (lvPos > mSrcDragPos && lvPos < mExpDragPos) {
					return lvPos - 1;
				} else if (lvPos == mExpDragPos) {
					final int listYMidExpItem = getItemTop(lvPos) + mItemHeightNormal;
					if (listY < listYMidExpItem) {
						return lvPos - 1;
					} else {
						return lvPos;
					}
				} else {
					return lvPos;
				}
			case SRC_BELOW:
				if (lvPos > mExpDragPos && lvPos < mSrcDragPos) {
					return lvPos + 1;
				} else if (lvPos == mExpDragPos) {
					final int listYMidExpItem = getItemTop(lvPos) + mItemHeightNormal;
					if (listY < listYMidExpItem) {
						return lvPos;
					} else {
						return lvPos + 1;
					}
				} else {
					return lvPos;
				}
			case NO_DRAG:
			case SRC_EXP:
			default:
				return lvPos;
		}
		
		
	}
	
	
	
	private void dropFloatView(boolean removeSrcItem) {
		mDragScroller.stopScrolling(true);
		
		if (removeSrcItem) {
			if (mRemoveListener != null) {
				mRemoveListener.remove(mSrcDragPos - getHeaderViewsCount());
			}
		} else {
			if (mDropListener != null && mExpDragPos >= 0 && mExpDragPos < getCount()) {
				final int numHeaders = getHeaderViewsCount();
				mDropListener.drop(mSrcDragPos - numHeaders, mExpDragPos - numHeaders);
			}

			//Log.d("mobeta", "unexpand views called");
			int top = getChildAt(0).getTop();
			int firstPos = getFirstVisiblePosition();

			resizeItem(mExpDragPos, mItemHeightNormal, Gravity.CENTER_VERTICAL, View.VISIBLE);

			//Log.d("mobeta", "last pos=" + (getCount() - 1) + ", last vis pos=" + getLastVisiblePosition());
			//Log.d("mobeta", "src pos > last pos = " + (mSrcDragPos > getLastVisiblePosition()));

			if (mSrcDragPos < firstPos) {
				// collapsed src item is off screen, no need to expand it; but, we
				// must adjust the scroll accordingly
				setSelectionFromTop(firstPos - 1, top);
			} else if (mSrcDragPos <= getLastVisiblePosition()) {
				// collapsed src item is in view, expand it
				resizeItem(mSrcDragPos, mItemHeightNormal, Gravity.CENTER_VERTICAL, View.VISIBLE);
			}
		}
		
		removeFloatView();
		
		mDragState = NO_DRAG;
	}


	/**
	 * Resize list item at position.
	 * 
	 * @param position List position to be resized.
	 * @param height New height in pixels.
	 * @param gravity New gravity.
	 * @param visibility New visibility.
	 */
	private void resizeItem(int position, int height, int gravity, int visibility) {
		final int firstPos = getFirstVisiblePosition();
		final int lastPos = getLastVisiblePosition();
		
		if (position >= firstPos && position <= lastPos) {
			//LinearLayout v = (LinearLayout) getChildAt(position - firstPos);
			RelativeLayout v = (RelativeLayout) getChildAt(position - firstPos);
			v.setGravity(gravity);
		
			ViewGroup.LayoutParams params = v.getLayoutParams();
			params.height = height;
			v.setLayoutParams(params);
			
			v.setVisibility(visibility);
		} else {
			Log.d("mobeta", "invalid resize item " + (position - firstPos));
		}
	}
	
	/**
	 * Generic resize list item.
	 * 
	 * @param v List item View to be resized. Must be LinearLayout.
	 * @param height New height in pixels.
	 * @param gravity New gravity.
	 * @param visibility New visibility.
	 */
	public static void resizeItem(RelativeLayout v, int height, int gravity, int visibility) {
		v.setGravity(gravity);
		
		ViewGroup.LayoutParams params = v.getLayoutParams();
		params.height = height;
		v.setLayoutParams(params);
		
		v.setVisibility(visibility);
	}
	
	/**
	 * Call this when list items are shuffled while dragging.
	 */
	private void updateListState() {
		if (mFloatView == null) {
			mDragState = NO_DRAG;
			return;
		} else if (mExpDragPos == mSrcDragPos) {
			mDragState = SRC_EXP;
		} else if (mSrcDragPos < mExpDragPos) {
			mDragState = SRC_ABOVE;
		} else {
			mDragState = SRC_BELOW;
		}
		
		mSrcTop = getItemTop(mSrcDragPos);
		mExpTop = getItemTop(mExpDragPos);
		
		if (mDragState == SRC_ABOVE || mDragState == SRC_BELOW) {
			mSrcBottom = mSrcTop + mItemHeightCollapsed;
			mExpBottom = mExpTop + mItemHeightExpanded;
		} else {
			mSrcBottom = mSrcTop + mItemHeightNormal;
			mExpBottom = mExpTop + mItemHeightNormal;
		}
	}
	
	
	/**
	 * Shuffle list items given view-coordinates of touch. Calculates
	 * float position for current list y-coord of ListView top.
	 * 
	 * @param xView
	 * @param yView
	 */
	private void shuffleItems(int xView, int yView) {
		shuffleItems(getFloatPosition(xView, yView, viewYToListY(0)));
	}
	
	/**
	 * Shuffle list items given new float position.
	 * 
	 * @param floatPos 
	 */
	private void shuffleItems(int floatPos) {
		
		//Log.d("mobeta", "float position: " + floatPos);
		//Log.d("mobeta", "exp position: " + mExpDragPos);
		//Log.d("mobeta", "first position: " + getFirstVisiblePosition() + " height: " + getChildAt(0).getHeight());

		if (floatPos != mExpDragPos) {
			// if we get here, the ListView is inconsistent with the
			// floating view
			
			final int firstPos = getFirstVisiblePosition();
			final int lastPos = getLastVisiblePosition();
			
			// Always schedule collapse of expanded item.
			if (mExpDragPos >= firstPos && mExpDragPos <= lastPos) {
				if (mExpDragPos == mSrcDragPos) {
					resizeItem(mExpDragPos, mItemHeightCollapsed, Gravity.CENTER_VERTICAL, INVISIBLE);
				} else {
					resizeItem(mExpDragPos, mItemHeightNormal, Gravity.CENTER_VERTICAL, VISIBLE);
				}
			}
			
			// Schedule expansion of new item only if new float position is
			// currently visible
			
			if (floatPos >= firstPos && floatPos <= lastPos) {
				if (floatPos == mSrcDragPos) {
					resizeItem(floatPos, mItemHeightNormal, Gravity.CENTER_VERTICAL, INVISIBLE);
				} else if (floatPos > mSrcDragPos) {
					resizeItem(floatPos, mItemHeightExpanded, Gravity.TOP, VISIBLE);
				} else {
					resizeItem(floatPos, mItemHeightExpanded, Gravity.BOTTOM, VISIBLE);
				}
			}
			
			// callback
			if (mDragListener != null) {
				final int numHeaders = getHeaderViewsCount();
				mDragListener.drag(mExpDragPos - numHeaders, floatPos - numHeaders);
			}
			
			// update state
			mExpDragPos = floatPos;
			updateListState();
		}
	}
	

	@Override
	public boolean onTouchEvent(MotionEvent ev) {
		if (mGestureDetector != null) {
			mGestureDetector.onTouchEvent(ev);
		}
		if ((mDragListener != null || mDropListener != null) && mFloatView != null) {
			int action = ev.getAction(); 
			switch (action & MotionEvent.ACTION_MASK) {
			case MotionEvent.ACTION_UP:
			case MotionEvent.ACTION_CANCEL:
				Rect r = mTempRect;
				mFloatView.getDrawingRect(r);
				//mDragScroller.stopScrolling(true);
				
				if (mRemoveMode == SLIDE && ev.getX() > r.right * 3 / 4) {
					dropFloatView(true);
				} else {
					dropFloatView(false);
				}
				
				break;

			case MotionEvent.ACTION_DOWN:
				//doExpansion();
				break;
			case MotionEvent.ACTION_MOVE:
				int x = (int) ev.getX();
				int y = (int) ev.getY();
				
				// make src item invisible on first move away from pickup
				// point. Reduces flicker.
				if (mLastY == mDownY) {
          // should we be this careful? shuffleItems is!
          final View item = getChildAt(mSrcDragPos - getFirstVisiblePosition());
          if (item != null) {
            item.setVisibility(INVISIBLE);
          }
					//getChildAt(mSrcDragPos - getFirstVisiblePosition()).setVisibility(INVISIBLE);
				}
				
				dragView(x, y);

				shuffleItems(x, y);
				
				// start or stop drag scrolling
				if (y > mDownScrollStartY) {

					if (mLastY <= mDownScrollStartY) {
						if (mDragScroller.isScrolling()) {
							// moved directly from upscroll to down scroll
							mDragScroller.stopScrolling(true);
						}
						//Log.d("mobeta", "start drag scrolling down");
						// just entered lower drag-scroll region
						mLastY = y;
						mDragScroller.startScrolling(DragScroller.DOWN);
					}
				} else if (y < mUpScrollStartY) {
					if (mLastY >= mUpScrollStartY) {
						// just entered upper drag-scroll region
						if (mDragScroller.isScrolling()) {
							// moved directly from down-scroll to up-scroll
							mDragScroller.stopScrolling(true);
						}
						mLastY = y;
						mDragScroller.startScrolling(DragScroller.UP);
					}
				} else if (mLastY > mDownScrollStartY || mLastY < mUpScrollStartY) {
					mDragScroller.stopScrolling(false);
				}
				
				mLastX = x;
				mLastY = y;
				break;
			}
			return true;
		}
		return super.onTouchEvent(ev);
	}

	private void startDragging(Bitmap bm, int x, int y) {
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

		Context context = getContext();
		ImageView v = new ImageView(context);
		//int backGroundColor = context.getResources().getColor(R.color.dragndrop_background);
		//v.setBackgroundColor(mFloatBGColor);
		//v.setBackgroundResource(R.drawable.playlist_tile_drag);
		v.setPadding(0, 0, 0, 0);
		v.setImageBitmap(bm);
		mDragBitmap = bm;

		mWindowManager = (WindowManager)context.getSystemService(Context.WINDOW_SERVICE);
		mWindowManager.addView(v, mWindowParams);
		mFloatView = v;
		
		mDragState = SRC_EXP;
	}

	private void dragView(int x, int y) {
    //Log.d("mobeta", "float view pure x=" + x + " y=" + y);
		if (mRemoveMode == SLIDE) {
			float alpha = 1.0f;
			int width = mFloatView.getWidth();
			if (x > width / 2) {
				alpha = ((float)(width - x)) / (width / 2);
			}
			mWindowParams.alpha = alpha;
		}

		if (mRemoveMode == FLING || mRemoveMode == TRASH) {
			mWindowParams.x = x - mDragPointX + mXOffset;
		} else {
			mWindowParams.x = 0;
		}
		
		
		// keep floating view from going past bottom of last header view
		final int numHeaders = getHeaderViewsCount();
		final int numFooters = getFooterViewsCount();
		final int firstPos = getFirstVisiblePosition();
		final int lastPos = getLastVisiblePosition();
    //Log.d("mobeta", "nHead="+numHeaders+" nFoot="+numFooters+" first="+firstPos+" last="+lastPos);
		int limit = 0;
		if (firstPos < numHeaders) {
			limit = getChildAt(numHeaders - firstPos - 1).getBottom();
		}
		int footerLimit = mHeight;
		if (lastPos >= getCount() - numFooters) {
			// get top-most footer view
			footerLimit = getChildAt(getChildCount() - 1 - lastPos + getCount() - numFooters).getTop();
		}
		
		//Log.d("mobeta", "dragView top=" + (y - mDragPointY));
		//Log.d("mobeta", "limit=" + limit);
    //Log.d("mobeta", "mDragPointY=" + mDragPointY);
		if (y - mDragPointY < limit) {
			mWindowParams.y = mYOffset + limit;
		} else if (y - mDragPointY + mItemHeightNormal > footerLimit) {
			mWindowParams.y = mYOffset + footerLimit - mItemHeightNormal;
		} else {
			mWindowParams.y = y - mDragPointY + mYOffset;
		}
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
		
		public final static int UP = 0;
		public final static int DOWN = 1;
		
		private float mScrollSpeed; // pixels per ms
		
		private boolean mScrolling = false;
		
		private int mLastHeader;
		private int mFirstFooter;
		
		public boolean isScrolling() {
			return mScrolling;
		}
		
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
			
			if (scrollDir == UP) {
				mScrollSpeed = -mScrollProfile.getSpeed((mUpScrollStartYF - mLastY) / mDragUpScrollHeight, mPrevTime);
			} else {
				mScrollSpeed = mScrollProfile.getSpeed((mLastY - mDownScrollStartYF) / mDragDownScrollHeight, mPrevTime);
			}
			
			dt = SystemClock.uptimeMillis() - mPrevTime;
			// dy: negative means items move down (window moves up)
			//     positive means items move up (window moves down)
			
			dy = (int) Math.round(mPosSpeedToPixelSpeed * mScrollSpeed * dt);
		
			int viewTop = viewYToListY(dy);
			
			// If trying to scroll past bounds, end scroll after this lap.
			// ListView handles overscrolls.
			if (viewTop < 0 || (dy > 0 && viewTop + mHeight > getListHeight())) {
				mAbort = true;
			}
			
			// Where will floating view end up given current list state?
			int newFloatPos = getFloatPosition(mLastX, mLastY, viewTop);
			
			//if (newFloatPos <= mLastHeader || newFloatPos >= mFirstFooter) {
			//	dragView(mLastX, mLastY);
			//}
			
			// Schedule expand/collapse where needed and update list state.
			// Important that this goes before the following underscroll move.
			shuffleItems(newFloatPos);
			
			// Do underscroll (assumes/uses new list state)
			int pos = listYToPosition(viewTop);
			int itemTop = getItemTop(pos) - viewTop;
			//Log.d("mobeta", "viewTop=" + viewTop + " pos=" + pos + " itemTop=" + itemTop);
			setSelectionFromTop(pos, itemTop);
			
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
					int dragViewBottom = mLastY - mDragPointY + mItemHeightNormal;
					int firstFooterTop = getChildAt(mFirstFooter - firstVisibleItem).getTop();
					if (dragViewBottom > firstFooterTop) {
						mWindowParams.y = mYOffset + firstFooterTop - mItemHeightNormal;
						mWindowManager.updateViewLayout(mFloatView, mWindowParams);
					}
				}
			}
		}

		@Override
		public void onScrollStateChanged(AbsListView view, int scrollState) {}

	}

}
