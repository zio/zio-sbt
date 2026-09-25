// Proves the middle tier of releaseDrafterWorkflow's branch fallback (spec.md §5.1):
// `ciReleaseDrafterBranch` (unset here) -> first of `ciEnabledBranches` (non-empty here,
// and deliberately NOT starting with "main") -> literal "main". `ciEnabledBranches` is
// ordered `series/2.x` first specifically so this test would fail if the fallback ever
// regressed to jumping straight past `ciEnabledBranches` to the literal "main" default -
// a bug that a `Seq("main", ...)` ordering would hide.

ThisBuild / name                   := "Test Project"
ThisBuild / ciEnableReleaseDrafter := true
ThisBuild / ciEnabledBranches      := Seq("series/2.x", "main")

lazy val root = (project in file("."))
  .settings(
    version                         := "0.1",
    TaskKey[Unit]("checkWorkflows") := Golden.check(
      baseDirectory.value,
      "releaseDrafterBranchFallback",
      "release-drafter.yml"
    )
  )
