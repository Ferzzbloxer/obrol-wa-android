#!/usr/bin/env bash
set -e

# Pre-requisite: go install golang.org/x/mobile/cmd/gomobile@latest && gomobile init
echo "Building obrolcore.aar..."
gomobile bind -target=android -androidapi 24 -o ../android/app/libs/obrolcore.aar .
echo "Done! Generated android/app/libs/obrolcore.aar"