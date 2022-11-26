import sbtcrossproject.CrossPlugin.autoImport.crossProject
import org.scalajs.sbtplugin.ScalaJSPlugin.autoImport.scalaJSUseMainModuleInitializer

sbtPlugin         := true
publishMavenStyle := true

enablePlugins(ZioEcosystemProjectPlugin)
addCommand(List("scripted"), "testPlugin", "Runs the scripted SBT plugin tests.")

inThisBuild(
  List(
    organization := "dev.zio",
    startYear    := Some(2022),
    homepage     := Some(url("https://github.com/zio/zio-sbt")),
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
    .settings(Seq(name := "zio-sbt-tests", publish / skip := true))
    .settings(buildInfoSettings("zio.sbt"))
    .enablePlugins(ZioEcosystemProjectPlugin)

lazy val zioSbtWebsite =
  project
    .in(file("zio-sbt-website"))
    .settings(buildInfoSettings("zio.sbt"))
    .settings(addCommand(List("scripted"), "testPlugin", "Runs the scripted SBT plugin tests."))
    .settings(
      name               := "zio-sbt-website",
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
    mdocVariables := Map(
      "SNAPSHOT_VERSION" -> version.value,
      "RELEASE_VERSION" -> previousStableVersion.value.getOrElse(
        "can't find release"
      ),
      "ORG"            -> organization.value,
      "NAME"           -> (root / name).value,
      "CROSS_VERSIONS" -> (root / crossScalaVersions).value.mkString(", ")
    ),
    moduleName := "zio-sbt-docs",
    scalacOptions -= "-Yno-imports",
    scalacOptions -= "-Xfatal-warnings",
    ScalaUnidoc / unidoc / unidocProjectFilter := inProjects(root),
    ScalaUnidoc / unidoc / target              := (LocalRootProject / baseDirectory).value / "website" / "static" / "api",
    cleanFiles += (ScalaUnidoc / unidoc / target).value,
    docusaurusCreateSite := docusaurusCreateSite
      .dependsOn(Compile / unidoc)
      .value,
    docusaurusPublishGhpages := docusaurusPublishGhpages
      .dependsOn(Compile / unidoc)
      .value
  )
  .dependsOn(root)
  .enablePlugins(ZioEcosystemProjectPlugin, MdocPlugin, DocusaurusPlugin, ScalaUnidocPlugin)
