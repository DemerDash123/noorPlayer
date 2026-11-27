#!/bin/bash
# Compiles the plain-Java core and its unit tests on the JVM and runs them with JUnit 4.
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
OUT="$ROOT/build/test"
mkdir -p "$OUT"
find "$OUT" -name '*.class' -delete
CP=/usr/share/java/junit4.jar:/usr/share/java/hamcrest-core.jar
javac -nowarn -encoding UTF-8 -source 8 -target 8 -Xlint:-options -d "$OUT" -cp "$CP" \
  $(find "$ROOT/src/com/ahmed/noorplayer/core" "$ROOT/test" -name '*.java') 2>&1 | grep -v '^Picked up' || true
java -cp "$OUT:$CP" org.junit.runner.JUnitCore \
  com.ahmed.noorplayer.core.FormatTest \
  com.ahmed.noorplayer.core.SubtitleTest \
  com.ahmed.noorplayer.core.LogicTest \
  com.ahmed.noorplayer.core.VolumeChordTest \
  com.ahmed.noorplayer.core.GestureGateTest 2>&1 | grep -v '^Picked up'
