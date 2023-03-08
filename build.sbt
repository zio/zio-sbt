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
    ciEnabledBranches    := Seq("main"),
    documentationProject := Some(docs),
    supportedScalaVersions := Map(
      (zioSbtWebsite / thisProject).value.id   -> (zioSbtWebsite / crossScalaVersions).value,
      (zioSbtEcosystem / thisProject).value.id -> (zioSbtEcosystem / crossScalaVersions).value,
      (zioSbtCi / thisProject).value.id        -> (zioSbtCi / crossScalaVersions).value,
      (zioSbtTests / thisProject).value.id     -> (zioSbtTests / crossScalaVersions).value
    )
  )
)

lazy val root = project
  .in(file("."))
  .settings(
    headerEndYear  := Some(2023),
    publish / skip := true
  )
  .aggregate(
    zioSbtGithubActions,
    zioSbtWebsite,
    zioSbtEcosystem,
    zioSbtCi,
    zioSbtTests
  )
  .enablePlugins(ZioSbtCiPlugin)

lazy val zioSbtTests =
  project
    .in(file("zio-sbt-tests"))
    .settings(
      stdSettings(name = "zio-sbt-tests"),
      publish / skip := true,
      headerEndYear  := Some(2023)
    )

lazy val zioSbtWebsite =
  project
    .in(file("zio-sbt-website"))
    .settings(stdSettings(name = "zio-sbt-website"))
    .settings(
      headerEndYear := Some(2023),
      scriptedLaunchOpts := {
        scriptedLaunchOpts.value ++
          Seq("-Xmx1024M", "-Dplugin.version=" + version.value)
      },
      scriptedBufferLog := false
    )
    .enablePlugins(SbtPlugin)

lazy val zioSbtEcosystem =
  project
    .in(file("zio-sbt-ecosystem"))
    .settings(stdSettings(name = "zio-sbt-ecosystem"))
    .settings(
      headerEndYear := Some(2023),
      scriptedLaunchOpts := {
        scriptedLaunchOpts.value ++
          Seq("-Xmx1024M", "-Dplugin.version=" + version.value)
      },
      scriptedBufferLog := false
    )
    .enablePlugins(SbtPlugin)

lazy val zioSbtCi =
  project
    .in(file("zio-sbt-ci"))
    .settings(stdSettings(name = "zio-sbt-ci"))
    .settings(
      headerEndYear := Some(2023),
      scriptedLaunchOpts := {
        scriptedLaunchOpts.value ++
          Seq("-Xmx1024M", "-Dplugin.version=" + version.value)
      },
      scriptedBufferLog := false
    )
    .enablePlugins(SbtPlugin)
    .dependsOn(zioSbtGithubActions, zioSbtEcosystem)

lazy val zioSbtGithubActions =
  project
    .in(file("zio-sbt-githubactions"))
    .settings(
      stdSettings(name = "zio-sbt-githubactions"),
      headerEndYear := Some(2023)
    )

lazy val docs = project
  .in(file("zio-sbt-docs"))
  .settings(
    moduleName := "zio-sbt-docs",
    scalacOptions -= "-Yno-imports",
    scalacOptions -= "-Xfatal-warnings",
    projectName                                := (ThisBuild / name).value,
    mainModuleName                             := (zioSbtWebsite / moduleName).value,
    projectStage                               := ProjectStage.ProductionReady,
    ScalaUnidoc / unidoc / unidocProjectFilter := inProjects(zioSbtWebsite),
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
  .dependsOn(zioSbtWebsite)
  .enablePlugins(WebsitePlugin)
