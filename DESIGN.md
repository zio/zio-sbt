# Design: compile Scaladoc examples with a shared sbt plugin (ZIO-204)

Status: **draft v2, waiting for Milad's approval.** No code yet. This file is removed before the PR.

Decided so far (Milad, 2026-11-20):
- the plugin lives in a **new zio-sbt module, `zio-sbt-scaladoc`** (section 3);
- **no checker projects**: every module checks its own examples, on JVM, Scala.js and Scala Native
  (sections 4 and 8);
- wrapper imports use **zio-blocks' name scan** (section 8);
- this PR also turns the check on for **zio-sbt's own code** (section 11);
- everything is written in **Scala** (section 5).

## 1. The problem

Scaladoc comments contain code examples between `{{{` and `}}}`. Nothing compiles them, so they rot: they call
methods that were renamed, use types they never define, or show wrong results. zio-blocks fixed this for itself
(PR #1813, then #1850, #1969, #1977): every example in its Scaladoc is compiled on every Scala version in CI.

About 30 other ZIO repos have the same problem, and the Scaladoc rollout (ZIO-201) is about to add many more
examples to them.

## 2. The goal

Any ZIO library can compile its Scaladoc examples on every Scala version and every platform (JVM, Scala.js,
Scala Native) it builds, in CI, by adding **two lines** to its build. The code that does this lives in one
place and is maintained once.

## 3. Where it lives

zio-blocks' checker is **not** a reusable module. It is build-local code:

- `project/ScaladocSnippets.scala` (337 lines): finds the examples and writes each one into its own `object`.
  It lives in zio-blocks' meta-build (the `project/` folder that holds code the build itself runs), so only
  zio-blocks' own build can call it.
- About 150 lines in `build.sbt`: three unpublished checker projects (`scaladoc-check`,
  `scaladoc-check-scala3`, `scaladoc-check-jdk21`) that depend on the checked modules and compile the
  generated objects.
- `scaladoc-check/src/test/scala/ScaladocSnippetsSpec.scala` (589 lines): unit tests for the extractor.

Nothing of it is published, so another repo could only copy it. Options considered: copy it into each repo
(30 copies that drift apart), publish it from zio-blocks (a library repo would have to cross-build and publish
an sbt plugin for sbt 1 and sbt 2), or port it into zio-sbt, the ecosystem's shared build toolkit, which
already cross-builds for sbt 1 and 2, has scripted tests and generates CI. **Chosen: port it into zio-sbt, as a
new module `zio-sbt-scaladoc`**, published as its own sbt plugin artifact `dev.zio:zio-sbt-scaladoc`.

New module in zio-sbt's `build.sbt`, set up like `zio-sbt-ecosystem`: `stdSettings()`,
`crossScalaVersions := Seq(Scala212, Scala3)`, `pluginCrossBuildSettings`, `scala3JavaPlatformSettings`,
`scriptedTestSettings`, zio-test for the unit tests, `enablePlugins(SbtPlugin)`, and added to `root`'s
`aggregate`. It depends on nothing but sbt (not on sbt-scalajs or sbt-scala-native; see section 8).

zio-blocks keeps its own copy for now. Once zio-sbt has released the plugin, zio-blocks may switch to it in a
separate PR (section 14).

## 4. What a library writes

```scala
// project/plugins.sbt
addSbtPlugin("dev.zio" % "zio-sbt-scaladoc" % "<zio-sbt version>")

// build.sbt (with the other ci* settings)
ThisBuild / ciCheckScaladocExamples := true
```

That is all. Then regenerate CI with `sbt ciGenerateGithubWorkflow`.

- Loading the plugin is the opt-in: once it is in `project/plugins.sbt`, every module of the build checks its
  own examples. No checker project, no list of modules, no project names.
- Run it locally: `sbt +scaladocCheck` (all modules, all Scala versions), or `sbt +core/scaladocCheck`.
- Turn it off for one module (benchmarks, say): `.settings(scaladocCheck / skip := true)`.
- An example that needs a library the module itself does not use (rare) gets it only for the examples, the
  same way `% Test` works for tests: `libraryDependencies += "dev.zio" %%% "zio-streams" % v % ScaladocCheck`.
  The published library does not change.

`sbt compile`, `sbt test` and `sbt publishLocal` do **not** run the check, so the daily loop stays as fast as
today. Only `scaladocCheck` (by hand or in CI) does.

## 5. Implementation language and file layout

Everything is **Scala**. No Python, shell or JavaScript is added, and a library needs nothing beyond sbt.
(The out-of-tree checker of plan §3 step 1, `snippets-scala-cli.py`, is a separate dev tool in the skill folder
and is not part of this PR.)

| Part | Language | Compiled with | File |
|---|---|---|---|
| The plugin: keys, the `ScaladocCheck` configuration, task wiring | Scala, using the sbt plugin API (`AutoPlugin`, `projectConfigurations`, `inConfig`, `Defaults.configSettings`, `sourceGenerators`, `sourcePositionMappers`, `ScopeFilter`) | Scala 2.12.21 (for sbt 1.x) **and** Scala 3.8.4 (for sbt 2.x), zio-sbt's two cross-build axes | `zio-sbt-scaladoc/src/main/scala/zio/sbt/ScaladocCheckPlugin.scala` |
| The extractor, wrapper writer and name scan (the port of zio-blocks' `ScaladocSnippets`) | Plain Scala: pure functions from strings to strings, using only the Scala standard library and `java.util.regex`. **No sbt types**, so it is unit-tested without sbt | the same two | `zio-sbt-scaladoc/src/main/scala/zio/sbt/scaladoc/ScaladocSnippets.scala` |
| Any sbt 1 / sbt 2 difference | Scala, one copy per sbt version | 2.12 copy for sbt 1, Scala 3 copy for sbt 2 | `src/main/scala-2.12/zio/sbt/ScaladocCheckCompat.scala` and `src/main/scala-3/zio/sbt/ScaladocCheckCompat.scala`, the same pattern as `WebsitePluginCompat` (#885). Created only if a real difference shows up |
| The generated wrappers | Scala | the **library's** Scala versions (2.12, 2.13, 3.x) and platforms, inside each module | written at build time into the module's `target/.../src_managed/scaladoccheck/`; never committed |
| The CI switch | Scala, inside zio-sbt-ci; it emits YAML through zio-sbt-githubactions' existing `Step`/`Job` model, never hand-written YAML | 2.12 and 3.8.4 | `zio-sbt-ci/src/main/scala/zio/sbt/ZioSbtCiPlugin.scala` |
| Unit tests | Scala, zio-test (as in zio-sbt-website) | 2.12 and 3.8.4 | `zio-sbt-scaladoc/src/test/scala/zio/sbt/scaladoc/ScaladocSnippetsSpec.scala` |
| Scripted tests | Tiny sbt builds (`build.sbt`, `project/plugins.sbt`, a few `.scala` files with Scaladoc examples) driven by sbt's scripted `test` scripts | sbt 1.13.1 and sbt 2.0.7 | `zio-sbt-scaladoc/src/sbt-test/zio-sbt-scaladoc/<fixture>/` |

Rules for the plugin code:

- **One source for both sbt versions.** The shared files compile on Scala 2.12 and 3.8.4, using the syntax the
  other zio-sbt modules already use. Only a real API difference between sbt 1 and sbt 2 goes into the
  `ScaladocCheckCompat` pair.
- **Why Scala and not a script:** the check runs inside the library's own sbt build, with its own classpath,
  compiler flags, Scala versions and platforms, so CI runners need no extra tool, and the same code runs on a
  laptop and in CI.
- **Why the extractor has no sbt types:** zio-blocks' extractor is already plain Scala, so the port stays close
  to it; it can be tested with plain unit tests; and zio-blocks could call the same functions later.
- **Formatting and lint:** zio-sbt's own scalafmt and scalafix settings; `sbt fmt` and `sbt lint` must pass; the
  standard license header on every new file.

## 6. Public keys (the whole public surface)

| Key | Type | Default | Meaning |
|---|---|---|---|
| `scaladocCheck` | `TaskKey[Unit]` | (task) | Compiles this module's Scaladoc examples. Runs on every aggregated module from the root: `sbt +scaladocCheck` |
| `scaladocCheck / skip` | `SettingKey[Boolean]` | `false` | `true` turns the check off for one module |
| `ScaladocCheck` | `Configuration` | (added to every module) | The hidden build area the examples compile in. Public only so that `% ScaladocCheck` can add an example-only library |

zio-sbt-ci adds one key (section 10): `ciCheckScaladocExamples: SettingKey[Boolean]`, default `false`.

Everything else (the wrapper folder, the name scan, the compiler flags) is internal.

The plugin is `trigger = allRequirements`, `requires = plugins.JvmPlugin`: it switches itself on in every
module of a build that loads it. (sbt's `JvmPlugin` is on in Scala.js and Scala Native modules too.)

## 7. How examples are found (ported from zio-blocks)

For one module, the extractor reads each `.scala` file in that module's `Compile / unmanagedSources`. sbt
already fills that list with exactly the files for the current Scala version and platform (`scala/`,
`scala-2/`, `scala-2.13/`, `scala-3/`, `shared/`, `jvm/`, `js/`, `native/`, `jvm-native/`, ... folders), so
version- and platform-specific copies are handled for free.

Rules, the same as zio-blocks so the style guide and the writers' habits carry over:

1. A block is a multi-line `{{{` ... `}}}` (or ```` ```scala ```` ... ```` ``` ````) **inside a `/** */`
   comment**, each marker on its own line. One-line `{{{ x }}}` blocks are not checked.
2. The leading ` * ` margin is removed; `\$` is read as `$` (that is how Scaladoc renders it).
3. The block may start with **marker lines** (consecutive comment lines at the top of the block):
   - from zio-blocks, unchanged:
     - `// scala-3-only`: compiled on Scala 3 only;
     - `// scala-2-only`: compiled on Scala 2.12 and 2.13 only;
     - `// no-check: <reason>`: never compiled; the reason is required;
   - **new, for platforms** (zio-blocks is JVM-only, so it never needed them):
     - `// jvm-only`, `// js-only`, `// native-only`: compiled on that platform only;
     - `// no-js`, `// no-native`: compiled everywhere except that platform (for an example in `shared/` that
       uses, say, `java.io.File`, which exists on JVM and Native but not on Scala.js).
   - A Scala marker and a platform marker can be combined, one per line (`// scala-3-only` then `// jvm-only`).
4. These are **errors** (the task fails with `path:line: message` before compiling):
   - a line that looks like a marker but isn't one of the exact forms (`// scala3-only`, `// jvm only`,
     `// no-check` without a reason);
   - two markers that contradict each other (`// jvm-only` and `// js-only`);
   - a block that is never closed;
   - a code line containing `}}}`, which ends the Scaladoc block early.

An example in a file that only exists in `jvm/` needs no marker: that file is only in the JVM module's sources.

## 8. How each module compiles its examples

### The `ScaladocCheck` configuration

sbt modules already have more than one "build area", called a **configuration**: `Compile` for the main code,
`Test` for the tests. Each has its own sources, classpath and compile step. The plugin adds one more,
`ScaladocCheck`, to every module (`AutoPlugin.projectConfigurations` plus `inConfig(ScaladocCheck)(...)`;
both still exist in sbt 2.0.7, checked in its jars). It is set up so that the examples compile **exactly like
the module's main code**:

- **Sources:** only the generated wrappers (`ScaladocCheck / sourceGenerators`); no hand-written sources.
- **Classpath:** the module's own compiled classes plus everything on its main classpath
  (`Compile / fullClasspath`, which includes `Provided` libraries such as sbt for an sbt plugin module), plus
  any library added with `% ScaladocCheck`. So an example sees what a user of the module sees.
- **Compiler flags:** the module's own `Compile / scalacOptions`, minus the flags that turn warnings into errors
  (`-Xfatal-warnings`, `-Werror`), because a warning in an example (a deprecation, say) must not fail the
  build; zio-blocks does the same. Warnings still show in the log.
- **Platforms:** Scala.js and Scala Native compile Scala through a compiler plugin (`-Xplugin:...` on Scala 2)
  or a flag (`-scalajs` on Scala 3) that their sbt plugins put into `Compile / scalacOptions`, and their
  runtime libraries into `Compile / fullClasspath`. Because `ScaladocCheck` copies both, the examples of a JS
  or Native module are compiled **by the Scala.js or Scala Native compiler, against that platform's
  libraries**, with no dependency on sbt-scalajs or sbt-scala-native. Only compilation runs, no linking, so
  Scala Native needs no clang on the CI runner.
- **Platform of a module:** read from the module's main classpath: `scalajs-library` means Scala.js,
  `nativelib` means Scala Native, otherwise JVM. It decides which platform markers apply (section 7).
- **IDE:** `ScaladocCheck / bspEnabled := false`, so Metals and IntelliJ don't show it as an extra target.
- **Publishing:** the configuration is not published and not added to the POM.

**To check in the first implementation step (spike):** that the JS and Native compiler flags really are in
`Compile / scalacOptions` on both sbt 1 and sbt 2, for Scala 2.12, 2.13 and 3. If one isn't, the plugin copies
it from wherever that platform plugin keeps it, looked up by key name (no dependency on the platform plugin).

### The generated wrapper

Each example becomes one file in the module's `target/.../src_managed/scaladoccheck/`:

```scala
// Generated from core/shared/src/main/scala/zio/query/DataSource.scala:57 by zio-sbt-scaladoc. Do not edit.
package scaladoccheck

import zio._
import zio.query._

object DataSource_L57_3f9a1c2e {
  <the example, indented>
}
```

- **Imports come from the file's package clauses** (the main change from zio-blocks, which used a hand-made
  folder-to-package map). Scala opens one scope per clause, so the imports copy what the file's own code can
  see, and nothing more:
  - `package zio.query` gives `import zio.query._`;
  - `package zio` + `package query` (two clauses) gives `import zio._` and `import zio.query._`;
  - Scala 3 `package zio.query:` is read the same way; a file with no package clause gets no import.
- **Why imports and not the same `package` clause:** a wrapper inside `package zio.query` could use
  `private[query]` members, which a user cannot. An import gives exactly the user's view.
- **Only needed imports are written (name scan, ported from zio-blocks #1969 and #1977).** Many examples don't
  use their package at all, and zio-sbt's `stdSettings` warns about unused imports. So, before writing the
  import of the file's own package `P`, the plugin checks whether the example needs it:
  1. It collects the **names of `P`**: every non-private `class`, `trait`, `object`, `type`, `enum`, `def`,
     `val` and `var` declared in files whose package is `P`, read from the sources of this module **and of the
     modules it depends on** (`ScopeFilter(inDependencies(ThisProject))`), plus the first segment of each
     sub-package (`stream` for `zio.query.stream`). In a file that defines implicit members (an
     `implicit class`, an `extension`, a `package object`, a trait), indented declarations count too, because
     users call those members without naming their owner (`md"..."`).
  2. It collects the names the **example uses** (identifiers not after a `.`, outside `//` comments) and the
     names it **binds itself** (its own imports and declarations, which hide the wrapper's import).
  3. It writes `import P._` only if the example uses a name of `P` that it does not bind itself, and the
     example does not already write `import P._` (or `import P.syntax._`) itself.

  The scan errs towards keeping the import: when in doubt it is written, and at worst warns.
- **Outer package clauses are always imported.** For a file with `package zio` + `package query`, only the
  file's own package (`zio.query`) goes through the name scan; `import zio._` is always written. The scan only
  sees sources, and an outer package such as `zio` gets most of its names (`ZIO`, `Chunk`) from library jars,
  so the scan would wrongly drop it. Such an import may warn when unused.
- **Object name:** `<FileName>_L<line>_<8-char hash of the path>`. Short (no class-file name limits on 2.12),
  stable, and unique even when two files share a name.
- **Each Scala version and platform** gets only the examples its markers allow (section 7). Files of removed
  examples are deleted on each run; unchanged files keep their content, so incremental compilation stays fast.
- **Examples in `shared/` are compiled once per platform module** (JVM, JS, Native). That is the point: an
  example that only works on the JVM is caught, and gets a marker.

## 9. What a failure looks like

The compiler error points at the Scaladoc line, not the generated file:

```
[error] core/shared/src/main/scala/zio/query/DataSource.scala:61:5: value contramapZIO is not a member of ...
[error]  *   ds.contramapZIO(f)
```

This uses sbt's `sourcePositionMappers` (present in sbt 1.x and in sbt 2.0.7, checked in its jar): a generated
line is mapped to `example start line + offset` in the source file, and the column is shifted by the removed
margin. If the mapping can't be done (an error in the import lines, say), the error shows the generated file,
whose first line names the source file and line. Errors from a JS or Native module carry the module name in
sbt's log prefix, so it is clear which platform failed.

Extractor errors (section 7, rule 4) fail the task before compiling, with `path:line: message`.

## 10. The CI switch (zio-sbt-ci)

One new key, build-scoped like the other `ci*` keys:

| Key | Type | Default | Meaning |
|---|---|---|---|
| `ciCheckScaladocExamples` | `SettingKey[Boolean]` | `false` | Adds the step below to the generated `ci.yml` |

When it is on, the **build** job gets one more step after "Check artifacts build process":

```yaml
- name: Check Scaladoc examples
  run: sbt +scaladocCheck
```

- `+` runs the check once per Scala version; from the root it runs in every aggregated module, JVM, JS and
  Native, each with its own versions.
- The build job runs once (not per Java version), so this is not a matrix.
- When the switch is off (the default), `ci.yml` is byte-for-byte the same as today, so no existing repo
  changes.
- zio-sbt-ci does **not** depend on zio-sbt-scaladoc; it only writes the sbt command.

## 11. zio-sbt's own CI (dogfooding, in this PR)

zio-sbt itself has 6 example blocks (`zio-sbt-githubactions/.../model.scala:417` and 5 in
`zio-sbt-source/.../ExprEval.scala`, scala-2 and scala-3 copies). The 5 `ExprEval` blocks are fragments that
use names they never define (`isPrimitive`, `TypeId`), so they would fail today.

1. zio-sbt's meta-build loads the plugin sources it ships (`project/build.sbt` adds each module's
   `src/main/scala` and `src/main/scala-2.12`). Add the same lines for `zio-sbt-scaladoc`. Every zio-sbt
   module then checks its own examples, each on its own Scala versions (2.12/3 for the plugin modules,
   2.13/3 for `zio-sbt-source`), with no extra projects.
2. `scaladocCheck / skip := true` on `docs` (it has no Scala sources of its own) if needed.
3. Make the 6 blocks compile, or mark a block `// no-check: <reason>` where it truly cannot.
4. Turn on `ciCheckScaladocExamples` in zio-sbt's `build.sbt` and run `sbt ciGenerateGithubWorkflow`; `ci.yml`
   gets the `sbt +scaladocCheck` step.

## 12. Tests

1. **Unit tests** (zio-test, in the module's `src/test`): port zio-blocks' `ScaladocSnippetsSpec`, including
   its name-scan tests, minus the folder-to-package tests, plus new ones for: package-clause imports (one
   clause, nested clauses with the outer import always kept, Scala 3 colon syntax, no package, `package
   object`), platform markers and their errors, object names, and the line mapping.
2. **Scripted tests** in `zio-sbt-scaladoc/src/sbt-test/zio-sbt-scaladoc/` (run by the module's `Test / test`
   through `scriptedTestSettings`). Each is a tiny build:
   - `jvm`: two modules (one depends on the other), examples that need the package import, nested package
     clauses, both Scala markers and a `no-check`, cross-built on 2.12, 2.13 and 3 → `> +scaladocCheck`
     passes; the generated files are checked for the expected imports; `compile` and `test` do not run it.
   - `crossPlatform`: one `shared/` source compiled by a JVM, a Scala.js and a Scala Native module (plain sbt
     projects sharing a source folder, so only sbt-scalajs and sbt-scala-native are needed), with a shared
     example, a `// jvm-only` example that uses `java.io.File`, and a `// no-js` example → passes on all three.
   - `crossPlatformBroken`: the same `java.io.File` example **without** a marker → the JS module fails, the
     JVM module passes.
   - `broken`: one wrong example → `-> scaladocCheck` fails, and the log names the source file and line.
   - `badMarker`: `// no-check` without a reason, and `// jvm only` → fail with the extractor message.
   - `skip`: `scaladocCheck / skip := true` → a broken example in that module is not compiled.
   - `extraDependency`: an example that needs a library added with `% ScaladocCheck` → passes, and the
     module's published POM does not list that library.
   - `jvm-sbt2` and `crossPlatform-sbt2`: the same fixtures on sbt 2.0.7 (Scala.js 1.23.0 and Scala Native
     0.5.12 both publish sbt 2 plugins), added to the existing "Check sbt-2.x scripted fixtures" CI step in
     zio-sbt's `build.sbt` as `"zio-sbt-scaladoc/scripted zio-sbt-scaladoc/jvm-sbt2 zio-sbt-scaladoc/crossPlatform-sbt2"`.
3. **zio-sbt-ci golden test**: new fixture `scaladocExamples` with the switch on; its `expected/ci.yml` has the
   new step. All existing golden files stay unchanged (proves the default is off).
4. **Manual check (phase 2, step 5):** a scratch clone of zio-query (JVM, JS and Native modules) with the
   snapshot plugin: a broken example must fail, a good one must pass, on 2.12, 2.13 and 3, on every platform.

## 13. Limitations (written into the plugin's Scaladoc)

- Only multi-line blocks inside `/** */` are checked.
- An example sees only its own module and that module's dependencies (the user's view). An example in `core`
  that uses `streams` needs `core` to depend on `streams`, a `% ScaladocCheck` library, or a `no-check`.
- A package import can shadow a root package. zio-blocks' `sql` module has a sub-package named `zio`, so
  `import zio.blocks.sql._` hides the root `zio` package; such an example writes `_root_.zio`.
- Results in comments (`// Some(80)`) are not checked, only compilation. Examples are compiled, not run.
- The name scan cannot see names that an example uses without writing them: a `given`, an anonymous
  implicit. If such an example uses no other name of its package, the import is dropped and the example fails
  to compile, loudly; it then needs its own import.
- The name scan reads only sources. A package split across a module and a library jar (both declare
  `package zio.stream`, say) has names the scan cannot see; an example that uses only those fails to compile
  and needs its own import.
- Outer package-clause imports (section 8) are always written and may warn when unused.
- Shared examples compile once per platform, so the check takes longer on JS and Native builds.

## 14. What zio-blocks would need to switch later (not in this PR)

- Its three checker projects and the ~150 build lines go away; each module checks itself.
- `packageOverrides` and the folder rule go away (imports come from package clauses).
- The `sql` examples write `_root_.zio` where needed.
- Examples that used another module than their own (possible in today's shared checker project) need that
  module as a dependency, a `% ScaladocCheck` library, or a `no-check`.
- Its Scala 3 "drop `-experimental` on the main line" rule stays in its own build (repo-specific).

## 15. Implementation order (phase 2)

1. **Spike:** the `ScaladocCheck` configuration compiling one hand-written file in a JVM, a JS and a Native
   module, on sbt 1 and sbt 2. Confirms section 8's assumptions before anything else is built.
2. Port the extractor and the name scan, with their unit tests.
3. The plugin: configuration, wrapper generation, position mapping, `skip`.
4. Scripted fixtures (sbt 1, then sbt 2).
5. zio-sbt-ci switch and its golden test.
6. Dogfood zio-sbt (section 11), regenerate the workflows.
7. `sbt fmt`, `sbt lint`, the full scripted run, and the zio-query trial.
