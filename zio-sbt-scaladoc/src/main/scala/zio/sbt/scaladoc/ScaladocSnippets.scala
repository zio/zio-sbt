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

import scala.collection.mutable.ListBuffer

/**
 * Finds the code examples in Scaladoc comments and turns each one into a source
 * file that a compiler can check. Plain functions over source text: no sbt
 * types, so [[zio.sbt.ScaladocCheckPlugin]] calls them from a task and the unit
 * tests call them directly.
 *
 * Ported from zio-blocks' `project/ScaladocSnippets.scala`. The changes: the
 * wrapper's imports come from each file's `package` clauses instead of a
 * folder-to-package map, there are platform markers, and every wrapper line is
 * mapped back to its Scaladoc line.
 */
object ScaladocSnippets {

  /** A platform an example can be compiled for. */
  sealed abstract class Platform(val name: String) extends Product with Serializable

  /** The platforms an example can be compiled for: `Jvm`, `Js` and `Native`. */
  object Platform {

    /** The JVM. */
    case object Jvm extends Platform("jvm")

    /** Scala.js. */
    case object Js extends Platform("js")

    /** Scala Native. */
    case object Native extends Platform("native")

    /**
     * All three platforms: the ones an example without a platform marker is
     * compiled for.
     */
    val all: Set[Platform] = Set(Jvm, Js, Native)
  }

  /**
   * One code example.
   *
   * @param sourcePath
   *   path of the source file, relative to the build root (absolute when the
   *   file lies outside it), with `/` separators
   * @param line
   *   1-based line of the line that opens the code block
   * @param code
   *   the example, with the comment margin removed and each `\$` read as `$`,
   *   as Scaladoc renders it
   * @param margins
   *   for each line of `code`, how many characters of the source line the
   *   margin removal dropped, to map a column back to the source line
   * @param sourceLines
   *   the source lines `code` was read from, unchanged
   * @param onlyScalaMajor
   *   `Some(3)` for a `// scala-3-only` example, `Some(2)` for a
   *   `// scala-2-only` one
   * @param platforms
   *   the platforms the example's markers allow
   */
  final case class Snippet(
    sourcePath: String,
    line: Int,
    code: List[String],
    margins: List[Int],
    sourceLines: List[String],
    onlyScalaMajor: Option[Int],
    platforms: Set[Platform]
  ) {

    /** Whether this example is compiled for `scalaMajor` on `platform`. */
    def appliesTo(scalaMajor: Int, platform: Platform): Boolean =
      onlyScalaMajor.forall(_ == scalaMajor) && platforms.contains(platform)
  }

  /**
   * The wrapper source file of one example.
   *
   * @param fileName
   *   the wrapper's file name, unique within one module
   * @param content
   *   the wrapper's source code
   * @param snippet
   *   the example it wraps
   * @param firstCodeLine
   *   1-based line of the wrapper that holds the example's first line
   */
  final case class Generated(fileName: String, content: String, snippet: Snippet, firstCodeLine: Int) {

    /**
     * The position in the source file of a 1-based `line` and 0-based `column`
     * of the wrapper, as `(line, column, sourceLine)`, or `None` for a line
     * outside the example (the header, the imports or the closing brace).
     */
    def mapPosition(line: Int, column: Option[Int]): Option[(Int, Option[Int], String)] = {
      val index = line - firstCodeLine
      if (index < 0 || index >= snippet.code.length) None
      else {
        val sourceColumn = column.map(c => math.max(0, c - indent) + snippet.margins(index))
        Some((snippet.line + 1 + index, sourceColumn, snippet.sourceLines(index)))
      }
    }
  }

  /** The indentation of an example line inside its wrapper object. */
  val indent: Int = 2

  /** The package of every generated wrapper. */
  val wrapperPackage: String = "scaladoccheck"

  /**
   * Removes the leading ` * ` of a Scaladoc line, keeping the indentation after
   * it.
   */
  def stripMargin(line: String): String = line.replaceFirst("""^\s*\*(?: |$)""", "")

