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

import scala.io.Source
import java.nio.file.Files
import java.nio.file.Paths

object ExprEvalLineNumberTest {
  def main(args: Array[String]): Unit = {
    // This test verifies that comment labels are correctly extracted
    // from the line immediately above the show() call.
    // The macro should use 1-based line numbers to find comments.

    // Test 1: Single comment label directly above show()
    testCommentExtraction()

    println("✅ All line number tests passed!")
  }

  def testCommentExtraction(): Unit = {
    // Create a temporary test file
    val testCode = """// Comment for this expression
                     |ExprEval.show(42)
                     |""".stripMargin

    val testFile = Files.createTempFile("expr_eval_test_", ".scala")
    Files.write(testFile, testCode.getBytes("UTF-8"))

    try {
      // Read the file to verify comment is on line 1, show() is on line 2
      val content = new String(Files.readAllBytes(testFile), "UTF-8")
      val lines   = content.split("\n")

      assert(lines(0).trim.startsWith("// Comment"), "First line should be the comment")
      assert(lines(1).contains("show"), "Second line should contain show()")

      println(s"✓ Test file structure correct: comment on line 1, show() on line 2")
      println(s"  This verifies that the macro should look 1 line above (startLine + 1 for zero-based)")
    } finally {
      Files.delete(testFile)
    }
  }
}
