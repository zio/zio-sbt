// A workflow made entirely of hand-built jobs that must not gain anything the plugin adds on its
// own: no workflow-level `permissions` block, no aggregate `ci` job, no concurrency group.
//
// The `permissions` case is the one that matters. The default block grants only `contents: read`
// (plus `id-token: write`), which would stop the `tag` job pushing a release tag with GITHUB_TOKEN.
// `None` leaves the block out, so the workflow keeps the repository's default token permissions;
// that is deliberately different from `Some(Map.empty)`, which renders `permissions: {}` and grants
// nothing.
//
// The optional generated workflows are switched off so the fixture only covers `ci.yml`.

import zio.sbt.ZioSbtCiPlugin._
import zio.sbt.githubactions.{
  Branch,
  Condition,
  ImageRef,
  Job,
  Service,
  ServicePort,
  Step,
  Strategy,
  Trigger
}

import zio.Chunk

ThisBuild / name := "Test Project"

val notFromBot = Condition.Expression("github.actor != 'github-actions[bot]'")
val isMaster   = Condition.Expression("github.ref == 'refs/heads/master'")

inThisBuild(
  List(
    ciWorkflowPermissions := None,
    ciConcurrency         := None,
    ciWorkflowEnv         := Map.empty,
    ciWorkflowTriggers    := Seq(
      Trigger.PullRequest(ignoredBranches = Seq(Branch.Named("gh-pages"))),
      Trigger.Push(branches = Seq(Branch.Named("master")))
    ),
    ciEnableReleaseDrafter      := false,
    ciEnableScalaSteward        := false,
    ciEnableDependabot          := false,
    ciEnableRegenerateWorkflows := false,
    ciBuildJobs                 := Seq(
      Job(
        id        = "tag",
        name      = "Tag build",
        condition = Some(notFromBot),
        steps     = Seq(
          Checkout.value,
          SetupJava("17"),
          SetupSBT,
          Step.SingleStep(
            name = "Tag release",
            condition = Some(isMaster),
            run = Some("sbt ciReleaseTagNextVersion")
          )
        )
      ),
      Job(
        id        = "integration-test",
        name      = "Integration test",
        runsOn    = "${{ matrix.os }}",
        need      = Seq("tag"),
        condition = Some(notFromBot),
        strategy  = Some(
          Strategy(
            matrix = Map(
              "os"    -> List("ubuntu-latest"),
              "scala" -> List("2.13.x", "3.x")
            )
          )
        ),
        services = Seq(
          Service(
            name  = "floci",
            image = ImageRef("floci/floci:latest"),
            env   = Map("AWS_DEFAULT_REGION" -> "us-east-1"),
            ports = Chunk(ServicePort(4566, 4566))
          )
        ),
        steps = Seq(
          Checkout.value,
          SetupJava("17"),
          SetupSBT,
          Step.SingleStep(
            name = "Build and run tests",
            run = Some("sbt ++${{ matrix.scala }} integtests/test"),
            env = Map("PGP_PASSPHRASE" -> "${{ secrets.PGP_PASSPHRASE }}")
          )
        )
      )
    ),
    ciLintJobs             := Seq.empty,
    ciTestJobs             := Seq.empty,
    ciUpdateReadmeJobs     := Seq.empty,
    ciReportSuccessfulJobs := Seq.empty,
    ciReleaseJobs          := Seq(
      Job(
        id        = "release",
        name      = "Release",
        need      = Seq("integration-test"),
        condition = Some(isMaster && notFromBot),
        steps     = Seq(Checkout.value, SetupJava("17"), SetupSBT, Step.SingleStep(name = "Publish", run = Some("sbt sonaRelease")))
      )
    ),
    ciPostReleaseJobs := Seq.empty
  )
)

lazy val root = (project in file("."))
  .settings(
    version                         := "0.1",
    TaskKey[Unit]("checkWorkflows") := Golden.check(baseDirectory.value, "inheritedPermissions", "ci.yml"),
    TaskKey[Unit]("checkNoPermissionsBlock") := {
      val ci = IO.read(baseDirectory.value / ".github" / "workflows" / "ci.yml")
      if (ci.contains("permissions:")) sys.error("ci.yml should have no workflow-level permissions block")
      if (ci.contains("concurrency:")) sys.error("ci.yml should have no concurrency block")
      if (ci.contains("\n  ci:\n")) sys.error("ci.yml should have no aggregate `ci` job")
    }
  )