  /**
   * Returns the multi-line code blocks (between triple braces, or in a `scala`
   * fence) inside the Scaladoc comments of `content`, leaving out the ones
   * marked `// no-check: <reason>`. Throws `IllegalArgumentException`, with
   * `path:line: message`, for an unknown or contradicting marker, a
   * `// no-check` without a reason, a block that is never closed, and a code
   * line of a triple-brace block that contains three closing braces.
   */
  def extract(sourcePath: String, content: String): List[Snippet] = {
    val lines  = content.split("\n", -1).map(_.stripSuffix("\r"))
    val result = ListBuffer.empty[Snippet]
    var inDoc  = false
    var i      = 0
    while (i < lines.length) {
      if (!inDoc && lines(i).contains("/**")) inDoc = true
      if (inDoc) {
        closingMarker(stripMargin(lines(i)).trim) match {
          case Some(close) =>
            val start   = i + 1
            val code    = ListBuffer.empty[String]
            val margins = ListBuffer.empty[Int]
            val raw     = ListBuffer.empty[String]
            i += 1
            while (i < lines.length && stripMargin(lines(i)).trim != close) {
              if (lines(i).contains("*/")) fail(sourcePath, start, "code block is never closed")
              val stripped = stripMargin(lines(i))
              if (close == "}}}" && stripped.contains("}}}"))
                fail(
                  sourcePath,
                  start,
                  "a code line contains }}}, which ends the Scaladoc code block early; reword it (e.g. split the braces or drop the output comment)"
                )
              // Scaladoc renders `\$` as `$` (`$name` alone would be a doc variable), so compile what
              // the reader sees: `sql"... \$id"` is the interpolation `sql"... $id"`.
              code += stripped.replace("\\$", "$")
              margins += lines(i).length - stripped.length
              raw += lines(i)
              i += 1
            }
            if (i == lines.length) fail(sourcePath, start, "code block is never closed")
            toSnippet(sourcePath, start, code.toList, margins.toList, raw.toList).foreach(result += _)
          case None => ()
        }
        if (lines(i).contains("*/")) inDoc = false
      }
      i += 1
    }
    result.toList
  }

  /**
   * The package clauses at the top of a file, in order: `package zio` then
   * `package query` gives `List("zio", "query")`, and `package zio.query` gives
   * `List("zio.query")`. A `package object query` clause counts as `query`.
   * Scala 3's `package zio.query:` is read the same way.
   */
  def packageClauses(content: String): List[String] =
    content.split("\n", -1).toList.map(_.trim).takeWhile(isHeaderLine).flatMap(packageClause)

  /**
   * The packages whose names the code of a file sees without importing them,
   * one per package clause, outermost first: `package zio` then `package query`
   * gives `List("zio", "zio.query")`.
   */
  def openedPackages(clauses: List[String]): List[String] =
    clauses.indices.toList.map(i => clauses.take(i + 1).mkString("."))

  // A name the code uses unqualified: an identifier not preceded by a letter, digit, `_` or `.`.
  // `Stream` in `zio.stream.Stream` is qualified, so it does not count.
  private val mentionedIdentifier = """(?<![\w.])[A-Za-z_]\w*""".r

  // A name an import line brings into scope: an identifier not followed by `.` (a path segment), by `=>` or by
  // Scala 3 `as` (a rename, whose left side is hidden). `Stream` in `import a.b.Stream` and in `import a.{Stream, X}`
  // counts.
  private val importedIdentifier = """(?<!\w)[A-Za-z_]\w*(?!\w)(?!\s*\.)(?!\s*=>)(?!\s+as\b)""".r

  // A declaration: `class X`, `object X`, `def x`, ..., after optional annotations (`@deprecated`), an optional
  // one-line `extension (...)` and optional non-private modifiers. Matched from the start of a line, so `private` and
  // `private[pkg]` declarations never match.
  private val declaration =
    """(?:@\w+(?:\([^)]*\))?\s+)*(?:extension\s*\([^)]*\)\s*)?(?:(?:final|sealed|abstract|case|implicit|inline|open|opaque|override|lazy|transparent|infix|erased)\s+)*(?:class|trait|object|type|enum|def|val|var)\s+([A-Za-z_]\w*)""".r

