// A job with a service container, in the shape a database-backed test job needs.
//
// `options` is the part that matters: it carries the raw `docker create` arguments, which is where
// the health check lives. Without it the job races the container and steps can run before postgres
// is accepting connections.

import zio.Chunk
import zio.sbt.ZioSbtCiPlugin._
import zio.sbt.githubactions.{ImageRef, Job, Service, ServicePort, Step, Strategy}

ThisBuild / name := "Test Project"

inThisBuild(
  List(
    ciTestJobs := Seq(
      Job(
        id         = "test",
        name       = "Test",
        jobTimeout = Some(25),
        strategy   = Some(
          Strategy(matrix = Map("java" -> List("17", "25")), failFast = false)
        ),
        services = Seq(
          Service(
            name  = "postgres",
            image = ImageRef("postgres:16"),
            env   = Map(
              "POSTGRES_USER"     -> "postgres",
              "POSTGRES_PASSWORD" -> "postgres",
              "POSTGRES_DB"       -> "postgres"
            ),
            // 32886 on the runner, 5432 inside the container.
            ports   = Chunk(ServicePort(32886, 5432)),
            options = Some(
              "--health-cmd pg_isready --health-interval 10s --health-timeout 5s --health-retries 5"
            )
          )
        ),
        steps = Seq(
          Checkout.value,
          SetupJava("${{ matrix.java }}"),
          CacheDependencies,
          Step.SingleStep(name = "Test", run = Some("sbt test"))
        )
      )
    )
  )
)

lazy val root = (project in file("."))
  .settings(
    version                         := "0.1",
    TaskKey[Unit]("checkWorkflows") := Golden.check(baseDirectory.value, "services", "ci.yml")
  )
