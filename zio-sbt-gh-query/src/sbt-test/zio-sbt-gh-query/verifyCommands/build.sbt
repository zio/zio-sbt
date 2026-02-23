import GhQueryPlugin.autoImport._
import sbt.Command

lazy val root = (project in file("."))
  .settings(
    version := "0.1",
    ghRepo  := "test-org/test-repo",
    TaskKey[Unit]("check") := {
      val state = Keys.state.value
      val commandNames = state.definedCommands.flatMap { cmd =>
        cmd.nameOption
      }.toSet

      val expected = Set("gh-fetch", "gh-update", "gh-update-db", "gh-rebuild-db", "gh-status", "gh-query")
      val missing = expected -- commandNames
      assert(missing.isEmpty, s"Missing commands: ${missing.mkString(", ")}")

      println(s"All ${expected.size} commands registered!")
      println("Command registration check passed!")
    }
  )
  .enablePlugins(GhQueryPlugin)
