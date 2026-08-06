// Pins the workflows generated from the plugin's own defaults. No `ci*` setting is overridden and
// `ZioSbtCiPlugin` is not enabled explicitly: it declares `trigger = allRequirements`, so merely
// having it on the classpath must be enough to generate a workflow.

ThisBuild / name := "Test Project"

lazy val root = (project in file("."))
  .settings(
    version                          := "0.1",
    TaskKey[Unit]("checkWorkflows")  := Golden.check(
      baseDirectory.value,
      "defaults",
      "ci.yml",
      "auto-approve.yml",
      "auto-merge.yml"
    )
  )
