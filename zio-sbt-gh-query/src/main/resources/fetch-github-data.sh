#!/bin/bash
# =============================================================================
# Fetch all GitHub Issues, PRs, and Comments
# Usage: GH_QUERY_REPO=owner/repo GH_QUERY_DATA_DIR=/path/to/data ./fetch-github-data.sh
# =============================================================================

set -e

REPO="${GH_QUERY_REPO:?GH_QUERY_REPO environment variable must be set (e.g. zio/zio-sbt)}"
OUTPUT_DIR="${GH_QUERY_DATA_DIR:?GH_QUERY_DATA_DIR environment variable must be set}"
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

echo "Fetching full issue details..."
retry gh api "repos/$REPO/issues?state=all&per_page=100" --paginate -q '.[] | select(.pull_request == null) | {number: .number, title: .title, state: .state, author: .user.login, created: .created_at, updated: .updated_at, body: .body, comments: .comments}' > "$OUTPUT_DIR/issues.json"

echo "Fetching full PR details..."
retry gh api "repos/$REPO/pulls?state=all&per_page=100" --paginate -q '.[] | {number: .number, title: .title, state: .state, author: .user.login, created: .created_at, updated: .updated_at, body: .body, merged: .merged_at}' > "$OUTPUT_DIR/prs.json"

echo "Fetching issue comments (this may take a few minutes)..."
> "$OUTPUT_DIR/comments.json"
# Extract issue numbers from the already-fetched issues.json (which uses paginated API)
# instead of gh issue list --limit 1000 which may miss issues in large repos.
numbers=$(jq -r '.number' < "$OUTPUT_DIR/issues.json")
count=0
failed=0
for num in $numbers; do
    if retry gh api "repos/$REPO/issues/$num/comments" --paginate -q '.[] | {issue_number: '$num', author: .user.login, created: .created_at, body: .body}' >> "$OUTPUT_DIR/comments.json"; then
        : # success
    else
        echo "  [warn] Skipping comments for issue #$num after $MAX_RETRIES failed attempts"
        failed=$((failed + 1))
    fi
    count=$((count + 1))
    if [ $((count % 20)) -eq 0 ]; then
        echo "  Fetched comments for $count issues..."
    fi
done
echo "  Fetched comments for $count issues ($failed failed)"

echo "Fetching PR review comments (this may take a few minutes)..."
> "$OUTPUT_DIR/pr_comments.json"
count=0
failed=0
# Extract PR numbers from the already-fetched prs.json (which uses paginated API)
# instead of gh pr list --limit 1000 which may miss PRs in large repos.
pr_numbers=$(jq -r '.number' < "$OUTPUT_DIR/prs.json")
for num in $pr_numbers; do
    comments=$(retry gh api "repos/$REPO/pulls/$num/comments" --paginate -q '.[] | {pr_number: '$num', author: .user.login, created: .created_at, body: .body}' 2>/dev/null) || true
    if [ -n "$comments" ]; then
        echo "$comments" >> "$OUTPUT_DIR/pr_comments.json"
    fi
    count=$((count + 1))
    if [ $((count % 20)) -eq 0 ]; then
        echo "  Fetched review comments for $count PRs..."
    fi
done
echo "  Fetched review comments for $count PRs"

echo ""
echo "Done! Data saved to $OUTPUT_DIR/"
echo "  - issues.json: $(wc -l < "$OUTPUT_DIR/issues.json") issue details"
echo "  - prs.json: $(wc -l < "$OUTPUT_DIR/prs.json") PR details"
echo "  - comments.json: $(wc -l < "$OUTPUT_DIR/comments.json") issue comments"
echo "  - pr_comments.json: $(wc -l < "$OUTPUT_DIR/pr_comments.json") PR review comments"
