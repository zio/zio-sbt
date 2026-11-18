---
id: index
title: "Introduction to ZIO SBT"
sidebar_label: "Introduction"
---

_ZIO SBT_ contains multiple sbt plugins that are useful for ZIO projects. It provides high-level SBT utilities that simplify the development of ZIO applications.

@PROJECT_BADGES@

## Overview

ZIO SBT is made of five modules, three of which are sbt plugins that a ZIO project enables in its build:

- **ZIO SBT Ecosystem** (`zio-sbt-ecosystem`) holds the standard settings, compiler options and commands (`fmt`, `lint`, `prepare`) that every ZIO project shares.
- **ZIO SBT Website** (`zio-sbt-website`) compiles the documentation in `docs/` with mdoc, builds and previews the Docusaurus website, publishes the docs to npm, and generates the `README.md`.
- **ZIO SBT CI** (`zio-sbt-ci`) generates the GitHub Actions workflows of a project from its `build.sbt`.
- **ZIO SBT GitHub Actions** (`zio-sbt-githubactions`) is the model of GitHub workflows (`Workflow`, `Job`, `Step`, ...) that ZIO SBT CI is built on. It comes with `zio-sbt-ci`, so it needs no installation of its own.
- **ZIO SBT Source** (`zio-sbt-source`) is a library, not an sbt plugin, that embeds source files and evaluated expressions into the documentation.

## Installation

Add the following lines to your `project/plugins.sbt` file:

```scala
// Standard settings, compiler options and the fmt, lint and prepare commands
addSbtPlugin("dev.zio" % "zio-sbt-ecosystem" % "@VERSION@")

// Generates the GitHub Actions workflows from build.sbt
addSbtPlugin("dev.zio" % "zio-sbt-ci"        % "@VERSION@")

// Builds, previews and publishes the documentation website
addSbtPlugin("dev.zio" % "zio-sbt-website"   % "@VERSION@")
```

Then you can enable them by using the following code in your `build.sbt` file:

```scala
enablePlugins(
  ZioSbtWebsitePlugin,
  ZioSbtEcosystemPlugin,
  ZioSbtCiPlugin
)
```

:::note
Always try to keep the SBT version specified in the `project/build.properties` file up to date to ensure compatibility with the ZIO SBT plugins.
:::

To embed source files into the documentation, also add `zio-sbt-source`, which is a regular library dependency and belongs in the `build.sbt` of the docs project:

```scala
libraryDependencies += "dev.zio" %% "zio-sbt-source" % "@VERSION@"
```

## Where to Go Next

Read [Getting Started](https://zio.dev/zio-sbt/getting-started) to set up a project, the [Reference](https://zio.dev/zio-sbt/reference/) for every setting, task and command, and the [How-to Guides](https://zio.dev/zio-sbt/how-to-guides/publish-a-releases-docs-again) for common CI tasks.
