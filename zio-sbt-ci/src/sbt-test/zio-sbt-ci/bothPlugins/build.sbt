// Regression test for the `ciWorkflowName` collision.
//
// sbt imports the `autoImport` of every plugin on the build classpath, so when zio-sbt-ci and
// zio-sbt-website both declared a key of that name, any reference to it failed to compile and the
// project would not load at all:
//
//   error: reference to ciWorkflowName is ambiguous;
//   it is imported twice in the same scope by
//   import _root_.zio.sbt.ZioSbtCiPlugin.autoImport._
//   and import _root_.zio.sbt.WebsitePlugin.autoImport._
//
// The CI plugin's key is now `ciWorkflowTitle`, so both can be referenced side by side. Referencing
// both below is the point of this fixture: if either plugin reintroduces the other's name, this
// build stops loading and the test fails.

ThisBuild / name := "Test Project"

inThisBuild(
  List(
    // zio-sbt-ci: names the workflow and, lowercased, the file it is written to.
    ciWorkflowTitle := "CI",
    // zio-sbt-website: the workflow name used by the README CI badge.
    ciWorkflowName  := "CI"
  )
)

lazy val root = (project in file("."))
  .settings(
    version                         := "0.1",
    TaskKey[Unit]("checkWorkflows") := Golden.check(baseDirectory.value, "bothPlugins", "ci.yml")
  )
