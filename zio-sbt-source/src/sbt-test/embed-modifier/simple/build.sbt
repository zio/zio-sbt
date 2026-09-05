enablePlugins(MdocPlugin)

scalaVersion := "3.9.0"

mdocIn  := baseDirectory.value / "src"
mdocOut := baseDirectory.value / "target" / "mdoc"
