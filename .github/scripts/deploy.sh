#!/usr/bin/env bash
# 通过 SSH stdin 在服务器运行；不上传源码、.env、数据库密码或证书。
set -euo pipefail
deploy_directory="${1:?缺少部署目录}"
service="${2:?缺少服务名}"
image_ref="${3:?缺少镜像digest}"
[[ "$service" == server || "$service" == web ]]
expected_image="crpi-ow7mi2apt624v3c8.cn-heyuan.personal.cr.aliyuncs.com/xiye-docker/focus-garden-$service"
[[ "$image_ref" == "$expected_image"@sha256:* ]]
digest="${image_ref##*@}"
[[ "$digest" =~ ^sha256:[a-f0-9]{64}$ ]]
cd "$deploy_directory"
[[ -f .env && -f docker-compose.yml ]]

# 两个独立 GitHub 仓库也共用此锁，避免并发覆盖 .env 或重复更新 Nginx。
exec 9>.github-deploy.lock
flock -w 600 9
umask 077
mkdir -p deploy/backups deploy/releases
chmod 700 deploy/backups deploy/releases
release_id="$(date -u +%Y%m%dT%H%M%SZ)-$service-${digest:7:12}"
backup="deploy/backups/$release_id.env"
cp -p .env "$backup"
chmod 600 "$backup"

image_key=IMAGE_SERVER
[[ "$service" != web ]] || image_key=IMAGE_WEB
old_container="$(docker compose ps -q "$service")"
if [[ -n "$old_container" ]]; then
  old_image="$(docker inspect --format '{{.Config.Image}}' "$old_container")"
  printf 'Previous image: %s\n' "$old_image"
fi
printf -v "$image_key" '%s' "$image_ref"
export "${image_key?}"
docker compose config -q
docker compose pull "$service"
if [[ "$service" == server ]]; then
  # 新后端可能包含Flyway迁移，先备份业务库，备份失败则停止发布。
  database_backup="deploy/backups/$release_id.sql"
  if ! docker compose exec -T mysql sh -c \
    'export MYSQL_PWD="$MYSQL_ROOT_PASSWORD"; exec mysqldump -uroot --single-transaction --no-tablespaces --routines --triggers --events "$MYSQL_DATABASE"' \
    > "$database_backup"; then
    rm -f "$database_backup"
    echo "数据库备份失败，停止发布后端。" >&2
    exit 1
  fi
fi
docker compose up -d --no-deps --no-build "$service"

container="$(docker compose ps -q "$service")"
[[ -n "$container" ]]
healthy=false
for attempt in $(seq 1 90); do
  state="$(docker inspect --format '{{.State.Status}}' "$container")"
  if [[ "$state" == exited || "$state" == dead ]]; then
    break
  fi
  if [[ "$service" == server ]]; then
    health="$(docker inspect --format '{{if .State.Health}}{{.State.Health.Status}}{{else}}missing{{end}}' "$container")"
    if [[ "$health" == healthy ]]; then
      healthy=true
      break
    fi
  elif docker compose exec -T web wget -q -O /dev/null http://127.0.0.1/; then
    healthy=true
    break
  fi
  sleep 2
done
if [[ "$healthy" != true ]]; then
  echo "服务健康检查失败；未更新.env的固定镜像版本。配置备份：$backup" >&2
  echo "请检查 docker compose logs；本脚本不会自动回退数据库迁移。" >&2
  exit 1
fi

if [[ "$service" == server ]]; then
  # web中的静态upstream可能还缓存旧server容器IP。
  docker compose exec -T web nginx -t
  docker compose exec -T web nginx -s reload
fi
proxy_healthy=false
for attempt in $(seq 1 12); do
  if curl --fail --silent --show-error --connect-timeout 5 --max-time 10 \
    http://127.0.0.1:8083/api/health > /dev/null; then
    proxy_healthy=true
    break
  fi
  sleep 2
done
if [[ "$proxy_healthy" != true ]]; then
  echo "宿主8083端口的API健康检查失败；未记录发布成功。" >&2
  exit 1
fi

# 通过验证后原子更新当前服务的镜像变量，保留所有其他配置及真实凭据。
python3 - "$image_key" "$image_ref" <<'PY'
import os
import re
import sys
import tempfile
key, image = sys.argv[1:]
with open('.env', encoding='utf-8') as handle:
    lines = handle.read().splitlines()
pattern = re.compile(r'^\s*(?:export\s+)?' + re.escape(key) + r'\s*=')
updated = [line for line in lines if not pattern.match(line)]
updated.append(key + '=' + image)
descriptor, temporary = tempfile.mkstemp(prefix='.env.deploy-', dir='.')
try:
    with os.fdopen(descriptor, 'w', encoding='utf-8') as handle:
        handle.write('\n'.join(updated) + '\n')
        handle.flush()
        os.fsync(handle.fileno())
    os.replace(temporary, '.env')
finally:
    if os.path.exists(temporary):
        os.unlink(temporary)
PY
printf '%s\n' "$image_key=$image_ref" > "deploy/releases/$release_id.txt"
printf 'Deployment healthy: %s\n' "$image_ref"
