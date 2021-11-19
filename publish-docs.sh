#!/bin/sh
./gradlew clean dokkaHtml
rm -r docs
cp -r build/dokka/html docs
git add docs
git commit
