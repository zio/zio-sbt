---
id: index
title: "ZIO SBT"
---

_ZIO SBT_ contains multiple sbt plugins that are useful for ZIO projects. It provides high-level SBT utilities that simplify the development of ZIO applications.

@PROJECT_BADGES@

## Installation

Add the following lines to your `plugin.sbt` file:

```scala
addSbtPlugin("dev.zio" % "zio-sbt-ecosystem" % "@VERSION@")
addSbtPlugin("dev.zio" % "zio-sbt-ci"        % "@VERSION@")
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

## ZIO SBT Shared

`ZioSbtSharedPlugin` is a plugin that provides common settings and tasks which can be shared across multiple ZIO projects. This plugin includes the following settings:

- `welcomeBannerEnabled`: A boolean setting that indicates whether or not to enable the welcome banner.
- `banners`: A sequence of strings representing banners that will be displayed as part of the welcome message.
- `usefulTasksAndSettings`: A sequence of tuples where each tuple contains a task or setting name and its description.
- `welcomeMessage`: A setting that generates the welcome message based on the `banners` and `usefulTasksAndSettings` settings.

These settings can be used to customize the welcome message displayed when sbt starts. The welcome message can include useful tasks and settings that are relevant to the project.

The plugin also provides the following tasks:

- `allBanners`: A task that aggregates all banners defined in the project.
- `allUsefulTasksAndSettings`: A task that aggregates all `usefulTasksAndSettings` defined in the project.

These tasks can be used to display a consolidated list of banners and useful tasks/settings in the welcome message.

To use the ZIO SBT Shared plugin, add the following lines to your `plugins.sbt` file:

```scala
addSbtPlugin("dev.zio" % "zio-sbt-shared" % "@VERSION@")
```

Then in your `build.sbt` file, you can configure the settings and tasks as needed:

```scala
welcomeBannerEnabled := true
banners := Seq("Welcome to the ZIO project!")
usefulTasksAndSettings := Seq(
  "compile" -> "Compiles the source code.",
  "test" -> "Runs the tests."
)
```

To enabled the welcome message for your project, add the following line to the root of your `build.sbt` file:

```scala
welcomeMessage
```

## ZIO SBT Project

depends on: ZioSbtShared

`ZioSbtProjectPlugin` is an sbt plugin that provides a set of common settings and tasks for compiling and testing ZIO projects. It is designed to simplify the process of setting up and configuring ZIO projects by providing a set of default settings that can be easily customized.

This modules provides settings and tasks for Scala versions, Scala platforms, Java versions and assertions to check what config the project is using.

### Quickstart SBT Project

To use the ZIO SBT Project plugin, add the following lines to your `plugins.sbt` file:

```scala
addSbtPlugin("dev.zio" % "zio-sbt-project" % "@VERSION@")
```

Then in your `build.sbt` file, enable the plugin by adding the following line:

```scala
enablePlugins(ZioSbtProjectPlugin)
```

and add some config to `ThisBuild` scope (these are the default values, if you omit them they plugin defaults are used):

```scala
inThisBuild(
  List(
    scala212     := ScalaVersion.scala212, 
    scala213     := ScalaVersion.scala213,
    scala3       := ScalaVersion.scala3,
    scalaVersion := scala213.value,
    defaultCrossScalaVersions := Seq(scala212.value, scala213.value, scala3.value)
  )
)
```

### Compiling

| Platform    | Scala 2.12 | Scala 2.13 | Scala 3   |
|-------------|------------|------------|-----------|
| JVM         | `compileJVM2_12` | `compileJVM2_13` | `compileJVM3` |
| Scala.js    | `compileJS2_12`  | `compileJS2_13`  | `compileJS3`  |
| Scala Native| `compileNative2_12` | `compileNative2_13` | `compileNative3` |

### Testing

| Platform    | Scala 2.12 | Scala 2.13 | Scala 3   |
|-------------|------------|------------|-----------|
| JVM         | `testJVM2_12` | `testJVM2_13` | `testJVM3` |
| Scala.js    | `testJS2_12`  | `testJS2_13`  | `testJS3`  |
| Scala Native| `testNative2_12` | `testNative2_13` | `testNative3` |

### Scala versions

The plugin provides the following settings for Scala versions. Use these settings to set your Scala versions.
The default values are the latest stable versions of Scala 2.12, 2.13, and Scala 3. All of these settings are of type `String` and can be overridden by the user.

- `scala212` - The default Scala 2.12 version
- `scala213` - The default Scala 2.13 version
- `scala3` - The default Scala 3 version
- `defaultCrossScalaVersions` - An optional setting to define the cross Scala versions
- `allScalaVersions` - This setting lists all Scala versions used by the project

By having these settings, we can use them in other sbt settings. For example, we can use them to define the `defaultCrossScalaVersions` setting:

```scala
ThisBuild / defaultCrossScalaVersions := Seq(scala212.value, scala213.value, scala3.value)
```

### Scala platforms

- `allScalaPlatforms` - This setting lists all Scala platforms used by the project

### Java versions

The plugin provides the following settings for Java versions. Use these settings to set your Java versions.

- `javaPlatform` - java target platform to release for, default is 11
- `currentJDK` - current (detected) JDK version`

