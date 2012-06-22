DragSortListView
================

This is an extension of the Android ListView that enables
drag-and-drop re-sorting of list items. The code is
based on the [TouchInterceptor](https://github.com/android/platform_packages_apps_music/blob/master/src/com/android/music/TouchInterceptor.java) (TI)
from the Google
Music app (silly if it weren't!); therefore,
the essential behavior is the same---list item heights and
visibilities are adjusted to create an empty slot that tracks
the item being dragged. User-provided Listener objects are
informed of drag and drop events.

While using the TI in an app of mine, I noticed the following
behaviors that I thought needed polishing (in order of
importance):

1. Scrolling while dragging is erratic. A scroll is initiated
in the TI only when a MOVE event is detected. I don't think
this is a user-expected behavior.
2. List item View heights must be homogeneous and pre-specified.
3. Shuffling of list items is buggy for some drag movements
(e.g. shuffle occurs only after large overlap of floating View
with visible list items, last/first item in list does not shuffle,
etc.).
4. Dropping the floating View can cause the list to unexpectedly
``scroll.''
5. Region for item drag initiation is hard-coded in the TI.
6. Floating View is not bounded to ListView (maybe not such
an issue, mostly asthetic).

The above shortcomings caused a major reworking of the
TI implementation details, resulting in DragSortListView (DSLV).
I see a
lot of potential in a clean drag-sort list; any app with
a user-created list (e.g. "favorites") should benefit.

1. Scrolling while dragging is now intuitive and easily
customizable.
2. Arbitrary item heights are supported.
3. Dragging/Dropping/Drag-scrolling are mostly clean.
4. (see 3)
5. Drag initiation is customizable at the per-item level.
6. Bounds on floating View (big whoop?)

I hope you find it useful! And please, help me improve the thing!


Installation
------------

Download and install the [Android sdk](http://developer.android.com/sdk/index.html). Clone/Download/Fork the repo
through GitHub or via (read-only)

> git clone https://github.com/bauerca/drag-sort-listview.git

Navigate to drag-sort-listview/ and type (assuming
/path/to/android_sdk/tools is in your PATH)

> android update project --path ./ --subprojects

Then, navigate to drag-sort-listview/demo/, build,
and try out the examples.

Debugging
---------

If you have python and [matplotlib](http://matplotlib.sourceforge.net/)
installed, you can use the script dslv.py
to debug drag scroll behavior (a drag scroll occurs when a list item
is dragged to the edge of the ListView, causing a scroll). This script
is found in the project tools/ directory.

To enable, change the member
variable mTrack to true in the inner class DragScroller.
While drag scrolling is occurring
on your emulator or device, this tracking causes
the DSLV to periodically dump its state to a file called
dslv_state.xml in the
device/emulator /sdcard/ directory. 

Navigate to the location of dslv.py, and do 

> adb [-e|-d|-s device] pull /sdcard/dslv_state.xml

then simply run

> python dslv.py

An image should appear that represents the DSLV in the final
recorded state. Right and left keys allow stepping
through the recorded drag scroll frame-by-frame. This tool has
been very useful for debugging jumpy drag scroll behavior.
