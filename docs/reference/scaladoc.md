---
id: scaladoc
title: "ZIO SBT Scaladoc"
---

ZIO SBT Scaladoc is an sbt plugin that compiles the code examples in Scaladoc comments, so they keep compiling as the code changes. It checks every module of the build, on every Scala version it builds and on the JVM, Scala.js and Scala Native. It works on sbt 1 and sbt 2.

## Installation

Add the following line to your `project/plugins.sbt` file:

```scala
addSbtPlugin("dev.zio" % "zio-sbt-scaladoc" % "@VERSION@")
```

The plugin turns itself on in every module of the build; nothing else is needed. To run the check in CI, also set `ciCheckScaladocExamples` from ZIO SBT CI (see [Check Scaladoc Examples in CI](../how-to-guides/check-scaladoc-examples-in-ci.md)).

## Tasks and Settings

| Key | Type | Default | Description |
| --- | --- | --- | --- |
| `scaladocCheck` | `TaskKey[Unit]` | | Compiles the Scaladoc examples of a module. From the root project it runs in every aggregated module; `sbt +scaladocCheck` runs it on every Scala version |
| `scaladocCheck / skip` | `SettingKey[Boolean]` | `false` | `true` turns the check off for one module |
| `ScaladocCheck` | `Configuration` | | The configuration the examples are compiled in. Use it to add a library that only the examples need |

`compile`, `test` and `publish` do not run the check, so they are as fast as before. Only `scaladocCheck` does.

## Which Examples Are Checked

A block is checked when it is a multi-line `{{{` ... `}}}` (or ```` ```scala ```` ... ```` ``` ````) block inside a `/** */` comment of a module's main sources, with each marker on its own line:

```scala
/**
 * Doubles a number:
 * {{{
 * val four = double(2)
 * }}}
 */
def double(n: Int): Int = n * 2
```

One-line `{{{ x }}}` blocks and blocks outside `/** */` comments are not checked. A `\$` in an example is read as `$`, the way Scaladoc renders it.

Files in version-specific folders (`scala-2`, `scala-3`, ...) and platform folders (`jvm`, `js`, `native`, ...) are checked only by the Scala versions and platforms that compile them, so they need no marker.

## Markers

The first lines of a block can be markers, one per line:

| Marker | The block is compiled |
| --- | --- |
| `// scala-3-only` | on Scala 3 only |
| `// scala-2-only` | on Scala 2.12 and 2.13 only |
| `// jvm-only` | on the JVM only |
| `// js-only` | on Scala.js only |
| `// native-only` | on Scala Native only |
| `// no-js` | everywhere except Scala.js |
| `// no-native` | everywhere except Scala Native |
| `// no-check: <reason>` | never. The reason is required |

A Scala marker and a platform marker can be combined:

```scala
/**
 * {{{
 * // scala-3-only
 * // jvm-only
 * given Ordering[java.io.File] = Ordering.by(_.getName)
 * }}}
 */
```

The check fails before compiling anything when a line looks like a marker but is not one of these exact forms (`// scala3-only`, `// jvm only`, `// no-check` without a reason), when two markers contradict each other (`// scala-2-only` with `// scala-3-only`, or `// jvm-only` with `// js-only`), when a block is never closed, and when a code line contains `}}}`, which ends the Scaladoc block early. The error names the file and line.

## How an Example Is Compiled

Each example is wrapped in an `object` of its own, in a file under the module's `target` folder, and compiled in the `ScaladocCheck` configuration:

- **Classpath:** the module's own classes and everything on its main classpath, so an example sees exactly what a user of the module sees, plus any library added with `% ScaladocCheck`.
- **Compiler options:** the module's own `Compile / scalacOptions`, without the flags that turn warnings into errors (`-Xfatal-warnings`, `-Werror`), because a warning in an example, such as a deprecation, must not fail the build. Warnings still show in the log.
- **Platforms:** a Scala.js or Scala Native module compiles its examples with the Scala.js or Scala Native compiler, against that platform's libraries. An example in shared sources is therefore compiled once per platform.
- **Imports:** the wrapper imports the packages that the source file's `package` clauses open, so an example sees what the code around it sees, but not package-private members. For a file starting with `package zio` then `package query`, the wrapper imports `zio._` and `zio.query._`. The outer packages are always imported; the file's own package only when the example uses one of its names, so that import is never unused.

## Failures

A compile error is reported at the Scaladoc line of the source file, not at the generated wrapper (path shortened):

```
[error] zio-query/shared/src/main/scala/zio/query/Cache.scala:45:31: value fromFunctionBatchedZIOO is not a member of object zio.query.DataSource
[error] did you mean fromFunctionBatchedZIO?
[error]  *     userNames = DataSource.fromFunctionBatchedZIOO[Any, Nothing, GetUserName, String]("UserNames") { requests =>
[error]                               ^
```

## Libraries Only the Examples Need

An example that uses a library its module does not depend on gets it from the `ScaladocCheck` configuration, the same way `% Test` works for tests. The published module does not depend on it:

```scala
lazy val core = project
  .settings(libraryDependencies += "dev.zio" %% "zio-streams" % "2.1.27" % ScaladocCheck)
```

Use `%%%` instead of `%%` in a cross-built Scala.js or Scala Native module.

## Limitations

- Examples are compiled, not run: a result shown in a comment, such as `// Some(80)`, is not checked.
- On Scala.js and Scala Native, examples are compiled, not linked. A JDK class the platform lacks, such as `java.io.File` on Scala.js, is only reported by the linker, so the check does not catch it. A library the platform's module does not depend on, and the platform compiler's own checks, are caught.
- An example sees only its own module and the modules it depends on.
- An example that uses only a `given` or an anonymous implicit of its package, or only names of its package that come from a library jar, needs its own import.
- A package import can hide a root package: when a package has a sub-package named `zio`, its import hides the root `zio` package, so the example writes `_root_.zio`.
