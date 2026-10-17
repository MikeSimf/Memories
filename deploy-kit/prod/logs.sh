#!/usr/bin/env bash
# logs экземпляра prod
exec bash "$(cd "$(dirname "$0")/../bin" && pwd)/dnd.sh" prod logs
