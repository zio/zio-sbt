/*
 * Copyright 2022 dev.zio
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

import scala.Console

import sbt.Keys._
import sbt._
// import org.scalafmt.sbt.ScalafmtPlugin.autoImport._
// import de.heikoseeberger.sbtheader.HeaderPlugin.autoImport._
// import scalafix.sbt.ScalafixPlugin.autoImport._

object Tasks {
  private val strictCompilationProp = "enable.strict.compilation"

  val enableStrictCompile: TaskKey[Unit] =
    taskKey[Unit]("Enables stricter compilation e.g. warnings become errors. Compiler Cat is happy 😺!")

  val disableStrictCompile: TaskKey[Unit] = taskKey[Unit](
    "Disables strict compilation e.g. warnings are no longer treated as errors.  Compiler Cat is aghast at your poor life choices 🙀!"
  )

  private def enableStrictCompileImpl =
    Def.task {
      val log = streams.value.log
      sys.props.put(strictCompilationProp, "true")
      log.info(Console.GREEN + Console.BOLD + s">>> 😸 Enabled strict compilation 😸 <<<" + Console.RESET)
    }

  private def disableStrictCompilePure(logger: String => Unit) = {
    sys.props.put(strictCompilationProp, "false")
    logger(Console.YELLOW + Console.BOLD + ">>> 🙀 Disabled strict compilation 🙀 <<<" + Console.RESET)
  }

  private def disableStrictCompileImpl =
    Def.task {
      val log = streams.value.log
      disableStrictCompilePure(log.info(_))
    }

  val build: TaskKey[Unit] = taskKey[Unit]("Prepares sources, compiles and runs tests.")

  private def buildImpl =
    Def
      .sequential(
        clean,
        enableStrictCompile,
        Compile / compile,
        Test / compile,
        Test / test
      )
      .andFinally(disableStrictCompilePure(s => println(s"[info] $s")))

  lazy val settings: Seq[Setting[_]] = Seq(
    build                := buildImpl.value,
    enableStrictCompile  := enableStrictCompileImpl.value,
    disableStrictCompile := disableStrictCompileImpl.value
  )

}
