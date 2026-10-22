// Pins the workflows generated with `ciEnableDeployPreview := true`: the build job gains
// the website-artifact/PR-metadata upload steps, and a fourth workflow file, deploy-preview.yml,
// is generated alongside the usual three.

ThisBuild / name := "Test Project"

ThisBuild / ciEnableDeployPreview := true

lazy val root = (project in file("."))
  .settings(
    version                         := "0.1",
    TaskKey[Unit]("checkWorkflows") := Golden.check(
      baseDirectory.value,
      "netlifyDeployPreview",
      "ci.yml",
      "auto-approve.yml",
      "auto-merge.yml",
      "deploy-preview.yml"
    )
  )