  private def declaredName(line: String): List[String] =
    declaration.findPrefixMatchOf(line).map(_.group(1)).toList

  // A line that can sit in the header of a file, before its first declaration: a package clause, an import, a comment
  // or a blank line. `takeWhile` over these reads a file's package clauses without reading past the header.
  private def isHeaderLine(trimmed: String): Boolean =
    trimmed.isEmpty || trimmed.startsWith("package ") || trimmed.startsWith("import ") ||
      trimmed.startsWith("//") || trimmed.startsWith("/*") || trimmed.startsWith("*")

  // A file whose members a reader uses without naming them: one that defines an implicit class or def, an extension or
  // a package object, or a trait (a package object mixes traits in, as `package object html extends HtmlElements`).
  // Its indented declarations count as names of the package (see `fileNames`).
  private val membersProvider = """(?:implicit\s+(?:final\s+)?(?:class|def)\b|extension\b|package object\b)""".r

  /**
   * The names that `sources`, the `(path, content)` files of a module and of
   * the modules it depends on, contribute to each package. A file's package is
   * read from its `package` clauses, not its directory. For each file of a
   * package, every declaration (`class`, `trait`, `object`, `type`, `enum`,
   * `def`, `val`, `var`, not `private`) at column 0 counts; in a file that
   * defines implicit members (an implicit class or def, an extension, a package
   * object, a line starting with `trait `) indented declarations count too,
   * because a reader uses those without naming their owner (`md` in `md"..."`).
   * Indented names over-include, which only keeps the import. For each file of
   * a sub-package, its first name below the package counts (`stream` for
   * `zio.stream`).
   *
   * Givens and anonymous implicit instances are not names. An example that
   * needs one only through the wrapper's import fails to compile, so it then
   * needs its own import.
   */
  def packageNames(sources: Seq[(String, String)]): Map[String, Set[String]] =
    sources.flatMap { case (_, content) => fileNames(content) }.groupBy { case (pkg, _) => pkg }.map {
      case (pkg, pairs) => pkg -> pairs.map { case (_, name) => name }.toSet
    }

  // The (package, name) pairs one file contributes: the names it declares in its package, and the name of each
  // package between the root and its own package (`zio` and `query` for `zio.query.internal`, so that `internal` is
  // reached from `zio.query`).
  private def fileNames(content: String): List[(String, String)] = {
    val raw     = content.split("\n", -1).toList
    val trimmed = raw.map(_.trim)
    val clauses = trimmed.takeWhile(isHeaderLine).flatMap(packageClause)
    if (clauses.isEmpty) Nil
    else {
      val full    = clauses.mkString(".")
      val members =
        // A declaration in a file that defines implicit members counts at any indentation, as the members of an
        // implicit class, an extension or a package object are used without their names. Elsewhere only a column-0
        // declaration counts: a method's local `val x` is not a name of the package.
        if (trimmed.exists(line => membersProvider.findPrefixOf(line).isDefined) || raw.exists(_.startsWith("trait ")))
          trimmed.flatMap(declaredName)
        else raw.flatMap(declaredName)
      val segments  = full.split('.').toList
      val ancestors = (1 until segments.length).toList.map(i => (segments.take(i).mkString("."), segments(i)))
      members.map(name => (full, name)) ++ ancestors
    }
  }

  // The name a `package` or `package object` clause adds to the package, or Nil for other lines.
  private def packageClause(trimmed: String): List[String] = {
    val name =
      if (trimmed.startsWith("package object ")) Some(trimmed.stripPrefix("package object "))
      else if (trimmed.startsWith("package ")) Some(trimmed.stripPrefix("package "))
      else None
    name.map(_.trim.takeWhile(c => !c.isWhitespace && !"{:;}".contains(c))).filter(_.nonEmpty).toList
  }

