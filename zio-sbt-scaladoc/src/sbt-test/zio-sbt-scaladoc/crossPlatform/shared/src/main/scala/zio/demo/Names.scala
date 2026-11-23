package zio.demo

/**
 * Compiled on every platform:
 * {{{
 * val n = Names.count(List("a", "b"))
 * }}}
 *
 * `java.io.File` exists on the JVM and on Scala Native, not on Scala.js:
 * {{{
 * // no-js
 * val file = new java.io.File("build.sbt")
 * val m    = Names.count(List(file.getName))
 * }}}
 *
 * {{{
 * // jvm-only
 * val uptime = java.lang.management.ManagementFactory.getRuntimeMXBean.getUptime
 * }}}
 *
 * {{{
 * // js-only
 * val console = scala.scalajs.js.Dynamic.global.console
 * }}}
 *
 * {{{
 * // native-only
 * import scala.scalanative.unsafe._
 *
 * val pointer: Ptr[Int] = null
 * }}}
 */
object Names {
  def count(names: List[String]): Int = names.size
}
