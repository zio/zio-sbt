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

import mdoc.{PostModifier, PostModifierContext}

/**
 * An mdoc post-modifier named `embed`. It replaces a code fence whose info
 * string is `scala mdoc:embed:path/to/Example.scala` with the file at that
 * path, printed by `SourceFile.printSource` with a `title`. A
 * `:showLineNumbers` or `:show-line-numbers` suffix adds the `showLineNumbers`
 * flag, and double quotes around the path are removed.
 *
 * An empty path is reported as the mdoc error "embed modifier requires a path
 * argument: mdoc:embed:path/to/file.scala". A file that cannot be read is
 * reported as "Failed to embed source from 'path': " followed by the
 * exception's message. In both cases `process` returns an empty string.
 *
 * The class is listed in `META-INF/services/mdoc.PostModifier`, so mdoc finds
 * it when the library is on its classpath.
 */
class EmbedSourceModifier extends PostModifier {
  override val name = "embed"

  override def process(ctx: PostModifierContext): String = {
    val info           = ctx.info.stripPrefix("embed:").stripPrefix(":")
    val showLineNums   = info.endsWith(":showLineNumbers") || info.endsWith(":show-line-numbers")
    val pathWithQuotes =
      if (showLineNums)
        info.stripSuffix(":showLineNumbers").stripSuffix(":show-line-numbers")
      else info
    // Strip surrounding quotes for forward compatibility with quoted path syntax
    val path =
      if (pathWithQuotes.startsWith("\"") && pathWithQuotes.endsWith("\""))
        pathWithQuotes.substring(1, pathWithQuotes.length - 1)
      else
        pathWithQuotes

    if (path.isEmpty) {
      ctx.reporter.error(
        "embed modifier requires a path argument: mdoc:embed:path/to/file.scala"
      )
      return ""
    }

    val baos = new ByteArrayOutputStream()
    val ps   = new PrintStream(baos)
    try {
      Console.withOut(ps) {
        SourceFile.printSource(path, showLineNumbers = showLineNums)
      }
      ps.flush()
      baos.toString(StandardCharsets.UTF_8.name())
    } catch {
      case e: Exception =>
        ctx.reporter.error(s"Failed to embed source from '$path': ${e.getMessage}")
        ""
    }
  }
}
