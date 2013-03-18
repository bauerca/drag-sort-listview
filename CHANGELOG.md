Changelog
=========

0.6.1
-----

* Add git version tags
* Fix #77: OnClickListener in list item crashes
DragSortController.
* Fix #81: Enabling FastScroller causes crash.

0.6.0
-----

* Remove modes are now `fling_remove` and `click_remove`.
 
0.5.0
-----

* Multiple-choice and single-choice selections handled.

0.4.0
-----

* Implement remove and drop animations.
* Update on(Intercept)TouchEvent to make sure DSLV ignores
touch events during remove/drop animations.
* Add `removeItem(int)` method to DragSortListView, which allows item
removal outside drag-sorting and animates the removal process.
* Add click-to-remove convenience to DragSortController.
* Fix #51: NullPointerException when a null ListAdapter is passed to
`setAdapter(ListAdapter)`.
