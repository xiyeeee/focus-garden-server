#!/usr/bin/env bash
set -euo pipefail

# 仅允许普通主机名、用户名和绝对路径，避免 SSH 远程命令的二次解析。
[[ "$SSH_HOST" =~ ^[a-zA-Z0-9.-]+$ ]]
[[ "$SSH_USER" =~ ^[a-z_][a-z0-9_-]*$ ]]
[[ "$SSH_PORT" =~ ^[0-9]+$ ]] && (( SSH_PORT >= 1 && SSH_PORT <= 65535 ))
[[ "$DEPLOY_PATH" =~ ^/[a-zA-Z0-9_./-]+$ ]]
[[ "$DEPLOY_SERVICE" == server || "$DEPLOY_SERVICE" == web ]]
[[ "$ACR_REGISTRY" == crpi-ow7mi2apt624v3c8.cn-heyuan.personal.cr.aliyuncs.com ]]
[[ "$IMAGE_NAME" == "xiye-docker/focus-garden-$DEPLOY_SERVICE" ]]
[[ "$IMAGE_DIGEST" =~ ^sha256:[a-f0-9]{64}$ ]]

umask 077
ssh_directory="$(mktemp -d)"
trap 'rm -rf "$ssh_directory"' EXIT
printf '%s\n' "$SSH_PRIVATE_KEY" | tr -d '\r' > "$ssh_directory/key"
printf '%s\n' "$SSH_KNOWN_HOSTS" > "$ssh_directory/known_hosts"
chmod 600 "$ssh_directory/key" "$ssh_directory/known_hosts"
ssh-keygen -y -P '' -f "$ssh_directory/key" > /dev/null
image_ref="$ACR_REGISTRY/$IMAGE_NAME@$IMAGE_DIGEST"

ssh -T -p "$SSH_PORT" -i "$ssh_directory/key" \
  -o BatchMode=yes -o IdentitiesOnly=yes -o StrictHostKeyChecking=yes \
  -o "UserKnownHostsFile=$ssh_directory/known_hosts" \
  -o ConnectTimeout=15 -o ServerAliveInterval=15 -o ServerAliveCountMax=3 \
  "$SSH_USER@$SSH_HOST" \
  "bash -s -- '$DEPLOY_PATH' '$DEPLOY_SERVICE' '$image_ref'" \
  < .github/scripts/deploy.sh
