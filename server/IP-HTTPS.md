# Подключение напрямую по IP — 46.30.41.204

APK 0.11.1 использует **https://46.30.41.204/v1**, без обращения к DNS-имени.
Обычная проверка доверия сертификату и его IP SAN в Android остаётся включённой.
Не используйте HTTP, `curl -k` или отключение проверки TLS для логинов/паролей.

На VPS пока нужно установить сертификат именно на IP. Это НЕ сделано автоматически.
Certbot 5.4+ поддерживает IP в webroot-режиме; используем современный Docker-образ,
а не установленный через apt Certbot 0.40. IP-сертификаты Let's Encrypt короткоживущие
(примерно 6 дней), поэтому автоматическое продление обязательно.

Официальное описание: https://www.eff.org/deeplinks/2026/03/certbot-and-lets-encrypt-now-support-ip-address-certificates

## 1. Получить обновлённые файлы

Команды выполняются на VPS, не на телефоне. Если текущая установка находится
в `/opt/openfool` и рабочее дерево чистое:

```bash
cd /opt/openfool
sudo git pull --ff-only origin arena/055fadce-openfool12
ls server/nginx-ip-http.conf server/nginx-ip.conf
```

Если git сообщает о локальных изменениях/расхождении — остановитесь, не применяйте
reset/clean. После слияния этой ветки можно обновляться с master.
Контейнер игрового сервера менять не требуется: меняются адрес APK и конфигурация nginx.

## Быстрая установка после обновления файлов

Для уже работающего сервера в `/opt/openfool`, с существующим nginx-сайтом
`openfool`, можно вместо ручных шагов 2–5 выполнить:

```bash
sudo bash /opt/openfool/server/setup-ip-https.sh
```

Введите свой email в английской раскладке. Скрипт сохраняет старый nginx-конфиг,
запрашивает сертификат, включает HTTPS, проверяет ответ и устанавливает таймер
продления. При ошибке восстанавливает старый конфиг. Другие сайты и базу аккаунтов
не меняет. Порты 80 и 443 должны быть открыты заранее. Если скрипт завершился
успешно, ручные шаги ниже выполнять не нужно. На вашем VPS скрипт ещё не запускался.

## 2. HTTP-проверка владения IP

Откройте входящие TCP 80 и 443 у хостера и в действующем firewall. Не включайте
новый firewall вслепую, не потеряйте доступ к SSH. Порт 8080 остаётся локальным.
Сначала убедитесь, что сервер жив: `curl --fail http://127.0.0.1:8080/health`.

Сохраняем конфигурацию существующего сайта и заменяем только сайт openfool:

```bash
sudo mkdir -p /var/www/letsencrypt/.well-known/acme-challenge
sudo mkdir -p /etc/nginx/sites-available /etc/nginx/sites-enabled
if [ -f /etc/nginx/sites-available/openfool ]; then
  sudo cp -a /etc/nginx/sites-available/openfool "/etc/nginx/sites-available/openfool.backup.$(date +%Y%m%d%H%M%S)"
fi
sudo cp /opt/openfool/server/nginx-ip-http.conf /etc/nginx/sites-available/openfool
sudo ln -sfn /etc/nginx/sites-available/openfool /etc/nginx/sites-enabled/openfool
sudo nginx -t && sudo systemctl reload nginx
curl --fail http://46.30.41.204/health
```

Ожидается `{"ok":true,"version":1}`. Конфигурация разрешает HTTP только для health
и ACME-проверок, а не для регистрации. Не продолжайте, если nginx или curl дают ошибку.
Не запускайте bootstrap повторно на уже настроенном HTTPS без необходимости.

## 3. Выпустить доверенный сертификат на IP

```bash
sudo docker pull certbot/certbot:latest
sudo docker run --rm certbot/certbot:latest --version
```

Нужна версия 5.4 или выше. Затем введите свой действующий email по запросу команды read:

```bash
read -r -p 'Ваш email для сертификата: ' CERTBOT_EMAIL
sudo docker run --rm \
  -v /etc/letsencrypt:/etc/letsencrypt \
  -v /var/lib/letsencrypt:/var/lib/letsencrypt \
  -v /var/www/letsencrypt:/var/www/letsencrypt \
  certbot/certbot:latest certonly \
  --non-interactive --agree-tos --email "$CERTBOT_EMAIL" \
  --preferred-profile shortlived \
  --webroot --webroot-path /var/www/letsencrypt \
  --ip-address 46.30.41.204 --cert-name 46.30.41.204
```

Не добавляйте `--staging` к рабочему сертификату: Android не доверяет тестовому CA.
Не используйте старую команду `certbot --nginx -d 46.30.41.204` — нужен именно IP SAN.
Если проверка владения IP не прошла, пришлите вывод ошибки; не повторяйте выпуск
много раз подряд, чтобы не упереться в лимиты CA.

## 4. Включить HTTPS

Только после успешного выпуска сертификата:

```bash
sudo test -s /etc/letsencrypt/live/46.30.41.204/fullchain.pem
sudo test -s /etc/letsencrypt/live/46.30.41.204/privkey.pem
sudo cp /opt/openfool/server/nginx-ip.conf /etc/nginx/sites-available/openfool
sudo nginx -t && sudo systemctl reload nginx
curl --fail https://46.30.41.204/health
```

Ответ должен быть `{"ok":true,"version":1}` **без** флага `-k`.
Если тест nginx не прошёл, не перезагружайте его, пришлите ошибку.

## 5. Включить автоматическое продление

```bash
sudo cp /opt/openfool/server/systemd/openfool-ip-renew.service /etc/systemd/system/
sudo cp /opt/openfool/server/systemd/openfool-ip-renew.timer /etc/systemd/system/
sudo systemctl daemon-reload
sudo systemctl enable --now openfool-ip-renew.timer
sudo systemctl start openfool-ip-renew.service
sudo systemctl list-timers openfool-ip-renew.timer
```

Проверка запускается дважды в день. Скрипт использует новый Certbot в Docker,
затем проверяет и перезагружает nginx на хосте. Порт 80 должен оставаться открытым
для последующих ACME-проверок. Старый apt Certbot не используйте для продления IP;
если он обслуживает другие сайты, не удаляйте его настройки и таймеры вслепую.

Проверка тестового продления без подмены рабочего сертификата:

```bash
sudo docker run --rm \
  -v /etc/letsencrypt:/etc/letsencrypt \
  -v /var/lib/letsencrypt:/var/lib/letsencrypt \
  -v /var/www/letsencrypt:/var/www/letsencrypt \
  certbot/certbot:latest renew --cert-name 46.30.41.204 --dry-run
sudo journalctl -u openfool-ip-renew.service --no-pager -n 50
```

Сохраните резервную копию `/etc/letsencrypt` в защищённом месте; не публикуйте
её содержимое: там закрытые ключи. Контролируйте успешность продления и срок сертификата.

После успешного HTTPS и настройки таймера установите APK 0.11.1. Можно закрывать SSH:
Docker, nginx и systemd timer работают независимо от терминала.

## Что проверено в разработке

Изменение адреса и unit-тест HTTPS/IP, сборка Android, синтаксис shell-скрипта и
календарь systemd проверяются отдельно. Выпуск сертификата, nginx на вашем VPS и
подключение с реального Android из этой среды не выполнялись. Доступность IP,
портов и успешность ACME-проверки нужно подтвердить на VPS.
