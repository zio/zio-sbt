// Exercises every non-default release-drafter setting in one fixture (mirroring `settingsOverrides`'s
// role for the main workflow), so the case-class -> YAML encoding path for `ReleaseDrafterCategory`,
// `ReleaseDrafterVersionResolver`, and `ReleaseDrafterAutolabelerRule` all get golden coverage in one
// place rather than three separate fixtures.

import zio.sbt.githubactions.{Branch, ReleaseDrafterAutolabelerRule, ReleaseDrafterCategory, ReleaseDrafterVersionResolver}

ThisBuild / name := "Test Project"

ThisBuild / ciEnableReleaseDrafter := true
ThisBuild / ciReleaseDrafterBranch := Some(Branch.Named("series/2.x"))
ThisBuild / ciReleaseDrafterCategories := Seq(
  ReleaseDrafterCategory("Features", Seq("feature", "enhancement")),
  ReleaseDrafterCategory("Fixes", Seq("bug"))
)
ThisBuild / ciReleaseDrafterExcludeLabels := Seq("skip-changelog", "internal")
ThisBuild / ciReleaseDrafterVersionResolver := Some(
  ReleaseDrafterVersionResolver(major = Seq("breaking"), minor = Seq("feature"), patch = Seq("fix"))
)
ThisBuild / ciReleaseDrafterAutolabeler := Seq(
  ReleaseDrafterAutolabelerRule(label = "documentation", files = Seq("*.md", "docs/**")),
  ReleaseDrafterAutolabelerRule(label = "feature", branch = Seq("feature/.+"))
)

lazy val root = (project in file("."))
  .settings(
    version                        := "0.1",
    TaskKey[Unit]("checkWorkflows") := Golden.check(
      baseDirectory.value,
      "releaseDrafterSettings",
      "release-drafter.yml"
    ),
    TaskKey[Unit]("checkConfig")    := Golden.checkFile(
      baseDirectory.value,
      "releaseDrafterSettings",
      ".github/release-drafter.yml"
    )
  )
