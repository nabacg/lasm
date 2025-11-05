#!/bin/bash
# Run a lasm example file

if [ $# -eq 0 ]; then
    echo "Usage: ./run.sh <lasm-file>"
    exit 1
fi

cd "$(dirname "$0")/.."
clojure -M -m run-example "$1"
