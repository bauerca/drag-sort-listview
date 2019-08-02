#!/bin/bash

DOCLAVA_PATH=../../doclava-1.0.6/doclava-1.0.6.jar
AND_SDK=/Users/bauerca/Downloads/android-sdk-mac_86
AND_SRC=${AND_SDK}/sources/android-16
AND_API_XML=${AND_SRC}/current.xml

javadoc \
  -sourcepath ${AND_SRC} -J-Xmx240m \
  -subpackages android.widget \
  -doclet com.google.doclava.Doclava \
  -docletpath ${DOCLAVA_PATH} \
  -nodocs -apixml ${AND_API_XML}

javadoc \
  -doclet com.google.doclava.Doclava \
  -docletpath ${DOCLAVA_PATH} \
  -title DragSortListView \
  -federate Android http://developer.android.com/reference \
  -federationxml Android ${AND_API_XML} \
  com.mobeta.android.dslv
