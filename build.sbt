import BuildHelper._

inThisBuild(
  List(
    name := "zio-sbt",
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
    licenses := Seq("Apache-2.0" -> url(s"${scmInfo.value.map(_.browseUrl).get}/blob/v${version.value}/LICENSE")),
    pgpPassphrase := sys.env.get("PGP_PASSWORD").map(_.toArray),
    pgpPublicRing := file("/tmp/public.asc"),
    pgpSecretRing := file("/tmp/secret.asc")
  )
)

ThisBuild / publishTo := sonatypePublishToBundle.value

lazy val root = project
  .in(file("."))
  .settings(
    name := "zio-schema",
    publish / skip := true
  )
  .aggregate(
    zioSbtWebsite
  )
  
lazy val zioSbtWebsite =
  project
    .in(file("zio-sbt-website"))
    .settings(stdSettings("zio-schema"))
    .settings(crossProjectSettings)
    .settings(buildInfoSettings("zio.sbt"))
    .enablePlugins(SbtPlugin)
