# ZIO SBT GitHub Query Plugin

SBT plugin for fetching GitHub issues/PRs and building a searchable SQLite database with full-text search.

## Installation

Add to `project/plugins.sbt`:

```scala
addSbtPlugin("dev.zio" % "zio-sbt-gh-query" % "0.1.0")
```

The plugin is auto-enabled. Optionally configure in `build.sbt`:

```scala
ghRepo := "zio/zio-blocks"
ghDir := file(".zio-sbt")
```

## Commands

All commands start with `gh-` prefix:

| Command | Description |
|---------|-------------|
| `gh-fetch` | Fetch all issues and PRs from GitHub (full fetch) |
| `gh-update` | Incrementally update only new/updated issues/PRs since last fetch |
| `gh-update-db` | Incrementally update database with new/updated data |
| `gh-rebuild-db` | Rebuild database from scratch (drops and recreates all data) |
| `gh-query <query>` | Query the GitHub database |
| `gh-status` | Show database statistics |

## Usage Examples

### Initial Setup

```bash
# Fetch all issues and PRs, then build the database
sbt gh-fetch gh-rebuild-db
```

### Regular Updates

```bash
# Update with new issues/PRs since last fetch
sbt gh-update gh-update-db
```

### Query

```bash
# Basic query
sbt "gh-query Into"

# Query with full body content (--verbose flag)
sbt "gh-query --verbose Into"
```

### Check Status

```bash
sbt gh-status
```

## Dependencies

- `gh` CLI (GitHub CLI) - for fetching data
- Python 3 with sqlite3 - for database operations

## Database Schema

The plugin creates a SQLite database with:

- `issues` table - stores issues and PRs
- `comments` table - stores issue and PR comments
- `search_index` - FTS5 full-text search index

## Project Structure

```
your-project/
├── build.sbt
├── project/
│   └── plugins.sbt          # Add plugin here
├── github-data/              # Fetched JSON files (auto-created)
│   ├── issues.json
│   ├── prs.json
│   ├── comments.json
│   └── pr_comments.json
└── github-search.db          # SQLite database (auto-created)
```
