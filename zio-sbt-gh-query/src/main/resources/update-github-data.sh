#!/bin/bash
# =============================================================================
# Update GitHub Issues and PRs - Fetch only new/updated items since last fetch
# Usage: GH_QUERY_REPO=owner/repo GH_QUERY_DATA_DIR=/path/to/data \
#        GH_QUERY_DB_PATH=/path/to/db GH_QUERY_SCRIPTS=/path/to/scripts \
#        ./update-github-data.sh
# =============================================================================

set -e

REPO="${GH_QUERY_REPO:?GH_QUERY_REPO environment variable must be set (e.g. zio/zio-sbt)}"
OUTPUT_DIR="${GH_QUERY_DATA_DIR:?GH_QUERY_DATA_DIR environment variable must be set}"
DB_PATH="${GH_QUERY_DB_PATH:?GH_QUERY_DB_PATH environment variable must be set}"
SCRIPTS_DIR="${GH_QUERY_SCRIPTS:?GH_QUERY_SCRIPTS environment variable must be set}"
LAST_FETCH_FILE="$OUTPUT_DIR/.last_fetch"
MAX_RETRIES=3
RETRY_DELAY=5

mkdir -p "$OUTPUT_DIR"

# Retry wrapper: runs a command up to MAX_RETRIES times with exponential backoff.
# Usage: retry <command...>
retry() {
    local attempt=1
    while true; do
        if "$@"; then
            return 0
        fi
        if [ "$attempt" -ge "$MAX_RETRIES" ]; then
            echo "  [error] Failed after $MAX_RETRIES attempts: $*" >&2
            return 1
        fi
        local delay=$((RETRY_DELAY * attempt))
        echo "  [warn] Attempt $attempt failed, retrying in ${delay}s..."
        sleep "$delay"
        attempt=$((attempt + 1))
    done
}

if [ -f "$LAST_FETCH_FILE" ]; then
    SINCE=$(cat "$LAST_FETCH_FILE")
    echo "Fetching issues/PRs updated since: $SINCE"
else
    SINCE="1970-01-01T00:00:00Z"
    echo "First fetch - getting all issues/PRs"
fi

TEMP_ISSUES=$(mktemp "${TMPDIR:-/tmp}/gh_query_issues_XXXXXX.json")
TEMP_PRS=$(mktemp "${TMPDIR:-/tmp}/gh_query_prs_XXXXXX.json")
TEMP_COMMENTS=$(mktemp "${TMPDIR:-/tmp}/gh_query_comments_XXXXXX.json")
TEMP_PR_COMMENTS=$(mktemp "${TMPDIR:-/tmp}/gh_query_pr_comments_XXXXXX.json")
TEMP_PR_NUMBERS=$(mktemp "${TMPDIR:-/tmp}/gh_query_pr_nums_XXXXXX.json")

trap "rm -f '$TEMP_PR_NUMBERS' '$TEMP_ISSUES' '$TEMP_PRS' '$TEMP_COMMENTS' '$TEMP_PR_COMMENTS'" EXIT

echo "Fetching updated issues for $REPO..."
retry gh api "repos/$REPO/issues?state=all&per_page=100&since=$SINCE" --paginate -q '.[] | select(.pull_request == null) | {number: .number, title: .title, state: .state, author: .user.login, created: .created_at, updated: .updated_at, body: .body, comments: .comments}' > "$TEMP_ISSUES"

echo "Fetching updated PRs for $REPO..."
# The Pulls API does not support the 'since' parameter, so we use the
# Issues API (which includes PRs) to discover updated PR numbers, then
# fetch full details from the Pulls API for each one.
retry gh api "repos/$REPO/issues?state=all&per_page=100&since=$SINCE" --paginate \
    -q '.[] | select(.pull_request != null) | .number' > "$TEMP_PR_NUMBERS"
> "$TEMP_PRS"
while read -r pr_num; do
    retry gh api "repos/$REPO/pulls/$pr_num" \
        -q '{number: .number, title: .title, state: .state, author: .user.login, created: .created_at, updated: .updated_at, body: .body, merged: .merged_at}' >> "$TEMP_PRS"
done < "$TEMP_PR_NUMBERS"
rm -f "$TEMP_PR_NUMBERS"

ISSUE_COUNT=$(wc -l < "$TEMP_ISSUES")
PR_COUNT=$(wc -l < "$TEMP_PRS")

echo "Found $ISSUE_COUNT updated issues, $PR_COUNT updated PRs"

# Fetch comments for all updated issues and PRs.
# We re-fetch all comments for each updated item to capture new/edited comments.
> "$TEMP_COMMENTS"
> "$TEMP_PR_COMMENTS"

