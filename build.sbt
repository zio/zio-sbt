ThisBuild / version := "0.0.1-SNAPSHOT"
ThisBuild / organization := "dev.zio"
ThisBuild / homepage := Some(url("https://github.com/khajavi/zio-sbt"))

lazy val website =
  project
    .in(file("zio-sbt-website"))
    .enablePlugins(SbtPlugin)
    .settings(name := "zio-sbt-website")
