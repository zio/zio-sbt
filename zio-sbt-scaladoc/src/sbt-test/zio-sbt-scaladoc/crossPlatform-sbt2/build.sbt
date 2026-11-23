ThisBuild / scalaVersion       := "2.13.18"
ThisBuild / crossScalaVersions := Seq("2.12.21", "2.13.18", "3.3.7")

lazy val shared = Compile / unmanagedSourceDirectories +=
  (LocalRootProject / baseDirectory).value / "shared" / "src" / "main" / "scala"

lazy val root   = project.in(file(".")).aggregate(jvm, js, native)
lazy val jvm    = project.settings(shared)
lazy val js     = project.enablePlugins(ScalaJSPlugin).settings(shared)
lazy val native = project.enablePlugins(ScalaNativePlugin).settings(shared)

def wrappers(dir: File): List[String] =
  Option(dir.listFiles).toList.flatten.map(f => IO.read(f))

def check(condition: Boolean, message: String): Unit = if (!condition) sys.error(message)

lazy val checkPlatforms = taskKey[Unit]("each platform compiles the examples its markers allow")
checkPlatforms := {
  def has(files: List[String], text: String) = files.exists(_.contains(text))
  val onJvm    = wrappers((jvm / ScaladocCheck / sourceManaged).value / "scaladoccheck")
  val onJs     = wrappers((js / ScaladocCheck / sourceManaged).value / "scaladoccheck")
  val onNative = wrappers((native / ScaladocCheck / sourceManaged).value / "scaladoccheck")
  check(onJvm.size == 3 && has(onJvm, "java.io.File") && has(onJvm, "ManagementFactory"), s"JVM: $onJvm")
  check(onJs.size == 2 && has(onJs, "js.Dynamic") && !has(onJs, "java.io.File"), s"Scala.js: $onJs")
  check(onNative.size == 3 && has(onNative, "Ptr[Int]") && has(onNative, "java.io.File"), s"Scala Native: $onNative")
}
