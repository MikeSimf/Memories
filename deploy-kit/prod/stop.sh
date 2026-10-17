#!/usr/bin/env bash
# stop экземпляра prod
exec "$(cd "$(dirname "$0")/../bin" && pwd)/dnd.sh" prod down
