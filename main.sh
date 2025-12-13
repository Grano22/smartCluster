#!/usr/bin/env bash
mvn archetype:generate \
 -DinteractiveMode=false \
 -DarchetypeGroupId=org.openjdk.jmh \
 -DarchetypeArtifactId=jmh-java-benchmark-archetype \
 -DgroupId=org.sample \
 -DartifactId=test \
 -Dversion=1.0
