#!/usr/bin/env bash
# Captures README screenshots of the sample app on a running emulator.
# Pinches and handle drags can't be performed reliably by adb, so the sample sets up each scene from the `scene` extra.
set -euo pipefail
source "$(dirname "$0")/screenshot-lib.sh"

install_sample
for mode in light dark; do
  set_night_mode "$mode"
  for scene in crop circle ratios; do
    fresh_launch --es scene "$scene"
    capture "$scene-$mode"
  done
done
