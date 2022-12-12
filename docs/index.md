---
id: index
title: "ZIO SBT"
---

_ZIO SBT_ is an sbt plugin for ZIO projects. It provides high-level SBT utilities that simplify the development of ZIO applications.

@PROJECT_BADGES@

## Installation

Add the following lines to your `plugin.sbt` file:

```scala
addSbtPlugin("dev.zio" % "zio-sbt-website" % "@VERSION@")
```

Then you can enable it by using the following code in your `build.sbt` file:

```scala
enablePlugins(WebsitePlugin)
```

## ZIO SBT Website

ZIO SBT Website is an SBT plugin that has the following tasks:

- `sbt compileDocs`— compile documentation inside `docs` directory. The compilation result will be inside `website/docs` directory.
- `sbt installWebsite`— creates a website for the project inside the `website` directory
- `sbt previewWebsite`— runs a local webserver that serves documentation locally on http://localhost:3000. By changing the documentation inside the `docs` directory, the website will be reloaded with new content.
- `sbt publishToNpm`— publishes documentation inside the `docs` directory to the npm registry.
- `sbt generateGithubWorkflow`— generates GitHub workflow which publishes documentation for each library release.
- `sbt generateReadme`— generate README.md file using `README.template.md` and `docs/index.md` files.

