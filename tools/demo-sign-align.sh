#!/bin/bash

BINDIR="/Users/bauerca/db-workspace/drag-sort-listview/demo/bin"
KEYSTORE="/Users/bauerca/bauerca.keystore"
PROJ_NAME="DemoDSLV"
APK_US="${BINDIR}/${PROJ_NAME}-release-unsigned.apk"
APK="${BINDIR}/${PROJ_NAME}-release.apk"

if [ -f $APK ]
then
    rm $APK
fi

jarsigner -verbose -sigalg MD5withRSA -digestalg SHA1 -keystore \
    $KEYSTORE $APK_US bauerca
zipalign -v 4 $APK_US $APK
