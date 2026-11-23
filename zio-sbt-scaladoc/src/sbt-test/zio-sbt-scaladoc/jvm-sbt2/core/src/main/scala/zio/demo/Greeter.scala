package zio.demo

/**
 * Greets people:
 * {{{
 * val greeter = Greeter("Hello")
 * greeter.greet("ZIO")
 * }}}
 */
final case class Greeter(prefix: String) {

  /**
   * {{{
   * // scala-3-only
   * extension (g: Greeter) def shout(name: String): String = g.greet(name).toUpperCase
   * }}}
   */
  def greet(name: String): String = prefix + ", " + name + "!"

  /**
   * {{{
   * // scala-2-only
   * implicit class Loud(g: Greeter) { def loud(name: String): String = g.greet(name).toUpperCase }
   * }}}
   *
   * {{{
   * // no-check: reads from the terminal
   * scala.io.StdIn.readLine().nope
   * }}}
   *
   * An example that uses nothing from its package:
   * {{{
   * val two = 1 + 1
   * }}}
   */
  def twice(name: String): String = greet(name) + greet(name)
}
