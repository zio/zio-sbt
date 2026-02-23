import GhQueryPlugin.autoImport._

lazy val root = (project in file("."))
  .settings(
    version := "0.1",
    ghRepo  := "my-org/my-repo",
    ghDir   := file("custom-data-dir"),
    TaskKey[Unit]("check") := {
      val repo = ghRepo.value
      assert(repo == "my-org/my-repo", s"Expected ghRepo to be my-org/my-repo, got $repo")

      val dir = ghDir.value
      assert(dir == file("custom-data-dir"), s"Expected ghDir to be custom-data-dir, got $dir")

      println("All custom settings checks passed!")
    }
  )
  .enablePlugins(GhQueryPlugin)
