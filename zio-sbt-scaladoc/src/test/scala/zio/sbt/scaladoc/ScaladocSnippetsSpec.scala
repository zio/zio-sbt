/*
 * Copyright 2022-2026 dev.zio
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

package zio.sbt.scaladoc

import scala.util.Try

import zio.sbt.scaladoc.ScaladocSnippets.{Platform, Snippet}
import zio.test._

object ScaladocSnippetsSpec extends ZIOSpecDefault {
  private val path = "core/shared/src/main/scala/zio/query/A.scala"

  private def source(lines: String*): String = lines.mkString("\n")

  private def failure(content: String): Option[String] =
    Try(ScaladocSnippets.extract(path, content)).failed.toOption.map(_.getMessage)

  private def example(line: Int, code: String*): Snippet =
    Snippet(path, line, code.toList, code.toList.map(_ => 3), code.toList.map(" * " + _), None, Platform.all)

  private def block(lines: String*): String = source(("/**" +: " * {{{" +: lines.map(" * " + _)) ++ Seq(" * }}}", " */")*)

  private val knownMarkers =
    "`// scala-3-only`, `// scala-2-only`, `// jvm-only`, `// js-only`, `// native-only`, `// no-js`, `// no-native` or `// no-check: <reason>`"

  def spec: Spec[TestEnvironment, Any] = suite("ScaladocSnippets")(
    suite("extract")(
      test("returns a {{{ block with the line of its opening marker, the margin removed and the margin widths") {
        val content = source(
          "package p",
          "/**",
          " * Area of a book cover:",
          " * {{{",
          " * val area = width * height",
          " *   .toString",
          " *",
          " * }}}",
          " */",
          "object A"
        )
        assertTrue(
          ScaladocSnippets.extract(path, content) == List(
            Snippet(
              path,
              4,
              List("val area = width * height", "  .toString", ""),
              List(3, 3, 2),
              List(" * val area = width * height", " *   .toString", " *"),
              None,
              Platform.all
            )
          )
        )
      },
      test("returns a ```scala fence") {
        val content = source("/**", " * ```scala", " * val title = \"Zionomicon\"", " * ```", " */")
        assertTrue(ScaladocSnippets.extract(path, content).map(_.code) == List(List("val title = \"Zionomicon\"")))
      },
      test("reads an escaped dollar as a dollar, the way Scaladoc renders it") {
        val d       = "$" // built up so that no string literal here looks like a missing interpolator
        val content = block("val q = s\"id = \\" + d + "id\"")
        assertTrue(ScaladocSnippets.extract(path, content).map(_.code) == List(List("val q = s\"id = " + d + "id\"")))
      },
      test("ignores blocks outside Scaladoc comments") {
        val content = source("/* plain comment", " * {{{", " * val x = 1", " * }}}", " */", "// {{{")
        assertTrue(ScaladocSnippets.extract(path, content) == Nil)
      },
      test("ignores one-line blocks") {
        assertTrue(ScaladocSnippets.extract(path, source("/** Use {{{ f(1) }}} here. */", "def f(i: Int) = i")) == Nil)
      },
      test("keeps scanning after a one-line Scaladoc") {
        val content = source("/** One line. */", "def f = 1", "/**", " * {{{", " * val y = 2", " * }}}", " */")
        assertTrue(ScaladocSnippets.extract(path, content).map(_.line) == List(4))
      },
      test("reads files with Windows line endings") {
        val content = "/**\r\n * {{{\r\n * val y = 2\r\n * }}}\r\n */\r\n"
        assertTrue(ScaladocSnippets.extract(path, content).map(_.code) == List(List("val y = 2")))
      },
      test("skips a block whose first line is // no-check with a reason") {
        assertTrue(ScaladocSnippets.extract(path, block("// no-check: needs a running database", "db.run()")) == Nil)
      },
      test("rejects // no-check without a reason") {
        assertTrue(
          failure(block("// no-check", "db.run()")) ==
            Some(s"$path:2: `// no-check` needs a reason, as in `// no-check: needs a running database`")
        )
      },
      test("rejects a {{{ code line that contains }}}") {
        assertTrue(
          failure(block("val json = 1 // {\"a\":{\"b\":1}}}")) == Some(
            s"$path:2: a code line contains }}}, which ends the Scaladoc code block early; reword it (e.g. split the braces or drop the output comment)"
          )
        )
      },
      test("rejects marker-like lines that are not exact markers") {
        assertTrue(
          failure(block("// scala-3-only: uses given", "given Int = 1")) ==
            Some(s"$path:2: unknown marker `// scala-3-only: uses given`; use $knownMarkers"),
          failure(block("// scala3-only", "given Int = 1")) ==
            Some(s"$path:2: unknown marker `// scala3-only`; use $knownMarkers"),
          failure(block("// jvm only", "val f = new java.io.File(\"a\")")) ==
            Some(s"$path:2: unknown marker `// jvm only`; use $knownMarkers"),
          failure(block("// no-jvm", "val a = 1")) == Some(s"$path:2: unknown marker `// no-jvm`; use $knownMarkers")
        )
      },
      test("does not read a plain comment as a marker") {
        assertTrue(
          ScaladocSnippets.extract(path, block("// Build a cache:", "val a = 1")).map(_.platforms) == List(Platform.all),
          ScaladocSnippets.extract(path, block("// JSON output:", "val a = 1")).map(_.platforms) == List(Platform.all)
        )
      },
      test("fails with path and line when the comment ends before the block closes") {
        assertTrue(failure(source("/**", " * {{{", " * val x = 1", " */", "object A")) == Some(s"$path:2: code block is never closed"))
      },
      test("fails with path and line when the file ends before the block closes") {
        assertTrue(failure(source("/**", " * {{{", " * val x = 1")) == Some(s"$path:2: code block is never closed"))
      },
      test("records scala-3-only and scala-2-only markers") {
        val content = source(
          "/**",
          " * {{{",
          " * // scala-3-only",
          " * given Int = 1",
          " * }}}",
          " * {{{",
          " * // scala-2-only",
          " * implicit val i: Int = 1",
          " * }}}",
          " */"
        )
        assertTrue(ScaladocSnippets.extract(path, content).map(_.onlyScalaMajor) == List(Some(3), Some(2)))
      },
      test("records platform markers") {
        def platforms(markers: String*): List[Set[Platform]] =
          ScaladocSnippets.extract(path, block(markers :+ "val a = 1"*)).map(_.platforms)
        assertTrue(
          platforms("// jvm-only") == List(Set[Platform](Platform.Jvm)),
          platforms("// js-only") == List(Set[Platform](Platform.Js)),
          platforms("// native-only") == List(Set[Platform](Platform.Native)),
          platforms("// no-js") == List(Set[Platform](Platform.Jvm, Platform.Native)),
          platforms("// no-js", "// no-native") == List(Set[Platform](Platform.Jvm))
        )
      },
      test("combines a Scala marker and a platform marker") {
        val snippets = ScaladocSnippets.extract(path, block("// scala-3-only", "// jvm-only", "given Int = 1"))
        assertTrue(
          snippets.map(_.onlyScalaMajor) == List(Some(3)),
          snippets.map(_.platforms) == List(Set[Platform](Platform.Jvm)),
          snippets.map(_.appliesTo(3, Platform.Jvm)) == List(true),
          snippets.map(_.appliesTo(2, Platform.Jvm)) == List(false),
          snippets.map(_.appliesTo(3, Platform.Js)) == List(false)
        )
      },
      test("rejects contradicting markers") {
        assertTrue(
          failure(block("// scala-2-only", "// scala-3-only", "val a = 1")) ==
            Some(s"$path:2: `// scala-2-only` and `// scala-3-only` contradict each other; keep one"),
          failure(block("// jvm-only", "// js-only", "val a = 1")) ==
            Some(s"$path:2: the markers `// jvm-only`, `// js-only` leave no platform to compile on"),
          failure(block("// js-only", "// no-js", "val a = 1")) ==
            Some(s"$path:2: the markers `// js-only`, `// no-js` leave no platform to compile on")
        )
      }
    ),
    test("stripMargin removes only the leading margin") {
      assertTrue(
        ScaladocSnippets.stripMargin("   *   val y = 2 * 3") == "  val y = 2 * 3",
        ScaladocSnippets.stripMargin("  *") == "",
        ScaladocSnippets.stripMargin("val x = 1") == "val x = 1"
      )
    },
    suite("package clauses")(
      test("read one clause, nested clauses, a Scala 3 colon clause and a package object") {
        assertTrue(
          ScaladocSnippets.packageClauses("package zio.query\n\nobject A") == List("zio.query"),
          ScaladocSnippets.packageClauses("// header\npackage zio\npackage query\n\nimport x._\nobject A") ==
            List("zio", "query"),
          ScaladocSnippets.packageClauses("package zio.query:\n\nobject A") == List("zio.query"),
          ScaladocSnippets.packageClauses("package zio\n\npackage object query {\n}") == List("zio", "query"),
          ScaladocSnippets.packageClauses("object A\npackage b") == Nil
        )
      },
      test("open one package per clause") {
        assertTrue(
          ScaladocSnippets.openedPackages(List("zio.query")) == List("zio.query"),
          ScaladocSnippets.openedPackages(List("zio", "query")) == List("zio", "zio.query"),
          ScaladocSnippets.openedPackages(Nil) == Nil
        )
      }
    ),
    suite("imports")(
      test("always import the outer clauses and the file's own package only when the example uses it") {
        val names                                         = Map("zio.query" -> Set("ZQuery"))
        def imports(code: String, clauses: List[String]) = ScaladocSnippets.imports(example(4, code), clauses, names)
        assertTrue(
          imports("val q = ZQuery.succeed(1)", List("zio", "query")) == List("zio._", "zio.query._"),
          imports("val n = 1", List("zio", "query")) == List("zio._"),
          imports("val q = ZQuery", List("zio.query")) == List("zio.query._"),
          imports("val n = 1", List("zio.query")) == Nil,
          imports("val q = ZQuery", Nil) == Nil
        )
      },
      test("keep the import when an import renames away the name the example uses") {
        def needs(code: String*): Boolean = ScaladocSnippets.needsImport(example(4, code*), "zio.query", Set("ZQuery"))
        assertTrue(
          needs("import zio.query.{ZQuery => Q}", "val a = ZQuery"),
          needs("import zio.query.{ZQuery as Q}", "val a = ZQuery")
        )
      },
      test("drop the import when the example binds the name it uses itself or does not use it") {
        def needs(names: Set[String], code: String*): Boolean =
          ScaladocSnippets.needsImport(example(4, code*), "zio.query", names)
        assertTrue(
          !needs(Set("ZQuery"), "import zio.query.ZQuery", "val a = ZQuery.succeed(1)"),
          !needs(Set("Reader"), "final case class Reader(x: Int)", "val r = Reader(1)"),
          !needs(Set("ZQuery"), "val a = zio.query.ZQuery.succeed(1)"),
          !needs(Set("deriver"), "lazy val d: Int = ??? // the deriver of your codecs"),
          !needs(Set("ZQuery"), "import zio.query._", "val a = ZQuery"),
          !needs(Set("ZQuery"), "import zio.query.*", "val a = ZQuery"),
          !needs(Set("ZQuery"), "import zio.query.syntax._", "val a = ZQuery")
        )
      },
      test("keep the import next to an import of another package") {
        val needs = ScaladocSnippets.needsImport(example(4, "import zio._", "val a = ZQuery"), "zio.query", Set("ZQuery"))
        assertTrue(needs)
      }
    ),
    suite("packageNames")(
      test("reads the top-level declarations and the sub-packages of a package") {
        val names = ScaladocSnippets.packageNames(
          Seq(
            "a/Q.scala" -> source(
              "package zio.query",
              "",
              "final class ZQuery[A]",
              "private[query] object Hidden",
              "private val x = 1",
              "  def indented = 1",
              "object ZQuery"
            ),
            "a/R.scala" -> "package zio.query.internal\n\ntrait Reader"
          )
        )
        assertTrue(names("zio.query") == Set("ZQuery", "internal"), names("zio") == Set("query"))
      },
      test("reads indented declarations only from a file that defines implicit members") {
        val names = ScaladocSnippets.packageNames(
          Seq(
            "a/package.scala" -> source("package zio.docs", "implicit class Interp(sc: StringContext) {", "  def md = 1", "}"),
            "a/Plain.scala"   -> source("package zio.docs", "object Plain {", "  val local = 1", "}")
          )
        )("zio.docs")
        assertTrue(names == Set("Interp", "md", "Plain"))
      },
      test("reads the members of a package object at any indentation") {
        val names = ScaladocSnippets.packageNames(Seq("a/p.scala" -> source("package zio", "package object query {", "  def helper = 1")))
        assertTrue(names("zio.query") == Set("helper"))
      },
      test("reads a file by its package clause, not its directory") {
        val names = ScaladocSnippets.packageNames(
          Seq("zio/blocks/datastar/Signal.scala" -> source("package zio.http.datastar", "final class Signal"))
        )
        assertTrue(names("zio.http.datastar") == Set("Signal"), names("zio.http") == Set("datastar"))
      },
      test("reads a Scala 3 package clause with a colon, an annotation and a one-line extension") {
        val content = source("package zio.query:", "", "@deprecated def old = 1", "extension (s: String) def shout = s")
        assertTrue(ScaladocSnippets.packageNames(Seq("a/Api.scala" -> content))("zio.query") == Set("old", "shout"))
      },
      test("stops at the end of a braced package clause, so a sibling clause is not joined to it") {
        val content = source("package a {", "object X", "}", "package c {", "object Y", "}")
        assertTrue(ScaladocSnippets.packageNames(Seq("a/X.scala" -> content)).keySet == Set("a"))
      }
    ),
    suite("objectName")(
      test("is the file name, the line and a hash of the path") {
        val name = ScaladocSnippets.objectName(path, 57)
        assertTrue(
          name.startsWith("A_L57_"),
          name.length == "A_L57_".length + 8,
          ScaladocSnippets.objectName(path, 57) == name,
          ScaladocSnippets.objectName("other/src/main/scala/zio/query/A.scala", 57) != name,
          ScaladocSnippets.objectName("a/1-file.scala", 3).startsWith("S1_file_L3_")
        )
      }
    ),
    suite("render")(
      test("wraps the code in an object with the imports and the source location") {
        val generated = ScaladocSnippets.render(example(4, "val a = ZQuery", "", "  || b"), List("zio._", "zio.query._"))
        val name      = ScaladocSnippets.objectName(path, 4)
        assertTrue(
          generated.fileName == name + ".scala",
          generated.firstCodeLine == 8,
          generated.content ==
            s"""// Generated from $path:4 by zio-sbt-scaladoc. Do not edit.
               |package scaladoccheck
               |
               |import zio._
               |import zio.query._
               |
               |object $name {
               |  val a = ZQuery
               |
               |    || b
               |}
               |""".stripMargin
        )
      },
      test("writes no import lines when there are no imports") {
        val generated = ScaladocSnippets.render(example(4, "val n = 1"), Nil)
        val name      = ScaladocSnippets.objectName(path, 4)
        assertTrue(
          generated.firstCodeLine == 5,
          generated.content ==
            s"""// Generated from $path:4 by zio-sbt-scaladoc. Do not edit.
               |package scaladoccheck
               |
               |object $name {
               |  val n = 1
               |}
               |""".stripMargin
        )
      },
      test("maps a wrapper position back to the Scaladoc line and column") {
        val content   = block("val a = 1", "val b = c")
        val extracted = ScaladocSnippets.extract(path, content).head
        val generated = ScaladocSnippets.render(extracted, List("zio._"))
        // Wrapper line 7 is `  val b = c`; column 10 is `c`. In the source it is line 4, ` * val b = c`, column 11.
        assertTrue(
          generated.mapPosition(generated.firstCodeLine + 1, Some(10)) == Some((4, Some(11), " * val b = c")),
          generated.mapPosition(generated.firstCodeLine, None) == Some((3, None, " * val a = 1")),
          generated.mapPosition(1, Some(0)).isEmpty,
          generated.mapPosition(generated.firstCodeLine + 2, Some(0)).isEmpty
        )
      }
    ),
    suite("generate")(
      test("keeps the import only for the examples that use a name of the package") {
        val demo = source(
          "package zio.query",
          "/**",
          " * {{{",
          " * val s = ZQuery.succeed(1)",
          " * }}}",
          " */",
          "object Demo",
          "/**",
          " * {{{",
          " * val n = 1",
          " * }}}",
          " */"
        )
        val sources = Seq("a/ZQuery.scala" -> "package zio.query\n\nobject ZQuery", "a/Demo.scala" -> demo)
        val files   = ScaladocSnippets.generate(Seq(sources(1)), sources, 3, Platform.Jvm).map(g => g.snippet.line -> g.content).toMap
        assertTrue(files(3).contains("import zio.query._"), !files(9).contains("import zio.query._"))
      },
      test("keeps each example for the Scala versions and platforms its markers allow") {
        val content = source(
          "/**",
          " * {{{",
          " * // scala-3-only",
          " * given Int = 1",
          " * }}}",
          " * {{{",
          " * // no-js",
          " * val f = new java.io.File(\"a\")",
          " * }}}",
          " */"
        )
        def lines(major: Int, platform: Platform) =
          ScaladocSnippets.generate(Seq(path -> content), Nil, major, platform).map(_.snippet.line).toList
        assertTrue(
          lines(3, Platform.Jvm) == List(2, 6),
          lines(2, Platform.Jvm) == List(6),
          lines(3, Platform.Js) == List(2),
          lines(2, Platform.Native) == List(6)
        )
      },
      test("fails when two examples would get the same wrapper name") {
        val content = block("val a = 1")
        val result  = Try(ScaladocSnippets.generate(Seq(path -> content, path -> content), Nil, 3, Platform.Jvm)).failed.toOption
          .map(_.getMessage)
        assertTrue(
          result == Some(s"two examples generate ${ScaladocSnippets.objectName(path, 2)}.scala; rename one of their source files")
        )
      }
    )
  )
}
