#!/bin/bash
# VPS-only installer for the existing dedicated OpenFool nginx site.
# Does not change Docker's game database or other nginx sites.
set -euo pipefail
if [[ $EUID -ne 0 ]]; then echo 'Запустите через sudo bash.' >&2; exit 1; fi
DIR=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)
if [[ "$DIR" != /opt/openfool/server ]]; then
  echo 'Ожидается установка в /opt/openfool/server (путь используется таймером).' >&2; exit 1
fi
for cmd in docker nginx systemctl curl; do command -v "$cmd" >/dev/null; done
for file in nginx-ip-http.conf nginx-ip.conf renew-ip-certificate.sh systemd/openfool-ip-renew.service systemd/openfool-ip-renew.timer; do
  test -s "$DIR/$file"
done
curl --fail --silent --show-error http://127.0.0.1:8080/health
printf '\nНастройка HTTPS для 46.30.41.204. Порты 80 и 443 должны быть открыты.\n'
read -r -p 'Ваш email для сертификата: ' EMAIL
if [[ ! "$EMAIL" =~ ^[^[:space:]@]+@[^[:space:]@]+\.[^[:space:]@]+$ ]]; then
  echo 'Неверный email. Изменения не применены.' >&2; exit 1
fi
docker pull certbot/certbot:latest
docker run --rm certbot/certbot:latest --version
mkdir -p /var/www/letsencrypt/.well-known/acme-challenge /etc/nginx/sites-available /etc/nginx/sites-enabled
SITE=/etc/nginx/sites-available/openfool
BACKUP="${SITE}.backup.$(date +%Y%m%d%H%M%S)"
if [[ ! -f "$SITE" ]]; then
  echo 'Сначала установите существующий сайт openfool по основной инструкции.' >&2; exit 1
fi
# Preserve the original site and enabled-link arrangement; do not replace other sites.
if [[ ! -L /etc/nginx/sites-enabled/openfool ]] || [[ $(readlink -f /etc/nginx/sites-enabled/openfool) != "$SITE" ]]; then
  echo 'Необычная конфигурация sites-enabled/openfool. Требуется ручная проверка.' >&2; exit 1
fi
cp -a "$SITE" "$BACKUP"
rollback() {
  local status=$?
  if (( status != 0 )); then
    echo "Ошибка. Возвращаю прежний nginx-конфиг из $BACKUP" >&2
    cp -a "$BACKUP" "$SITE"
    if nginx -t; then systemctl reload nginx || true; fi
    echo 'Исправьте причину выше. Сертификат/данные аккаунтов не удалялись.' >&2
  fi
  exit "$status"
}
trap rollback EXIT
cp "$DIR/nginx-ip-http.conf" "$SITE"
nginx -t
systemctl reload nginx
curl --fail --silent --show-error http://46.30.41.204/health
printf '\n'
docker run --rm \
  -v /etc/letsencrypt:/etc/letsencrypt \
  -v /var/lib/letsencrypt:/var/lib/letsencrypt \
  -v /var/www/letsencrypt:/var/www/letsencrypt \
  certbot/certbot:latest certonly \
  --non-interactive --agree-tos --email "$EMAIL" \
  --preferred-profile shortlived \
  --webroot --webroot-path /var/www/letsencrypt \
  --ip-address 46.30.41.204 --cert-name 46.30.41.204
test -s /etc/letsencrypt/live/46.30.41.204/fullchain.pem
test -s /etc/letsencrypt/live/46.30.41.204/privkey.pem
cp "$DIR/nginx-ip.conf" "$SITE"
nginx -t
systemctl reload nginx
curl --fail --silent --show-error https://46.30.41.204/health
printf '\n'
cp "$DIR/systemd/openfool-ip-renew.service" /etc/systemd/system/
cp "$DIR/systemd/openfool-ip-renew.timer" /etc/systemd/system/
systemctl daemon-reload
systemctl enable --now openfool-ip-renew.timer
systemctl start openfool-ip-renew.service
systemctl list-timers openfool-ip-renew.timer
trap - EXIT
printf '\nГотово: HTTPS по IP проверен, таймер продления включён. Можно установить APK 0.11.1.\n'
