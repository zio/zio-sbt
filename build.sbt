import V.Scala211
import zio.sbt.Commands._
sbtPlugin         := true
publishMavenStyle := true

enablePlugins(EcosystemPlugin)

addCommand(
  (ComposableCommand
    .make(
      "project zioSbtEcosystem"
    ) >> "scripted" >> "project zioSbtWebsite" >> "scripted" >> "project root") ?? ("testPlugins", "Runs the scripted SBT plugin tests.")
)

ThisBuild / scalaVersion       := V.Scala212
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
    name           := "zio-sbt",
    headerEndYear  := Some(2023),
    publish / skip := true
  )
  .aggregate(
    zioSbtWebsite,
    zioSbtEcosystem,
    tests
  )

lazy val tests =
  project
    .in(file("tests"))
    .settings(
      name           := "zio-sbt-tests",
      publish / skip := true,
      headerEndYear  := Some(2023)
    )
    .settings(buildInfoSettings("zio.sbt"))
    .enablePlugins(EcosystemPlugin)

lazy val zioSbtWebsite =
  project
    .in(file("zio-sbt-website"))
    .settings(
      stdSettings(
        name = "zio-sbt-website",
        packageName = "zio.sbt.website",
        scalaVersion = V.Scala212,
        crossScalaVersions = Seq(V.Scala212)
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
    .enablePlugins(SbtPlugin, EcosystemPlugin)

lazy val zioSbtEcosystem =
  project
    .in(file("zio-sbt-ecosystem"))
    .settings(
      stdSettings(
        name = "zio-sbt-ecosystem",
        packageName = "zio.sbt.ecosystem",
        scalaVersion = V.Scala212,
        crossScalaVersions = Seq(V.Scala212)
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
    .enablePlugins(SbtPlugin, EcosystemPlugin)

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
         |""".stripMargin,
    supportedScalaVersions := List(Scala211)
  )
  .dependsOn(zioSbtWebsite, zioSbtEcosystem)
  .enablePlugins(WebsitePlugin)
