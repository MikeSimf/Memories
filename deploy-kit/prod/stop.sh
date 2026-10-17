#!/usr/bin/env bash
# stop экземпляра prod
exec bash "$(cd "$(dirname "$0")/../bin" && pwd)/dnd.sh" prod down
