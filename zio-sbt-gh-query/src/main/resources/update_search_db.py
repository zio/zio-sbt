#!/usr/bin/env python3
"""
Incremental update of SQLite database from new GitHub Issues/PRs data.

Configuration via environment variables:
    GH_QUERY_REPO     - GitHub repository (owner/repo), e.g. "zio/zio-sbt"
    GH_QUERY_DATA_DIR - Directory containing JSON data files
    GH_QUERY_DB_PATH  - Path to the SQLite database file

Usage:
    python3 update_search_db.py [issues.json] [prs.json] [comments.json]

This script:
- Reads new/updated issues and PRs from JSON Lines files
- Uses INSERT ... ON CONFLICT to update existing records or insert new ones
- Rebuilds the FTS index after updates
"""

import os
import sqlite3
import json
import sys
from pathlib import Path
from datetime import datetime

REPO = os.environ["GH_QUERY_REPO"]
DATA_DIR = Path(os.environ.get("GH_QUERY_DATA_DIR", "github-data"))
DB_PATH = Path(os.environ.get("GH_QUERY_DB_PATH", "github-search.db"))


def make_url(item_type, number):
    """Build a GitHub URL for the given item type and number."""
    if item_type == "pr":
        return f"https://github.com/{REPO}/pull/{number}"
    return f"https://github.com/{REPO}/issues/{number}"


def get_connection():
    if not DB_PATH.exists():
        print("Database not found. Run build_search_db.py first.")
        sys.exit(1)
    return sqlite3.connect(DB_PATH)


def update_issues(conn, filepath):
    """Update or insert issues from JSON Lines file.

    Uses INSERT ... ON CONFLICT ... DO UPDATE to preserve the existing
    auto-increment id (rowid) so the FTS content_rowid mapping stays valid
    (issue #9).
    """
    cursor = conn.cursor()

    if not filepath.exists() or filepath.stat().st_size == 0:
        print("No issues file to update")
        return 0

    with open(filepath) as f:
        lines = f.readlines()

    count = 0
    for line in lines:
        line = line.strip()
        if not line:
            continue
        try:
            item = json.loads(line)
        except json.JSONDecodeError:
            continue

        number = item.get('number', 0)
        title = item.get('title', '')
        state = item.get('state', 'UNKNOWN')
        author = item.get('author', '') or 'unknown'
        created = item.get('created', '')
        updated = item.get('updated', '')
        body = item.get('body', '') or ''
        url = make_url("issue", number)
        now = datetime.now().isoformat()

        cursor.execute("""
            INSERT INTO issues (type, number, title, state, author, created_at, updated_at, body, url, fetched_at)
            VALUES ('issue', ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT(type, number) DO UPDATE SET
                title=excluded.title,
                state=excluded.state,
                author=excluded.author,
                created_at=excluded.created_at,
                updated_at=excluded.updated_at,
                body=excluded.body,
                url=excluded.url,
                fetched_at=excluded.fetched_at
        """, (number, title, state, author, created, updated, body, url, now))
        count += 1

    conn.commit()
    print(f"Updated {count} issues")
    return count


def update_prs(conn, filepath):
    """Update or insert PRs from JSON Lines file.

    Uses INSERT ... ON CONFLICT ... DO UPDATE to preserve the existing
    auto-increment id (rowid) so the FTS content_rowid mapping stays valid
    (issue #9).
    """
    cursor = conn.cursor()

    if not filepath.exists() or filepath.stat().st_size == 0:
        print("No PRs file to update")
        return 0

    with open(filepath) as f:
        lines = f.readlines()

    count = 0
    for line in lines:
        line = line.strip()
        if not line:
            continue
        try:
            item = json.loads(line)
        except json.JSONDecodeError:
            continue

        number = item.get('number', 0)
        title = item.get('title', '')
        state = item.get('state', 'UNKNOWN')
        if item.get('merged'):
            state = 'MERGED'
        author = item.get('author', '') or 'unknown'
        created = item.get('created', '')
        updated = item.get('updated', '')
        body = item.get('body', '') or ''
        url = make_url("pr", number)
        now = datetime.now().isoformat()

        cursor.execute("""
            INSERT INTO issues (type, number, title, state, author, created_at, updated_at, body, url, fetched_at)
            VALUES ('pr', ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT(type, number) DO UPDATE SET
                title=excluded.title,
                state=excluded.state,
                author=excluded.author,
                created_at=excluded.created_at,
                updated_at=excluded.updated_at,
                body=excluded.body,
                url=excluded.url,
                fetched_at=excluded.fetched_at
        """, (number, title, state, author, created, updated, body, url, now))
        count += 1

    conn.commit()
    print(f"Updated {count} PRs")
    return count


def rebuild_fts(conn):
    """Rebuild full-text search index"""
    cursor = conn.cursor()
    cursor.execute("INSERT INTO search_index(search_index) VALUES('rebuild')")
    conn.commit()


def get_status(conn):
    """Get database statistics"""
    cursor = conn.cursor()

    cursor.execute("SELECT COUNT(*) FROM issues WHERE type = 'issue'")
    issues = cursor.fetchone()[0]

    cursor.execute("SELECT COUNT(*) FROM issues WHERE type = 'pr'")
    prs = cursor.fetchone()[0]

    cursor.execute("SELECT fetched_at FROM issues ORDER BY fetched_at DESC LIMIT 1")
    row = cursor.fetchone()
    fetched_at = row[0] if row else "unknown"

    return {
        'issues': issues,
        'prs': prs,
        'fetched_at': fetched_at
    }


def main():
    issues_file = Path(sys.argv[1]) if len(sys.argv) > 1 else DATA_DIR / "issues_new.json"
    prs_file = Path(sys.argv[2]) if len(sys.argv) > 2 else DATA_DIR / "prs_new.json"

    print("=" * 60)
    print(f"Updating GitHub Issues/PRs Search Database for {REPO}")
    print("=" * 60)

    conn = get_connection()

    print("\nUpdating issues...")
    update_issues(conn, issues_file)

    print("\nUpdating PRs...")
    update_prs(conn, prs_file)

    print("\nRebuilding search index...")
    rebuild_fts(conn)

    status = get_status(conn)
    conn.close()

    print(f"\nDatabase updated: {DB_PATH}")

    print(f"\nDatabase Status")
    print(f"==============")
    print(f"Issues:        {status['issues']}")
    print(f"PRs:           {status['prs']}")
    print(f"Last updated:  {status['fetched_at']}")


if __name__ == "__main__":
    main()
