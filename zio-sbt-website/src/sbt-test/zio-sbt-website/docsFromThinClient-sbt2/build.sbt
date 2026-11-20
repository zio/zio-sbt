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

// A command sent by the thin client carries a `network-*` source, which is what
// `State.isNetworkCommand` checks. Scripted commands carry none, so these commands
// run a task with such a source to exercise the path every sbt 2 user is on.
def fromThinClient(state: State)(run: (Extracted, State) => Unit): State = {
  val exec = Exec("thin-client", Some(CommandSource("network-scripted")))
  run(Project.extract(state), state.copy(currentCommand = Some(exec)))
  state
}

lazy val root = (project in file("."))
  .settings(
    version        := "0.1.0",
    projectName    := "ZIO SBT",
    mainModuleName := "test-project",
    projectStage   := ProjectStage.ProductionReady,
    commands ++= Seq(
      Command.command("compileDocsFromThinClient") { s =>
        fromThinClient(s)((extracted, s1) => extracted.runInputTask(compileDocs, "", s1))
      },
      Command.command("generateReadmeFromThinClient") { s =>
        fromThinClient(s)((extracted, s1) => extracted.runTask(generateReadme, s1))
      }
    )
  )
  .enablePlugins(WebsitePlugin)
