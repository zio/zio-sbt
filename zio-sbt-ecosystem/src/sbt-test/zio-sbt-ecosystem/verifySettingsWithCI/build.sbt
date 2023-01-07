
lazy val root = (project in file("."))
  .settings(
    version := "0.1",
    TaskKey[Unit]("check") := {
      // These values must match the values in .github/workflows/ci.yml.
      Testing.eq("versions.Scala212", "2.12.16", versions.Scala212)
      Testing.eq("versions.Scala213", "2.13.9", versions.Scala213)
      Testing.eq("versions.Scala3", "3.2.0", versions.Scala3)
      ()
    }
  )
  .enablePlugins(ZioEcosystemProjectPlugin)
