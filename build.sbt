import Versions._

sbtPlugin         := true
publishMavenStyle := true

enablePlugins(ZioSbtEcosystemPlugin, ZioSbtCiPlugin)

addCommandAlias("test", "scripted")

inThisBuild(
  List(
    name      := "ZIO SBT",
    startYear := Some(2022),
    // sbt 2.0 builds sbt plugins with the Scala 3 version sbt itself uses (3.8.4), so zio-sbt's own
    // plugin modules must use it. This overrides the Scala 2.13 default that
    // ZioSbtEcosystemPlugin.buildSettings sets for downstream consumers (intentionally left
    // unchanged for cross-building libraries). The `zio-sbt-source` library module overrides this
    // back to the 2.13/3.3 LTS it publishes for.
    scalaVersion       := SbtScala,
    crossScalaVersions := Seq(scalaVersion.value),
    developers         := List(
      Developer("khajavi", "Milad Khajavi", "khajavi@gmail.com", uri("https://github.com/khajavi"))
    ),
    ciEnabledBranches := Seq("main")
  )
)

// The plugin modules build only with the Scala 3 version sbt 2.0 uses (see `SbtScala`), where
// scalafix's semantic rules would need a SemanticDB + `-Wunused` wired through the shared
// `scalafixSettings` (which every downstream plugin user inherits) — out of scope for the sbt 2.0
// upgrade. They therefore run only the syntactic scalafix rules; see `.scalafix-syntactic.conf`.
// `zio-sbt-source` (a 2.13/3.3 library) keeps the full semantic `.scalafix.conf`.
lazy val syntacticScalafixOnly: Seq[Setting[?]] =
  Seq(scalafixConfig := Some((LocalRootProject / baseDirectory).value / ".scalafix-syntactic.conf"))

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
      stdSettings(javaPlatform = "17"),
      syntacticScalafixOnly,
      publish / skip := true,
      headerEndYear  := Some(2023)
    )

lazy val `zio-sbt-website` =
  project
    .settings(stdSettings(javaPlatform = "17"), syntacticScalafixOnly)
    .settings(
      headerEndYear      := Some(2023),
      scriptedLaunchOpts := {
        scriptedLaunchOpts.value ++
          Seq("-Xmx1024M", "-Dplugin.version=" + version.value)
      },
      scriptedBufferLog := false
    )
    .enablePlugins(SbtPlugin)

lazy val `zio-sbt-ecosystem` =
  project
    .settings(stdSettings(javaPlatform = "17"), syntacticScalafixOnly)
    .settings(
      headerEndYear      := Some(2023),
      scriptedLaunchOpts := {
        scriptedLaunchOpts.value ++
          Seq("-Xmx1024M", "-Dplugin.version=" + version.value)
      },
      scriptedBufferLog := false
    )
    .enablePlugins(SbtPlugin)

lazy val `zio-sbt-ci` =
  project
    .settings(stdSettings(javaPlatform = "17"), syntacticScalafixOnly)
    .settings(
      headerEndYear      := Some(2023),
      scriptedLaunchOpts := {
        scriptedLaunchOpts.value ++
          Seq("-Xmx1024M", "-Dplugin.version=" + version.value)
      },
      scriptedBufferLog := false
    )
    .enablePlugins(SbtPlugin)
    .dependsOn(`zio-sbt-githubactions`)

lazy val `zio-sbt-githubactions` =
  project
    .settings(
      stdSettings(javaPlatform = "17"),
      syntacticScalafixOnly,
      headerEndYear := Some(2023)
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
        "org.scalameta" %% "mdoc"         % "2.9.0",
        "dev.zio"       %% "zio-test"     % zio % Test,
        "dev.zio"       %% "zio-test-sbt" % zio % Test
      ) ++ {
        if (scalaBinaryVersion.value == "2.13") Seq("org.scala-lang" % "scala-reflect" % scalaVersion.value)
        else Seq()
      },
      jvmSettings, // fork JVM tests for readable logs
      testFrameworks += new TestFramework("zio.test.sbt.ZTestFramework")
    )

lazy val docs = project
  .in(file("zio-sbt-docs"))
  .settings(
    moduleName := "zio-sbt-docs",
    scalacOptions -= "-Yno-imports",
    scalacOptions -= "-Xfatal-warnings",
    projectName                                := (ThisBuild / name).value,
    mainModuleName                             := (`zio-sbt-website` / moduleName).value,
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
