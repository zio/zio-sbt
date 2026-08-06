// The zio-prelude shape: stock jobs replaced wholesale with hand-built `Job` values, using the
// reusable step values the plugin exports, and `ciPullRequestApprovalJobs` renamed to match the
// custom job ids.
//
// This fixture is also the canary for rendering `timeout-minutes`. `timeoutMinutes = 60` below is
// currently accepted and silently discarded, exactly as in zio-prelude. When that starts being
// emitted, this golden file must change and no other should.

import zio.sbt.ZioSbtCiPlugin._
import zio.sbt.githubactions.{Job, Step, Strategy}

ThisBuild / name := "Test Project"

inThisBuild(
  List(
    ciSwapSizeGB              := 7,
    ciPullRequestApprovalJobs := Seq("lint", "compile"),
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
