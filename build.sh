#!/bin/sh

set -e

BUKKIT=bukkit-1.6.2-R0.1.jar
BUILD=build

if [ ! -d $BUILD ]; then
    mkdir $BUILD
fi

echo "Compiling..."
javac -cp $BUKKIT -sourcepath src -d $BUILD -Xlint \
  src/net/glouser/jumpgameplugin/JumpGamePlugin.java

echo "Packaging..."
jar cf $BUILD/jumpgameplugin.jar plugin.yml config.yml -C $BUILD net

echo "Done"
