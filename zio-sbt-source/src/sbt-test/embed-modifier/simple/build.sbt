enablePlugins(MdocPlugin)

scalaVersion := "2.13.16"

mdocIn  := baseDirectory.value / "src"
mdocOut := baseDirectory.value / "target" / "mdoc"

libraryDependencies += "dev.zio" %% "zio-sbt-source" % sys.props.getOrElse("plugin.version", "0.1.0-SNAPSHOT")
