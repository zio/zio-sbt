# Design: compile Scaladoc examples with a shared sbt plugin (ZIO-204)

Status: **draft, waiting for Milad's approval.** No code yet. This file is removed before the PR.

## 1. The problem

Scaladoc comments contain code examples between `{{{` and `}}}`. Nothing compiles them, so they rot: they call
methods that were renamed, use types they never define, or show wrong results. zio-blocks fixed this for itself
(PR #1813, then #1850, #1969, #1977): every example in its Scaladoc is compiled on every Scala version in CI.

About 30 other ZIO repos have the same problem, and the Scaladoc rollout (ZIO-201) is about to add many more
examples to them.

## 2. The goal

Any ZIO library can compile its Scaladoc examples on every Scala version it builds (2.12, 2.13, 3), in CI, by
adding about 10 lines to its build. The code that does this lives in **one** place and is maintained once.

## 3. Where should this live? (zio-blocks, zio-sbt, or elsewhere)

First, what zio-blocks actually has. It is **not** a reusable module. It is build-local code:

- `project/ScaladocSnippets.scala` (337 lines): finds the examples and writes each one into its own `object`.
  It lives in zio-blocks' meta-build (the `project/` folder that holds code the build itself runs), so only
  zio-blocks' own build can call it.
- About 150 lines in `build.sbt`: three unpublished projects (`scaladoc-check`, `scaladoc-check-scala3`,
  `scaladoc-check-jdk21`) that depend on the checked modules and compile the generated objects.
- `scaladoc-check/src/test/scala/ScaladocSnippetsSpec.scala` (589 lines): unit tests for the extractor.

Nothing of it is published, so another repo can only use it by **copying** it. Three options:

| Option | How other repos use it | Cost |
|---|---|---|
| **A. Leave it in zio-blocks, copy it** | Copy ~500 lines of build code into each repo | 30 build PRs, 30 copies that drift apart. zio-blocks-specific rules (a folder-to-package map, an `sql` special case) would have to be undone in each copy. |
| **B. Publish it from zio-blocks** | `addSbtPlugin("dev.zio" % "zio-blocks-scaladoc-check" ...)` | zio-blocks is a library, not a build toolkit. It would need to cross-build an sbt plugin for sbt 1 (Scala 2.12) and sbt 2 (Scala 3), plus scripted tests and plugin publishing, which its build has none of. Libraries would depend on zio-blocks releases for a build tool. |
| **C. Port it into zio-sbt (recommended)** | Already load zio-sbt; enable one plugin | One copy. zio-sbt is already the shared build toolkit of the ZIO ecosystem, already cross-builds for sbt 1 and sbt 2, already has scripted tests, and already generates CI, so the CI switch sits next to it. |
| D. Only the out-of-tree scala-cli script (ZIO-203) | No build change | Compiles against a published or `publishLocal` artifact, is slow, and does not run in the repo's CI. Good for small repos and for writers; not a CI gate. It complements C. |

**Recommendation: C.** zio-blocks keeps its own copy for now (it works and is tuned to its 40 modules). Once
zio-sbt has released the plugin, zio-blocks *may* switch to it in a separate PR, which deletes its 337-line
extractor; section 13 lists what that needs. That decision is not part of this PR.

**Which zio-sbt module: a new module `zio-sbt-scaladoc` (decided by Milad, 2026-11-20).** It is published as
its own sbt plugin artifact, `dev.zio:zio-sbt-scaladoc`, cross-built for sbt 1 (Scala 2.12) and sbt 2
(Scala 3) like the other modules. A repo loads it with one `addSbtPlugin` line. (The alternative, putting it
inside `zio-sbt-website` next to `UnifiedScaladocPlugin`, was not chosen.)

New module in zio-sbt's `build.sbt`, set up like `zio-sbt-ecosystem`: `stdSettings()`,
`crossScalaVersions := Seq(Scala212, Scala3)`, `pluginCrossBuildSettings`, `scala3JavaPlatformSettings`,
`scriptedTestSettings`, zio-test for the unit tests, `enablePlugins(SbtPlugin)`, and added to `root`'s
`aggregate`. It depends on nothing but sbt.

## 4. What a library writes (the downstream setup)

Example for zio-query (one JVM module `zioQueryJVM`, Scala 2.12, 2.13 and 3):

```scala
// project/plugins.sbt
addSbtPlugin("dev.zio" % "zio-sbt-scaladoc" % "<zio-sbt version>")

// build.sbt
lazy val scaladocCheck = project
  .dependsOn(zioQueryJVM)               // the modules whose Scaladoc examples are compiled
  .enablePlugins(ScaladocCheckPlugin)
  // optional: libraries that only the examples use
  // .settings(libraryDependencies += "dev.zio" %% "zio-streams" % "2.1.27")

// turn on the CI step (zio-sbt-ci), next to the other ci* settings
inThisBuild(ciCheckScaladocExamples := true)
```

Then regenerate CI with `sbt ciGenerateGithubWorkflow`. Run it locally with `sbt +scaladocCheck/compile`.

- The checked modules are taken from the project's own `dependsOn` list, so they are named once.
- `scaladocCheck` should **not** be in the root project's `aggregate` list: then `sbt compile`, `+test` and
  `+publishLocal` stay as fast as today, and the examples are compiled only by the CI step (or by hand).
  If a repo aggregates it anyway, nothing breaks: the plugin sets `publish / skip := true`.

## 5. Keys and tasks

All in `ScaladocCheckPlugin.autoImport`. The plugin is `noTrigger` (opt-in) and `requires = plugins.JvmPlugin`.

| Key | Type | Default | Meaning |
|---|---|---|---|
| `scaladocCheckProjects` | `SettingKey[Seq[ProjectReference]]` | the project's direct `dependsOn` projects | Whose Scaladoc examples are compiled |
| `scaladocCheckImports` | `SettingKey[String => Seq[String]]` | package `a.b` gives `Seq("a.b._")` | Turns a source file's package into the wrapper's imports. Lets a repo handle a package that would shadow something (zio-blocks' `sql` case, section 12) |
| `scaladocCheckSnippets` | `TaskKey[Seq[File]]` | (task) | Writes the wrapper files; registered in `Compile / sourceGenerators`. Run alone to see what is generated |

Settings the plugin sets on the checker project:

- `scalaVersion` and `crossScalaVersions`: taken from the first checked project, so `+scaladocCheck/compile`
  runs on the same versions as the library. A repo can override them.
- `Compile / scalacOptions`: removes `-Xfatal-warnings` and `-Werror`. Examples are user code; a warning in an
  example (a deprecation, say) must not fail the build. zio-blocks does the same.
- `Compile / scalacOptions`: silences unused-import warnings (see section 7).
- `publish / skip := true`, `publishLocal / skip := true`, `Compile / doc / sources := Nil`.
- `Compile / compile / sourcePositionMappers`: maps errors back to the Scaladoc (section 8).

## 6. How examples are found (ported from zio-blocks, unchanged rules)

The extractor reads each `.scala` file in `Compile / unmanagedSources` of each checked project. That list
already holds exactly the files for the current Scala version (`scala/`, `scala-2/`, `scala-2.13/`,
`scala-3/`, `shared/` and `jvm/` folders), so version-specific copies are handled for free.

Rules, the same as zio-blocks so the style guide and the writers' habits carry over:

1. A block is a multi-line `{{{` ... `}}}` (or ```` ```scala ```` ... ```` ``` ````) **inside a `/** */`
   comment**, each marker on its own line. One-line `{{{ x }}}` blocks are not checked.
2. The leading ` * ` margin is removed; `\$` is read as `$` (that is how Scaladoc renders it).
3. The first non-empty line may be a marker:
   - `// scala-3-only`: compiled on Scala 3 only;
   - `// scala-2-only`: compiled on Scala 2.12 and 2.13 only;
   - `// no-check: <reason>`: not compiled; the reason is required.
4. These are **errors** (the build fails with `path:line: message`):
   - a marker that looks like one but isn't (`// scala3-only`, `// no-check` without a reason);
   - a block that is never closed;
   - a code line containing `}}}`, which ends the Scaladoc block early.

## 7. How each example is compiled

Each example becomes one file in `target/.../src_managed/main/scaladoc-snippets/`:

```scala
// Generated from core/src/main/scala/zio/query/DataSource.scala:57 by ScaladocCheckPlugin. Do not edit.
package scaladoccheck

import zio._
import zio.query._

object DataSource_L57_3f9a1c2e {
  <the example, indented>
}
```

- **Imports come from the file's package clauses** (the main change from zio-blocks, which used a hand-made
  folder-to-package map). Scala opens one scope per clause, so the imports copy exactly what the file's own
  code can see, and nothing more:
  - `package zio.query` gives `import zio.query._`;
  - `package zio` + `package query` (two clauses) gives `import zio._` and `import zio.query._`;
  - Scala 3 `package zio.query:` is read the same way; a file with no package clause gets no import.
- **Why imports and not the same `package` clause:** a wrapper inside `package zio.query` could use
  `private[query]` members, which a user cannot. An import gives exactly the user's view.
- **Unused imports:** most examples don't need every import, and zio-sbt's `stdSettings` warns about unused
  imports. zio-blocks solved that with ~120 lines of name-scanning regexes (#1969, #1977). The proposal is
  simpler: the checker project silences only the unused-import warning (`-Wconf:cat=unused-imports:s` on 2.12
  and 2.13, the matching `-Wconf` filter on Scala 3). Other warnings stay visible in the log.
- **Object name:** `<FileName>_L<line>_<8-char hash of the path>`. Short (no class-file name limits on 2.12),
  stable, and unique even when two modules have a file with the same name.
- **Each Scala version** gets only the examples it should compile (the markers in section 6). The whole folder
  is rewritten on each run, and files of removed examples are deleted; unchanged files keep their content so
  incremental compilation stays fast.
- **Classpath:** the checker project's own classpath, i.e. the checked projects (through `dependsOn`) plus their
  libraries plus any `libraryDependencies` the repo adds for examples.

## 8. What a failure looks like

The compiler error points at the Scaladoc line, not the generated file:

```
[error] core/src/main/scala/zio/query/DataSource.scala:61:5: value contramapZIO is not a member of ...
[error]  *   ds.contramapZIO(f)
```

This uses sbt's `sourcePositionMappers` (present in sbt 1.x and in sbt 2.0.7, checked in its jar): a generated
line is mapped to `example start line + offset` in the source file, and the column is shifted by the removed
margin. If the mapping can't be done (e.g. an error in the import lines), the error shows the generated file,
whose first line names the source file and line.

Extractor errors (section 6, rule 4) fail the task before compiling, with `path:line: message`.

## 9. The CI switch (zio-sbt-ci)

New keys, build-scoped like the other `ci*` keys:

| Key | Type | Default | Meaning |
|---|---|---|---|
| `ciCheckScaladocExamples` | `SettingKey[Boolean]` | `false` | Adds the step below to the generated `ci.yml` |
| `ciScaladocCheckProjects` | `SettingKey[Seq[String]]` | `Seq("scaladocCheck")` | Project ids the step compiles (see open question 4) |

When the switch is on, the **build** job gets one more step after "Check artifacts build process":

```yaml
- name: Check Scaladoc examples
  run: sbt +scaladocCheck/compile
```

- `+` compiles once per Scala version of the checker project.
- The build job runs once (not per Java version), so it adds one compile per Scala version, not a matrix.
- When the switch is off (the default), `ci.yml` is byte-for-byte the same as today, so no existing repo changes.
- zio-sbt-ci does **not** depend on the plugin; it only writes the sbt command, so repos using a custom
  checker project name still work.

## 10. zio-sbt's own CI (dogfooding)

zio-sbt itself has 6 example blocks (`zio-sbt-githubactions/.../model.scala:417` and 5 in
`zio-sbt-source/.../ExprEval.scala`, scala-2 and scala-3 copies). The 5 `ExprEval` blocks are fragments that
use names they never define (`isPrimitive`, `TypeId`), so they would fail today.

Proposal for this PR:

1. zio-sbt's meta-build loads the plugin sources it ships (`project/build.sbt` adds each module's
   `src/main/scala` and `src/main/scala-2.12`). Add the same lines for `zio-sbt-scaladoc`, so zio-sbt's own
   build can enable the plugin.
2. Add `scaladocCheck` (2.12 and 3, depends on `zio-sbt-githubactions`, `zio-sbt-ci`, `zio-sbt-website`, `zio-sbt-scaladoc`,
   `zio-sbt-ecosystem`) and `scaladocCheckSource` (2.13 and 3, depends on `zio-sbt-source`; different Scala
   versions, so a second checker project, the same way zio-blocks has `scaladoc-check-scala3`).
3. Make the 6 blocks compile, or mark a block `// no-check: <reason>` where it truly cannot.
4. Turn on `ciCheckScaladocExamples` in zio-sbt's `build.sbt` and run `sbt ciGenerateGithubWorkflow`.
   Because there are two checker projects, zio-sbt sets
   `ciScaladocCheckProjects := Seq("scaladocCheck", "scaladocCheckSource")`, and the step runs
   `sbt +scaladocCheck/compile +scaladocCheckSource/compile`.

Risk: the sbt plugin projects get sbt on the classpath as `Provided`, which `dependsOn` doesn't pass on.
If the `model.scala` example needs sbt types, the checker adds the sbt dependency itself. To verify in phase 2.

## 11. Tests

1. **Unit tests** (zio-test, in the module's `src/test`): port zio-blocks' `ScaladocSnippetsSpec`, minus the
   folder-to-package tests, plus new ones for package-clause imports (one clause, nested clauses, Scala 3
   colon syntax, no package, `package object`), object names, and the line mapping.
2. **Scripted tests** in `zio-sbt-scaladoc/src/sbt-test/zio-sbt-scaladoc/` (sbt 1 fixtures, run by the
   module's `Test / test` through `scriptedTestSettings`), each a tiny build with two modules:
   - `scaladocCheck/good`: examples that need the package imports, nested package clauses, both markers and a
     `no-check`, cross-built on 2.12, 2.13 and 3 → `> +scaladocCheck/compile` passes, and the generated files
     are checked for the expected imports.
   - `scaladocCheck/broken`: one wrong example → `-> scaladocCheck/compile` fails, and the error log names the
     source file and line.
   - `scaladocCheck/badMarker`: `// no-check` without a reason → fails with the extractor message.
   - `good-sbt2`: the `good` fixture on sbt 2.0.7, added to the existing "Check sbt-2.x scripted fixtures" CI
     step in zio-sbt's `build.sbt` as `"zio-sbt-scaladoc/scripted zio-sbt-scaladoc/good-sbt2"` (like
     `installWebsite-sbt2`).
3. **zio-sbt-ci golden test**: new fixture `scaladocExamples` with the switch on; its `expected/ci.yml` has the
   new step. All existing golden files stay unchanged (proves the default is off).
4. **Manual check (phase 2, step 5):** a scratch clone of zio-query with the snapshot plugin: a broken example
   must fail, a good one must pass, on 2.12, 2.13 and 3.

## 12. Limitations (written into the plugin's Scaladoc)

- Only multi-line blocks inside `/** */` are checked.
- A checker project has one Scala version set. Modules with different version sets need separate checker
  projects (as in section 10).
- JVM only: list the `.jvm` side of a cross project. Scala.js/Native-only examples need `// no-check: <reason>`.
- A package import can shadow a root package. zio-blocks' `sql` module has a sub-package named `zio`, so
  `import zio.blocks.sql._` hides the root `zio` package. Fix per repo with `scaladocCheckImports`
  (e.g. `zio.blocks.sql.{zio => _, _}`), or write `_root_.zio` in the example.
- Results in comments (`// Some(80)`) are not checked, only compilation.
- Examples that need a given or implicit from a package object get it through the wildcard import; examples
  that need an implicit from somewhere else must import it themselves.
- Silencing unused-import warnings also hides an unused import that the example itself writes.

## 13. What zio-blocks would need to switch later (not in this PR)

- Its three checker projects become three projects with `enablePlugins(ScaladocCheckPlugin)`.
- `packageOverrides` and the folder rule go away (imports come from package clauses).
- The `sql` exception becomes a `scaladocCheckImports` override.
- Its Scala 3 "drop `-experimental` on the main line" rule stays in its own build (repo-specific).

## 14. Open questions for Milad

1. ~~Home~~: **decided: a new `zio-sbt-scaladoc` module** (section 3).
2. Unused imports: **silence the warning in the checker** (recommended, simple) or port zio-blocks' name scan
   so only needed imports are written?
3. Dogfooding zio-sbt (section 10) in **this** PR (recommended: it proves the plugin on a real build and
   gives zio-sbt the CI check you asked for), or in a follow-up PR?
4. CI key: `ciScaladocCheckProject: String` or `ciScaladocCheckProjects: Seq[String]`? zio-sbt itself needs two
   checker projects, so the recommendation is `Seq[String]`, default `Seq("scaladocCheck")`.
