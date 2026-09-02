import Versions._
import _root_.zio.sbt.githubactions.Step

sbtPlugin         := true
publishMavenStyle := true

enablePlugins(ZioSbtEcosystemPlugin, ZioSbtCiPlugin)

// Run the sbt-plugin scripted tests from the plain `test` task. A command
// alias like `addCommandAlias("test", "scripted")` is not enough: the `+`
// cross command runs the `test` *task* directly without expanding command
// aliases, so CI's `sbt +test` used to skip the scripted tests entirely.
// `SbtPlugin`'s own default for `pluginCrossBuild / sbtVersion` maps the Scala-3 axis to
// `sbtVersion.value`, i.e. whatever sbt is *currently running the build* - correct only when the
// meta-build itself has moved to sbt 2.x. Since this repo's meta-build stays on sbt 1.13.0 (see
// project/build.properties) while its plugins dual-cross-build for sbt-2.x consumers, the
// Scala-3 axis must instead be pinned to a real sbt 2.x release explicitly.
lazy val Sbt2Version = "2.0.7"

lazy val pluginCrossBuildSettings = Seq(
  // Only overridden for the Scala-3 axis; every other axis falls through to whatever
  // `SbtPlugin`'s own default (or anything else already in the settings sequence) already
  // computed, rather than a value hardcoded here that could drift out of sync with it.
  pluginCrossBuild / sbtVersion := {
    if (scalaBinaryVersion.value == "3") Sbt2Version
    else (pluginCrossBuild / sbtVersion).value
  }
)

// Scala 3.8.4 (see project/Versions.scala) rejects `-release:11`, the flag `stdSettings()` bakes in
// from its `javaPlatform` *parameter* default (a plain String captured at settings-definition time,
// not a dynamic key read, so overriding the `javaPlatform` SettingKey itself has no effect on it).
// Scoped to the Scala-3 axis only by rewriting `scalacOptions` after the fact - the sbt-1.x/2.12 axis
// keeps its existing `-release:11` untouched.
lazy val scala3JavaPlatformSettings = Seq(
  scalacOptions := {
    val opts = scalacOptions.value
    if (scalaBinaryVersion.value == "3") opts.filterNot(_.startsWith("-release:")) :+ "-release:17"
    else opts
  }
)

lazy val scriptedTestSettings = Seq(
  scriptedLaunchOpts := {
    scriptedLaunchOpts.value ++
      Seq("-Xmx1024M", "-Dplugin.version=" + version.value)
  },
  scriptedBufferLog := false,
  // Scripted fixtures are sbt-1.x consumers today, so only run them on the 2.12 axis; running
  // them again under `++3.3.8` would just re-exercise the same sbt-1.x fixtures a second time.
  Test / test := Def.taskDyn {
    if (scalaBinaryVersion.value == "3") Def.task(()) else scripted.toTask("")
  }.value
)

inThisBuild(
  List(
    name               := "ZIO SBT",
    startYear          := Some(2022),
    scalaVersion       := Scala212,
    crossScalaVersions := Seq(scalaVersion.value),
    developers         := List(
      Developer("khajavi", "Milad Khajavi", "khajavi@gmail.com", url("https://github.com/khajavi"))
    ),
    ciEnabledBranches := Seq("main"),
    // The docs site already has NETLIFY_AUTH_TOKEN/NETLIFY_SITE_ID secrets configured, so this
    // repo dogfoods deploy previews for its own Docusaurus site (website/build).
    ciEnableDeployPreview := true
  )
)

// `ciCheckArtifactsBuildSteps` comes from `ZioSbtCiPlugin.buildSettings`, which is a ThisBuild-
// scoped (not per-project) setting; `generateGithubWorkflowTask` reads it unscoped from within
// another ThisBuild-scoped setting, so an override placed inside `root`'s own `.settings(...)`
// (project scope) is silently never read - it must be scoped to `ThisBuild` explicitly here.
//
// `+publishLocal` (the default `ciCheckArtifactsBuildSteps` step) already publishes every
// cross-built module's sbt-2.x/Scala-3 artifacts, so this step just proves a real sbt 2.0.7
// process can resolve and use them - the `-sbt2` scripted fixtures added alongside the existing
// sbt-1.x ones in zio-sbt-ecosystem/zio-sbt-website/zio-sbt-ci.
ThisBuild / ciCheckArtifactsBuildSteps += Step.SingleStep(
  name = "Check sbt-2.x scripted fixtures",
  run = Some(
    "sbt --no-colors " +
      "\"zio-sbt-ecosystem/scripted zio-sbt-ecosystem/verifySettings-sbt2\" " +
      "\"zio-sbt-website/scripted zio-sbt-website/installWebsite-sbt2\" " +
      "\"zio-sbt-ci/scripted zio-sbt-ci/defaults-sbt2\""
  )
)

