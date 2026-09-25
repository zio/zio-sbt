// ciEnableReleaseDrafter defaults to true, so this fixture never touches it - the real-world
// scenario is a repo upgrading zio-sbt-ci without ever mentioning the setting. That repo may
// already hand-maintain release-drafter.yml (release-drafter's own README convention; every ZIO
// repo surveyed in research.md §3 already does). The very first ciGenerateGithubWorkflow run
// after the upgrade must never overwrite that pre-existing file - regression test for the
// "enabled by default" counterpart of the earlier disabled-path deletion bug.

ThisBuild / name := "Test Project"

val handMaintainedWorkflow = "name: Release Drafter\non:\n  push:\n    branches: [main]\n"

lazy val root = (project in file("."))
  .settings(
    version := "0.1",

    TaskKey[Unit]("writeHandMaintainedWorkflow") := {
      val f = baseDirectory.value / ".github" / "workflows" / "release-drafter.yml"
      IO.write(f, handMaintainedWorkflow)
    },

    TaskKey[Unit]("checkWorkflowHandMaintainedPreserved") := {
      val f = baseDirectory.value / ".github" / "workflows" / "release-drafter.yml"
      if (!f.exists) sys.error(s"expected the hand-maintained $f to still exist")
      val actual = IO.read(f)
      if (actual != handMaintainedWorkflow)
        sys.error(
          s"expected the hand-maintained $f to be untouched under the default " +
            s"ciEnableReleaseDrafter (true), but its content changed.\n" +
            s"--- expected ---\n$handMaintainedWorkflow\n--- actual ---\n$actual"
        )
    }
  )
