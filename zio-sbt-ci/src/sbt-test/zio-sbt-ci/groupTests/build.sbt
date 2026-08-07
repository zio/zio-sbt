// `ciGroupSimilarTests := true` selects the GroupTests strategy (a java x scala matrix with one
// conditional step per Scala version), combined with `ciTargetMinJavaVersions` to gate modules by
// minimum JDK.
//
// This fixture also covers the hard-coded JDK sets in the GroupTests/FlattenTests code paths: they
// iterate Set("17","21","25") rather than `ciTargetJavaVersions`, so the non-default value below
// should expose that once it is fixed.

ThisBuild / name := "Test Project"

inThisBuild(
  List(
    ciGroupSimilarTests    := true,
    ciTargetJavaVersions   := List("17", "21"),
    ciTargetScalaVersions  := Map(
      "moduleA" -> Seq("2.13.16"),
      "moduleB" -> Seq("2.13.16", "3.3.4")
    ),
    ciTargetMinJavaVersions := Map("moduleB" -> "21")
  )
)

lazy val root = (project in file("."))
  .settings(
    version                         := "0.1",
    TaskKey[Unit]("checkWorkflows") := Golden.check(baseDirectory.value, "groupTests", "ci.yml")
  )