  /**
   * Whether `snippet` already imports everything it gets from `pkg`: a line
   * starting `import <pkg>._` (or `.*`), or one starting `import <pkg>.syntax.`
   * (as in `._` or `.{`), whose `syntax` objects hold extension methods that no
   * name check can find. Other sub-package imports do not count: they bring in
   * only part of the package.
   */
  def importsPackage(snippet: Snippet, pkg: String): Boolean = {
    val quoted   = java.util.regex.Pattern.quote(pkg)
    val covering = ("import " + quoted + """(?:\._(?!\w)|\.\*(?!\w)|\.syntax[._{])""").r
    snippet.code.exists(line => covering.findPrefixOf(line.trim).isDefined)
  }

  /**
   * Whether the wrapper of `snippet` needs `import <pkg>._`: the example uses
   * one of `names`, the names of `pkg` from [[packageNames]], that it does not
   * bind itself by an import or a declaration of its own (those hide the
   * wrapper's import, which would then be unused), and it does not import `pkg`
   * itself ([[importsPackage]]).
   *
   * The check errs towards keeping the import: a name it cannot tell apart
   * keeps it, which is then used or at worst warns. The one way to drop it
   * wrongly is a name the example reaches without writing it, which
   * [[packageNames]] does not list: a given or an anonymous implicit. That
   * fails to compile loudly, so the example then needs its own import.
   */
  def needsImport(snippet: Snippet, pkg: String, names: Set[String]): Boolean = {
    // A `//` comment is not code: a word in it (`the deriver of your codecs`) is not a use of a name.
    val used = snippet.code
      .map(line => line.indexOf("//") match { case -1 => line; case i => line.take(i) })
      .flatMap(line => mentionedIdentifier.findAllIn(line).toList)
      .toSet
    val imported = snippet.code
      .filter(_.trim.startsWith("import "))
      .flatMap(line => importedIdentifier.findAllIn(line).toList)
      .toSet
    val declared = snippet.code.flatMap(line => declaredName(line.trim)).toSet
    !importsPackage(snippet, pkg) && ((used intersect names) -- (imported ++ declared)).nonEmpty
  }

  /**
   * The imports of the wrapper of `snippet`, a file whose package clauses are
   * `clauses`. Every package the file's clauses open is imported, outermost
   * first; the file's own package (the last one) only when [[needsImport]] says
   * so. The outer ones are always imported: an outer package such as `zio` gets
   * most of its names from library jars, which [[packageNames]] cannot see.
   */
  def imports(snippet: Snippet, clauses: List[String], namesByPackage: Map[String, Set[String]]): List[String] = {
    val opened = openedPackages(clauses)
    if (opened.isEmpty) Nil
    else {
      val own = opened.last
      opened.init.map(_ + "._") ++
        (if (needsImport(snippet, own, namesByPackage.getOrElse(own, Set.empty[String]))) List(own + "._") else Nil)
    }
  }

  /**
   * The name of the wrapper object for the example at `line` of `sourcePath`:
   * the file's name, the line and a short hash of the path, as
   * `DataSource_L57_3f9a1c2e`. Short, stable, and unique even when two files
   * share a name.
   */
  def objectName(sourcePath: String, line: Int): String = {
    val base = sourcePath.split('/').last.stripSuffix(".scala").replaceAll("[^A-Za-z0-9_]", "_")
    val safe = if (base.isEmpty || base.head.isDigit) "S" + base else base
    s"${safe}_L${line}_${hash(sourcePath)}"
  }

  private def hash(text: String): String =
    java.security.MessageDigest
      .getInstance("SHA-1")
      .digest(text.getBytes("UTF-8"))
      .take(4)
      .map(b => "%02x".format(b & 0xff))
      .mkString

  /** The wrapper source file of `snippet`, with `imports` written above it. */
  def render(snippet: Snippet, imports: List[String]): Generated = {
    val name   = objectName(snippet.sourcePath, snippet.line)
    val header = List(
      s"// Generated from ${snippet.sourcePath}:${snippet.line} by zio-sbt-scaladoc. Do not edit.",
      s"package $wrapperPackage",
      ""
    ) ++ (if (imports.isEmpty) Nil else imports.map("import " + _) :+ "") :+ s"object $name {"
    val body    = snippet.code.map(line => if (line.trim.isEmpty) "" else (" " * indent) + line)
    val content = (header ++ body ++ List("}", "")).mkString("\n")
    Generated(name + ".scala", content, snippet, header.length + 1)
  }

