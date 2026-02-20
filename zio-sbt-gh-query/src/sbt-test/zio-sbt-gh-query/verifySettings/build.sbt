import GhQueryPlugin.autoImport._

lazy val root = (project in file("."))
  .settings(
    version := "0.1",
    TaskKey[Unit]("check") := {
      val repo = ghRepo.value
      assert(repo == "zio/zio-blocks", s"Expected default ghRepo to be zio/zio-blocks, got $repo")

      val dir = ghDir.value
      assert(dir == file(".zio-sbt"), s"Expected default ghDir to be .zio-sbt, got $dir")

      println("All checks passed!")
    }
  )
  .enablePlugins(GhQueryPlugin)
