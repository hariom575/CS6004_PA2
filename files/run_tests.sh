#!/bin/bash

SOOT_JAR="soot-4.6.0-jar-with-dependencies.jar"

if [ -z "$1" ]; then
    echo "Usage: ./run_test.sh Test5"
    exit 1
fi

TEST=$1

echo "Compiling PA2..."
javac -cp .:$SOOT_JAR PA2.java

echo "Compiling $TEST..."
javac "$TEST/Test.java"

echo "Running $TEST..."
java -cp .:$SOOT_JAR PA2 "$TEST"
