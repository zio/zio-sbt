// `ciCheckGithubWorkflow` scopes its `git diff`/`git ls-files` comparison to `.github/workflows`
// only (see `WorkflowsDir` in ZioSbtCiPlugin.scala). release-drafter.yml (the generated workflow)
// participates in that check like any other generated workflow; .github/release-drafter.yml (the
// scaffolded config, outside .github/workflows/) must not - decision 5/spec.md §8. Directly modeled
// on the `driftCheck` fixture's `gitInit`/hand-edit/commit mechanics using `scala.sys.process.Process`.

import scala.sys.process.Process

ThisBuild / name                   := "Test Project"
ThisBuild / ciEnableReleaseDrafter := true

def run(dir: File, args: String*): Unit = {
  val code = Process("git" +: args, dir).!
  if (code != 0) sys.error(s"git ${args.mkString(" ")} failed with $code")
}

lazy val root = (project in file("."))
  .settings(
    version := "0.1",

    // Scripted runs in a plain directory, so the repository has to be created here. A `baseline`
    // tag marks the clean, fully-generated-and-committed state so it can be returned to later.
    TaskKey[Unit]("gitInit") := {
      val dir = baseDirectory.value
      run(dir, "init", "-q", "-b", "main")
      run(dir, "config", "user.email", "test@example.com")
      run(dir, "config", "user.name", "Test")
      run(dir, "add", "-A")
      run(dir, "commit", "-q", "-m", "initial")
      run(dir, "tag", "baseline")
    },

    // A hand-edited generated WORKFLOW file, committed. This must be caught, same as a hand-edit
    // to ci.yml/auto-approve.yml/auto-merge.yml today.
    TaskKey[Unit]("commitHandEditWorkflow") := {
      val dir  = baseDirectory.value
      val file = dir / ".github" / "workflows" / "release-drafter.yml"
      IO.write(file, IO.read(file) + "\n# hand-edited\n")
      run(dir, "add", "-A")
      run(dir, "commit", "-q", "-m", "hand-edit the generated release-drafter workflow")
    },

    // Back to the clean baseline, discarding the workflow hand-edit committed above.
    TaskKey[Unit]("gitResetToBaseline") := {
      val dir = baseDirectory.value
      run(dir, "reset", "-q", "--hard", "baseline")
    },

    // A hand-edited scaffolded CONFIG file (outside .github/workflows/), committed. Unlike the
    // workflow file, this must NOT be caught by `ciCheckGithubWorkflow` - the key scenario this
    // fixture exists to lock in.
    TaskKey[Unit]("commitHandEditConfig") := {
      val dir  = baseDirectory.value
      val file = dir / ".github" / "release-drafter.yml"
      IO.write(file, IO.read(file) + "\n# hand-edited-config\n")
      run(dir, "add", "-A")
      run(dir, "commit", "-q", "-m", "hand-edit the scaffolded release-drafter config")
    }
  )
