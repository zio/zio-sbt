/*
 * Copyright 2024-2026 John A. De Goes and the ZIO Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package zio.sbt

import scala.language.experimental.macros

/**
 * Prints one or more expressions together with their evaluated results.
 *
 * For each expression, `show` prints its source text, then each line of its
 * value's `String.valueOf` text with a `// ` prefix. Consecutive `//` comment
 * lines directly above the call are printed first, as a label, and a blank line
 * ends the output.
 *
 * Supports two call styles:
 *
 * Comma-separated:
 * {{{
 *   // scala-3-only
 *
 *   // Sum and upper case
 *   ExprEval.show(
 *     List(1, 2, 3).sum,
 *     "zio".toUpperCase
 *   )
 * }}}
 *
 * It prints:
 * {{{
 *   // no-check: console output, not Scala code
 *   // Sum and upper case
 *   List(1, 2, 3).sum
 *   // 6
 *   "zio".toUpperCase
 *   // ZIO
 * }}}
 *
 * Block form (newline-separated), where each statement of the block is printed
 * as its own expression:
 * {{{
 *   // scala-3-only
 *   ExprEval.show {
 *     List(4, 5).max
 *     "abc".reverse
 *   }
 * }}}
 *
 * A block that declares something (a `val`, a `def`, an import, a class) is
 * printed as one expression.
 *
 * The label is read at run time from the source file, at the path the compiler
 * saw. When no file exists at that path, no label is printed.
 *
 * On Scala 2.13, `show` compiles only when called from code in package
 * `zio.sbt` or a package inside it: a call from `object Main` in package `demo`
 * fails with "method commentsAbove in object SourceReader cannot be accessed as
 * a member of object zio.sbt.SourceReader from object Main in package demo". On
 * Scala 3, the same call compiles and runs.
 */
object ExprEval {

  /**
   * Prints the source text and the value of each of `exprs`, after the `//`
   * comment lines directly above the call.
   */
  def show(exprs: Any*): Unit = macro ExprEvalMacro.showImpl
}
