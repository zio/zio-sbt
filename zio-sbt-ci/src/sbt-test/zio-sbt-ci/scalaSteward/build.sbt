// Pins the workflow generated under the plugin's actual defaults (ciEnableScalaSteward defaults
// to true, and every setting here is left at its default) - the common case for the 6+
// ZIO-ecosystem repos this feature targets.

ThisBuild / name := "Test Project"

lazy val root = (project in file("."))
  .settings(
    version                        := "0.1",
    TaskKey[Unit]("checkWorkflows") := Golden.check(
      baseDirectory.value,
      "scalaSteward",
      "ci.yml", "auto-approve.yml", "auto-merge.yml", "scala-steward.yml"
    ),
    TaskKey[Unit]("checkConfigAbsent") := {
      val f = baseDirectory.value / ".scala-steward.conf"
      if (f.exists) sys.error(s"expected no $f under an all-default ciScalaStewardConfig, but it exists")
    }
  )
