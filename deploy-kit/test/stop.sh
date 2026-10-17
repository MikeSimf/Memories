#!/usr/bin/env bash
# stop экземпляра test
exec bash "$(cd "$(dirname "$0")/../bin" && pwd)/dnd.sh" test down
