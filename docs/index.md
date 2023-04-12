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
- `sbt installWebsite`— creates a website for the project inside the `website` directory.
- `sbt previewWebsite`— runs a local webserver that serves documentation locally on http://localhost:3000. By changing the documentation inside the `docs` directory, the website will be reloaded with new content.
- `sbt publishToNpm`— publishes documentation inside the `docs` directory to the npm registry.
- `sbt generateGithubWorkflow`— generates GitHub workflow which publishes documentation for each library release.
- `sbt generateReadme`— generate README.md file from `docs/index.md` and sbt setting keys.

## ZIO SBT CI Plugin

ZIO SBT CI is an sbt plugin which generates a GitHub workflow for a project, making it easier to set up continuous integration (CI) pipelines for Scala projects. With this plugin, developers can streamline their development workflow by automating the testing and deployment process, reducing manual effort and errors. The plugin is designed to work seamlessly with sbt, the popular build tool for Scala projects, and integrates smoothly with GitHub Actions, the CI/CD platform provided by GitHub.

ZIO SBT CI provides a simple and efficient way to configure, manage, and run CI pipelines, helping teams to deliver high-quality software faster and with greater confidence.

ZIO SBT CI plugin generates a default GitHub workflow that includes common CI tasks such as building, testing, and publishing artifacts. However, users can also manually customize the workflow. This plugin is designed to be flexible and extensible, making it easy for users to tailor the workflow to their specific needs. Additionally, the plugin also provides tons of optional sbt settings that users can modify to change various aspects of the generated workflow. Overall, ZIO SBT CI plugin strikes a balance between automation and flexibility, allowing users to automate their CI process while still giving them control over how the workflow is generated.

### Getting Started

To use ZIO SBT CI plugin, add the following lines to your `plugins.sbt` file:

```scala
addSbtPlugin("dev.zio" % "zio-sbt-ci" % "@VERSION@")

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

The default testing strategy for ZIO SBT CI plugin is to run `sbt ++test` on java 8, 11 and 17. So this will generate the following job:

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
    run: sbt ++test
```

The `sbt ++test` command will run the `test` task for all submodules in the project against all Scala versions defined in the `crossScalaVersions` setting.

### Concurrent Testing Strategy

In some cases, we may have multiple submodules in our project and we want to test them concurrently using GitHub Actions matrix strategy.

The `ciTargetScalaVersions` setting key is used to define a mapping of project names to the Scala versions that should be used for testing phase of continuous integration (CI).

For example, suppose we have a project with the name "submoduleA" and we want to test it against Scala `2.11.12` and `2.12.17`, and for the "submoduleB" we want to test it against Scala `2.12.17` and `2.13.10` and `3.2.2`, We can define the `ciTargetScalaVersions` setting as follows:

```scala
ThisBuild / ciTargetScalaVersions := Map(
    "submoduleA" -> Seq("2.11.12", "2.12.17"),
    "submoduleB" -> Seq("2.12.17", "2.13.10", "3.2.2")
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
      - ++2.12.17 submoduleA
      - ++2.12.17 submoduleB
      - ++2.13.10 submoduleB
      - ++3.2.2 submoduleB
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