lazy val root = project
  .in(file("."))
  .settings(
    headerEndYear  := Some(2026),
    publish / skip := true
  )
  .aggregate(
    `zio-sbt-githubactions`,
    `zio-sbt-website`,
    `zio-sbt-ecosystem`,
    `zio-sbt-ci`,
    `zio-sbt-tests`,
    `zio-sbt-source`
  )
  .enablePlugins(ZioSbtCiPlugin)

lazy val `zio-sbt-tests` =
  project
    .settings(
      stdSettings(),
      publish / skip := true,
      headerEndYear  := Some(2023)
    )

lazy val `zio-sbt-website` =
  project
    .settings(stdSettings())
    .settings(
      headerEndYear      := Some(2023),
      crossScalaVersions := Seq(Scala212, Scala3),
      pluginCrossBuildSettings,
      scala3JavaPlatformSettings,
      scriptedTestSettings
    )
    .enablePlugins(SbtPlugin)

lazy val `zio-sbt-ecosystem` =
  project
    .settings(stdSettings())
    .settings(
      headerEndYear      := Some(2023),
      crossScalaVersions := Seq(Scala212, Scala3),
      pluginCrossBuildSettings,
      scala3JavaPlatformSettings,
      scriptedTestSettings
    )
    .enablePlugins(SbtPlugin)

lazy val `zio-sbt-ci` =
  project
    .settings(stdSettings())
    .settings(
      headerEndYear      := Some(2023),
      crossScalaVersions := Seq(Scala212, Scala3),
      pluginCrossBuildSettings,
      scala3JavaPlatformSettings,
      scriptedTestSettings,
      // The `bothPlugins` fixture puts zio-sbt-website on the same build classpath, which is the
      // configuration that used to fail to load because both plugins declared `ciWorkflowName`.
      scriptedDependencies := {
        val _ = scriptedDependencies.value
        (`zio-sbt-website` / publishLocal).value
      }
    )
    .enablePlugins(SbtPlugin)
    .dependsOn(`zio-sbt-githubactions`)

lazy val `zio-sbt-githubactions` =
  project
    .settings(
      stdSettings(),
      headerEndYear      := Some(2023),
      crossScalaVersions := Seq(Scala212, Scala3),
      scala3JavaPlatformSettings
    )

lazy val `zio-sbt-source` =
  project
    .settings(
      crossScalaVersions := Seq(Scala213, Scala3),
      scalaVersion       := Scala213,
      sbtPlugin          := false,
      headerEndYear      := Some(2026),
      semanticdbEnabled  := true,
      semanticdbVersion  := scalafixSemanticdb.revision,
      scalacOptions ++= {
        if (scalaBinaryVersion.value == "2.13") Seq("-Wunused:imports") else Seq()
      },
      libraryDependencies ++= Seq(
        "org.scalameta" %% "mdoc"         % "2.9.1",
        "dev.zio"       %% "zio-test"     % zio % Test,
        "dev.zio"       %% "zio-test-sbt" % zio % Test
      ) ++ {
        if (scalaBinaryVersion.value == "2.13") Seq("org.scala-lang" % "scala-reflect" % scalaVersion.value)
        else Seq()
      },
      testFrameworks += new TestFramework("zio.test.sbt.ZTestFramework")
    )

lazy val docs = project
  .in(file("zio-sbt-docs"))
  .settings(
    moduleName := "zio-sbt-docs",
    scalacOptions -= "-Yno-imports",
    scalacOptions -= "-Xfatal-warnings",
    projectName    := (ThisBuild / name).value,
    mainModuleName := (`zio-sbt-website` / moduleName).value,
    // sbt plugins are published with an extra sbt cross-version suffix, e.g. zio-sbt-website_2.12_1.0
    badgeArtifactId                            := mainModuleName.value + '_' + scalaBinaryVersion.value + '_' + sbtBinaryVersion.value,
    projectStage                               := ProjectStage.ProductionReady,
    ScalaUnidoc / unidoc / unidocProjectFilter := inProjects(`zio-sbt-website`),
    headerLicense                              := None,
    readmeContribution                         := readmeContribution.value +
      """|
         |#### TL;DR
         |
         |Before you submit a PR, make sure your tests are passing, and that the code is properly formatted
         |
         |```
         |sbt prepare
         |
         |sbt +test
         |```
         |""".stripMargin
  )
  .dependsOn(`zio-sbt-website`)
  .enablePlugins(WebsitePlugin)
