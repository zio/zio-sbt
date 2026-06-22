enablePlugins(MdocPlugin)

scalaVersion := "2.13.18"

mdocIn  := baseDirectory.value / "src"
mdocOut := baseDirectory.value / "target" / "mdoc"
