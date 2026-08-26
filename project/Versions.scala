object Versions {
  val Scala212 = "2.12.21"
  val Scala213 = "2.13.18"
  // Pinned to match the Scala version sbt 2.0.7 itself is built with (not the newest available
  // Scala 3 release): Scala 3.3.8 hits a dotc compiler crash ("module class X$ has non-class
  // parent") when a downstream module cross-module-TASTy-references a sealed trait's companion,
  // which 3.8.4 does not reproduce.
  val Scala3 = "3.8.4"
  val zio    = "2.1.26"
}