## ZIO SBT Ecosystem

ZIO SBT Ecosystem plugin is an sbt plugin that provides a set of sbt settings and tasks that are very common and useful for configuring and managing ZIO projects. It is designed to help developers quickly set up a new ZIO project with minimal effort.

### Quickstart SBT Ecosystem

depends on: ZioSbtProjectPlugin

To use the ZIO SBT Ecosystem plugin, add the following lines to your `plugins.sbt` file:

```scala
addSbtPlugin("dev.zio" % "zio-sbt-ecosystem" % "@VERSION@")
```

Then in your `build.sbt` file, enable the plugin by adding the following line:

```scala
enablePlugins(ZioSbtEcosystemPlugin)
```

use the predefined settings to configure your project:

```scala
lazy val zio-awesome = crossProject(JSPlatform, JVMPlatform, NativePlatform)
  .in(file("zio-something"))
  .settings(stdSettings(enableCrossProject = true))
  .settings(enableZIO)
  .jsSettings(jsSettings)
  .jvmSettings(jvmSettings)
  .nativeSettings(nativeSettings)
```

or for an sbt-projectmatrix project:

```scala
lazy val zio-awesome = projectMatrix
  .crossScalaVersions("2.12.20", "2.13.15", "3.3.4")
  .crossPlatforms(JSPlatform, JVMPlatform, NativePlatform)
  .settings(stdSettings()) // enableCrossProject is not required, sbt-projectmatrix brings in default source directories for all scala versions and platforms
  .settings(enableZIO)
  .jvmPlatform(scalaVersions = Seq(Scala212, Scala213, Scala3), settings = jvmSettings)
  .jsPlatform(scalaVersions = Seq(Scala212, Scala213, Scala3), settings = jsSettings)
  .nativePlatform(scalaVersions = Seq(Scala212, Scala213, Scala3), settings = nativeSettings)
```

### Settings

There are also some other (optional) settings that are useful for configuring the projects:

- `stdSettings`— a set of standard settings which are common for every ZIO project, which includes configuring:
  - silencer plugin
  - kind projector plugin
  - cross project source directories (custom source directories for different scala versions)
  - scalafix plugin
  - java target platform
- `enableZIO`— a set of ZIO related settings such as enabling ZIO streams and ZIO test framework.
- `jvmSettings`— common platform-specific settings for JVM.
- `jsSettings` — common platform-specific settings for Scala.js.
- `nativeSettings`— common platform-specific settings for Scala Native.

### Roll your own settings

It is still possible to configure the project as you like. The plugin provides some helper methods that are useful for configuring a compiler option for a specific Scala version:

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

- `sbt compileDocs`— compile documentation inside the `docs` directory. The compilation result will be inside the `website/docs` directory.
- `sbt installWebsite`— creates a website for the project inside the `website` directory.
- `sbt previewWebsite`— runs a local webserver that serves documentation locally on http://localhost:3000. By changing the documentation inside the `docs` directory, the website will be reloaded with new content.
- `sbt publishToNpm`— publishes documentation inside the `docs` directory to the npm registry.
- `sbt generateReadme`— generate README.md file from `docs/index.md` and sbt setting keys.

