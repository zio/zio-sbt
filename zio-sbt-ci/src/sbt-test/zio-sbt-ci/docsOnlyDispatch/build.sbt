// The `docs-only` input of the CI workflow (zio/zio-sbt#869).
//
// A build that sets its own `ciWorkflowTriggers` still gets the input on its `workflow_dispatch`
// trigger, after any inputs it declares itself, so the "Run workflow" form always offers the switch
// the release jobs check for. A workflow without a `release-docs` job has nothing for the switch to
// run, so it gets no input.

import zio.sbt.githubactions.{Branch, Input, Trigger}

ThisBuild / name := "Test Project"

inThisBuild(
  List(
    ciEnabledBranches := Seq("main"),
    ciWorkflowTriggers := Seq(
      Trigger.WorkflowDispatch(Seq(Input("dry-run", "Only print what would be released", false, "false"))),
      Trigger.Release(Seq("published")),
      Trigger.Push(branches = Seq(Branch.Named("main")))
    )
  )
)

lazy val root = (project in file("."))
  .settings(
    version                         := "0.1",
    TaskKey[Unit]("checkWorkflows") := Golden.check(baseDirectory.value, "docsOnlyDispatch", "ci.yml"),
    TaskKey[Unit]("checkNoDocsOnlyInput") := {
      val ci = IO.read(baseDirectory.value / ".github" / "workflows" / "ci.yml")
      if (ci.contains("      docs-only:"))
        sys.error("ci.yml declares a docs-only input although it has no release-docs job:\n" + ci)
      if (!ci.contains("      dry-run:"))
        sys.error("ci.yml lost the build's own dry-run input:\n" + ci)
    }
  )
