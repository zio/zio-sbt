// The most common adopter shape (zio-kafka, zio-streams-compress): stock jobs, a handful of
// `ci*` settings overridden. Pins how those settings reach the generated YAML - JVM and Node
// options into the workflow-level `env`, enabled branches into `on.push.branches`, and the
// Java versions into the test job's matrix.

import zio.sbt.githubactions.Condition

ThisBuild / name := "Test Project"

inThisBuild(
  List(
    ciEnabledBranches       := Seq("main", "series/2.x"),
    ciJvmOptions            ++= Seq("-Xmx4G", "-Xss2M", "-XX:+UseG1GC"),
    ciNodeOptions           ++= Seq("--max_old_space_size=4096"),
    ciTargetJavaVersions    := List("17", "21"),
    ciUpdateReadmeCondition := Some(
      Condition.Expression("github.ref == format('refs/heads/{0}', github.event.repository.default_branch)")
    )
  )
)

lazy val root = (project in file("."))
  .settings(
    version                         := "0.1",
    TaskKey[Unit]("checkWorkflows") := Golden.check(baseDirectory.value, "settingsOverrides", "ci.yml")
  )
