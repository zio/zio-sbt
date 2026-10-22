// The zio-logging shape: keep the stock jobs but transform them, and append one extra job.
//
// Note `ciTestJobs := ciTestJobs.value.map(...)` - a key referencing itself. zio-logging relies on
// this working, so it is pinned here: if sbt ever treats it as a cycle, this fixture fails loudly
// rather than the breakage surfacing in a downstream repository.

import zio.sbt.ZioSbtCiPlugin._
import zio.sbt.githubactions.{Job, Step, Strategy}

lazy val ciRunsOn = "ubuntu-22.04"

def ciJobWithSetup(job: Job): Job = job.copy(runsOn = ciRunsOn)

lazy val compileExamplesJob = Def.setting(
  Job(
    id       = "compile-examples",
    name     = "Compile Examples",
    runsOn   = ciRunsOn,
    strategy = Some(Strategy(matrix = Map("scala" -> List("2.13.16", "3.3.4")))),
    steps    = Seq(
      Checkout.value,
      SetupJava("17"),
      CacheDependencies,
      Step.SingleStep(name = "Compile examples", run = Some("sbt ++${{ matrix.scala }} examples/compile"))
    )
  )
)

ThisBuild / name := "Test Project"

inThisBuild(
  List(
    ciTestJobs         := ciTestJobs.value.map(ciJobWithSetup) :+ compileExamplesJob.value,
    ciLintJobs         := ciLintJobs.value.map(ciJobWithSetup),
    ciBuildJobs        := ciBuildJobs.value.map(ciJobWithSetup),
    ciReleaseJobs      := ciReleaseJobs.value.map(ciJobWithSetup),
    ciUpdateReadmeJobs := ciUpdateReadmeJobs.value.map(ciJobWithSetup),
    ciPostReleaseJobs  := ciPostReleaseJobs.value.map(ciJobWithSetup)
  )
)

lazy val root = (project in file("."))
  .settings(
    version                         := "0.1",
    TaskKey[Unit]("checkWorkflows") := Golden.check(baseDirectory.value, "mappedJobs", "ci.yml")
  )
