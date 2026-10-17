#!/usr/bin/env bash
# logs экземпляра test
exec bash "$(cd "$(dirname "$0")/../bin" && pwd)/dnd.sh" test logs
