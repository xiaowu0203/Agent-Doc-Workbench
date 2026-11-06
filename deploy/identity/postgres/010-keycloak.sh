#!/bin/sh
set -eu

# 使用独立非超级用户连接Keycloak数据库；凭据从环境读取，不放到命令行参数。
psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname postgres <<'SQL'
\getenv keycloak_password KEYCLOAK_DB_PASSWORD
CREATE ROLE keycloak LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOREPLICATION PASSWORD :'keycloak_password';
CREATE DATABASE keycloak OWNER keycloak;
SQL
