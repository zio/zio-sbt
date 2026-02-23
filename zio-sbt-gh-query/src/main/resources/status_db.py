#!/usr/bin/env python3
"""
Show status of the GitHub Issues/PRs SQLite database.

Usage:
    python3 status_db.py <db_path>

Arguments:
    db_path - Path to the SQLite database file
"""

import sqlite3
import sys
from pathlib import Path

db_path = sys.argv[1]
if not Path(db_path).exists():
    print('Database not found. Run gh-sync first.')
    sys.exit(1)
conn = sqlite3.connect(db_path)
cursor = conn.cursor()
cursor.execute("SELECT COUNT(*) FROM issues WHERE type = 'issue'")
issues = cursor.fetchone()[0]
cursor.execute("SELECT COUNT(*) FROM issues WHERE type = 'pr'")
prs = cursor.fetchone()[0]
cursor.execute("SELECT COUNT(*) FROM comments WHERE is_pr_comment = 0")
issue_comments = cursor.fetchone()[0]
cursor.execute("SELECT COUNT(*) FROM comments WHERE is_pr_comment = 1")
pr_comments = cursor.fetchone()[0]
cursor.execute("SELECT fetched_at FROM issues ORDER BY fetched_at DESC LIMIT 1")
row = cursor.fetchone()
fetched = row[0] if row else "unknown"
print('=' * 50)
print('GitHub Query Database Status')
print('=' * 50)
print(f'Issues:         {issues}')
print(f'PRs:            {prs}')
print(f'Issue Comments: {issue_comments}')
print(f'PR Comments:    {pr_comments}')
print(f'Last fetched:   {fetched}')
print('=' * 50)
conn.close()
