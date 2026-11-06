#!/usr/bin/env bash
set -Eeuo pipefail

usage() {
    echo "Usage: sudo bash deploy/setup-ubuntu.sh <domain> <email-for-ssl>"
}

if [[ $# -ne 2 ]]; then
    usage
    exit 2
fi

DOMAIN="${1,,}"
EMAIL="$2"
if [[ ! "$DOMAIN" =~ ^([a-z0-9]([a-z0-9-]{0,61}[a-z0-9])?\.)+[a-z]{2,63}$ ]]; then
    echo "Invalid domain: use a public DNS name such as app.example.com." >&2
    exit 2
fi
if [[ ! "$EMAIL" =~ ^[^[:space:]@]+@[^[:space:]@]+\.[^[:space:]@]+$ ]]; then
    echo "Invalid email address for Let's Encrypt certificate notices." >&2
    exit 2
fi
if [[ "$EUID" -ne 0 ]]; then
    echo "Run this setup script with sudo/root privileges." >&2
    exit 1
fi

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
NGINX_AVAILABLE="/etc/nginx/sites-available/hunter"
NGINX_ENABLED="/etc/nginx/sites-enabled/hunter"

if [[ ! -f "$REPO_ROOT/docker-compose.yml" || ! -f "$REPO_ROOT/.env.example" ]]; then
    echo "Run this script from a HUNTER repository clone." >&2
    exit 1
fi
if ! command -v apt-get >/dev/null 2>&1; then
    echo "This deployment script supports Ubuntu/Debian hosts with apt." >&2
    exit 1
fi
if ! command -v docker >/dev/null 2>&1 || ! docker compose version >/dev/null 2>&1; then
    echo "Install Docker Engine and the Docker Compose plugin before continuing." >&2
    echo "On Ubuntu: sudo apt update && sudo apt install -y docker.io docker-compose-v2" >&2
    exit 1
fi
if ! docker info >/dev/null 2>&1; then
    echo "Docker Engine is not running. Start it with: sudo systemctl enable --now docker" >&2
    exit 1
fi

export DEBIAN_FRONTEND=noninteractive
apt-get update
apt-get install -y nginx certbot python3-certbot-nginx curl

if [[ ! -f "$REPO_ROOT/.env" ]]; then
    install -m 600 "$REPO_ROOT/.env.example" "$REPO_ROOT/.env"
fi

set_env_value() {
    local key="$1"
    local value="$2"
    local file="$REPO_ROOT/.env"
    sed -i "/^${key}=/d" "$file"
    printf '%s=%s\n' "$key" "$value" >> "$file"
}

set_env_value SESSION_COOKIE_SECURE true
if ! grep -q '^HUNTER_PORT=' "$REPO_ROOT/.env"; then
    set_env_value HUNTER_PORT 5173
fi
chmod 600 "$REPO_ROOT/.env"

HUNTER_PORT="$(sed -n 's/^HUNTER_PORT=//p' "$REPO_ROOT/.env" | tail -n 1 | tr -d '\r')"
if [[ ! "$HUNTER_PORT" =~ ^[0-9]{1,5}$ ]] || (( HUNTER_PORT < 1 || HUNTER_PORT > 65535 )); then
    echo "HUNTER_PORT in .env must be a port number between 1 and 65535." >&2
    exit 1
fi

cd "$REPO_ROOT"
docker compose up --build -d --wait --wait-timeout 240

if [[ -e "$NGINX_AVAILABLE" ]]; then
    if ! grep -Fq '# Managed by HUNTER deploy script' "$NGINX_AVAILABLE" \
        || ! grep -Fq "server_name $DOMAIN;" "$NGINX_AVAILABLE" \
        || ! grep -Fq "proxy_pass http://127.0.0.1:$HUNTER_PORT;" "$NGINX_AVAILABLE"; then
        echo "Refusing to overwrite existing Nginx config: $NGINX_AVAILABLE" >&2
        echo "Back it up and remove it only if you are sure it is not used by another app." >&2
        exit 1
    fi
else
    TEMPLATE="$REPO_ROOT/deploy/hunter.nginx.conf.template"
    if [[ ! -f "$TEMPLATE" ]]; then
        echo "Nginx template not found: $TEMPLATE" >&2
        exit 1
    fi
    TEMP_CONFIG="$(mktemp)"
    trap 'rm -f "$TEMP_CONFIG"' EXIT
    sed -e "s/__DOMAIN__/$DOMAIN/g" -e "s/__PORT__/$HUNTER_PORT/g" "$TEMPLATE" > "$TEMP_CONFIG"
    sed -i '1i# Managed by HUNTER deploy script' "$TEMP_CONFIG"
    install -m 644 "$TEMP_CONFIG" "$NGINX_AVAILABLE"
    rm -f "$TEMP_CONFIG"
    trap - EXIT
fi

if [[ -e "$NGINX_ENABLED" && ! -L "$NGINX_ENABLED" ]]; then
    echo "Refusing to overwrite existing Nginx site: $NGINX_ENABLED" >&2
    exit 1
fi
if [[ -L "$NGINX_ENABLED" && "$(readlink -f "$NGINX_ENABLED")" != "$NGINX_AVAILABLE" ]]; then
    echo "Nginx site link points to a different config: $NGINX_ENABLED" >&2
    exit 1
fi
if [[ ! -L "$NGINX_ENABLED" ]]; then
    ln -s "$NGINX_AVAILABLE" "$NGINX_ENABLED"
fi

nginx -t
systemctl enable --now nginx
systemctl reload nginx

certbot --nginx --non-interactive --agree-tos --email "$EMAIL" --redirect --domain "$DOMAIN"
nginx -t
systemctl reload nginx
curl --fail --silent --show-error --retry 6 --retry-delay 5 "https://$DOMAIN/api/health"
printf '\nHUNTER is deployed at https://%s\n' "$DOMAIN"
