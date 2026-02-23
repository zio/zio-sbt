import GhQueryPlugin.autoImport._

lazy val root = (project in file("."))
  .settings(
    version := "0.1",
    ghRepo  := "zio/zio-sbt",
    TaskKey[Unit]("check") := {
      val repo = ghRepo.value
      assert(repo == "zio/zio-sbt", s"Expected ghRepo to be zio/zio-sbt, got $repo")

      val dir = ghDir.value
      assert(dir == file(".zio-sbt"), s"Expected default ghDir to be .zio-sbt, got $dir")

      println("All checks passed!")
    }
  )
  .enablePlugins(GhQueryPlugin)
