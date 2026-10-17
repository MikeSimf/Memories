#!/usr/bin/env bash
# restart экземпляра prod
exec bash "$(cd "$(dirname "$0")/../bin" && pwd)/dnd.sh" prod restart
