ThisBuild / scalaVersion := "2.13.18"

lazy val shared = Compile / unmanagedSourceDirectories +=
  (LocalRootProject / baseDirectory).value / "shared" / "src" / "main" / "scala"

lazy val root      = project.in(file(".")).aggregate(core, marker, sharedJvm, sharedJs)
lazy val core      = project
lazy val marker    = project
lazy val sharedJvm = project.settings(shared, libraryDependencies += "org.slf4j" % "slf4j-api" % "2.0.17")
lazy val sharedJs  = project.enablePlugins(ScalaJSPlugin).settings(shared)

def check(condition: Boolean, message: String): Unit = if (!condition) sys.error(message)

// The compile error of the broken example must point at its Scaladoc line, not at the wrapper:
// feed the module's position mappers the position of `42` in the wrapper and check the result.
lazy val checkMapper = taskKey[Unit]("errors in a wrapper are reported at the Scaladoc line")
checkMapper := {
  val dir     = (core / ScaladocCheck / sourceManaged).value / "scaladoccheck"
  val wrapper = Option(dir.listFiles).toList.flatten
    .find(_.getName.startsWith("Broken_L"))
    .getOrElse(sys.error(s"no wrapper in $dir"))
  val lines  = IO.readLines(wrapper)
  val index  = lines.indexWhere(_.contains("greet(42)"))
  val column = lines(index).indexOf("42")
  val inWrapper = new xsbti.Position {
    def line()         = java.util.Optional.of(Integer.valueOf(index + 1))
    def lineContent()  = lines(index)
    def offset()       = java.util.Optional.empty[Integer]()
    def pointer()      = java.util.Optional.of(Integer.valueOf(column))
    def pointerSpace() = java.util.Optional.empty[String]()
    def sourcePath()   = java.util.Optional.of(wrapper.getPath)
    def sourceFile()   = java.util.Optional.of(wrapper)
  }
  val mapped = (core / ScaladocCheck / sourcePositionMappers).value.iterator
    .map(mapper => mapper(inWrapper))
    .collectFirst { case Some(position) => position }
    .getOrElse(sys.error("the wrapper position was not mapped"))
  val sourceLine = " * val x = Broken.greet(42)"
  check(mapped.sourcePath.get.endsWith("core/src/main/scala/zio/demo/Broken.scala"), s"path: ${mapped.sourcePath}")
  check(mapped.line.get.intValue == 6, s"line: ${mapped.line}")
  check(mapped.pointer.get.intValue == sourceLine.indexOf("42"), s"column: ${mapped.pointer}")
  check(mapped.lineContent == sourceLine, s"content: ${mapped.lineContent}")
}
