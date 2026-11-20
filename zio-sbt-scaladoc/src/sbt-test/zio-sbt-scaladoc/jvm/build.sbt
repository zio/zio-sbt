ThisBuild / scalaVersion       := "2.13.18"
ThisBuild / crossScalaVersions := Seq("2.12.21", "2.13.18", "3.3.7")

lazy val root = project.in(file(".")).aggregate(core, extra, skipped)

lazy val core = project

lazy val extra = project
  .dependsOn(core)
  .settings(libraryDependencies += "com.lihaoyi" %% "sourcecode" % "0.4.2" % ScaladocCheck)

lazy val skipped = project.settings(scaladocCheck / skip := true)

def wrappers(dir: File): Map[String, String] =
  Option(dir.listFiles).toList.flatten.map(f => f.getName -> IO.read(f)).toMap

def only(files: Map[String, String], prefix: String): String =
  files.collect { case (name, content) if name.startsWith(prefix) => content }.toList match {
    case List(content) => content
    case other         => sys.error(s"expected one wrapper starting with $prefix, found ${other.size}: ${files.keys}")
  }

def check(condition: Boolean, message: String): Unit = if (!condition) sys.error(message)

lazy val checkNoWrappers = taskKey[Unit]("`compile` writes no wrappers")
checkNoWrappers := {
  val dir = (core / ScaladocCheck / sourceManaged).value / "scaladoccheck"
  check(wrappers(dir).isEmpty, s"compile wrote wrappers into $dir")
}

lazy val checkWrappers = taskKey[Unit]("the wrappers of the current Scala version")
checkWrappers := {
  val core3   = scalaBinaryVersion.value == "3"
  val files   = wrappers((core / ScaladocCheck / sourceManaged).value / "scaladoccheck")
  val names   = files.keys.toList.sorted
  val greeter = files.filter(_._1.startsWith("Greeter_L")).values.toList
  // greet (always), shout (3 only) or Loud (2 only), two; the no-check block is never written.
  check(greeter.size == 3, s"expected 3 Greeter wrappers, found $names")
  check(greeter.exists(_.contains("shout")) == core3, s"scala-3-only example on ${scalaVersion.value}: $names")
  check(greeter.exists(_.contains("class Loud")) == !core3, s"scala-2-only example on ${scalaVersion.value}: $names")
  check(!greeter.exists(_.contains("readLine")), "the no-check example was written")
  val greet = greeter.find(_.contains("greeter.greet")).get
  check(greet.contains("import zio.demo._"), s"the Greeter example lacks its package import:\n$greet")
  val two = greeter.find(_.contains("1 + 1")).get
  check(!two.contains("import zio.demo._"), s"an example that uses no name of its package got its import:\n$two")
  val counter = only(files, "Counter_L")
  check(
    counter.contains("import zio.demo._") && counter.contains("import zio.demo.nested._"),
    s"the nested-clause example lacks an import:\n$counter"
  )
  val extraFiles = wrappers((extra / ScaladocCheck / sourceManaged).value / "scaladoccheck")
  check(only(extraFiles, "Twice_L").contains("import zio.extra._"), s"Twice example: $extraFiles")
  check(wrappers((skipped / ScaladocCheck / sourceManaged).value / "scaladoccheck").isEmpty, "skipped module wrote wrappers")
}

lazy val checkPom = taskKey[Unit]("a library added with % ScaladocCheck stays out of the POM")
checkPom := {
  val pom = IO.read((extra / makePom).value)
  check(!pom.contains("sourcecode"), s"the POM lists the examples' library:\n$pom")
}
