#!/bin/sh
# start up

if [ ! -d ./release ]; then
  mkdir release
fi

rm -fr ./release/*
ant clean
ant dist
mv dist release/mongoser
cd ./release
tar zcf mongoser.tar.gz mongoser
svn export $SVNROOT/DailyBibleVerse/server2 mongoser-src
tar zcf mongoser-src.tar.gz mongoser-src
mv *gz /tmp
cd ..
rm -fr ./release
ant clean

