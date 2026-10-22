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

object SourceReaderSpec extends ZIOSpecDefault {
  def spec: Spec[Environment with TestEnvironment with Scope, Any] =
    suite("SourceReaderSpec")(
      test("non-existent file returns empty list") {
        val result = SourceReader.commentsAbove("/nonexistent/path/file.scala", 1)
        assertTrue(result == Nil)
      },
      test("lineNum = 0 returns empty list") {
        val testFile = Files.createTempFile("test_", ".scala")
        try {
          Files.write(testFile, "// comment\ncode".getBytes("UTF-8"))
          val result = SourceReader.commentsAbove(testFile.toString, 0)
          assertTrue(result == Nil)
        } finally {
          Files.delete(testFile)
        }
      },
      test("lineNum = 1 (nothing above first line) returns empty list") {
        val testFile = Files.createTempFile("test_", ".scala")
        try {
          Files.write(testFile, "// comment\ncode".getBytes("UTF-8"))
          val result = SourceReader.commentsAbove(testFile.toString, 1)
          assertTrue(result == Nil)
        } finally {
          Files.delete(testFile)
        }
      },
      test("no comment above target line returns empty list") {
        val testFile = Files.createTempFile("test_", ".scala")
        try {
          Files.write(testFile, "val x = 1\nval y = 2".getBytes("UTF-8"))
          val result = SourceReader.commentsAbove(testFile.toString, 2)
          assertTrue(result == Nil)
        } finally {
          Files.delete(testFile)
        }
      },
      test("single comment above target line is collected") {
        val testFile = Files.createTempFile("test_", ".scala")
        try {
          Files.write(testFile, "// result is 42\nExprEval.show(42)".getBytes("UTF-8"))
          val result = SourceReader.commentsAbove(testFile.toString, 2)
          assertTrue(result == List("// result is 42"))
        } finally {
          Files.delete(testFile)
        }
      },
      test("multiple consecutive comments above are collected in order") {
        val testFile = Files.createTempFile("test_", ".scala")
        try {
          Files.write(
            testFile,
            "// first comment\n// second comment\n// third comment\nExprEval.show(42)".getBytes("UTF-8")
          )
          val result = SourceReader.commentsAbove(testFile.toString, 4)
          assertTrue(result == List("// first comment", "// second comment", "// third comment"))
        } finally {
          Files.delete(testFile)
        }
      },
      test("blank line separates comments from target line") {
        val testFile = Files.createTempFile("test_", ".scala")
        try {
          Files.write(testFile, "// isolated comment\n\nExprEval.show(42)".getBytes("UTF-8"))
          val result = SourceReader.commentsAbove(testFile.toString, 3)
          assertTrue(result == Nil)
        } finally {
          Files.delete(testFile)
        }
      },
      test("code line separates comments from target line") {
        val testFile = Files.createTempFile("test_", ".scala")
        try {
          Files.write(testFile, "// isolated comment\nval x = 1\nExprEval.show(42)".getBytes("UTF-8"))
          val result = SourceReader.commentsAbove(testFile.toString, 3)
          assertTrue(result == Nil)
        } finally {
          Files.delete(testFile)
        }
      },
      test("block comment above is not collected") {
        val testFile = Files.createTempFile("test_", ".scala")
        try {
          Files.write(testFile, "/* comment */\nExprEval.show(42)".getBytes("UTF-8"))
          val result = SourceReader.commentsAbove(testFile.toString, 2)
          assertTrue(result == Nil)
        } finally {
          Files.delete(testFile)
        }
      },
      test("indented comment is trimmed and collected") {
        val testFile = Files.createTempFile("test_", ".scala")
        try {
          Files.write(testFile, "  // indented comment\nExprEval.show(42)".getBytes("UTF-8"))
          val result = SourceReader.commentsAbove(testFile.toString, 2)
          assertTrue(result == List("// indented comment"))
        } finally {
          Files.delete(testFile)
        }
      },
      test("lineNum beyond file length clamps to last line and scans upward") {
        val testFile = Files.createTempFile("test_", ".scala")
        try {
          Files.write(testFile, "// comment\ncode\n// another".getBytes("UTF-8"))
          val result = SourceReader.commentsAbove(testFile.toString, 999)
          assertTrue(result == List("// another"))
        } finally {
          Files.delete(testFile)
        }
      }
    )
}
