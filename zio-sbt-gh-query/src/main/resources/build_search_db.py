#!/usr/bin/env python3
"""
Build searchable SQLite database from GitHub Issues/PRs for zio/zio-blocks

This script:
1. Reads full issue/PR details from JSON Lines files
2. Reads comments from JSON Lines files
3. Creates SQLite database with FTS5 full-text search
4. Stores title, body, comments, author, state, dates, URL

Usage:
    python3 scripts/build_search_db.py

To update the database later:
    bash scripts/fetch-github-data.sh    # Fetch latest data
    python3 scripts/build_search_db.py   # Rebuild database
"""

import sqlite3
import json
from pathlib import Path
from datetime import datetime

DATA_DIR = Path("github-data")
DB_PATH = Path("github-search.db")

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
            content='issues',
            content_rowid='id'
        )
    """)
    
    conn.commit()
    return conn

def load_jsonl_issues(conn, filepath):
    """Load issues from JSON Lines file"""
    cursor = conn.cursor()
    
    if not filepath.exists():
        print(f"Warning: {filepath} not found")
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
        url = f"https://github.com/zio/zio-blocks/issues/{number}"
        
        cursor.execute("""
            INSERT INTO issues (type, number, title, state, author, created_at, updated_at, body, url, fetched_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
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
        url = f"https://github.com/zio/zio-blocks/pull/{number}"
        
        cursor.execute("""
            INSERT INTO issues (type, number, title, state, author, created_at, updated_at, body, url, fetched_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
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

def rebuild_fts(conn):
    """Rebuild full-text search index including comments"""
    cursor = conn.cursor()
    
    # Rebuild the index
    cursor.execute("INSERT INTO search_index(search_index) VALUES('rebuild')")
    conn.commit()

def search(query, limit=10):
    """Search the database"""
    if not DB_PATH.exists():
        return []
    
    conn = sqlite3.connect(DB_PATH)
    cursor = conn.cursor()
    
    try:
        cursor.execute("""
            SELECT i.type, i.number, i.title, i.state, i.author, i.url,
                   snippet(search_index, 3, '', '', '...', 64)
            FROM search_index s
            JOIN issues i ON i.id = s.rowid
            WHERE search_index MATCH ?
            ORDER BY rank
            LIMIT ?
        """, (query, limit))
        
        results = []
        for row in cursor.fetchall():
            results.append({
                'type': row[0],
                'number': row[1],
                'title': row[2],
                'state': row[3],
                'author': row[4],
                'url': row[5],
                'snippet': row[6]
            })
    except Exception as e:
        print(f"Search error: {e}")
        results = []
    
    conn.close()
    return results

def get_full_issue(number):
    """Get full issue details by number including comments"""
    conn = sqlite3.connect(DB_PATH)
    cursor = conn.cursor()
    
    # Get issue/PR
    cursor.execute("SELECT * FROM issues WHERE number = ?", (number,))
    row = cursor.fetchone()
    
    if not row:
        conn.close()
        return None
    
    issue = {
        'id': row[0], 'type': row[1], 'number': row[2], 'title': row[3],
        'state': row[4], 'author': row[5], 'created_at': row[6], 
        'updated_at': row[7], 'body': row[8], 'url': row[9], 'fetched_at': row[10],
        'comments': []
    }
    
    # Get comments
    cursor.execute("SELECT author, created_at, body FROM comments WHERE issue_pr_number = ? AND is_pr_comment = 0 ORDER BY created_at", (number,))
    issue['comments'] = [
        {'author': r[0], 'created_at': r[1], 'body': r[2]}
        for r in cursor.fetchall()
    ]
    
    conn.close()
    return issue

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
    print("Building GitHub Issues/PRs Search Database")
    print("=" * 60)
    
    print("\nInitializing database...")
    conn = init_db()
    
    print("\nLoading data from JSON Lines files...")
    load_jsonl_issues(conn, DATA_DIR / "issues.json")
    load_jsonl_prs(conn, DATA_DIR / "prs.json")
    load_comments(conn, DATA_DIR / "comments.json", is_pr_comment=False)
    load_comments(conn, DATA_DIR / "pr_comments.json", is_pr_comment=True)
    
    print("\nBuilding search index...")
    rebuild_fts(conn)
    
    conn.close()
    
    print(f"\nDatabase created: {DB_PATH}")
    
    # Show status
    status = get_status()
    print(f"\nDatabase Status")
    print(f"==============")
    print(f"Issues:        {status['issues']}")
    print(f"PRs:           {status['prs']}")
    print(f"Issue comments:{status['issue_comments']}")
    print(f"PR comments:   {status['pr_comments']}")
    print(f"Last fetched:  {status['fetched_at']}")

if __name__ == "__main__":
    main()
