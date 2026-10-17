#!/usr/bin/env bash
# stop экземпляра test
exec "$(cd "$(dirname "$0")/../bin" && pwd)/dnd.sh" test down
