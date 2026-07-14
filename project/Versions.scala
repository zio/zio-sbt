object Versions {
  val Scala212 = "2.12.21"
  val Scala213 = "2.13.18"
  val Scala3   = "3.3.8"
  val zio      = "2.1.26"

  // sbt 2.0 builds sbt plugins with the Scala 3 version that sbt itself is built with. sbt 2.0.2
  // uses Scala 3.8.4, and the plugin modules must use it too so they can read sbt's TASTy.
  val SbtScala = "3.8.4"
}
