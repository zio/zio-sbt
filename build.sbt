import Versions._

sbtPlugin         := true
publishMavenStyle := true

enablePlugins(ZioSbtEcosystemPlugin, ZioSbtCiPlugin)

addCommandAlias("test", "scripted")

ThisBuild / scalaVersion       := Scala212
ThisBuild / crossScalaVersions := Seq(scalaVersion.value)

inThisBuild(
  List(
    organization := "dev.zio",
    startYear    := Some(2022),
    homepage     := Some(url("https://zio.dev/zio-sbt")),
    developers := List(
      Developer(
        "khajavi",
        "Milad Khajavi",
        "khajavi@gmail.com",
        url("https://github.com/khajavi")
      )
    ),
    scmInfo := Some(
      ScmInfo(
        homepage.value.get,
        "scm:git:git@github.com:zio/zio-sbt.git"
      )
    )
  )
)

ThisBuild / publishTo := sonatypePublishToBundle.value

lazy val root = project
  .in(file("."))
  .settings(
    name                 := "zio-sbt",
    headerEndYear        := Some(2023),
    publish / skip       := true,
    ciEnabledBranches    := Seq("main"),
    documentationProject := Some(docs),
    supportedScalaVersions := Map(
      (zioSbtWebsite / thisProject).value.id   -> (zioSbtWebsite / crossScalaVersions).value,
      (zioSbtEcosystem / thisProject).value.id -> (zioSbtEcosystem / crossScalaVersions).value,
      (zioSbtCi / thisProject).value.id        -> (zioSbtCi / crossScalaVersions).value,
      (tests / thisProject).value.id           -> (tests / crossScalaVersions).value
    )
  )
  .aggregate(
    zioSbtGithubActions,
    zioSbtWebsite,
    zioSbtEcosystem,
    zioSbtCi,
    tests
  )
  .enablePlugins(ZioSbtCiPlugin)

lazy val tests =
  project
    .in(file("tests"))
    .settings(
      name           := "zio-sbt-tests",
      publish / skip := true,
      headerEndYear  := Some(2023)
    )
    .settings(buildInfoSettings("zio.sbt"))
    .enablePlugins(ZioSbtEcosystemPlugin)

lazy val zioSbtWebsite =
  project
    .in(file("zio-sbt-website"))
    .settings(
      stdSettings(
        name = "zio-sbt-website",
        packageName = "zio.sbt.website",
        scalaVersion = Scala212,
        crossScalaVersions = Seq(Scala212)
      )
    )
    .settings(
      headerEndYear := Some(2023),
      scriptedLaunchOpts := {
        scriptedLaunchOpts.value ++
          Seq("-Xmx1024M", "-Dplugin.version=" + version.value)
      },
      scriptedBufferLog := false
    )
    .enablePlugins(SbtPlugin, ZioSbtEcosystemPlugin)
    .dependsOn(zioSbtGithubActions)

lazy val zioSbtEcosystem =
  project
    .in(file("zio-sbt-ecosystem"))
    .settings(
      stdSettings(
        name = "zio-sbt-ecosystem",
        packageName = "zio.sbt.ecosystem",
        scalaVersion = Scala212,
        crossScalaVersions = Seq(Scala212)
      )
    )
    .settings(
      headerEndYear := Some(2023),
      scriptedLaunchOpts := {
        scriptedLaunchOpts.value ++
          Seq("-Xmx1024M", "-Dplugin.version=" + version.value)
      },
      scriptedBufferLog := false
    )
    .enablePlugins(SbtPlugin, ZioSbtEcosystemPlugin)

lazy val zioSbtCi =
  project
    .in(file("zio-sbt-ci"))
    .settings(
      stdSettings(
        name = "zio-sbt-ci",
        packageName = "zio.sbt.ci",
        scalaVersion = Scala212,
        crossScalaVersions = Seq(Scala212)
      )
    )
    .settings(
      headerEndYear := Some(2023),
      scriptedLaunchOpts := {
        scriptedLaunchOpts.value ++
          Seq("-Xmx1024M", "-Dplugin.version=" + version.value)
      },
      scriptedBufferLog := false
    )
    .enablePlugins(SbtPlugin, ZioSbtEcosystemPlugin)
    .dependsOn(zioSbtGithubActions)

lazy val zioSbtGithubActions =
  project
    .in(file("zio-sbt-githubactions"))
    .settings(
      stdSettings(
        name = "zio-sbt-githubactions",
        packageName = "zio.sbt.githubactions",
        scalaVersion = Scala212,
        crossScalaVersions = Seq( /*Scala211, */ Scala212 /*, Scala213, Scala3*/ )
      )
    )
    .settings(headerEndYear := Some(2023))
    .enablePlugins(ZioSbtEcosystemPlugin)

lazy val docs = project
  .in(file("zio-sbt-docs"))
  .settings(
    moduleName := "zio-sbt-docs",
    scalacOptions -= "-Yno-imports",
    scalacOptions -= "-Xfatal-warnings",
    projectName                                := "ZIO SBT",
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
  .dependsOn(zioSbtWebsite, zioSbtEcosystem)
  .enablePlugins(WebsitePlugin)
