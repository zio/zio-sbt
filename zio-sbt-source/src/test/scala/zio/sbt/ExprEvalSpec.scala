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

import java.io.{ByteArrayOutputStream, PrintStream}

import zio.Scope
import zio.test._

object ExprEvalSpec extends ZIOSpecDefault {

  private def capture(block: => Unit): String = {
    val baos = new ByteArrayOutputStream()
    val ps   = new PrintStream(baos)
    Console.withOut(ps)(block)
    ps.flush()
    baos.toString("UTF-8")
  }

  def spec: Spec[Environment with TestEnvironment with Scope, Any] =
    suite("ExprEvalSpec")(
      test("single literal expression prints source and result") {
        val output = capture {
          ExprEval.show(42)
        }
        assertTrue(
          output.contains("42") && output.contains("// 42")
        )
      },
      test("multiple comma-separated expressions each printed with result") {
        val output = capture {
          ExprEval.show(1 + 1, 2 + 2)
        }
        assertTrue(
          output.contains("1 + 1") &&
            output.contains("// 2") &&
            output.contains("2 + 2") &&
            output.contains("// 4")
        )
      },
      test("string expression with multiline toString prefixes each line with //") {
        val output = capture {
          ExprEval.show("line1\nline2")
        }
        val lines = output.split("\n").toList
        assertTrue(
          lines.exists(_.startsWith("\"")) &&
            lines.exists(_ == "// line1") &&
            lines.exists(_ == "// line2")
        )
      },
      test("expression with side effects executes exactly once") {
        var callCount = 0
        val output    = capture {
          ExprEval.show { callCount += 1; callCount }
        }
        assertTrue(callCount == 1 && output.contains("// 1"))
      },
      test("block with single expression is printed") {
        val output = capture {
          ExprEval.show(42)
        }
        assertTrue(output.contains("42") && output.contains("// 42"))
      },
      test("block with multiple expressions unpacks and prints each individually") {
        val output = capture {
          ExprEval.show {
            1 + 1
            2 + 2
          }
        }
        assertTrue(
          output.contains("1 + 1") &&
            output.contains("// 2") &&
            output.contains("2 + 2") &&
            output.contains("// 4")
        )
      },
      test("block with val definition is treated as single unit (not unpacked)") {
        val output = capture {
          ExprEval.show {
            val x = 42
            x
          }
        }
        assertTrue(
          output.contains("x") && output.contains("// 42")
        )
      },
      test("output always ends with trailing blank line") {
        val output = capture {
          ExprEval.show(1)
        }
        assertTrue(output.endsWith("\n\n"))
      },
      test("single comment label above call is printed before expression") {
        val output = capture {
          // This is a test label
          ExprEval.show(99)
        }
        val lines      = output.split("\n").toList
        val labelIndex = lines.indexWhere(_.contains("This is a test label"))
        val exprIndex  = lines.indexWhere(_.contains("99"))
        assertTrue(labelIndex >= 0 && exprIndex > labelIndex)
      },
      test("multiple comment labels above call are printed in order") {
        val output = capture {
          // First label
          // Second label
          ExprEval.show(100)
        }
        val lines  = output.split("\n").toList
        val first  = lines.indexWhere(_.contains("First label"))
        val second = lines.indexWhere(_.contains("Second label"))
        val expr   = lines.indexWhere(_.contains("100"))
        assertTrue(first >= 0 && second > first && expr > second)
      }
    )
}
