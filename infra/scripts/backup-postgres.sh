#!/bin/sh
# Daily logical backup of PostgreSQL, uploaded to S3-compatible storage (MinIO).
set -eu

STAMP=$(date -u +%Y%m%dT%H%M%SZ)
FILE="/backups/possaas-${STAMP}.sql.gz"

echo "[backup] dumping ${PGDATABASE} → ${FILE}"
pg_dump --format=plain --no-owner --no-privileges | gzip -9 > "${FILE}"

export AWS_ACCESS_KEY_ID="${S3_ACCESS_KEY}"
export AWS_SECRET_ACCESS_KEY="${S3_SECRET_KEY}"
export AWS_DEFAULT_REGION="${S3_REGION:-us-east-1}"

echo "[backup] uploading to s3://${S3_BUCKET}/backups/"
aws --endpoint-url="${S3_ENDPOINT}" s3 cp "${FILE}" "s3://${S3_BUCKET}/backups/$(basename "${FILE}")"

# Prune local copies older than retention.
find /backups -name 'possaas-*.sql.gz' -mtime +"${BACKUP_RETENTION_DAYS:-14}" -delete || true
echo "[backup] done"
