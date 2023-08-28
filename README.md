[//]: # (This file was autogenerated using `zio-sbt-website` plugin via `sbt generateReadme` command.)
[//]: # (So please do not edit it manually. Instead, change "docs/index.md" file or sbt setting keys)
[//]: # (e.g. "readmeDocumentation" and "readmeSupport".)

# ZIO SBT

_ZIO SBT_ contains multiple sbt plugins that are useful for ZIO projects. It provides high-level SBT utilities that simplify the development of ZIO applications.

[![Production Ready](https://img.shields.io/badge/Project%20Stage-Production%20Ready-brightgreen.svg)](https://github.com/zio/zio/wiki/Project-Stages) ![CI Badge](https://github.com/zio/zio-sbt/workflows/CI/badge.svg) [![Sonatype Releases](https://img.shields.io/nexus/r/https/oss.sonatype.org/dev.zio/zio-sbt-website_2.12.svg?label=Sonatype%20Release)](https://oss.sonatype.org/content/repositories/releases/dev/zio/zio-sbt-website_2.12/) [![Sonatype Snapshots](https://img.shields.io/nexus/s/https/oss.sonatype.org/dev.zio/zio-sbt-website_2.12.svg?label=Sonatype%20Snapshot)](https://oss.sonatype.org/content/repositories/snapshots/dev/zio/zio-sbt-website_2.12/) [![javadoc](https://javadoc.io/badge2/dev.zio/zio-sbt-docs_2.12/javadoc.svg)](https://javadoc.io/doc/dev.zio/zio-sbt-docs_2.12) [![ZIO SBT](https://img.shields.io/github/stars/zio/zio-sbt?style=social)](https://github.com/zio/zio-sbt)

## Installation

Add the following lines to your `plugin.sbt` file:

```scala
addSbtPlugin("dev.zio" % "zio-sbt-ecosystem" % "0.4.0-alpha.12")
addSbtPlugin("dev.zio" % "zio-sbt-ci"        % "0.4.0-alpha.12")
addSbtPlugin("dev.zio" % "zio-sbt-website"   % "0.4.0-alpha.12")
```

Then you can enable them by using the following code in your `build.sbt` file:

```scala
enablePlugins(
  ZioSbtWebsitePlugin,
  ZioSbtEcosystemPlugin,
  ZioSbtCiPlugin
)
```

## ZIO SBT Ecosystem

ZIO SBT Ecosystem plugin is an sbt plugin that provides a set of sbt settings and tasks that are very common and useful for configuring and managing ZIO projects. It is designed help developers to quickly set up a new ZIO project with a minimal amount of effort.

This pluging provides the following settings with default values:

- scala211
- scala212
- scala213
- scala3

The default values are the latest stable versions of Scala 2.11, 2.12, 2.13, and Scala 3. All of these settings are of type `String` and can be overridden by the user.

By having these settings, then we can use them in other sbt settings. For example, we can use them to define the `crossScalaVersions` setting:

```scala
crossScalaVersions := Seq(scala211.value, scala212.value, scala213.value, scala3.value)
```

There are also some other settings that are useful for configuring the projects:

- `stdSettings`— a set of standard settings which are common for every ZIO project, which includes configuring:
  - silencer plugin
  - kind projector plugin
  - cross project plugin
  - scalafix plugin
  - java target platform
- `enableZIO`- a set of ZIO related settings such as enabling zio streams and ZIO test framework.
- `jsSettings`, `nativeSettings`- common platform specific settings for Scala.js and Scala Native.

It also provides some helper methods that are useful for configuring a compiler option for a specific Scala version:

- `optionsOn`
- `optionsOnExcept`
- `optionsOnOrElse`
- `addOptionsOn`
- `addOptionsOnOrElse`
- `addOptionsOnExcept`

And the same for adding a dependency for a specific Scala version:

- `dependenciesOn`
- `dependenciesOnExcept`
- `dependenciesOnOrElse`
- `addDependenciesOn`
- `addDependenciesOnExcept`
- `addDependenciesOnOrElse`

## ZIO SBT Website

ZIO SBT Website is an SBT plugin that has the following tasks:

- `sbt compileDocs`— compile documentation inside `docs` directory. The compilation result will be inside `website/docs` directory.
- `sbt installWebsite`— creates a website for the project inside the `website` directory.
- `sbt previewWebsite`— runs a local webserver that serves documentation locally on http://localhost:3000. By changing the documentation inside the `docs` directory, the website will be reloaded with new content.
- `sbt publishToNpm`— publishes documentation inside the `docs` directory to the npm registry.
- `sbt generateReadme`— generate README.md file from `docs/index.md` and sbt setting keys.

## ZIO SBT CI Plugin

ZIO SBT CI is an sbt plugin which generates a GitHub workflow for a project, making it easier to set up continuous integration (CI) pipelines for Scala projects. With this plugin, developers can streamline their development workflow by automating the testing and deployment process, reducing manual effort and errors. The plugin is designed to work seamlessly with sbt, the popular build tool for Scala projects, and integrates smoothly with GitHub Actions, the CI/CD platform provided by GitHub.

ZIO SBT CI provides a simple and efficient way to configure, manage, and run CI pipelines, helping teams to deliver high-quality software faster and with greater confidence.

ZIO SBT CI plugin generates a default GitHub workflow that includes common CI tasks such as building, testing, and publishing artifacts. However, users can also manually customize the workflow. This plugin is designed to be flexible and extensible, making it easy for users to tailor the workflow to their specific needs. Additionally, the plugin also provides tons of optional sbt settings that users can modify to change various aspects of the generated workflow. Overall, ZIO SBT CI plugin strikes a balance between automation and flexibility, allowing users to automate their CI process while still giving them control over how the workflow is generated.

### Getting Started

To use ZIO SBT CI plugin, add the following lines to your `plugins.sbt` file:

```scala
addSbtPlugin("dev.zio" % "zio-sbt-ci" % "0.4.0-alpha.12")

resolvers ++= Resolver.sonatypeOssRepos("public")
```

Then in your `build.sbt` file, enable the plugin by adding the following line:

```scala
enablePlugins(ZioSbtCiPlugin)
```

Now you can generate a Github workflow by running the following command:

```bash
sbt ciGenerateGithubWorkflow
```

This will generate a GitHub workflow file inside the `.github/workflows` directory, named `ci.yml`. The workflow file contains following default Jobs:

- Build
- Lint
- Test
- Update Readme

> **Note:**
> 
> To use this plugin, we also need to install [ZIO Assistant](https://github.com/apps/zio-assistant) bot.

## Testing Strategies

### Default Testing Strategy

The default testing strategy for ZIO SBT CI plugin is to run `sbt +test` on java 8, 11 and 17. So this will generate the following job:

```yaml
test:
  name: Test
  runs-on: ubuntu-latest
  continue-on-error: false
  strategy:
    fail-fast: false
    matrix:
      java:
      - '8'
      - '11'
      - '17'
  steps:
  - name: Install libuv
    run: sudo apt-get update && sudo apt-get install -y libuv1-dev
  - name: Setup Scala
    uses: actions/setup-java@v3.10.0
    with:
      distribution: temurin
      java-version: ${{ matrix.java }}
      check-latest: true
  - name: Cache Dependencies
    uses: coursier/cache-action@v6
  - name: Git Checkout
    uses: actions/checkout@v3.3.0
    with:
      fetch-depth: '0'
  - name: Test
    run: sbt +test
```

The `sbt +test` command will run the `test` task for all submodules in the project against all Scala versions defined in the `crossScalaVersions` setting.

### Concurrent Testing Strategy

In some cases, we may have multiple submodules in our project and we want to test them concurrently using GitHub Actions matrix strategy.

The `ciTargetScalaVersions` setting key is used to define a mapping of project names to the Scala versions that should be used for testing phase of continuous integration (CI).

For example, suppose we have a project with the name "submoduleA" and we want to test it against Scala `2.11.12` and `2.12.18`, and for the "submoduleB" we want to test it against Scala `2.12.18` and `2.13.11` and `3.3.0`, We can define the `ciTargetScalaVersions` setting as follows:

```scala
ThisBuild / ciTargetScalaVersions := Map(
    "submoduleA" -> Seq("2.11.12", "2.12.18"),
    "submoduleB" -> Seq("2.12.18", "2.13.11", "3.3.0")
  )
```

In the example provided, `ciTargetScalaVersions` is defined at the `ThisBuild` level, meaning that the setting will apply to all projects within the build. The setting defines a Map where the key is the name of the current project, obtained by calling the `id` method on the `thisProject` setting, and the value is a sequence of Scala versions obtained from the `crossScalaVersions` of each submodule setting.

To simplify this process, we can populate the versions using each submodule's crossScalaVersions setting as follows:

```scala
ThisBuild / ciTargetScalaVersions := Map(
  (submoduleA / thisProject).value.id -> (submoduleA / crossScalaVersions).value,
  (submoduleB / thisProject).value.id -> (submoduleB / crossScalaVersions).value
)
```

The above code can be simplified further by using `targetScalaVersionsFor` helper method, it takes a list of submodules and returns a Map of project names to their `crossScalaVersions`:

```scala
ThisBuild / ciTargetScalaVersions := targetScalaVersionsFor(submoduleA, submoduleB).value
```

This will generate the following job:

```yaml
test:
  name: Test
  runs-on: ubuntu-latest
  continue-on-error: false
  strategy:
    fail-fast: false
    matrix:
      java:
      - '8'
      - '11'
      - '17'
      scala-project:
      - ++2.11.12 submoduleA
      - ++2.12.18 submoduleA
      - ++2.12.18 submoduleB
      - ++2.13.11 submoduleB
      - ++3.3.0 submoduleB
  steps:
  - name: Install libuv
    run: sudo apt-get update && sudo apt-get install -y libuv1-dev
  - name: Setup Scala
    uses: actions/setup-java@v3.10.0
    with:
      distribution: temurin
      java-version: ${{ matrix.java }}
      check-latest: true
  - name: Cache Dependencies
    uses: coursier/cache-action@v6
  - name: Git Checkout
    uses: actions/checkout@v3.3.0
    with:
      fetch-depth: '0'
  - name: Test
    run: sbt ${{ matrix.scala-project }}/test
```

## Documentation

Learn more on the [ZIO SBT homepage](https://zio.dev/zio-sbt)!

## Contributing

For the general guidelines, see ZIO [contributor's guide](https://zio.dev/contributor-guidelines).
#### TL;DR

Before you submit a PR, make sure your tests are passing, and that the code is properly formatted

```
sbt prepare

sbt testPlugin
```

## Code of Conduct

See the [Code of Conduct](https://zio.dev/code-of-conduct)

## Support

Come chat with us on [![Badge-Discord]][Link-Discord].

[Badge-Discord]: https://img.shields.io/discord/629491597070827530?logo=discord "chat on discord"
[Link-Discord]: https://discord.gg/2ccFBr4 "Discord"

## License

[License](LICENSE)
