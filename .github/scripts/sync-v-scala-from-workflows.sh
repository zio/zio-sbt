#!/usr/bin/env bash
# Copies GitHub Action version pins from the generated workflow files back into V.scala.
#
# Dependabot's github-actions ecosystem can only edit `.github/workflows/*.yml`; V.scala is the
# source of truth that `sbt ciGenerateGithubWorkflow` renders those files from. Running this
# before regeneration makes V.scala follow Dependabot's bump instead of the regeneration
# reverting it.
set -euo pipefail

V_SCALA="zio-sbt-ci/src/main/scala/zio/sbt/V.scala"
GENERATED=(ci auto-approve auto-merge deploy-preview)

for name in "${GENERATED[@]}"; do
  file=".github/workflows/${name}.yml"
  [ -f "$file" ] || continue
  # `uses: owner/repo@version` (optionally with a trailing `# comment`).
  # `|| true`: grep exits 1 on files with no match (e.g. auto-approve.yml), which under
  # `pipefail` + `set -e` would abort the whole script and skip the remaining files.
  { grep -oE 'uses:[[:space:]]+[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+@[^[:space:]#]+' "$file" || true; } | sed -E 's/uses:[[:space:]]+//' | sort -u |
  while IFS='@' read -r action version; do
    # only actions V.scala already knows about; skips sub-path actions and unmanaged ones
    if grep -qE "\"${action}\"[[:space:]]*->" "$V_SCALA"; then
      sed -i -E "s#(\"${action}\"[[:space:]]*->[[:space:]]*\")[^\"]*\"#\1${version}\"#" "$V_SCALA"
    fi
  done
done
