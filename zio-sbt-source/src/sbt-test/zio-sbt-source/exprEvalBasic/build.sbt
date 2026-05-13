lazy val root = project
  .in(file("."))
  .settings(
    name := "expr-eval-test",
    scalaVersion := "2.13.16",
    libraryDependencies ++= Seq(
      "dev.zio" %% "zio" % "2.1.22"
    ),
    libraryDependencies ++= {
      sys.props.get("plugin.version") match {
        case Some(v) => Seq("dev.zio" %% "zio-sbt-source" % v)
        case None => Seq.empty
      }
    }
  )
