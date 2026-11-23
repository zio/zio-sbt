---
id: check-scaladoc-examples-in-ci
title: "Check Scaladoc Examples in CI"
---

Code examples in Scaladoc comments are not compiled with the code, so they break silently when a method is renamed or a signature changes. ZIO SBT Scaladoc compiles them, and ZIO SBT CI can run it on every pull request. This guide turns both on.

## 1. Add the Plugin

Add the plugin to `project/plugins.sbt`:

```scala
addSbtPlugin("dev.zio" % "zio-sbt-scaladoc" % "@VERSION@")
```

It turns itself on in every module of the build.

## 2. Run the Check Locally

```bash
sbt +scaladocCheck
```

This compiles the examples of every module on every Scala version it builds, and on every platform (JVM, Scala.js, Scala Native) the build has. To check one module on the current Scala version, run `sbt core/scaladocCheck`.

Fix what fails. A failure points at the Scaladoc line. For a block that only works on some versions or platforms, add a marker as its first line, such as `// scala-3-only` or `// jvm-only`; for one that cannot compile on its own, add `// no-check: <reason>`. The [reference](../reference/scaladoc.md#markers) lists every marker.

## 3. Turn On the CI Step

Set `ciCheckScaladocExamples` next to the other `ci*` settings in `build.sbt`:

```scala
ThisBuild / ciCheckScaladocExamples := true
```

Then regenerate the workflows:

```bash
sbt ciGenerateGithubWorkflow
```

The `build` job of `ci.yml` gains one step, after "Check artifacts build process":

```yaml
- name: Check Scaladoc examples
  run: sbt --no-colors +scaladocCheck
```

Commit the build change and the regenerated `ci.yml` together.

## Optional: Leave a Module Out

To skip a module, such as benchmarks or examples:

```scala
lazy val benchmarks = project
  .settings(scaladocCheck / skip := true)
```

## Optional: A Library Only the Examples Use

When an example needs a library its module does not depend on, add it to the `ScaladocCheck` configuration. The published module does not depend on it:

```scala
lazy val core = project
  .settings(libraryDependencies += "dev.zio" %% "zio-streams" % "2.1.27" % ScaladocCheck)
```
