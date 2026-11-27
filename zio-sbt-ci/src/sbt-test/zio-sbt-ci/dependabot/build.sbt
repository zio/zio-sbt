ThisBuild / name := "Test Project"

lazy val root = (project in file("."))
  .settings(
    version                       := "0.1",
    TaskKey[Unit]("checkDependabot") := Golden.checkFile(
      baseDirectory.value,
      "dependabot",
      ".github/dependabot.yml"
    ),
    TaskKey[Unit]("checkNoExcludePaths") := {
      val f = baseDirectory.value / ".github" / "dependabot.yml"
      if (IO.read(f).contains("exclude-paths")) sys.error(s"expected no exclude-paths in $f")
    }
  )
