// Pins regenerate-workflows.yml as generated under the plugin's defaults (ciEnableRegenerateWorkflows
// defaults to true; the bots default to Dependabot, Renovate and zio-scala-steward).

ThisBuild / name := "Test Project"

lazy val root = (project in file("."))
  .settings(
    version                        := "0.1",
    TaskKey[Unit]("checkWorkflows") := Golden.check(
      baseDirectory.value,
      "regenerateWorkflows",
      "regenerate-workflows.yml"
    )
  )
