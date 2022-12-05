---
id: index
title: "ZIO SBT"
---

_ZIO SBT_ is an sbt plugin for ZIO projects. It provides high-level SBT utilities that simplify the development of ZIO applications.

| Project Stage                         | CI              | Release                                                               | Snapshot                                                                 | Issues                                                     | Discord                          |
|---------------------------------------|-----------------|-----------------------------------------------------------------------|--------------------------------------------------------------------------|------------------------------------------------------------|----------------------------------|
| [![Project stage][Stage]][Stage-Page] | ![CI][Badge-CI] | [![Release Artifacts][Badge-SonatypeReleases]][Link-SonatypeReleases] | [![Snapshot Artifacts][Badge-SonatypeSnapshots]][Link-SonatypeSnapshots] | [![Average time to resolve an issue][badge-iim]][link-iim] | [![badge-discord]][link-discord] |


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

[Badge-CI]: https://github.com/zio/zio-sbt/workflows/CI/badge.svg
[Badge-SonatypeReleases]: https://img.shields.io/nexus/r/https/oss.sonatype.org/dev.zio/zio-sbt-website_2.12.svg "Sonatype Releases"
[Badge-SonatypeSnapshots]: https://img.shields.io/nexus/s/https/oss.sonatype.org/dev.zio/zio-sbt-website_2.12.svg "Sonatype Snapshots"
[Badge-Discord]: https://img.shields.io/discord/629491597070827530?logo=discord "chat on discord"
[Link-SonatypeReleases]: https://oss.sonatype.org/content/repositories/releases/dev/zio/zio-sbt_2.12_1.0/ "Sonatype Releases"
[Link-SonatypeSnapshots]: https://oss.sonatype.org/content/repositories/snapshots/dev/zio/zio-sbt-website_2.12_1.0/ "Sonatype Snapshots"
[badge-iim]: https://isitmaintained.com/badge/resolution/zio/zio-sbt.svg
[link-iim]: https://isitmaintained.com/project/zio/zio-sbt
[badge-discord]: https://img.shields.io/discord/630498701860929559?logo=discord "chat ondiscord"
[link-discord]: https://discord.gg/2ccFBr4 "Discord"
[Stage]: https://img.shields.io/badge/Project%20Stage-Development-yellowgreen.svg
[Stage-Page]: https://github.com/zio/zio/wiki/Project-Stages