if [ "$ISSUE_COUNT" -gt 0 ] || [ "$PR_COUNT" -gt 0 ]; then
    echo "Fetching comments for updated issues..."
    while IFS= read -r line; do
        num=$(echo "$line" | python3 -c "import sys,json; print(json.load(sys.stdin).get('number',0))" 2>/dev/null) || continue
        [ "$num" = "0" ] && continue
        retry gh api "repos/$REPO/issues/$num/comments" --paginate --jq \
            ".[] | {issue_number: $num, author: .user.login, created: .created_at, body: .body}" >> "$TEMP_COMMENTS" 2>/dev/null || true
    done < "$TEMP_ISSUES"

    echo "Fetching comments for updated PRs..."
    while IFS= read -r line; do
        num=$(echo "$line" | python3 -c "import sys,json; print(json.load(sys.stdin).get('number',0))" 2>/dev/null) || continue
        [ "$num" = "0" ] && continue
        # Fetch issue-style comments on the PR
        retry gh api "repos/$REPO/issues/$num/comments" --paginate --jq \
            ".[] | {issue_number: $num, author: .user.login, created: .created_at, body: .body}" >> "$TEMP_COMMENTS" 2>/dev/null || true
        # Fetch PR review comments
        retry gh api "repos/$REPO/pulls/$num/comments" --paginate --jq \
            ".[] | {pr_number: $num, author: .user.login, created: .created_at, body: .body}" >> "$TEMP_PR_COMMENTS" 2>/dev/null || true
    done < "$TEMP_PRS"

    COMMENT_COUNT=$(wc -l < "$TEMP_COMMENTS")
    PR_COMMENT_COUNT=$(wc -l < "$TEMP_PR_COMMENTS")
    echo "Fetched $COMMENT_COUNT issue comments, $PR_COMMENT_COUNT PR review comments"

    echo "Updating database..."
    GH_QUERY_REPO="$REPO" GH_QUERY_DATA_DIR="$OUTPUT_DIR" GH_QUERY_DB_PATH="$DB_PATH" \
        python3 "$SCRIPTS_DIR/update_search_db.py" "$TEMP_ISSUES" "$TEMP_PRS" "$TEMP_COMMENTS" "$TEMP_PR_COMMENTS"

    # Merge updated issues into snapshot, de-duplicating by issue number.
    if [ -s "$TEMP_ISSUES" ]; then
        python3 - "$OUTPUT_DIR/issues.json" "$TEMP_ISSUES" <<'PY_MERGE_ISSUES' || { echo "[error] Failed to merge issues snapshot" >&2; exit 1; }
import sys, json, os

out_path, new_path = sys.argv[1:3]
items = {}

def load(path):
    if not os.path.exists(path):
        return
    with open(path, "r", encoding="utf-8") as f:
        for line in f:
            line = line.strip()
            if not line:
                continue
            try:
                obj = json.loads(line)
            except json.JSONDecodeError:
                continue
            num = obj.get("number")
            if num is not None:
                items[num] = obj

load(out_path)
load(new_path)

with open(out_path, "w", encoding="utf-8") as out:
    for obj in items.values():
        out.write(json.dumps(obj, ensure_ascii=False) + "\n")
PY_MERGE_ISSUES
    fi

    # Merge updated PRs into snapshot, de-duplicating by PR number.
    if [ -s "$TEMP_PRS" ]; then
        python3 - "$OUTPUT_DIR/prs.json" "$TEMP_PRS" <<'PY_MERGE_PRS' || { echo "[error] Failed to merge PRs snapshot" >&2; exit 1; }
import sys, json, os

out_path, new_path = sys.argv[1:3]
items = {}

def load(path):
    if not os.path.exists(path):
        return
    with open(path, "r", encoding="utf-8") as f:
        for line in f:
            line = line.strip()
            if not line:
                continue
            try:
                obj = json.loads(line)
            except json.JSONDecodeError:
                continue
            num = obj.get("number")
            if num is not None:
                items[num] = obj

load(out_path)
load(new_path)

with open(out_path, "w", encoding="utf-8") as out:
    for obj in items.values():
        out.write(json.dumps(obj, ensure_ascii=False) + "\n")
PY_MERGE_PRS
    fi

    # Merge comment snapshots, deduplicating by (issue_number/pr_number, author, created).
    if [ -s "$TEMP_COMMENTS" ]; then
        python3 - "$OUTPUT_DIR/comments.json" "$TEMP_COMMENTS" <<'PY_MERGE_COMMENTS' || { echo "[error] Failed to merge comments snapshot" >&2; exit 1; }
import sys, json, os

out_path, new_path = sys.argv[1:3]
items = {}

def comment_key(obj):
    num = obj.get("issue_number") or obj.get("pr_number") or 0
    return (num, obj.get("author", ""), obj.get("created", ""))

def load(path):
    if not os.path.exists(path):
        return
    with open(path, "r", encoding="utf-8") as f:
        for line in f:
            line = line.strip()
            if not line:
                continue
            try:
                obj = json.loads(line)
            except json.JSONDecodeError:
                continue
            items[comment_key(obj)] = obj

load(out_path)
load(new_path)

with open(out_path, "w", encoding="utf-8") as out:
    for obj in items.values():
        out.write(json.dumps(obj, ensure_ascii=False) + "\n")
PY_MERGE_COMMENTS
    fi
    if [ -s "$TEMP_PR_COMMENTS" ]; then
        python3 - "$OUTPUT_DIR/pr_comments.json" "$TEMP_PR_COMMENTS" <<'PY_MERGE_PR_COMMENTS' || { echo "[error] Failed to merge PR comments snapshot" >&2; exit 1; }
import sys, json, os

out_path, new_path = sys.argv[1:3]
items = {}

def comment_key(obj):
    num = obj.get("pr_number") or obj.get("issue_number") or 0
    return (num, obj.get("author", ""), obj.get("created", ""))

def load(path):
    if not os.path.exists(path):
        return
    with open(path, "r", encoding="utf-8") as f:
        for line in f:
            line = line.strip()
            if not line:
                continue
            try:
                obj = json.loads(line)
            except json.JSONDecodeError:
                continue
            items[comment_key(obj)] = obj

load(out_path)
load(new_path)

with open(out_path, "w", encoding="utf-8") as out:
    for obj in items.values():
        out.write(json.dumps(obj, ensure_ascii=False) + "\n")
PY_MERGE_PR_COMMENTS
    fi

    echo "Cleaning up temp files..."
else
    echo "No new items to update"
fi

echo "$(date -u +"%Y-%m-%dT%H:%M:%SZ")" > "$LAST_FETCH_FILE"

echo ""
echo "Update complete!"