  /**
   * Turns the `(path, content)` source files of one module into wrapper files
   * for one Scala major version and platform. `own` are the module's files for
   * that version and platform; `visible` are those of the module and of the
   * modules it depends on, from which [[packageNames]] reads the names each
   * package contributes. Throws `IllegalArgumentException` for a malformed
   * block (see [[extract]]) and when two examples get the same wrapper file
   * name.
   */
  def generate(
    own: Seq[(String, String)],
    visible: Seq[(String, String)],
    scalaMajor: Int,
    platform: Platform
  ): Seq[Generated] = {
    val namesByPackage = packageNames(visible)
    val files          = own.flatMap { case (path, content) =>
      val clauses = packageClauses(content)
      extract(path, content)
        .filter(_.appliesTo(scalaMajor, platform))
        .map(snippet => render(snippet, imports(snippet, clauses, namesByPackage)))
    }
    files.groupBy(_.fileName).collectFirst {
      case (name, same) if same.size > 1 =>
        throw new IllegalArgumentException(s"two examples generate $name; rename one of their source files")
    }
    files
  }

  private def closingMarker(trimmed: String): Option[String] =
    if (trimmed == "{{{") Some("}}}")
    else if (trimmed == "```scala") Some("```")
    else None

  // A comment line that is, or looks like, a marker. Plain comments (`// Build a cache:`) are not markers.
  private val markerLike = """// (?:scala|no-|no check|jvm\b|js\b|native\b)""".r

  private val platformOnly: Map[String, Platform] =
    Map("// jvm-only" -> Platform.Jvm, "// js-only" -> Platform.Js, "// native-only" -> Platform.Native)

  private val platformNot: Map[String, Platform] = Map("// no-js" -> Platform.Js, "// no-native" -> Platform.Native)

  private val knownMarkers =
    "`// scala-3-only`, `// scala-2-only`, `// jvm-only`, `// js-only`, `// native-only`, `// no-js`, `// no-native` or `// no-check: <reason>`"

  private def toSnippet(
    sourcePath: String,
    line: Int,
    code: List[String],
    margins: List[Int],
    raw: List[String]
  ): Option[Snippet] = {
    val markers = code.map(_.trim).dropWhile(_.isEmpty).takeWhile(t => markerLike.findPrefixOf(t).isDefined)
    markers.foreach { marker =>
      if (marker.startsWith("// no-check") || marker.startsWith("// no check")) {
        if (!marker.startsWith("// no-check:") || marker.stripPrefix("// no-check:").trim.isEmpty)
          fail(sourcePath, line, "`// no-check` needs a reason, as in `// no-check: needs a running database`")
      } else if (
        marker != "// scala-3-only" && marker != "// scala-2-only" && !platformOnly.contains(marker) &&
        !platformNot.contains(marker)
      )
        fail(sourcePath, line, s"unknown marker `$marker`; use $knownMarkers")
    }
    if (markers.exists(_.startsWith("// no-check:"))) None
    else {
      val scalaMajors = markers.collect { case "// scala-3-only" => 3; case "// scala-2-only" => 2 }.distinct
      if (scalaMajors.size > 1)
        fail(sourcePath, line, "`// scala-2-only` and `// scala-3-only` contradict each other; keep one")
      val only      = markers.collect(platformOnly).toSet
      val excluded  = markers.collect(platformNot).toSet
      val platforms = (if (only.isEmpty) Platform.all else only) -- excluded
      if (only.size > 1 || platforms.isEmpty)
        fail(sourcePath, line, s"the markers ${markers.mkString("`", "`, `", "`")} leave no platform to compile on")
      Some(Snippet(sourcePath, line, code, margins, raw, scalaMajors.headOption, platforms))
    }
  }

  private def fail(sourcePath: String, line: Int, message: String): Nothing =
    throw new IllegalArgumentException(s"$sourcePath:$line: $message")
}
