// The zio-prelude shape: stock jobs replaced wholesale with hand-built `Job` values, using the
// reusable step values the plugin exports, and `ciPullRequestApprovalJobs` renamed to match the
// custom job ids.
//
// This fixture is also where `timeout-minutes` is pinned, via both routes: the deprecated
// `timeoutMinutes` on the compile job (the zio-prelude shape, which was accepted and silently
// discarded before 0.6.4) and the current `jobTimeout` on the test job.

import zio.sbt.ZioSbtCiPlugin._
import zio.sbt.githubactions.{Job, Step, Strategy}

ThisBuild / name := "Test Project"

inThisBuild(
  List(
    ciSwapSizeGB              := 7,
    ciPullRequestApprovalJobs := Seq("lint", "compile", "integration-test"),
    // `jobTimeout` is the current way to ask for a timeout; `withTimeout` is equivalent.
    ciTestJobs                := Seq(
      Job(
        id         = "integration-test",
        name       = "Integration Test",
        jobTimeout = Some(45),
        steps      = Seq(
          Checkout.value,
          SetupJava("17"),
          CacheDependencies,
          Step.SingleStep(name = "Integration test", run = Some("sbt it:test"))
        )
      )
    ),
    ciBuildJobs               := Seq(
      Job(
        id             = "compile",
        name           = "Compile",
        runsOn         = "ubuntu-22.04",
        timeoutMinutes = 60,
        strategy       = Some(
          Strategy(
            matrix = Map("java" -> List("17", "21"), "platform" -> List("JVM", "JS")),
            failFast = false
          )
        ),
        steps = Seq(
          Checkout.value,
          SetupLibuv,
          SetupJava("17"),
          CacheDependencies,
          Step.SingleStep(
            name = "Compile",
            run  = Some("sbt ++${{ matrix.scala }} compile"),
            env  = Map("PLATFORM" -> "${{ matrix.platform }}")
          )
        )
      )
    )
  )
)

lazy val root = (project in file("."))
  .settings(
    version                         := "0.1",
    TaskKey[Unit]("checkWorkflows") := Golden.check(baseDirectory.value, "customJobs", "ci.yml")
  )
