package zio.extra

import zio.demo.Greeter

/**
 * Uses a module this one depends on, and a library only the examples need:
 * {{{
 * import zio.demo.Greeter
 *
 * val twice = Twice(Greeter("Yo"))
 * val line  = implicitly[sourcecode.Line].value
 * }}}
 */
final case class Twice(greeter: Greeter)
