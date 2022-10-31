import sbtcrossproject.CrossPlugin.autoImport.crossProject
import BuildHelper.{ crossProjectSettings, _ }
import org.scalajs.sbtplugin.ScalaJSPlugin.autoImport.scalaJSUseMainModuleInitializer

sbtPlugin := true

inThisBuild(
  List(
    name := "zio-sbt",
    version := "0.0.1-SNAPSHOT",
    organization := "dev.zio",
    homepage := Some(url("https://github.com/zio/zio-sbt")),
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
    licenses := Seq(
      "Apache-2.0" -> url(
        s"${scmInfo.value.map(_.browseUrl).get}/blob/v${version.value}/LICENSE"
      )
    ),
    pgpPassphrase := sys.env.get("PGP_PASSWORD").map(_.toArray),
    pgpPublicRing := file("/tmp/public.asc"),
    pgpSecretRing := file("/tmp/secret.asc")
  )
)

ThisBuild / publishTo := sonatypePublishToBundle.value

addCommandAlias("prepare", "fix; fmt")
addCommandAlias("fmt", "all scalafmtSbt scalafmtAll")
addCommandAlias("fmtCheck", "all scalafmtSbtCheck scalafmtCheckAll")
addCommandAlias("fix", "scalafixAll")
addCommandAlias("fixCheck", "scalafixAll --check")

lazy val root = project
  .in(file("."))
  .settings(
    name := "zio-sbt",
    publish / skip := true
  )
  .aggregate(
    zioSbtWebsite,
    tests
  )

lazy val tests =
  project
    .in(file("tests"))
    .settings(stdSettings("zio-sbt-tests"))
    .settings(publish / skip := true)
    .settings(buildInfoSettings("zio.sbt"))

lazy val zioSbtWebsite =
  project
    .in(file("zio-sbt-website"))
    .settings(stdSettings("zio-sbt-website"))
    .settings(buildInfoSettings("zio.sbt"))
    .enablePlugins(SbtPlugin)

lazy val docs = project
  .in(file("zio-sbt-docs"))
  .settings(
    publish / skip := true,
    mdocVariables := Map(
      "SNAPSHOT_VERSION" -> version.value,
      "RELEASE_VERSION"  -> previousStableVersion.value.getOrElse(
        "can't find release"
      ),
      "ORG"              -> organization.value,
      "NAME"             -> (root / name).value,
      "CROSS_VERSIONS"   -> (root / crossScalaVersions).value.mkString(", ")
    ),
    moduleName := "zio-sbt-docs",
    scalacOptions -= "-Yno-imports",
    scalacOptions -= "-Xfatal-warnings",
    libraryDependencies ++= Seq(
      "dev.zio" %% "zio" % zioVersion
    ),
    ScalaUnidoc / unidoc / unidocProjectFilter := inProjects(root),
    ScalaUnidoc / unidoc / target := (LocalRootProject / baseDirectory).value / "website" / "static" / "api",
    cleanFiles += (ScalaUnidoc / unidoc / target).value,
    docusaurusCreateSite := docusaurusCreateSite
      .dependsOn(Compile / unidoc)
      .value,
    docusaurusPublishGhpages := docusaurusPublishGhpages
      .dependsOn(Compile / unidoc)
      .value
  )
  .dependsOn(root)
  .enablePlugins(MdocPlugin, DocusaurusPlugin, ScalaUnidocPlugin)
