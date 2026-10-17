#!/usr/bin/env bash
# start экземпляра prod
exec bash "$(cd "$(dirname "$0")/../bin" && pwd)/dnd.sh" prod up
