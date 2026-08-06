// The scripted sandbox is not a git repository, but loading this project
// already evaluates docsVersion (which runs `git rev-parse HEAD`), and
// generateReadme runs `git fetch --tags`. Initialize a repo with one commit
// before the settings are evaluated, with the repo itself as remote so
// fetching succeeds offline.
val gitInit = {
  import scala.sys.process._
  if (!file(".git").exists()) {
    Process(Seq("git", "init", ".")).!!
    Process(Seq("git", "remote", "add", "origin", ".")).!!
    Process(
      Seq(
        "git",
        "-c",
        "user.name=scripted",
        "-c",
        "user.email=scripted@example.com",
        "commit",
        "--allow-empty",
        "-m",
        "init"
      )
    ).!!
  }
}

lazy val root = (project in file("."))
  .settings(
    version        := "0.1.0",
    projectName    := "ZIO SBT",
    mainModuleName := "test-project",
    projectStage   := ProjectStage.ProductionReady
  )
  .enablePlugins(WebsitePlugin)
