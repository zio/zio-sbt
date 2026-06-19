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

class EmbedSourceModifier extends PostModifier {
  override val name = "embed"

  override def process(ctx: PostModifierContext): String = {
    val info         = ctx.info.stripPrefix("embed:").stripPrefix(":")
    val showLineNums = info.endsWith(":showLineNumbers")
    val path         = if (showLineNums) info.stripSuffix(":showLineNumbers") else info

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
