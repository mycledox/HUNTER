#!/usr/bin/env bash
set -Eeuo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
if [[ ! -f "$REPO_ROOT/docker-compose.yml" || ! -d "$REPO_ROOT/.git" ]]; then
    echo "Run this script from a Git clone of HUNTER." >&2
    exit 1
fi
if ! command -v docker >/dev/null 2>&1 || ! docker compose version >/dev/null 2>&1; then
    echo "Docker Engine and the Docker Compose plugin are required." >&2
    exit 1
fi
if [[ ! -f "$REPO_ROOT/.env" ]]; then
    echo "Missing .env. Run deploy/setup-ubuntu.sh before updating." >&2
    exit 1
fi

if [[ "$EUID" -eq 0 ]]; then
    REPO_OWNER="$(stat -c '%U' "$REPO_ROOT/.git")"
    if [[ "$REPO_OWNER" != "root" ]]; then
        runuser -u "$REPO_OWNER" -- git -C "$REPO_ROOT" pull --ff-only
    else
        git -C "$REPO_ROOT" pull --ff-only
    fi
else
    git -C "$REPO_ROOT" pull --ff-only
fi
docker compose -f "$REPO_ROOT/docker-compose.yml" --project-directory "$REPO_ROOT" up --build -d --wait --wait-timeout 240
docker compose -f "$REPO_ROOT/docker-compose.yml" --project-directory "$REPO_ROOT" ps
