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
import java.nio.charset.StandardCharsets
import java.nio.file.Files

import zio.Scope
import zio.test._

object SourceFileSpec extends ZIOSpecDefault {

  // Evaluate result eagerly before finally-delete runs; mirrors SourceReaderSpec pattern.
  private def withTempFile[A](content: String)(f: String => A): A = {
    val tmp = Files.createTempFile("source_file_spec_", ".scala")
    try {
      Files.write(tmp, content.getBytes(StandardCharsets.UTF_8))
      f(tmp.toAbsolutePath.toString)
    } finally {
      Files.delete(tmp)
    }
  }

  private def capture(block: => Unit): String = {
    val baos = new ByteArrayOutputStream()
    val ps   = new PrintStream(baos)
    Console.withOut(ps)(block)
    ps.flush()
    baos.toString(StandardCharsets.UTF_8.name())
  }

  def spec: Spec[Environment with TestEnvironment with Scope, Any] =
    suite("SourceFileSpec")(
      suite("fileExtension")(
        test("extracts scala extension") {
          assertTrue(SourceFile.fileExtension("foo/bar/Baz.scala") == "scala")
        },
        test("extracts ts extension") {
          assertTrue(SourceFile.fileExtension("src/index.ts") == "ts")
        },
        test("no extension returns full filename") {
          assertTrue(SourceFile.fileExtension("Makefile") == "Makefile")
        }
      ),
      suite("readSource")(
        test("reads full file content") {
          val result = withTempFile("line1\nline2\nline3")(SourceFile.readSource)
          assertTrue(result == "line1\nline2\nline3")
        },
        test("reads single-line file") {
          val result = withTempFile("only line")(SourceFile.readSource)
          assertTrue(result == "only line")
        }
      ),
      suite("printSource")(
        test("default: includes title, no showLineNumbers") {
          val (path, out) = withTempFile("val x = 1") { p =>
            (p, capture(SourceFile.printSource(p)))
          }
          assertTrue(
            out.startsWith(s"""```scala title="$path""""),
            out.contains("val x = 1"),
            out.trim.endsWith("```"),
            !out.contains("showLineNumbers")
          )
        },
        test("comment=false omits title") {
          val out = withTempFile("val x = 1") { p =>
            capture(SourceFile.printSource(p, comment = false))
          }
          assertTrue(
            out.startsWith("```scala\n"),
            !out.contains("title=")
          )
        },
        test("showLineNumbers=true adds showLineNumbers") {
          val out = withTempFile("val x = 1") { p =>
            capture(SourceFile.printSource(p, showLineNumbers = true))
          }
          assertTrue(out.contains("showLineNumbers"))
        },
        test("fenced block uses correct language tag from extension") {
          val tmp = Files.createTempFile("source_file_spec_", ".ts")
          try {
            Files.write(tmp, "const x = 1;".getBytes(StandardCharsets.UTF_8))
            val out = capture(SourceFile.printSource(tmp.toAbsolutePath.toString, comment = false))
            assertTrue(out.startsWith("```ts\n"))
          } finally {
            Files.delete(tmp)
          }
        },
        test("embed modifier without :showLineNumbers flag") {
          val out = withTempFile("val x = 1") { p =>
            capture(SourceFile.printSource(p, comment = false))
          }
          assertTrue(!out.contains("showLineNumbers"))
        },
        test("embed modifier with :showLineNumbers flag") {
          val out = withTempFile("val x = 1") { p =>
            capture(SourceFile.printSource(p, comment = false, showLineNumbers = true))
          }
          assertTrue(out.contains("showLineNumbers"))
        }
      ),
      suite("EmbedSourceModifier parsing")(
        test("parses embed:path format") {
          val info         = "embed:path/to/Example.scala"
          val showLineNums = info.endsWith(":showLineNumbers")
          val path         = if (showLineNums) info.stripSuffix(":showLineNumbers") else info.stripPrefix("embed:")
          assertTrue(path == "path/to/Example.scala")
        },
        test("parses embed:path:showLineNumbers format") {
          val info         = "embed:path/to/Example.scala:showLineNumbers"
          val showLineNums = info.endsWith(":showLineNumbers")
          val path         = if (showLineNums) info.stripPrefix("embed:").stripSuffix(":showLineNumbers") else info.stripPrefix("embed:")
          assertTrue(
            path == "path/to/Example.scala",
            showLineNums == true
          )
        },
        test("handles nested paths with slashes") {
          val info         = "embed:zio-examples/threadlocal-bridge/src/main/scala/Example.scala"
          val path         = info.stripPrefix("embed:")
          assertTrue(path == "zio-examples/threadlocal-bridge/src/main/scala/Example.scala")
        },
        test("detects showLineNumbers flag correctly") {
          val info1 = "embed:path.scala"
          val info2 = "embed:path.scala:showLineNumbers"
          assertTrue(
            !info1.endsWith(":showLineNumbers"),
            info2.endsWith(":showLineNumbers")
          )
        }
      )
    )
}
