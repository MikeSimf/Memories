#!/usr/bin/env bash
# logs экземпляра prod
exec "$(cd "$(dirname "$0")/../bin" && pwd)/dnd.sh" prod logs
