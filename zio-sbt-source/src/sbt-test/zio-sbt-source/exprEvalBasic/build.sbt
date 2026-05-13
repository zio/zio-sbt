lazy val root = project
  .in(file("."))
  .settings(
    name := "expr-eval-test",
    scalaVersion := "2.13.16",
    libraryDependencies ++= Seq(
      "dev.zio" %% "zio" % "2.1.22",
      "dev.zio" %% "zio-sbt-source" % sys.props.getOrElse("plugin.version", "0.5.1")
    )
  )
