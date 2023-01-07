
lazy val root = (project in file("."))
  .settings(
    version := "0.1",
    TaskKey[Unit]("check") := {
      Testing.eq("versions.Scala212", "2.12.17", versions.Scala212)
      Testing.eq("versions.Scala213", "2.13.10", versions.Scala213)
      Testing.eq("versions.Scala3", "3.2.1", versions.Scala3)
      ()
    }
  )
  .enablePlugins(ZioEcosystemProjectPlugin)
