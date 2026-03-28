#!/bin/bash
APP_HOME="$(cd "$(dirname "$0")" && pwd)"
CLASSPATH="$APP_HOME/gradle/wrapper/gradle-wrapper.jar"
JAVACMD="/home/justin/android-studio/jbr/bin/java"

"$JAVACMD" -Xmx4096m -Xms256m -classpath "$CLASSPATH" org.gradle.wrapper.GradleWrapperMain "$@"
