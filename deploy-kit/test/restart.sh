#!/usr/bin/env bash
# restart экземпляра test
exec bash "$(cd "$(dirname "$0")/../bin" && pwd)/dnd.sh" test restart
