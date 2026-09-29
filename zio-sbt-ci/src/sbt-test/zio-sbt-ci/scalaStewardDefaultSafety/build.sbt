// A hand-maintained scala-steward.yml, planted before ciGenerateGithubWorkflow ever runs, must
// survive untouched under the plugin's actual default (ciEnableScalaSteward := true) - no setting
// in this fixture ever mentions it. This is the real upgrade path: every ZIO-ecosystem repo
// surveyed (zio/zio, zio-schema, zio-json, zio-kafka, zio-http, zio-config) plus this monorepo
// already hand-maintains this exact file today.

ThisBuild / name := "Test Project"

val handMaintainedWorkflow =
  "name: Scala Steward\non:\n  schedule:\n    - cron: '0 0 * * *'\n  workflow_dispatch: {}\n"

lazy val root = (project in file("."))
  .settings(
    version := "0.1",

    TaskKey[Unit]("writeHandMaintainedWorkflow") := {
      val f = baseDirectory.value / ".github" / "workflows" / "scala-steward.yml"
      IO.write(f, handMaintainedWorkflow)
    },

    TaskKey[Unit]("checkWorkflowHandMaintainedPreserved") := {
      val f = baseDirectory.value / ".github" / "workflows" / "scala-steward.yml"
      if (!f.exists) sys.error(s"expected the hand-maintained $f to still exist")
      val actual = IO.read(f)
      if (actual != handMaintainedWorkflow)
        sys.error(
          s"expected the hand-maintained $f to be untouched under the default " +
            s"ciEnableScalaSteward (true), but its content changed.\n" +
            s"--- expected ---\n$handMaintainedWorkflow\n--- actual ---\n$actual"
        )
    }
  )
