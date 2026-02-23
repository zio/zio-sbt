#!/usr/bin/env python3
"""
Search the GitHub Issues/PRs SQLite database using FTS5 full-text search.

Usage:
    python3 search_db.py <db_path> <query> <include_body>

Arguments:
    db_path      - Path to the SQLite database file
    query        - FTS5 search query string
    include_body - "True" to include full body text in results, "False" otherwise
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
        for row in results:
            print(f"{row[0]:5} #{row[1]}: {row[2][:60]}")
            print(f"       Author: {row[4]} | State: {row[3]}")
            print(f"       {row[5]}")
            if include_body and row[6]:
                print(f"       Body: {row[6]}")
            print()
except Exception as e:
    print(f'Search error: {e}')
finally:
    conn.close()
