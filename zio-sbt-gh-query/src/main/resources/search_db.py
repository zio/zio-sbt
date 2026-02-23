#!/usr/bin/env python3
"""
Search the GitHub Issues/PRs SQLite database using FTS5 full-text search.

Usage:
    python3 search_db.py <db_path> <query> <include_body>

Arguments:
    db_path      - Path to the SQLite database file
    query        - FTS5 search query string
    include_body - "True" for verbose output (full body + comments), "False" for compact
"""

import sqlite3
import sys

db_path = sys.argv[1]
query = sys.argv[2]
include_body = sys.argv[3] == "True"

conn = sqlite3.connect(db_path)
cursor = conn.cursor()
try:
    cursor.execute('''
        SELECT i.type, i.number, i.title, i.state, i.author, i.url, i.body
        FROM search_index s
        JOIN issues i ON i.id = s.rowid
        WHERE search_index MATCH ?
        ORDER BY rank
        LIMIT 20
    ''', (query,))
    results = cursor.fetchall()
    if not results:
        print('No results found')
    else:
        for idx, row in enumerate(results):
            item_type, number, title, state, author, url, body = row

            if include_body:
                # Verbose: full title, body, and all comments
                if idx > 0:
                    print("-" * 72)
                    print()
                label = "PR" if item_type == "pr" else "Issue"
                print(f"{label} #{number}: {title}")
                print(f"  Author: {author} | State: {state}")
                print(f"  {url}")
                if body:
                    print()
                    print(body)

                # Fetch and display all comments for this issue/PR
                cursor.execute('''
                    SELECT author, created_at, body
                    FROM comments
                    WHERE issue_pr_number = ?
                    ORDER BY created_at
                ''', (number,))
                comments = cursor.fetchall()
                if comments:
                    print()
                    for c_author, c_date, c_body in comments:
                        date_display = c_date[:10] if c_date else "unknown"
                        print(f"  --- Comment by {c_author} on {date_display} ---")
                        if c_body:
                            # Indent comment body for readability
                            for line in c_body.splitlines():
                                print(f"  {line}")
                        print()
            else:
                # Compact: full title, author, state, URL
                label = "PR" if item_type == "pr" else "Issue"
                print(f"{label:5} #{number}: {title}")
                print(f"       Author: {author} | State: {state}")
                print(f"       {url}")
                print()
except Exception as e:
    print(f'Search error: {e}')
finally:
    conn.close()
