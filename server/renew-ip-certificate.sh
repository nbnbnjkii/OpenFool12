#!/bin/sh
# Run as root on the VPS via the systemd timer. Keep port 80 publicly reachable.
set -eu
docker run --rm \
  -v /etc/letsencrypt:/etc/letsencrypt \
  -v /var/lib/letsencrypt:/var/lib/letsencrypt \
  -v /var/www/letsencrypt:/var/www/letsencrypt \
  certbot/certbot:latest renew --quiet --cert-name 46.30.41.204
# A container cannot reload the host nginx: do it here only on successful renewal/check.
nginx -t
systemctl reload nginx
