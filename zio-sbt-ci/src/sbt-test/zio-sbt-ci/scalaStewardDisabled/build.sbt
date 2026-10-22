ThisBuild / name := "Test Project"

ThisBuild / ciEnableScalaSteward := false

lazy val root = (project in file("."))
  .settings(
    version                     := "0.1",
    TaskKey[Unit]("checkAbsent") := {
      val f = baseDirectory.value / ".github" / "workflows" / "scala-steward.yml"
      if (f.exists) sys.error(s"expected no $f when ciEnableScalaSteward is false")
    }
  )
