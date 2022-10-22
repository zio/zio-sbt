ThisBuild / version := "0.1.0-SNAPSHOT"
ThisBuild / organization := "dev.zio"
ThisBuild / homepage := Some(url("https://github.com/zio/zio-sbt"))

lazy val website = (project in file("zio-sbt-website"))
  .enablePlugins(SbtPlugin)
  .settings(
    name := "zio-sbt-website"
  )
