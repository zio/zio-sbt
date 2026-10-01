ThisBuild / name := "Test Project"

lazy val root = (project in file("."))
  .settings(
    version                       := "0.1",
    TaskKey[Unit]("checkDependabot") := Golden.checkFile(
      baseDirectory.value,
      "dependabot",
      ".github/dependabot.yml"
    )
  )
