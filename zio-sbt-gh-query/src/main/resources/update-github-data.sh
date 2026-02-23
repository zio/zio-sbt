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

TEMP_ISSUES="$OUTPUT_DIR/issues_new.json"
TEMP_PRS="$OUTPUT_DIR/prs_new.json"
TEMP_COMMENTS="$OUTPUT_DIR/comments_new.json"

echo "Fetching updated issues for $REPO..."
retry gh api "repos/$REPO/issues?state=all&per_page=100&since=$SINCE" --paginate -q '.[] | select(.pull_request == null) | {number: .number, title: .title, state: .state, author: .user.login, created: .created_at, updated: .updated_at, body: .body, comments: .comments}' > "$TEMP_ISSUES"

echo "Fetching updated PRs for $REPO..."
retry gh api "repos/$REPO/pulls?state=all&per_page=100&since=$SINCE" --paginate -q '.[] | {number: .number, title: .title, state: .state, author: .user.login, created: .created_at, updated: .updated_at, body: .body, merged: .merged_at}' > "$TEMP_PRS"

ISSUE_COUNT=$(wc -l < "$TEMP_ISSUES")
PR_COUNT=$(wc -l < "$TEMP_PRS")

echo "Found $ISSUE_COUNT updated issues, $PR_COUNT updated PRs"

if [ "$ISSUE_COUNT" -gt 0 ] || [ "$PR_COUNT" -gt 0 ]; then
    echo "Updating database..."
    GH_QUERY_REPO="$REPO" GH_QUERY_DATA_DIR="$OUTPUT_DIR" GH_QUERY_DB_PATH="$DB_PATH" \
        python3 "$SCRIPTS_DIR/update_search_db.py" "$TEMP_ISSUES" "$TEMP_PRS" "$TEMP_COMMENTS"

    cat "$TEMP_ISSUES" >> "$OUTPUT_DIR/issues.json"
    cat "$TEMP_PRS" >> "$OUTPUT_DIR/prs.json"

    echo "Cleaning up temp files..."
    rm -f "$TEMP_ISSUES" "$TEMP_PRS" "$TEMP_COMMENTS"
else
    echo "No new items to update"
    rm -f "$TEMP_ISSUES" "$TEMP_PRS" "$TEMP_COMMENTS"
fi

echo "$(date -u +"%Y-%m-%dT%H:%M:%SZ")" > "$LAST_FETCH_FILE"

echo ""
echo "Update complete!"
