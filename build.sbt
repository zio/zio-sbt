import Versions._

sbtPlugin         := true
publishMavenStyle := true

enablePlugins(ZioSbtEcosystemPlugin, ZioSbtCiPlugin)

addCommandAlias("test", "scripted")

inThisBuild(
  List(
    name               := "ZIO SBT",
    startYear          := Some(2022),
    scalaVersion       := Scala212,
    crossScalaVersions := Seq(scalaVersion.value),
    developers := List(
      Developer("khajavi", "Milad Khajavi", "khajavi@gmail.com", url("https://github.com/khajavi"))
    ),
    ciEnabledBranches := Seq("main"),
    ciTargetScalaVersions :=
      targetScalaVersionsFor(
        `zio-sbt-website`,
        `zio-sbt-ecosystem`,
        `zio-sbt-ci`,
        `zio-sbt-tests`
      ).value
  )
)

lazy val root = project
  .in(file("."))
  .settings(
    headerEndYear  := Some(2023),
    publish / skip := true
  )
  .aggregate(
    `zio-sbt-githubactions`,
    `zio-sbt-website`,
    `zio-sbt-ecosystem`,
    `zio-sbt-ci`,
    `zio-sbt-tests`
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
      headerEndYear := Some(2023),
      scriptedLaunchOpts := {
        scriptedLaunchOpts.value ++
          Seq("-Xmx1024M", "-Dplugin.version=" + version.value)
      },
      scriptedBufferLog := false
    )
    .enablePlugins(SbtPlugin)

lazy val `zio-sbt-ecosystem` =
  project
    .settings(stdSettings())
    .settings(
      headerEndYear := Some(2023),
      scriptedLaunchOpts := {
        scriptedLaunchOpts.value ++
          Seq("-Xmx1024M", "-Dplugin.version=" + version.value)
      },
      scriptedBufferLog := false
    )
    .enablePlugins(SbtPlugin)

lazy val `zio-sbt-ci` =
  project
    .settings(stdSettings())
    .settings(
      headerEndYear := Some(2023),
      scriptedLaunchOpts := {
        scriptedLaunchOpts.value ++
          Seq("-Xmx1024M", "-Dplugin.version=" + version.value)
      },
      scriptedBufferLog := false
    )
    .enablePlugins(SbtPlugin)
    .dependsOn(`zio-sbt-githubactions`, `zio-sbt-ecosystem`)

lazy val `zio-sbt-githubactions` =
  project
    .settings(
      stdSettings(),
      headerEndYear := Some(2023)
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
    readmeContribution := readmeContribution.value +
      """|
         |#### TL;DR
         |
         |Before you submit a PR, make sure your tests are passing, and that the code is properly formatted
         |
         |```
         |sbt prepare
         |
         |sbt testPlugin
         |```
         |""".stripMargin
  )
  .dependsOn(`zio-sbt-website`)
  .enablePlugins(WebsitePlugin)
