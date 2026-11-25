/*
 * Copyright 2022-2023 dev.zio
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

/**
 * The sbt tasks `build`, `enableStrictCompile` and `disableStrictCompile`.
 */
object Tasks {
  private val strictCompilationProp = "enable.strict.compilation"

  /**
   * Sets the system property `enable.strict.compilation` to `true` and logs an
   * info message. The settings of this plugin do not read that property; a
   * build can read it in its own settings.
   */
  val enableStrictCompile: TaskKey[Unit] =
    taskKey[Unit]("Enables stricter compilation e.g. warnings become errors. Compiler Cat is happy 😺!")

  /**
   * Sets the system property `enable.strict.compilation` to `false` and logs an
   * info message.
   */
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

  /**
   * Runs `clean`, `enableStrictCompile`, `Compile / compile`, `Test / compile`
   * and `Test / test` in order. When the sequence ends, whether it succeeded or
   * failed, it sets `enable.strict.compilation` back to `false`.
   */
  val build: TaskKey[Unit] = taskKey[Unit]("Prepares sources, compiles and runs tests.")

  private def buildImpl =
    Def
      .sequential(
        clean,
        enableStrictCompile,
        Compile / compile,
        Test / compile,
        // Discards to `Unit` explicitly rather than sequencing `Test / test` directly: its value
        // type is `Unit` under sbt 1.x but `sbt.protocol.testing.TestResult` under sbt 2.x, and
        // `build`, the task this whole sequence feeds, is declared `TaskKey[Unit]` on both.
        Def.task { val _ = (Test / test).value; () }
      )
      .andFinally(disableStrictCompilePure(s => println(s"[info] $s")))

  /**
   * Settings that bind the `build`, `enableStrictCompile` and
   * `disableStrictCompile` tasks.
   */
  lazy val settings: Seq[Setting[_]] = Seq(
    build                := buildImpl.value,
    enableStrictCompile  := enableStrictCompileImpl.value,
    disableStrictCompile := disableStrictCompileImpl.value
  )

}
