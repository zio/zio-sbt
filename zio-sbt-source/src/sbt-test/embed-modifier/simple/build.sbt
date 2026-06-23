enablePlugins(MdocPlugin)

scalaVersion := "2.13.18"

mdocIn  := baseDirectory.value / "src"
mdocOut := baseDirectory.value / "target" / "mdoc"

libraryDependencies += "dev.zio" %% "zio-sbt-source" % sys.props.getOrElse("project.version", "0+")
