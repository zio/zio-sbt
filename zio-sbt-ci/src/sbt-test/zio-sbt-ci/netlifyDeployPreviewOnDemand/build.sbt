// Pins the workflows generated with `ciDeployPreviewMode := DeployPreviewMode.OnDemand`: the build
// job is unchanged from the automatic mode, but deploy-preview.yml posts a "Deploy preview"
// checkbox comment on the pull request and deploys only when that checkbox is ticked.

import zio.sbt.githubactions.DeployPreviewMode

ThisBuild / name := "Test Project"

ThisBuild / ciEnableDeployPreview := true
ThisBuild / ciDeployPreviewMode   := DeployPreviewMode.OnDemand

lazy val root = (project in file("."))
  .settings(
    version                         := "0.1",
    TaskKey[Unit]("checkWorkflows") := Golden.check(
      baseDirectory.value,
      "netlifyDeployPreviewOnDemand",
      "ci.yml",
      "auto-approve.yml",
      "auto-merge.yml",
      "deploy-preview.yml"
    )
  )
