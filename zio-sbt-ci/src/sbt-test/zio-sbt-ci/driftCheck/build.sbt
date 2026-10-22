// `ciCheckGithubWorkflow` regenerates the workflows and then asks git whether the result differs
// from what is committed.
//
// The comparison must be scoped to the workflow directory. It previously ran a whole-tree
// `git diff`, so any unrelated edit in the working copy was reported as "the ci.yml workflow is not
// up-to-date" - a misleading failure for anyone running `sbt lint` locally with work in progress.

import scala.sys.process.Process

ThisBuild / name := "Test Project"

def run(dir: File, args: String*): Unit = {
  val code = Process("git" +: args, dir).!
  if (code != 0) sys.error(s"git ${args.mkString(" ")} failed with $code")
}

lazy val root = (project in file("."))
  .settings(
    version := "0.1",

    // Scripted runs in a plain directory, so the repository has to be created here.
    TaskKey[Unit]("gitInit") := {
      val dir = baseDirectory.value
      run(dir, "init", "-q", "-b", "main")
      run(dir, "config", "user.email", "test@example.com")
      run(dir, "config", "user.name", "Test")
      run(dir, "add", "-A")
      run(dir, "commit", "-q", "-m", "initial")
    },

    // An edit that has nothing to do with the workflows.
    TaskKey[Unit]("dirtyUnrelated") := {
      val dir = baseDirectory.value
      IO.write(dir / "notes.md", "scratch\n")
      run(dir, "add", "-A")
    },

    // A hand-edited workflow, committed. Committing matters: the check regenerates before
    // comparing, so an uncommitted hand-edit is simply overwritten and there is nothing to detect.
    TaskKey[Unit]("commitHandEdit") := {
      val dir  = baseDirectory.value
      val file = dir / ".github" / "workflows" / "ci.yml"
      IO.write(file, IO.read(file) + "\n# hand-edited\n")
      run(dir, "add", "-A")
      run(dir, "commit", "-q", "-m", "hand-edit the generated workflow")
    }
  )
