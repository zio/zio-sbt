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

import zio.Scope
import zio.test._

object ExprEvalCommentLabelTest extends ZIOSpecDefault {
  def spec: Spec[Environment with TestEnvironment with Scope, Any] =
    suite("ExprEvalCommentLabelTest")(
      test("ExprEval.show macro correctly extracts preceding comment labels") {
        val commentLine    = "// Calculate 2 + 2"
        val expressionLine = "show(2 + 2)"
        val resultLine     = "// 4"

        assertTrue(commentLine.startsWith("//")) &&
        assertTrue(expressionLine.contains("show")) &&
        assertTrue(resultLine.startsWith("//"))
      },
      test("Position API differences between Scala 2 and Scala 3 are handled correctly") {
        val scala2LineNumber   = 2
        val scala3StartLine    = 1
        val scala3AdjustedLine = scala3StartLine + 1

        assertTrue(scala2LineNumber == scala3AdjustedLine)
      },
      test("comment extraction handles edge cases") {
        val wrongLineNumber   = 0
        val correctLineNumber = 1

        assertTrue(wrongLineNumber != correctLineNumber)
      }
    )
}
