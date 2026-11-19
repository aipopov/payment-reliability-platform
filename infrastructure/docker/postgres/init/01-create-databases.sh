#!/bin/bash
set -e

psql \
  -v ON_ERROR_STOP=1 \
  --username "$POSTGRES_USER" \
  --dbname "$POSTGRES_DB" \
  -v payment_password="$PAYMENT_DB_PASSWORD" \
  -v ledger_password="$LEDGER_DB_PASSWORD" \
  -v provider_password="$PROVIDER_DB_PASSWORD" \
  -v reconciliation_password="$RECONCILIATION_DB_PASSWORD" \
  -f /postgres-sql/02-create-databases.sql