## ZIO SBT CI Plugin

depends on: ZioSbtProjectPlugin

ZIO SBT CI is an sbt plugin which generates a GitHub workflow for a project, making it easier to set up continuous integration (CI) pipelines for Scala projects. With this plugin, developers can streamline their development workflow by automating the testing and deployment process, reducing manual effort and errors. The plugin is designed to work seamlessly with sbt, the popular build tool for Scala projects, and integrates smoothly with GitHub Actions, the CI/CD platform provided by GitHub.

ZIO SBT CI provides a simple and efficient way to configure, manage, and run CI pipelines, helping teams to deliver high-quality software faster and with greater confidence.

ZIO SBT CI plugin generates a default GitHub workflow that includes common CI tasks such as building, testing, and publishing artifacts. However, users can also manually customize the workflow. This plugin is designed to be flexible and extensible, making it easy for users to tailor the workflow to their specific needs. Additionally, the plugin also provides numerous optional sbt settings that users can modify to change various aspects of the generated workflow. Overall, the ZIO SBT CI plugin strikes a balance between automation and flexibility, allowing users to automate their CI process while still giving them control over how the workflow is generated.

### Settings

The ZIO SBT CI plugin provides the following settings:

- `ciTargetScalaVersions` - A mapping of project names to the Scala versions that should be used for the testing phase of continuous integration (CI). This setting is used to define the Scala versions that should be tested for each project in the build.
- `ciDefaultJavaVersion` - The default Java version to use in the CI workflow. The default value is `17`.
- `ciDefaultJavaDistribution` - The default Java distribution to use in the CI workflow. The default value is `corretto`.
- `ciDefaultNodeJSVersion` - The default Node.js version to use in the CI workflow. The default value is `16.x`.
- `ciJvmOptions` - The JVM options to use in the CI workflow.
- `ciNodeOptions` - The Node.js options to use in the CI workflow.

### Tasks

The ZIO SBT CI plugin provides the following tasks (steps can be overridden or modified):

- `ciCheckGithubWorkflow` - Checks if the GitHub workflow file exists in the `.github/workflows` directory.
- `ciGenerateGithubWorkflow` - Generates a GitHub workflow file in the `.github/workflows` directory.
- `ciJobPerScalaPlatform` - Whether to generate a separate job for each Scala platform, when not defined `allScalaPlatforms.value > 1` is used
- `ciJobPerScalaVersion` - Whether to generate a separate job for each Scala version, when not defined `allScalaVersions.value > 1` is used
- `ciCheckGithubWorkflowSteps` - Steps for the GitHub workflow, for no steps use `Nil`.
- `ciCheckArtifactsCompilationSteps` - Compilation steps for the artifacts job in the GitHub workflow.
- `ciCheckArtifactsBuildSteps` - Build steps for the artifacts job in the GitHub workflow.
- `ciCheckWebsiteBuildProcess` - Build process for the website job in the GitHub workflow.
- `ciPullRequestApprovalJobs` - Jobs for the pull request approval workflow.
- `ciReleaseApprovalJobs` - Jobs for the release approval workflow.
- `ciWorkflowName` - The name of the GitHub workflow file.
- `ciEnabledBranches` - The pull request target branches on which the GitHub workflow should run.
- `ciBuildJobs` - The build jobs for the GitHub workflow.
- `ciLintJobs` - The lint jobs for the GitHub workflow.
- `ciTestJobs` - The test jobs for the GitHub workflow.
- `ciUpdateReadmeJobs` - The update readme jobs for the GitHub workflow.
- `ciReleaseJobs` - The release jobs for the GitHub workflow.
- `ciPostReleaseJobs` - The post-release jobs for the GitHub workflow.

### Getting Started

To use the ZIO SBT CI plugin, add the following lines to your `plugins.sbt` file:

