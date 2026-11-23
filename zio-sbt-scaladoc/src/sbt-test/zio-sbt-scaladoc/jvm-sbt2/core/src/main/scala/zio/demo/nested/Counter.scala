package zio.demo
package nested

/**
 * Sees the names of both package clauses, as the code of this file does:
 * {{{
 * val counter = Counter(Greeter("Hi"))
 * }}}
 */
final case class Counter(greeter: Greeter)
