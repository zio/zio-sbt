// A workflow name is also the file name, so it has to be slugified: "Continuous Integration" must
// not produce a file with a space in it.
//
// The generated file is written relative to the build's base directory rather than the working
// directory, so `sbt ciGenerateGithubWorkflow` from a subdirectory still writes to the right place.

ThisBuild / name := "Test Project"

inThisBuild(List(ciWorkflowTitle := "Continuous Integration"))

lazy val root = (project in file("."))
  .settings(
    version := "0.1",
    TaskKey[Unit]("checkFileName") := {
      val dir      = baseDirectory.value / ".github" / "workflows"
      val expected = dir / "continuous-integration.yml"
      if (!expected.exists) {
        val found = if (dir.exists) IO.listFiles(dir).map(_.getName).sorted.mkString(", ") else "<dir missing>"
        sys.error(s"expected $expected, found: $found")
      }
      if (!IO.read(expected).contains("name: Continuous Integration"))
        sys.error("the workflow name inside the file should be unchanged")
    }
  )
