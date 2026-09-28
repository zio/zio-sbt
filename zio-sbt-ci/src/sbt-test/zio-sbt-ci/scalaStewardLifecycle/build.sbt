// Enable/disable/hand-edit-preservation lifecycle for `ciEnableScalaSteward`, following
// `releaseDrafterLifecycle`'s pattern of ad hoc `TaskKey`s doing direct `IO.read`/`.exists`
// assertions, and `workflowTriggers`'s precedent of `set ThisBuild / <key> := ...` taking effect
// for the very next task invocation in the same scripted session (no `reload` needed).

ThisBuild / name := "Test Project"

lazy val root = (project in file("."))
  .settings(
    version := "0.1",

    TaskKey[Unit]("checkWorkflowExists") := {
      val f = baseDirectory.value / ".github" / "workflows" / "scala-steward.yml"
      if (!f.exists) sys.error(s"expected $f to exist")
    },
    TaskKey[Unit]("checkWorkflowAbsent") := {
      val f = baseDirectory.value / ".github" / "workflows" / "scala-steward.yml"
      if (f.exists) sys.error(s"expected $f to be absent")
    },
    TaskKey[Unit]("checkConfigExists") := {
      val f = baseDirectory.value / ".scala-steward.conf"
      if (!f.exists) sys.error(s"expected $f to exist")
    },
    TaskKey[Unit]("handEditConfig") := {
      val f = baseDirectory.value / ".scala-steward.conf"
      IO.write(f, "# hand-edited\nupdates.limit = 1\n")
    },
    TaskKey[Unit]("checkConfigHandEditPreserved") := {
      val f        = baseDirectory.value / ".scala-steward.conf"
      val expected = "# hand-edited\nupdates.limit = 1\n"
      if (!f.exists) sys.error(s"expected the hand-edited $f to still exist")
      val actual = IO.read(f)
      if (actual != expected)
        sys.error(s"expected $f to be untouched after regeneration.\n--- expected ---\n$expected\n--- actual ---\n$actual")
    }
  )
