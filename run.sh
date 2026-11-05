#!/usr/bin/env bash
# Compile and run ShopSync (macOS / Linux). Requires JDK 11 or newer.
cd "$(dirname "$0")"
rm -rf out
javac -encoding UTF-8 -d out $(find src -name "*.java") || exit 1
java -cp out com.shopsync.Main "$@"
