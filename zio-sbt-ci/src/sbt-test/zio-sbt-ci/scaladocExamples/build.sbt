// Pins the ci.yml generated with `ciCheckScaladocExamples := true`: the build job gains a
// "Check Scaladoc examples" step that runs `sbt +scaladocCheck` after the artifacts build check.

ThisBuild / name := "Test Project"

ThisBuild / ciCheckScaladocExamples := true

lazy val root = (project in file("."))
  .settings(
    version                         := "0.1",
    TaskKey[Unit]("checkWorkflows") := Golden.check(baseDirectory.value, "scaladocExamples", "ci.yml")
  )
