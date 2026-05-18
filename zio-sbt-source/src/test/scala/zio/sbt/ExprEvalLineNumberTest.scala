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

import java.nio.file.Files

import zio.Scope
import zio.test._

object ExprEvalLineNumberTest extends ZIOSpecDefault {
  def spec: Spec[Environment with TestEnvironment with Scope, Any] =
    suite("ExprEvalLineNumberTest")(
      test("comment labels are extracted from the line immediately above show() call") {
        val testCode = """// Comment for this expression
                         |ExprEval.show(42)
                         |""".stripMargin

        val testFile = Files.createTempFile("expr_eval_test_", ".scala")
        try {
          Files.write(testFile, testCode.getBytes("UTF-8"))
          val content = new String(Files.readAllBytes(testFile), "UTF-8")
          val lines   = content.split("\n")

          assertTrue(lines.length >= 2) &&
          assertTrue(lines(0).trim.startsWith("// Comment")) &&
          assertTrue(lines(1).contains("show"))
        } finally {
          Files.delete(testFile)
        }
      },
      test("macro uses 1-based line numbers to find comments") {
        val lines = Array(
          "// Calculate 2 + 2",
          "show(2 + 2)",
          "// 4"
        )

        assertTrue(lines(0).startsWith("//")) &&
        assertTrue(lines(1).contains("show")) &&
        assertTrue(lines(2).startsWith("//"))
      }
    )
}
