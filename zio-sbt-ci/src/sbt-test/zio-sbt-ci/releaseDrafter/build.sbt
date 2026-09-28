// Pins the workflows generated with `ciEnableReleaseDrafter := true`: a fourth workflow file,
// release-drafter.yml, is generated alongside the usual three, and .github/release-drafter.yml
// (the action's own config, outside .github/workflows/) is scaffolded from the plugin defaults.

ThisBuild / name := "Test Project"

ThisBuild / ciEnableReleaseDrafter := true

lazy val root = (project in file("."))
  .settings(
    version                        := "0.1",
    TaskKey[Unit]("checkWorkflows") := Golden.check(
      baseDirectory.value,
      "releaseDrafter",
      "ci.yml", "auto-approve.yml", "auto-merge.yml", "release-drafter.yml"
    ),
    TaskKey[Unit]("checkConfig")    := Golden.checkFile(
      baseDirectory.value,
      "releaseDrafter",
      ".github/release-drafter.yml"
    )
  )
