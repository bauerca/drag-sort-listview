#!/bin/bash

if [ ! $1 ]
then
	echo "Need a version argument in the form of x.x.x"
	exit 1
fi

base="drag-sort-listview"
dslvdir=$base-$1

if [ -d $dslvdir ]
then
	rm -rf $dslvdir
fi

git clone https://github.com/bauerca/drag-sort-listview.git $dslvdir
rm -rf "${dslvdir}/.git"

tar -czf "${dslvdir}.tar.gz" $dslvdir
zip -r "${dslvdir}.zip" $dslvdir

exit 0
