#!/usr/bin/env bash
# start экземпляра prod
exec "$(cd "$(dirname "$0")/../bin" && pwd)/dnd.sh" prod up
