#!/usr/bin/env bash
# start экземпляра test
exec "$(cd "$(dirname "$0")/../bin" && pwd)/dnd.sh" test up
