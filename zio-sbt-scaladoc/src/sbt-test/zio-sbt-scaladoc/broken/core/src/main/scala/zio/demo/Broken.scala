package zio.demo

/**
 * Wrong on purpose:
 * {{{
 * val x = Broken.greet(42)
 * }}}
 */
object Broken {
  def greet(name: String): String = name
}
