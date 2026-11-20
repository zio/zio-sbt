# ZIO-198: website tasks read website/docs before mdoc wrote it (sbt 2)

## Root cause (confirmed by a minimal repro, no zio-sbt involved)

- sbt-mdoc's `mdoc` task is `(Compile / runMain).toTask(" mdoc.SbtMain ...")` (sbt-mdoc_sbt2_3 2.9.x, MdocPlugin.scala).
- In sbt 2, `runMain` is `RunUtil.defaultRunMainTask(..., runMain / clientSide)` and `clientSide :== true` by default
  (sbt 2.0.x Defaults.scala globalCore, RunUtil.scala). When the command came from a thin client
  (`state.isNetworkCommand`), runMain does NOT run the main class: it sends a `ClientJobParams`
  notification to the client and returns at once. The client forks the JVM itself.
- The sbt 2 launcher script runs every command through the thin client (sbtn) by default.
- So `compileDocs.toTask("").value` completes before mdoc has written anything, and the task body that
  reads `website/docs` runs too early:
  - `generateReadmeTask` (WebsitePlugin.scala:569-585) -> `FileNotFoundException website/docs/index.md`
  - `buildWebsiteTask` (:330) -> Docusaurus "Cannot find module './docs/sidebars.js'"
  - `publishToNpmTask`, `publishHashverToNpmTask`, `publishSnapshotToNpmTask`,
    `publishWebsiteSemanticVersioningTask`, `checkDocsOnFreshWebsiteTask`, `previewWebsiteTask`: same pattern.
- The misleading log order ("Compiled ... (0 errors)" before the exception) is the client printing the
  forked mdoc output before relaying the server's error.

Repro (scratch project, sbt 2.0.11 + sbt-mdoc 2.9.2, a task `mdoc.toTask("").value; IO.read(out/index.md)`):
- `sbt --server docs/readIndexU` -> passes.
- `sbt docs/readIndexU` (thin client, the default) -> `running (fork) mdoc.SbtMain`, `Compiled in 5.3s`,
  `exists=false`, `FileNotFoundException`; file exists afterwards. Same signature as zio-cli CI.
- add `docs / Compile / runMain / clientSide := false` -> thin client run passes.

Hypotheses ruled out: `.value` ordering (body runs after deps, as in sbt 1); `inputTaskDyn` + `toTask("")`
(works in `--server` mode); sbt 2 task caching (plugin task bodies are `Def.task`, uncached);
index revert/rename tasks (repro fails without them).

## Fix

In `WebsitePlugin.projectSettings`, set `Compile / runMain / clientSide := false` on sbt 2, so mdoc runs
inside the server and `compileDocs` returns only after mdoc has written its output. The key does not exist
in sbt 1, so it lives in a version-specific `WebsitePluginCompat` (scala-2.12: no settings; scala-3: the
setting). Public keys unchanged.

## Test

New sbt-2 scripted fixture that runs `compileDocs` and `generateReadme` as a thin-client command would
(a `network-*` command source, which is what `State.isNetworkCommand` checks), in one sbt command, from an
empty `website/docs`. Must fail before the fix and pass after it. Add it to the CI "Check sbt-2.x scripted
fixtures" step and regenerate the workflows.
