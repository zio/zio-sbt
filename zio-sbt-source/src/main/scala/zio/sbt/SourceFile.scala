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

import java.io.{File => JFile}
import java.nio.charset.StandardCharsets
import java.nio.file.Paths

import scala.io.Source

/**
 * Reads source files and prints them as Markdown code fences, for pages that
 * mdoc builds.
 */
object SourceFile {

  /**
   * Returns the content of the file at `path`, its lines joined with `\n` and
   * no line break at the end. When no file exists at `path`, it reads
   * `"../" + path` instead, and throws `java.io.FileNotFoundException` when
   * that file is missing too.
   */
  def readSource(path: String): String = {
    val f          = new JFile(path)
    val actualPath = if (f.exists()) path else "../" + path
    val src        = Source.fromFile(actualPath, StandardCharsets.UTF_8.name())
    try src.getLines().mkString("\n")
    finally src.close()
  }

  /**
   * Returns the extension of the file name of `path`, which [[printSource]]
   * uses as the language of the code fence: `scala` for `a/B.scala`, `gz` for
   * `x.tar.gz`, `gitignore` for `.gitignore`. A file name without a dot is
   * returned whole (`Makefile` gives `Makefile`), and a path without a file
   * name, such as `/`, gives an empty string.
   */
  def fileExtension(path: String): String = {
    val fileName = Paths.get(path).getFileName
    if (fileName == null) ""
    else fileName.toString.split('.').lastOption.getOrElse("")
  }

  /**
   * Prints the file at `path` to `Console.out` as a Markdown code fence: an
   * opening line whose language is [[fileExtension]] of `path`, the content
   * from [[readSource]], and a closing line.
   *
   * @param comment
   *   whether the opening line carries `title="path"`, with `path` as given
   * @param showLineNumbers
   *   whether the opening line carries the `showLineNumbers` flag
   */
  def printSource(
    path: String,
    comment: Boolean = true,
    showLineNumbers: Boolean = false
  ): Unit = {
    val title     = if (comment) s"""title="$path"""" else ""
    val showLines = if (showLineNumbers) "showLineNumbers" else ""
    val header    = Seq(s"```${fileExtension(path)}", title, showLines).filter(_.nonEmpty).mkString(" ")
    println(header)
    println(readSource(path))
    println("```")
  }
}
