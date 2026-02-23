#!/bin/bash
# =============================================================================
# Fetch all GitHub Issues, PRs, and Comments
# Usage: GH_QUERY_REPO=owner/repo GH_QUERY_DATA_DIR=/path/to/data ./fetch-github-data.sh
# =============================================================================

set -e

REPO="${GH_QUERY_REPO:?GH_QUERY_REPO environment variable must be set (e.g. zio/zio-sbt)}"
OUTPUT_DIR="${GH_QUERY_DATA_DIR:?GH_QUERY_DATA_DIR environment variable must be set}"

mkdir -p "$OUTPUT_DIR"

echo "Fetching issues list for $REPO..."
gh issue list --repo "$REPO" --state all --limit 1000 > "$OUTPUT_DIR/issues.csv"

echo "Fetching PRs list for $REPO..."
gh pr list --repo "$REPO" --state all --limit 1000 > "$OUTPUT_DIR/prs.csv"

echo "Fetching full issue details..."
gh api "repos/$REPO/issues?state=all&per_page=100" --paginate -q '.[] | select(.pull_request == null) | {number: .number, title: .title, state: .state, author: .user.login, created: .created_at, updated: .updated_at, body: .body, comments: .comments}' > "$OUTPUT_DIR/issues.json"

echo "Fetching full PR details..."
gh api "repos/$REPO/pulls?state=all&per_page=100" --paginate -q '.[] | {number: .number, title: .title, state: .state, author: .user.login, created: .created_at, updated: .updated_at, body: .body, merged: .merged_at}' > "$OUTPUT_DIR/prs.json"

echo "Fetching issue comments (this may take a few minutes)..."
> "$OUTPUT_DIR/comments.json"
TEMP_ISSUES=$(mktemp "${TMPDIR:-/tmp}/gh_query_issues_XXXXXX.json")
trap "rm -f '$TEMP_ISSUES'" EXIT
gh issue list --repo "$REPO" --state all --limit 1000 --json number > "$TEMP_ISSUES"
numbers=$(jq -r '.[].number' < "$TEMP_ISSUES")
count=0
for num in $numbers; do
    gh api "repos/$REPO/issues/$num/comments" --paginate -q '.[] | {issue_number: '$num', author: .user.login, created: .created_at, body: .body}' >> "$OUTPUT_DIR/comments.json"
    count=$((count + 1))
    if [ $((count % 20)) -eq 0 ]; then
        echo "  Fetched comments for $count issues..."
    fi
done
echo "  Fetched comments for $count issues"

echo "Fetching PR review comments (this may take a few minutes)..."
> "$OUTPUT_DIR/pr_comments.json"
count=0
pr_numbers=$(cut -f1 < "$OUTPUT_DIR/prs.csv" | head -100)
for num in $pr_numbers; do
    comments=$(gh api "repos/$REPO/pulls/$num/comments" --paginate -q '.[] | {pr_number: '$num', author: .user.login, created: .created_at, body: .body}' 2>/dev/null)
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
echo "  - issues.csv: $(wc -l < "$OUTPUT_DIR/issues.csv") issues"
echo "  - prs.csv: $(wc -l < "$OUTPUT_DIR/prs.csv") PRs"
echo "  - issues.json: $(wc -l < "$OUTPUT_DIR/issues.json") issue details"
echo "  - prs.json: $(wc -l < "$OUTPUT_DIR/prs.json") PR details"
echo "  - comments.json: $(wc -l < "$OUTPUT_DIR/comments.json") issue comments"
echo "  - pr_comments.json: $(wc -l < "$OUTPUT_DIR/pr_comments.json") PR review comments"
