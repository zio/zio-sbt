enablePlugins(MdocPlugin)

scalaVersion := "3.10.0"

mdocIn  := baseDirectory.value / "src"
mdocOut := baseDirectory.value / "target" / "mdoc"
