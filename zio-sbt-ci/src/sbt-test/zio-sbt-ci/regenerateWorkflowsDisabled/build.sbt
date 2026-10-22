ThisBuild / name := "Test Project"

ThisBuild / ciEnableRegenerateWorkflows := false

lazy val root = (project in file("."))
  .settings(
    version                     := "0.1",
    TaskKey[Unit]("checkAbsent") := {
      val f = baseDirectory.value / ".github" / "workflows" / "regenerate-workflows.yml"
      if (f.exists) sys.error(s"expected no $f when ciEnableRegenerateWorkflows is false")
    }
  )
