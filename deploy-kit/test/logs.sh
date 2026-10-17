#!/usr/bin/env bash
# logs экземпляра test
exec "$(cd "$(dirname "$0")/../bin" && pwd)/dnd.sh" test logs
