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
informed of drag and drop events; these Listeners perform the actual
reorderings within the user's [ListAdapter](http://developer.android.com/reference/android/widget/ListAdapter.html).

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
4. The list view sometimes jumps unexpectedly when dropping the
floating View.
5. Region for item drag initiation is hard-coded in the TI.
6. Floating View is not bounded to ListView (maybe not such
an issue, mostly aesthetic).

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

Usage
-----

The best place to learn DSLV semantics is in the `demo/` directory.
So, as a first step, I recommend building the examples, playing with
them, and then exploring `demo/src/` and `demo/res/` for the
details. That said, the following is a brief overview of DSLV usage.

The DSLV can be delared in an XML layout file just like the ListView.
Here is an example that also shows the available attributes.

Drag-sorting in the DSLV is enabled when:

1. A `DragSortListView.DragListener` or `DragSortListView.DropListener` is
registered with the DSLV instance and
2. A child View of the list item to be dragged has
an `android:id` named `drag`.

If you have ever used the TI, the Drag and Drop Listeners should be
familiar. Otherwise, there is limited documentation in the DSLV.
You can check it
out with Javadoc by navigating to `/path/to/drag-sort-listview/src/` and
typing

    javadoc com.mobeta.android.dslv *

I imagine most use cases will require only a DropListener to perform the
ListAdapter reordering. To register,
simply pass it to `DragSortListView.setDropListener()`.

To illustrate the second requirement,
the following is an example XML layout file for a ListView item
and can be found
[in the demo project](https://github.com/bauerca/drag-sort-listview/blob/master/demo/res/layout/jazz_artist_list_item.xml):

    <?xml version="1.0" encoding="utf-8"?>
    <LinearLayout
      xmlns:android="http://schemas.android.com/apk/res/android"
      android:layout_width="fill_parent"
      android:layout_height="wrap_content"
      android:orientation="horizontal">
      <LinearLayout
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:layout_weight="1"
        android:orientation="vertical"
        android:paddingRight="5dp">
        <TextView
          android:id="@+id/artist_name_textview"
          android:layout_width="wrap_content"
          android:layout_height="40dp"
          android:textAppearance="?android:attr/textAppearanceMedium" />
        <TextView
          android:id="@+id/artist_albums_textview"
          android:layout_width="wrap_content"
          android:layout_height="wrap_content"
          android:textAppearance="?android:attr/textAppearanceSmall" />
      </LinearLayout>
      <ImageView
        android:id="@id/drag"
        android:background="@drawable/drag"
        android:layout_width="wrap_content"
        android:layout_height="fill_parent"
        android:layout_weight="0" />
    </LinearLayout>

Okay, so all of the above is just fluff except for the line

    android:id="@id/drag"

which tells DSLV which child View is responsible for initiating the
item drag. You will notice that `@id` is used rather than `@+id`.
This is because the demo project references the DSLV as an external
Android library, in which case the id named `drag` is already defined.

Okay, maybe not all fluff; the above layout file is an example of how 
DSLV can handle arbitrary list item heights! Notice that the enclosing
LinearLayout uses `"wrap_content"` as its layout_height.

Another way to use the DSLV is by copying the DragSortListView.java
file directly into your project. In this case, you must also:

1. Use `android:id="@+id/drag"` (notice the +) in your list item layout
file OR copy `res/values/ids.xml` to your project and use `@id/drag`.
2. Change the package name declaration line at the top of
DragSortListView.java to your package name.
3. Copy `res/values/dslv_attrs.xml` to your project


Installation
------------

Download and install the [Android sdk](http://developer.android.com/sdk/index.html). Clone/Download/Fork the repo
through GitHub or via (read-only)

    git clone https://github.com/bauerca/drag-sort-listview.git

Navigate to drag-sort-listview/ and type (assuming
/path/to/android_sdk/tools is in your PATH)

    android update project --path ./ --subprojects

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
