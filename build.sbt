import zio.sbt.Commands._
sbtPlugin         := true
publishMavenStyle := true

enablePlugins(EcosystemPlugin)

//addCommand(
//  (ComposableCommand
//    .make(
//      "project zioSbtEcosystem"
//    ) >> "scripted" >> "project zioSbtWebsite" >> "scripted" >> "project root") ?? ("testPlugins", "Runs the scripted SBT plugin tests.")
//)

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
    ),
    pgpPassphrase := sys.env.get("PGP_PASSWORD").map(_.toArray),
    pgpPublicRing := file("/tmp/public.asc"),
    pgpSecretRing := file("/tmp/secret.asc")
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
    .settings(stdSettings(V.Scala3, V.Scala213))
    .settings(buildInfoSettings("zio.sbt"))
    .settings(
      name             := "zio-sbt-website",
      headerEndYear    := Some(2023),
      buildInfoPackage := "zio.sbt.website",
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
    .settings(stdSettings(V.Scala3, V.Scala213))
    .settings(buildInfoSettings("zio.sbt.ecosystem"))
    .settings(
      name          := "zio-sbt-ecosystem",
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
    ScalaUnidoc / unidoc / unidocProjectFilter := inProjects(zioSbtEcosystem, zioSbtWebsite),
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
