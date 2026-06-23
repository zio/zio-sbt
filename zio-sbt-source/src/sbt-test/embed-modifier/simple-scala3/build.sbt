enablePlugins(MdocPlugin)

scalaVersion := "3.3.8"

mdocIn  := baseDirectory.value / "src"
mdocOut := baseDirectory.value / "target" / "mdoc"

libraryDependencies += "dev.zio" %% "zio-sbt-source" % sys.props("project.version")
