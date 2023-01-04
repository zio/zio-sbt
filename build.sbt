sbtPlugin         := true
publishMavenStyle := true

enablePlugins(ZioEcosystemProjectPlugin)
addCommand(List("scripted"), "testPlugin", "Runs the scripted SBT plugin tests.")

inThisBuild(
  List(
    organization  := "dev.zio",
    startYear     := Some(2022),
    headerEndYear := Some(2023),
    homepage      := Some(url("https://zio.dev/zio-sbt")),
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
    .enablePlugins(ZioEcosystemProjectPlugin)

lazy val zioSbtWebsite =
  project
    .in(file("zio-sbt-website"))
    .settings(buildInfoSettings("zio.sbt"))
    .settings(addCommand(List("scripted"), "testPlugin", "Runs the scripted SBT plugin tests."))
    .settings(
      name               := "zio-sbt-website",
      headerEndYear      := Some(2023),
      crossScalaVersions := Seq.empty,
      scalaVersion       := versions.Scala212,
      scriptedLaunchOpts := {
        scriptedLaunchOpts.value ++
          Seq("-Xmx1024M", "-Dplugin.version=" + version.value)
      },
      scriptedBufferLog := false
    )
    .enablePlugins(SbtPlugin, ZioEcosystemProjectPlugin)

lazy val zioSbtEcosystem =
  project
    .in(file("zio-sbt-ecosystem"))
    .settings(buildInfoSettings("zio.sbt"))
    .settings(addCommand(List("scripted"), "testPlugin", "Runs the scripted SBT plugin tests."))
    .settings(
      name               := "zio-sbt-ecosystem",
      headerEndYear      := Some(2023),
      crossScalaVersions := Seq.empty,
      needsZio           := false,
      scalaVersion       := versions.Scala212,
      scriptedLaunchOpts := {
        scriptedLaunchOpts.value ++
          Seq("-Xmx1024M", "-Dplugin.version=" + version.value)
      },
      scriptedBufferLog := false
    )
    .enablePlugins(SbtPlugin, ZioEcosystemProjectPlugin)

lazy val docs = project
  .in(file("zio-sbt-docs"))
  .settings(
    publish / skip := true,
    headerEndYear  := Some(2023),
    moduleName     := "zio-sbt-docs",
    scalacOptions -= "-Yno-imports",
    scalacOptions -= "-Xfatal-warnings",
    projectName := "ZIO SBT",
    badgeInfo := Some(
      BadgeInfo(
        artifact = "zio-sbt-website_2.12",
        projectStage = ProjectStage.ProductionReady
      )
    ),
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
  .dependsOn(root)
  .enablePlugins(ZioEcosystemProjectPlugin, WebsitePlugin)
