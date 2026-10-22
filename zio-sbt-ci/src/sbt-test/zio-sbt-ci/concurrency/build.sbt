// Concurrency at both levels.
//
// The workflow group is scoped to the pull request rather than the ref, and cancellation is itself
// an expression: superseded pull request runs are cancelled, while pushes and releases are left
// alone. That is only expressible because `cancel-in-progress` accepts an expression as well as a
// boolean.
//
// The release job then opts out entirely with its own group, so a release is never cancelled by a
// later one.

import zio.sbt.ZioSbtCiPlugin._
import zio.sbt.githubactions.{CancelInProgress, Concurrency, Condition, Job, Step}

ThisBuild / name := "Test Project"

inThisBuild(
  List(
    ciConcurrency := Some(
      Concurrency(
        group = "ci-pr-${{ github.event_name == 'pull_request' && github.event.pull_request.number || github.ref }}",
        cancelInProgress = CancelInProgress.When(Condition.Expression("github.event_name == 'pull_request'"))
      )
    ),
    ciReleaseJobs := Seq(
      Job(
        id          = "release",
        name        = "Release",
        jobTimeout  = Some(60),
        concurrency = Some(
          Concurrency(
            group = "release-${{ github.ref }}",
            cancelInProgress = CancelInProgress.Never
          )
        ),
        need  = Seq("ci"),
        steps = Seq(
          Checkout.value,
          SetupJava("17"),
          CacheDependencies,
          Step.SingleStep(name = "Release", run = Some("sbt ci-release"))
        )
      )
    )
  )
)

lazy val root = (project in file("."))
  .settings(
    version                         := "0.1",
    TaskKey[Unit]("checkWorkflows") := Golden.check(baseDirectory.value, "concurrency", "ci.yml")
  )
