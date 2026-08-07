// `ciTargetScalaVersions` with `ciGroupSimilarTests` left at its default (false), which selects the
// FlattenTests strategy: one matrix entry per "++<version> <module>" pair.

ThisBuild / name := "Test Project"

inThisBuild(
  List(
    ciTargetScalaVersions := Map(
      "moduleA" -> Seq("2.13.16"),
      "moduleB" -> Seq("2.13.16", "3.3.4")
    )
  )
)

lazy val root = (project in file("."))
  .settings(
    version                         := "0.1",
    TaskKey[Unit]("checkWorkflows") := Golden.check(baseDirectory.value, "flattenTests", "ci.yml")
  )
