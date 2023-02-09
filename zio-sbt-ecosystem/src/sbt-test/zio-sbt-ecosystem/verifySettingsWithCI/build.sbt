lazy val root = (project in file("."))
  .settings(
    version := "0.1",
    TaskKey[Unit]("check") := {
      ()
    }
  )
  .enablePlugins(ZioSbtEcosystemPlugin)
