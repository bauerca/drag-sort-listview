DragSortListView
================

News
----

**Sept. 26, 2012**: Drag-sorting is now animated! (optional, of course)
Items slide around underneath the floating (dragged) View.


Overview
--------

DragSortListView (DSLV) is an extension of the Android ListView that enables
drag-and-drop re-sorting of list items. It is a major overhaul of
the [TouchInterceptor](https://github.com/android/platform_packages_apps_music/blob/master/src/com/android/music/TouchInterceptor.java) (TI) 
meant to give drag-sorting a polished feel. Some key features are:

1. Clean drag and drop (no visual glitches; I hope!)
2. Intuitive and smooth scrolling while dragging.
3. Support for heterogeneous item heights.
4. Customizable drag initiation at the per-item level.

DragSortListView is useful for all kinds of prioritized lists:
favorites, playlists, checklists, etc. Would love to hear about
your use case by email.
I hope you find it useful; and please, help me improve the thing!

Usage
-----

The best place to learn DSLV semantics is in the `demo/` directory.
So, as a first step, I recommend building the examples, playing with
them, and then exploring `demo/src/` and `demo/res/` for the
details. That said, the following is a brief overview of DSLV usage.

### XML layout declaration

DragSortListView can be declared in an XML layout file just like
the ListView. An [example layout
file](https://github.com/bauerca/drag-sort-listview/blob/master/demo/res/layout/dslv_main.xml)
is provided in the demo. The available attributes (in addition to the usual
ListView attributes) are given below. Read each bullet as "\<xml attr\>:
(\<datatype\>, \<default value\>) \<description\>."

* `collapsed_height`: (dimension, 1px) Height of placeholder at original
drag position.
* `drag_scroll_start`: (float, 0.3) Start of drag-scroll regions
(defined by a
fraction of the total DSLV height; i.e. between 0 and 1).
* `max_drag_scroll_speed`: (float, 0.3) Maximum drag-scroll speed for
default linear drag-scroll profile. Units of pixels/millisecond.
* `float_background_color`: (color, black) Background color of
floating View.
* `float_alpha`: (float, 1.0) Transparency of floating View. Value from
0 to 1 where 1 is opaque.
* `slide_shuffle_speed`: (float, 0.7) Speed of shuffle animations
underneath floating View. A value
of 0 means a shuffle animation is always in progress, whereas a value
of 1 means items snap from position to position without animation.
* `remove_mode`: (enum, "none") One of "none" "fling", "slide",
"slideRight", "slideLeft". This is inherited from the TI and may change.
* `track_drag_sort`: (bool, false) Debugging option; explained below.

### Drag-sort Listeners

DragSortListView is a ListView, and thus requires a [ListAdapter](http://developer.android.com/reference/android/widget/ListAdapter.html)
to populate
its items. Drag-sorting additionally implies a reordering of the items
in the ListAdapter, achieved through callbacks to special Listener
interfaces
defined in DSLV. If no Listeners are given to DSLV via the `set*Listener()`
methods, DSLV works just like a ListView.
The Listener interfaces are described below:

#### DragSortListView.DropListener

The DropListener interface has a single callback:
```java
public void drop(int from, int to);
```
This is called upon completion of the drag-sort; i.e. when the
floating View is dropped.
The parameter `from` is the ListView item that was originally dragged,
and `to` is the position where the item was dropped.
This is an important callback; without
a DropListener, DSLV is for all practical purposes useless.

For proper DSLV operation, this callback must perform
a reordering of the data in your ListAdapter. For example, one often
has a Cursor that pulls from a database and backs a
CursorAdapter. The order of items in the
Cursor is fixed; therefore, given drag-sorting, you must implement
a mapping from Cursor positions to DSLV positions. This is commonly
done
within in a custom ListAdapter or CursorWrapper that implements the
DropListener interface. See Issue #20 for a discussion of this.

#### DragSortListView.RemoveListener

As the TI did, DSLV provides gestures for removing the floating
View (and its associated list item) from the list. Upon completion of
a remove gesture, DSLV calls the RemoveListener method:
```java
public void remove(int which);
```
The position `which` should be "removed" from your ListAdapter; i.e.
the mapping from your data (e.g. in a Cursor) to your ListAdapter
should henceforth neglect the item previously pointed to by `which`.
Whether you actually remove the data or not is up to you.

#### DragSortListView.DragListener

The callback in the DragListener is
```java
public void drag(int from, int to);
```
This is called whenever the floating View hovers to a new potential
drop position; `to` is the current potential drop position, and `from` is
the previous one. The TI provided this callback; an example of usage
does not come to mind.

#### DragSortListView.DragSortListener

This is a convenience interface which combines all of the above
Listener interfaces.


### Drag initiation

A drag of item *i* is initiated when all the following are true:

* A DragListener or DropListener (or DragSortListener) is
registered with the DSLV instance.
* A child View of item *i* has an `android:id` named `drag` (more
on this below).
* The touch screen DOWN event hits the child View with id `drag`.

An [example XML layout](https://github.com/bauerca/drag-sort-listview/blob/master/demo/res/layout/jazz_artist_list_item.xml)
for a drag-sort-enabled ListView item can be found in the demo.
The key line to note in the example is

    android:id="@id/drag"

which tells DSLV which child View is responsible for initiating the
item drag. You will notice that `@id` is used rather than `@+id`.
This is because the demo project references the DSLV as an external
Android library, in which the id named `drag` is already defined
(external libraries cannot access ids defined by dependent
apps).

Another way to use DSLV is by copying the DragSortListView.java
file directly into your project. In this case, you must also:

1. Use `android:id="@+id/drag"` (notice the +) in your list item layout
file OR copy `res/values/ids.xml` to your project and use `@id/drag`
(If you do not copy res/values/ids.xml, DragSortListView.java will have
errors, as it always references `R.id.drag`).
2. Change the package name declaration line at the top of
DragSortListView.java to your package name.
3. Copy `res/values/dslv_attrs.xml` to your project
4. In the XML layout file that declares the DSLV, make sure to use
your package name (as opposed to `com.mobeta.android.(demo)dslv` in the
demos)

Additional documentation
------------------------

There is limited documentation in the DSLV.
You can check it
out with Javadoc by navigating to `/path/to/drag-sort-listview/src/` and
typing

    javadoc com.mobeta.android.dslv *


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

You can see Issue #1 for a discussion on using
DSLV in an Eclipse project. [This comment](https://github.com/bauerca/drag-sort-listview/issues/1#issuecomment-6596255)
might be particularly useful.

Debugging
---------

If you have python and [matplotlib](http://matplotlib.sourceforge.net/)
installed, you can use the script dslv.py
to debug drag-sort behavior. This script
is found in the project tools/ directory.

To enable, just set the `dslv:track_drag_sort` attribute to
`"true"` in XML. While drag-sorting on your emulator or device,
this tracking causes the DSLV to periodically dump its state to
a file called dslv_state.txt in the device/emulator /sdcard/ directory. 

Navigate to the location of dslv.py, and do 

    adb [-e|-d|-s device] pull /sdcard/dslv_state.txt

then simply run

    python dslv.py

An image should appear that represents the DSLV in the final
recorded state. Right and left keys allow stepping
through the recorded drag-sort frame-by-frame; up/down keys jump
30 frames. This tool has
been very useful for debugging jumpy behavior while drag-scrolling.

License
-------

```
A subclass of the Android ListView component that enables drag
and drop re-ordering of list items.

Copyright 2012 Carl Bauer

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
```

