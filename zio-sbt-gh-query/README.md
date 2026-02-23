# ZIO SBT GitHub Query Plugin

SBT plugin for fetching GitHub issues/PRs and building a searchable SQLite database with full-text search.

## Installation

Add to `project/plugins.sbt`:

```scala
addSbtPlugin("dev.zio" % "zio-sbt-gh-query" % "0.1.0")
```

The plugin is auto-enabled. Configure in `build.sbt`:

```scala
// Required: specify your GitHub repository
ghRepo := "your-org/your-repo"

// Optional: override the default data directory (defaults to .zio-sbt)
ghDir := file(".zio-sbt")
```

## Commands

| Command | Description |
|---------|-------------|
| `gh-sync` | Fetch data from GitHub and build/update the search database. On first run (or with `--force`), does a full fetch and rebuild. On subsequent runs, fetches only new/updated items incrementally. |
| `gh-query <query>` | Full-text search across issues and PRs. Supports `--verbose` flag to include body text. |
| `gh-status` | Show database statistics (issue/PR/comment counts, last fetch time). |

## Usage

### Initial Setup

```bash
# Fetch all issues/PRs and build the database
sbt gh-sync
```

### Regular Updates

```bash
# Incrementally fetch new/updated items and update the database
sbt gh-sync
```

### Force Full Refresh

```bash
# Re-fetch everything and rebuild the database from scratch
sbt "gh-sync --force"
```

### Search

```bash
# Basic query
sbt "gh-query codec"

# Query with full body content
sbt "gh-query --verbose codec"
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
└── .zio-sbt/                # Plugin data directory (auto-created)
    ├── github-data/          # Fetched JSON files
    │   ├── issues.json
    │   ├── prs.json
    │   ├── comments.json
    │   └── pr_comments.json
    └── gh.db                 # SQLite database
```
