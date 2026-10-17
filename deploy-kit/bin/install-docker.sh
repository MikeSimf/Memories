#!/usr/bin/env bash
# Устанавливает Docker Engine и плагин compose на чистый Ubuntu/Debian.
# Запускать НА СЕРВЕРЕ с правами root:  sudo bash bin/install-docker.sh
set -euo pipefail

if [[ "$(id -u)" != "0" ]]; then
  echo "Нужны права root: sudo bash $0" >&2
  exit 1
fi

. /etc/os-release
echo "==> Ставлю Docker для $PRETTY_NAME"

apt-get update
apt-get install -y ca-certificates curl gnupg
install -m 0755 -d /etc/apt/keyrings
curl -fsSL "https://download.docker.com/linux/${ID}/gpg" -o /etc/apt/keyrings/docker.asc
chmod a+r /etc/apt/keyrings/docker.asc
echo "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.asc] https://download.docker.com/linux/${ID} ${VERSION_CODENAME} stable" \
  > /etc/apt/sources.list.d/docker.list
apt-get update
apt-get install -y docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin

# автозапуск демона при загрузке сервера
systemctl enable --now docker

echo "==> Docker установлен:"
docker --version
docker compose version
echo
echo "Если работаете не под root, разрешите себе docker без sudo:"
echo "  sudo usermod -aG docker \$USER && newgrp docker"
