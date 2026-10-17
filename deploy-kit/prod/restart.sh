#!/usr/bin/env bash
# restart экземпляра prod
exec "$(cd "$(dirname "$0")/../bin" && pwd)/dnd.sh" prod restart