```scala
addSbtPlugin("dev.zio" % "zio-sbt-ci" % "@VERSION@")

resolvers ++= Resolver.sonatypeOssRepos("public")
```

Then in your `build.sbt` file, enable the plugin by adding the following line:

```scala
enablePlugins(ZioSbtCiPlugin)
```

Now you can generate a GitHub workflow by running the following command:

```bash
sbt ciGenerateGithubWorkflow
```

This will generate a GitHub workflow file inside the `.github/workflows` directory, named `ci.yml`. The workflow file contains the following default jobs:

- Build
- Lint
- Test
- Update Readme

> **Note:**
> 
> To use this plugin, we also need to install [ZIO Assistant](https://github.com/apps/zio-assistant) bot.

## Testing Strategies

### Default Testing Strategy

The default testing strategy for the ZIO SBT CI plugin is to run `sbt +test` on Corretto Java 11, 17, and 21. This will generate the following job:

```yaml
test:
  name: Test
  runs-on: ubuntu-latest
  continue-on-error: false
  strategy:
    fail-fast: false
    matrix:
      java: ['11', '17', '21']
  steps:
  - name: Install libuv
    run: sudo apt-get update && sudo apt-get install -y libuv1-dev
  - name: Setup Scala
    uses: actions/setup-java@v3.13.0
    with:
      distribution: corretto
      java-version: ${{ matrix.java }}
      check-latest: true
  - name: Cache Dependencies
    uses: coursier/cache-action@v6
  - name: Git Checkout
    uses: actions/checkout@v4.1.1
    with:
      fetch-depth: '0'
  - name: Test
    run: sbt +test
```

The `sbt +test` command will run the `test` task for all submodules in the project against all Scala versions defined in the `crossScalaVersions` setting.

### Concurrent Testing Strategy

In some cases, we may have multiple submodules in our project and we want to test them concurrently using GitHub Actions matrix strategy.

The `ciTargetScalaVersions` setting key is used to define a mapping of project names to the Scala versions that should be used for the testing phase of continuous integration (CI).

For example, suppose we have a project with the name "submoduleA" and we want to test it against Scala `2.12.20`, and for "submoduleB" we want to test it against Scala `2.12.20`, `2.13.15`, and `3.3.4`. We can define the `ciTargetScalaVersions` setting as follows:

```scala
ThisBuild / ciTargetScalaVersions := Map(
    "submoduleA" -> Seq("2.12.20"),
    "submoduleB" -> Seq("2.12.20", "2.13.15", "3.3.4")
  )
```

In the example provided, `ciTargetScalaVersions` is defined at the `ThisBuild` level, meaning that the setting will apply to all projects within the build. The setting defines a Map where the key is the name of the current project, obtained by calling the `id` method on the `thisProject` setting, and the value is a sequence of Scala versions obtained from the `crossScalaVersions` of each submodule setting.

To simplify this process, we can populate the versions using each submodule's `crossScalaVersions` setting as follows:

```scala
ThisBuild / ciTargetScalaVersions := Map(
  (submoduleA / thisProject).value.id -> (submoduleA / crossScalaVersions).value,
  (submoduleB / thisProject).value.id -> (submoduleB / crossScalaVersions).value
)
```

The above code can be simplified further by using the `targetScalaVersionsFor` helper method, which takes a list of submodules and returns a Map of project names to their `crossScalaVersions`:

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
      java: ['11', '17', '21']
      scala-project:
      - ++2.12.20 submoduleA
      - ++2.12.20 submoduleB
      - ++2.13.15 submoduleB
      - ++3.3.4 submoduleB
  steps:
  - name: Install libuv
    run: sudo apt-get update && sudo apt-get install -y libuv1-dev
  - name: Setup Scala
    uses: actions/setup-java@v3.10.0
    with:
      distribution: corretto
      java-version: ${{ matrix.java }}
      check-latest: true
  - name: Cache Dependencies
    uses: coursier/cache-action@v6
  - name: Git Checkout
    uses: actions/checkout@v4.1.1
    with:
      fetch-depth: '0'
  - name: Test
    run: sbt ${{ matrix.scala-project }}/test
```
