object Versions {
  val Scala212 = "2.12.21"
  val Scala213 = "2.13.18"
  // Pinned to match the Scala version sbt 2.0.7 itself is built with (not the newest available
  // Scala 3 release): Scala 3.3.8 hits a dotc compiler crash ("module class X$ has non-class
  // parent") when a downstream module cross-module-TASTy-references a sealed trait's companion,
  // which 3.8.4 does not reproduce. Newer Scala 3 releases break the sbt-2.x scripted fixtures
  // instead (3.9.0 was reverted in #786; 3.10.0 was #834), so Scala Steward is told to skip this
  // line. Bump it together with sbt 2.
  val Scala3 = "3.8.4" // scala-steward:off
  val zio    = "2.1.27"
}
