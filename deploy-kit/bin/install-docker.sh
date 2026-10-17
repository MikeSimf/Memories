#!/usr/bin/env bash
# Устанавливает Docker Engine и плагин compose на Ubuntu/Debian.
# Запускать НА СЕРВЕРЕ с правами root:  sudo bash bin/install-docker.sh
#
# Сбои посторонних репозиториев (сломанный repo.virtuozzo.com, недоступный
# archive.canonical.com) установке не мешают: индекс обновляется точечно,
# только для репозитория Docker.
set -uo pipefail

if [ "$(id -u)" != "0" ]; then
  echo "Нужны права root: sudo bash $0" >&2
  exit 1
fi

say()  { printf '\033[36m==>\033[0m %s\n' "$1"; }
ok()   { printf '  \033[32m✓\033[0m %s\n' "$1"; }
warn() { printf '  \033[33m!\033[0m %s\n' "$1" >&2; }
die()  { printf '  \033[31m✗\033[0m %s\n' "$1" >&2; exit 1; }

. /etc/os-release
say "Docker для $PRETTY_NAME"

# --- 0. Уже установлен? Тогда просто включаем -------------------------------
if command -v docker >/dev/null 2>&1; then
  ok "Docker уже установлен: $(docker --version 2>/dev/null)"
  systemctl enable --now docker 2>/dev/null || warn "не удалось запустить docker.service"
  if docker compose version >/dev/null 2>&1; then
    ok "плагин compose: $(docker compose version --short 2>/dev/null)"
    exit 0
  fi
  warn "нет плагина compose — доустанавливаю"
fi

# --- 1. Проверяем, не контейнер ли это -------------------------------------
# В OpenVZ/Virtuozzo/LXC демон Docker обычно не запускается: нужна вложенность.
VIRT="$(systemd-detect-virt 2>/dev/null || true)"
case "$VIRT" in
  kvm|none|microsoft|amazon|vmware|xen|oracle|parallels|bochs|qemu|"")
    ok "виртуализация: ${VIRT:-не определена}"
    ;;
  *)
    warn "сервер — контейнер ($VIRT). Docker внутри такого контейнера работает"
    warn "только если хостинг разрешает вложенность. Если установка встанет на"
    warn "запуске демона — используйте вариант без Docker (см. README, раздел «Без Docker»)."
    ;;
esac

# --- 2. Ключ и репозиторий Docker ------------------------------------------
apt-get install -y ca-certificates curl gnupg >/dev/null 2>&1 \
  || warn "ca-certificates/curl/gnupg уже стоят или недоступны — продолжаю"

install -m 0755 -d /etc/apt/keyrings
if [ ! -s /etc/apt/keyrings/docker.asc ]; then
  curl -fsSL "https://download.docker.com/linux/${ID}/gpg" -o /etc/apt/keyrings/docker.asc \
    || die "не скачался ключ репозитория Docker (проверьте доступ в интернет)"
fi
chmod a+r /etc/apt/keyrings/docker.asc
echo "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.asc] https://download.docker.com/linux/${ID} ${VERSION_CODENAME} stable" \
  > /etc/apt/sources.list.d/docker.list
ok "репозиторий: /etc/apt/sources.list.d/docker.list"

# --- 3. Обновление индексов ------------------------------------------------
# Общее обновление может ругаться на чужие репозитории — это не наша проблема,
# поэтому ошибку не прерываем, а индекс Docker тянем отдельно и точечно.
say "обновляю индекс пакетов"
apt-get update >/dev/null 2>&1 || warn "часть сторонних репозиториев недоступна — это не мешает установке"

say "обновляю индекс только репозитория Docker"
apt-get update \
  -o Dir::Etc::sourcelist="sources.list.d/docker.list" \
  -o Dir::Etc::sourceparts="-" \
  -o APT::Get::List-Cleanup="0" >/dev/null \
  || die "не удалось получить список пакетов Docker — проверьте доступ к download.docker.com"

# --- 4. Установка ----------------------------------------------------------
say "ставлю пакеты Docker (несколько минут)"
apt-get install -y docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin \
  || die "пакеты не установились — см. сообщение apt выше"

systemctl enable --now docker >/dev/null 2>&1 || warn "docker.service не включился в автозапуск"

# --- 5. Проверка -----------------------------------------------------------
sleep 2
if ! docker info >/dev/null 2>&1; then
  warn "демон Docker не отвечает. Диагностика:"
  systemctl status docker --no-pager -l 2>&1 | tail -15 >&2
  journalctl -u docker -n 25 --no-pager 2>&1 | tail -25 >&2
  die "Docker установлен, но не запустился (частые причины: контейнерная виртуализация без вложенности, cgroup v1, занятый порт)"
fi

ok "docker: $(docker --version)"
ok "compose: $(docker compose version --short 2>/dev/null || echo 'есть')"
if docker run --rm hello-world >/dev/null 2>&1; then
  ok "контейнеры запускаются (hello-world)"
else
  warn "hello-world не запустился — образ не скачался или нет прав; проверьте: docker run --rm hello-world"
fi

echo
echo "Готово. Дальше — шаг 2 из README: папки, .env, jar."
if [ "${SUDO_USER:-}" ]; then
  echo "Чтобы работать без sudo:  sudo usermod -aG docker $SUDO_USER && newgrp docker"
fi
