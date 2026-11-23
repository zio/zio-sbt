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

/**
 * Prints one or more expressions together with their evaluated results.
 *
 * Supports two call styles:
 *
 * Comma-separated:
 * {{{
 *   ExprEval.show(
 *     List(1, 2, 3).sum,
 *     "zio".toUpperCase
 *   )
 * }}}
 *
 * Block form (newline-separated):
 * {{{
 *   ExprEval.show {
 *     List(1, 2, 3).sum
 *     "zio".toUpperCase
 *   }
 * }}}
 *
 * In both cases, consecutive `//` comment lines immediately above the `show`
 * call are printed as a label, and each expression is followed by its result:
 * {{{
 *   List(1, 2, 3).sum
 *   // 6
 *   "zio".toUpperCase
 *   // ZIO
 * }}}
 */
object ExprEval {

  inline def show(inline exprs: Any*): Unit = ${ ExprEvalMacro.showImpl('exprs) }
}
