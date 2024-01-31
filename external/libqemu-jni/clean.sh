#!/bin/sh
# clean.sh - clean everything.
set -e -u

rm -Rf /data/* "$HOME/.cache/package_builder"
