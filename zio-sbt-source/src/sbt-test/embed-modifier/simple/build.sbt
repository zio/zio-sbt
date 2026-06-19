enablePlugins(MdocPlugin)

scalaVersion := "2.13.18"

mdocIn  := baseDirectory.value / "src"
mdocOut := baseDirectory.value / "target" / "mdoc"

// Use the locally published version for testing
libraryDependencies += "dev.zio" %% "zio-sbt-source" % "0.5.3+19-5feea33e+20260620-0257-SNAPSHOT"
