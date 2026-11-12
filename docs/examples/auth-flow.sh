#!/usr/bin/env bash
# Requires bash, curl, python3. No password/code/session ID is printed or stored in source.
set -euo pipefail
GARDEN_BASE_URL="${GARDEN_BASE_URL:-http://localhost:8080}"
GARDEN_FLOW="${1:-register}"
GARDEN_TEMP_DIR="$(mktemp -d)"
trap 'rm -rf "$GARDEN_TEMP_DIR"; unset GARDEN_PASSWORD GARDEN_CODE' EXIT
GARDEN_COOKIE_JAR="$GARDEN_TEMP_DIR/cookies"
touch "$GARDEN_COOKIE_JAR"
chmod 600 "$GARDEN_COOKIE_JAR"
case "$GARDEN_FLOW" in
  username-register|username-login) read -r -p '用户名: ' GARDEN_USERNAME ;;
  *) read -r -p '邮箱: ' GARDEN_EMAIL ;;
esac
export GARDEN_EMAIL GARDEN_USERNAME GARDEN_PASSWORD GARDEN_CODE

refresh_csrf() {
  GARDEN_CSRF="$(curl --silent --show-error --fail-with-body \
    -c "$GARDEN_COOKIE_JAR" -b "$GARDEN_COOKIE_JAR" \
    "$GARDEN_BASE_URL/api/auth/csrf" | python3 -c 'import json,sys; print(json.load(sys.stdin)["data"]["token"])')"
}

payload() {
  python3 - "$1" <<'PY'
import os,json,sys
mode=sys.argv[1]
body={'username':os.environ['GARDEN_USERNAME']} if mode.startswith('username-') else {'email':os.environ['GARDEN_EMAIL']}
if mode in ('REGISTER','RESET_PASSWORD'): body['purpose']=mode
if mode in ('register','reset'): body['verificationCode']=os.environ['GARDEN_CODE']
if mode in ('register','login','username-register','username-login'): body['password']=os.environ['GARDEN_PASSWORD']
if mode=='reset': body['newPassword']=os.environ['GARDEN_PASSWORD']
print(json.dumps(body,ensure_ascii=False))
PY
}

post_json() {
  curl --silent --show-error --fail-with-body -c "$GARDEN_COOKIE_JAR" -b "$GARDEN_COOKIE_JAR" \
    -H 'Content-Type: application/json' -H "X-CSRF-TOKEN: $GARDEN_CSRF" \
    --data-binary @- "$GARDEN_BASE_URL$1"
  printf '\n'
}

refresh_csrf
case "$GARDEN_FLOW" in
  username-register)
    read -r -s -p '设置密码（8–128字符）: ' GARDEN_PASSWORD
    printf '\n'
    payload username-register | post_json /api/auth/register
    ;;
  username-login)
    read -r -s -p '密码: ' GARDEN_PASSWORD
    printf '\n'
    ;;
  register)
    payload REGISTER | post_json /api/auth/email-codes
    read -r -p '收到的注册验证码: ' GARDEN_CODE
    read -r -s -p '设置密码（8–128字符，保留空格）: ' GARDEN_PASSWORD
    printf '\n'
    payload register | post_json /api/auth/register
    ;;
  reset)
    payload RESET_PASSWORD | post_json /api/auth/email-codes
    read -r -p '收到的重置验证码: ' GARDEN_CODE
    read -r -s -p '设置新密码: ' GARDEN_PASSWORD
    printf '\n'
    payload reset | post_json /api/auth/password/reset
    refresh_csrf
    ;;
  login)
    read -r -s -p '密码: ' GARDEN_PASSWORD
    printf '\n'
    ;;
  *) printf '用法: bash docs/examples/auth-flow.sh [username-register|username-login|register|login|reset]\n'; exit 2 ;;
esac
case "$GARDEN_FLOW" in
  username-register|username-login) payload username-login | post_json /api/auth/login ;;
  *) payload login | post_json /api/auth/login ;;
esac
# Authentication rotates the session and clears the old CSRF token.
refresh_csrf
curl --silent --show-error --fail-with-body -c "$GARDEN_COOKIE_JAR" -b "$GARDEN_COOKIE_JAR" "$GARDEN_BASE_URL/api/users/me"
printf '\n'
curl --silent --show-error --fail-with-body -c "$GARDEN_COOKIE_JAR" -b "$GARDEN_COOKIE_JAR" "$GARDEN_BASE_URL/api/tasks?page=1&size=20"
printf '\n'
curl --silent --show-error --fail-with-body -X POST -c "$GARDEN_COOKIE_JAR" -b "$GARDEN_COOKIE_JAR" \
  -H "X-CSRF-TOKEN: $GARDEN_CSRF" "$GARDEN_BASE_URL/api/auth/logout"
printf '\n'
# An unauthenticated new CSRF token is needed for the next login/register request.
refresh_csrf
