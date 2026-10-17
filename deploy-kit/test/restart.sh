#!/usr/bin/env bash
# restart экземпляра test
exec "$(cd "$(dirname "$0")/../bin" && pwd)/dnd.sh" test restart
