#!/bin/sh
# MYRAA gradlew: uses gradle/wrapper/gradle-wrapper.jar when it exists, otherwise downloads Gradle 8.7 by itself.
APP_HOME=$(cd "$(dirname "$0")" && pwd -P) || exit 1
JAVACMD=java
[ -n "$JAVA_HOME" ] && [ -x "$JAVA_HOME/bin/java" ] && JAVACMD="$JAVA_HOME/bin/java"
JAR="$APP_HOME/gradle/wrapper/gradle-wrapper.jar"
if [ -f "$JAR" ]; then
  exec "$JAVACMD" -Dorg.gradle.appname=gradlew -classpath "$JAR" org.gradle.wrapper.GradleWrapperMain "$@"
fi
VER=8.7
BASE="${GRADLE_USER_HOME:-$HOME/.gradle}/myraa-dist"
DIST="$BASE/gradle-$VER"
if [ ! -x "$DIST/bin/gradle" ]; then
  mkdir -p "$BASE" || exit 1
  ZIP="$BASE/gradle-$VER-bin.zip"
  URL="https://services.gradle.org/distributions/gradle-$VER-bin.zip"
  echo "gradlew: downloading Gradle $VER (one time) ..." >&2
  if command -v curl >/dev/null 2>&1; then
    curl -fL -o "$ZIP" "$URL" || { echo "gradlew: download failed" >&2; exit 1; }
  elif command -v wget >/dev/null 2>&1; then
    wget -O "$ZIP" "$URL" || { echo "gradlew: download failed" >&2; exit 1; }
  else
    echo "gradlew: need curl or wget" >&2; exit 1
  fi
  unzip -q -o "$ZIP" -d "$BASE" || { echo "gradlew: unzip failed (install unzip)" >&2; exit 1; }
fi
exec "$DIST/bin/gradle" "$@"
