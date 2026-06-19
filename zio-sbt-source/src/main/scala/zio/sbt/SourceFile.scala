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

object SourceFile {

  def readSource(path: String): String = {
    val f          = new JFile(path)
    val actualPath = if (f.exists()) path else "../" + path
    val src        = Source.fromFile(actualPath, StandardCharsets.UTF_8.name())
    try src.getLines().mkString("\n")
    finally src.close()
  }

  def fileExtension(path: String): String = {
    val fileName = Paths.get(path).getFileName
    if (fileName == null) ""
    else fileName.toString.split('.').lastOption.getOrElse("")
  }

  def printSource(
    path: String,
    comment: Boolean = true,
    showLineNumbers: Boolean = false,
  ): Unit = {
    val title     = if (comment) s"""title="$path"""" else ""
    val showLines = if (showLineNumbers) "showLineNumbers" else ""
    val header    = Seq(s"```${fileExtension(path)}", title, showLines).filter(_.nonEmpty).mkString(" ")
    println(header)
    println(readSource(path))
    println("```")
  }
}
