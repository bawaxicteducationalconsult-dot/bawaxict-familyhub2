#!/bin/bash
set -e

DB_PATH="/home/ubuntu/bawaxict-familyhub2/backend/bawaxict_chat.db"
BACKUP_DIR="/home/ubuntu/bawaxict-familyhub2/backend/backups"
TIMESTAMP=$(date +%Y%m%d-%H%M%S)
BACKUP_FILE="$BACKUP_DIR/bawaxict_chat-$TIMESTAMP.db"

sqlite3 "$DB_PATH" ".backup '$BACKUP_FILE'"

# Delete backups older than 14 days
find "$BACKUP_DIR" -name "bawaxict_chat-*.db" -mtime +14 -delete

echo "Backup complete: $BACKUP_FILE"
