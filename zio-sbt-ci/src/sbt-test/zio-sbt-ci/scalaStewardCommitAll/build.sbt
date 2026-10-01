// Scala Steward's post-update hook runs `ciGenerateGithubWorkflow` and commits with
// `git commit --all`, which skips untracked files. A workflow file the task creates for the
// first time must therefore be registered with git (intent-to-add) by the task itself, or
// the "Regenerate GitHub Actions workflow" commit misses it and `ciCheckGithubWorkflow` fails.

import scala.sys.process.Process

ThisBuild / name := "Test Project"

def run(dir: File, args: String*): Unit = {
  val code = Process("git" +: args, dir).!
  if (code != 0) sys.error(s"git ${args.mkString(" ")} failed with $code")
}

lazy val root = (project in file("."))
  .settings(
    version := "0.1",
    TaskKey[Unit]("gitInit") := {
      val dir = baseDirectory.value
      run(dir, "init", "-q", "-b", "main")
      run(dir, "config", "user.email", "test@example.com")
      run(dir, "config", "user.name", "Test")
      run(dir, "add", "-A")
      run(dir, "commit", "-q", "-m", "initial")
    },
    TaskKey[Unit]("removeScalaStewardWorkflow") := {
      val dir = baseDirectory.value
      run(dir, "rm", "-q", "-f", ".github/workflows/scala-steward.yml")
      run(dir, "commit", "-q", "-m", "drop scala-steward workflow")
    },
    // Exactly what Scala Steward's FileGitAlg.commitAll does.
    TaskKey[Unit]("commitAll") := {
      run(baseDirectory.value, "commit", "--all", "-q", "-m", "Regenerate GitHub Actions workflow")
    },
    TaskKey[Unit]("checkWorkflowCommitted") := {
      val dir  = baseDirectory.value
      val out  = Process(Seq("git", "ls-tree", "-r", "--name-only", "HEAD"), dir).!!
      if (!out.linesIterator.contains(".github/workflows/scala-steward.yml"))
        sys.error("scala-steward.yml was not part of the `git commit --all` commit")
    }
  )
