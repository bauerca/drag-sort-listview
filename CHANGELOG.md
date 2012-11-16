Changelog
=========

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
