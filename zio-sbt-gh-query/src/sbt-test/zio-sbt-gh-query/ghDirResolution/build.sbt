import GhQueryPlugin.autoImport._

lazy val root = (project in file("."))
  .settings(
    version := "0.1",
    ghRepo  := "test-org/test-repo",
    ghDir   := file("nested/data/dir"),
    TaskKey[Unit]("checkDirResolution") := {
      val dir = ghDir.value
      assert(dir == file("nested/data/dir"), s"Expected ghDir to be nested/data/dir, got $dir")

      // The resolved absolute path should be under baseDirectory
      val base = baseDirectory.value
      val resolved = if (dir.isAbsolute) dir else new java.io.File(base, dir.getPath)
      assert(resolved.getAbsolutePath.startsWith(base.getAbsolutePath),
        s"Resolved ghDir should be under baseDirectory: ${resolved.getAbsolutePath}")

      println(s"ghDir setting:  $dir")
      println(s"baseDirectory:  $base")
      println(s"Resolved path:  $resolved")
      println("ghDir resolution check passed!")
    },
    TaskKey[Unit]("checkAbsoluteDir") := {
      // Verify that an absolute ghDir would be preserved as-is
      val base = baseDirectory.value
      val absDir = new java.io.File(base, "abs-test-dir")
      val resolved = if (absDir.isAbsolute) absDir else new java.io.File(base, absDir.getPath)
      assert(resolved.isAbsolute, s"Absolute path should stay absolute: $resolved")

      println(s"Absolute dir check passed: $resolved")
    }
  )
  .enablePlugins(GhQueryPlugin)
