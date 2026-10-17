#!/usr/bin/env bash
# start экземпляра test
exec bash "$(cd "$(dirname "$0")/../bin" && pwd)/dnd.sh" test up
