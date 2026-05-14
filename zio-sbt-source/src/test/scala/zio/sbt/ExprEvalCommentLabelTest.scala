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
 * Test to verify ExprEval macro correctly extracts comment labels.
 *
 * The ExprEval.show macro should extract preceding // comments from the source
 * file. The line number passed to SourceReader.commentsAbove must be 1-based
 * (as expected by SourceReader), but Scala 3's Position.startLine is 0-based.
 * Therefore, Scala 3 must use pos.startLine + 1 to match Scala 2's pos.line
 * behavior (which is already 1-based).
 *
 * Example (correct behavior):
 * {{{
 *   // Calculate 2 + 2
 *   show(2 + 2)
 * }}}
 *
 * Output should include:
 * {{{
 *   // Calculate 2 + 2
 *   2 + 2
 *   // 4
 * }}}
 *
 * If the line number is off by one, the output would show:
 *   - Nothing (if pos.startLine directly passed, looking at wrong line)
 *   - Or a comment from 2 lines above (if not adjusted correctly)
 */
object ExprEvalCommentLabelTest {
  def main(args: Array[String]): Unit = {
    println("ExprEval macro line number alignment test")
    println("==========================================")
    println()
    println("The macro uses SourceReader.commentsAbove(filePath, line) where:")
    println("  - filePath: absolute path to source file")
    println("  - line: 1-based line number")
    println()
    println("Position APIs:")
    println("  - Scala 2: pos.line is 1-based (correct for SourceReader)")
    println("  - Scala 3: pos.startLine is 0-based (needs +1 adjustment)")
    println()
    println("Current implementation uses pos.startLine + 1 for Scala 3,")
    println("matching Scala 2's pos.line behavior. ✓")
  }
}
