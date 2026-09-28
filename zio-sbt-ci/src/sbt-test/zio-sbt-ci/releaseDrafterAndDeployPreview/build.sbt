// Proves the two independent opt-in generators (deploy-preview.yml and release-drafter.yml) don't
// interact: with both `ciEnableDeployPreview` and `ciEnableReleaseDrafter` set to true, all five
// files are generated in one `ciGenerateGithubWorkflow` run, and deploy-preview.yml's content is
// unaffected by release-drafter also being enabled - it must match the `netlifyDeployPreview`
// fixture's own golden exactly (that golden is reused here verbatim, not re-recorded).

ThisBuild / name := "Test Project"

ThisBuild / ciEnableDeployPreview  := true
ThisBuild / ciEnableReleaseDrafter := true

lazy val root = (project in file("."))
  .settings(
    version                        := "0.1",
    TaskKey[Unit]("checkWorkflows") := Golden.check(
      baseDirectory.value,
      "releaseDrafterAndDeployPreview",
      "ci.yml", "auto-approve.yml", "auto-merge.yml", "deploy-preview.yml", "release-drafter.yml"
    )
  )
