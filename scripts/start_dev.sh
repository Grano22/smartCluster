#!/usr/bin/env bash

cd ./test
CONFIG_FILE=src/test/kit/config_main.json  mvn -q exec:java -Dexec.mainClass=org.sample.App