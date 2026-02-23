#!/usr/bin/env python3
"""
Build searchable SQLite database from GitHub Issues/PRs.

This script:
1. Reads full issue/PR details from JSON Lines files
2. Reads comments from JSON Lines files
3. Creates SQLite database with FTS5 full-text search
4. Stores title, body, comments, author, state, dates, URL

Configuration via environment variables:
    GH_QUERY_REPO     - GitHub repository (owner/repo), e.g. "zio/zio-sbt"
    GH_QUERY_DATA_DIR - Directory containing JSON data files
    GH_QUERY_DB_PATH  - Path to the SQLite database file
"""

import os
import sqlite3
import json
from pathlib import Path
from datetime import datetime

REPO = os.environ["GH_QUERY_REPO"]
DATA_DIR = Path(os.environ.get("GH_QUERY_DATA_DIR", "github-data"))
DB_PATH = Path(os.environ.get("GH_QUERY_DB_PATH", "github-search.db"))


def init_db():
    """Create database schema"""
    conn = sqlite3.connect(DB_PATH)
    cursor = conn.cursor()

    cursor.execute("DROP TABLE IF EXISTS issues")
    cursor.execute("DROP TABLE IF EXISTS comments")
    cursor.execute("DROP TABLE IF EXISTS search_index")

    cursor.execute("""
        CREATE TABLE issues (
            id INTEGER PRIMARY KEY,
            type TEXT NOT NULL,
            number INTEGER,
            title TEXT,
            state TEXT,
            author TEXT,
            created_at TEXT,
            updated_at TEXT,
            body TEXT,
            url TEXT,
            comment_text TEXT,
            fetched_at TEXT,
            UNIQUE(type, number)
        )
    """)

    cursor.execute("""
        CREATE TABLE comments (
            id INTEGER PRIMARY KEY,
            issue_pr_number INTEGER,
            is_pr_comment INTEGER,
            author TEXT,
            created_at TEXT,
            body TEXT,
            fetched_at TEXT
        )
    """)

    cursor.execute("""
        CREATE VIRTUAL TABLE search_index USING fts5(
            type,
            number,
            title,
            body,
            author,
            comment_text,
            content='issues',
            content_rowid='id'
        )
    """)

    conn.commit()
    return conn


def make_url(item_type, number):
    """Build a GitHub URL for the given item type and number."""
    if item_type == "pr":
        return f"https://github.com/{REPO}/pull/{number}"
    return f"https://github.com/{REPO}/issues/{number}"


def load_jsonl_issues(conn, filepath):
    """Load issues from JSON Lines file"""
    cursor = conn.cursor()

    if not filepath.exists():
        print(f"Warning: {filepath} not found")
        return 0

    count = 0
    with open(filepath) as f:
        for line in f:
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

            cursor.execute("""
                INSERT INTO issues (type, number, title, state, author, created_at, updated_at, body, url, fetched_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT(type, number) DO UPDATE SET
                    title=excluded.title,
                    state=excluded.state,
                    author=excluded.author,
                    created_at=excluded.created_at,
                    updated_at=excluded.updated_at,
                    body=excluded.body,
                    url=excluded.url,
                    fetched_at=excluded.fetched_at
            """, ("issue", number, title, state, author, created, updated, body, url, datetime.now().isoformat()))
            count += 1

    conn.commit()
    print(f"Loaded {count} issues")
    return count


def load_jsonl_prs(conn, filepath):
    """Load PRs from JSON Lines file"""
    cursor = conn.cursor()

    if not filepath.exists():
        print(f"Warning: {filepath} not found")
        return 0

    count = 0
    with open(filepath) as f:
        for line in f:
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

            cursor.execute("""
                INSERT INTO issues (type, number, title, state, author, created_at, updated_at, body, url, fetched_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT(type, number) DO UPDATE SET
                    title=excluded.title,
                    state=excluded.state,
                    author=excluded.author,
                    created_at=excluded.created_at,
                    updated_at=excluded.updated_at,
                    body=excluded.body,
                    url=excluded.url,
                    fetched_at=excluded.fetched_at
            """, ("pr", number, title, state, author, created, updated, body, url, datetime.now().isoformat()))
            count += 1

    conn.commit()
    print(f"Loaded {count} PRs")
    return count


def load_comments(conn, filepath, is_pr_comment=False):
    """Load comments from JSON Lines file"""
    cursor = conn.cursor()

    if not filepath.exists():
        print(f"Warning: {filepath} not found (no comments)")
        return 0

    count = 0
    with open(filepath) as f:
        for line in f:
            line = line.strip()
            if not line:
                continue
            try:
                item = json.loads(line)
            except json.JSONDecodeError:
                continue

            issue_pr_number = item.get('issue_number') or item.get('pr_number') or 0
            author = item.get('author', '') or 'unknown'
            created = item.get('created', '')
            body = item.get('body', '') or ''

            cursor.execute("""
                INSERT INTO comments (issue_pr_number, is_pr_comment, author, created_at, body, fetched_at)
                VALUES (?, ?, ?, ?, ?, ?)
            """, (issue_pr_number, 1 if is_pr_comment else 0, author, created, body, datetime.now().isoformat()))
            count += 1

    conn.commit()
    comment_type = "PR review comments" if is_pr_comment else "issue comments"
    print(f"Loaded {count} {comment_type}")
    return count


def populate_comment_text(conn):
    """Denormalize comment bodies into the issues table for FTS indexing."""
    cursor = conn.cursor()
    cursor.execute("""
        UPDATE issues SET comment_text = (
            SELECT GROUP_CONCAT(c.body, char(10) || char(10))
            FROM comments c
            WHERE c.issue_pr_number = issues.number
            ORDER BY c.created_at
        )
    """)
    conn.commit()
    updated = cursor.rowcount
    print(f"Populated comment_text for {updated} issues/PRs")


def rebuild_fts(conn):
    """Rebuild full-text search index"""
    cursor = conn.cursor()
    cursor.execute("INSERT INTO search_index(search_index) VALUES('rebuild')")
    conn.commit()


def get_status():
    """Get database statistics"""
    if not DB_PATH.exists():
        return None

    conn = sqlite3.connect(DB_PATH)
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
    fetched_at = row[0] if row else "unknown"

    conn.close()

    return {
        'issues': issues,
        'prs': prs,
        'issue_comments': issue_comments,
        'pr_comments': pr_comments,
        'fetched_at': fetched_at
    }


def main():
    print("=" * 60)
    print(f"Building GitHub Issues/PRs Search Database for {REPO}")
    print("=" * 60)

    print("\nInitializing database...")
    conn = init_db()

    print("\nLoading data from JSON Lines files...")
    load_jsonl_issues(conn, DATA_DIR / "issues.json")
    load_jsonl_prs(conn, DATA_DIR / "prs.json")
    load_comments(conn, DATA_DIR / "comments.json", is_pr_comment=False)
    load_comments(conn, DATA_DIR / "pr_comments.json", is_pr_comment=True)

    print("\nPopulating comment text for search index...")
    populate_comment_text(conn)

    print("\nBuilding search index...")
    rebuild_fts(conn)

    conn.close()

    print(f"\nDatabase created: {DB_PATH}")

    # Show status
    status = get_status()
    print(f"\nDatabase Status")
    print(f"==============")
    print(f"Issues:         {status['issues']}")
    print(f"PRs:            {status['prs']}")
    print(f"Issue comments: {status['issue_comments']}")
    print(f"PR comments:    {status['pr_comments']}")
    print(f"Last fetched:   {status['fetched_at']}")


if __name__ == "__main__":
    main()
