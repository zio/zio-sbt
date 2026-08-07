// Narrowing the events the workflow runs on.
//
// The default set includes `workflow_dispatch`, which is not always wanted: a release job
// conditioned on "not a pull request" will happily publish from a manual run. Rather than
// complicate every such condition, a build can drop the trigger.

import zio.sbt.githubactions.{Branch, Trigger}

ThisBuild / name := "Test Project"

inThisBuild(
  List(
    ciEnabledBranches := Seq("main"),
    ciWorkflowTriggers := Seq(
      Trigger.Release(Seq("published")),
      Trigger.Push(branches = Seq(Branch.Named("main"))),
      Trigger.PullRequest()
    )
  )
)

lazy val root = (project in file("."))
  .settings(
    version                         := "0.1",
    TaskKey[Unit]("checkWorkflows") := Golden.check(baseDirectory.value, "workflowTriggers", "ci.yml")
  )
