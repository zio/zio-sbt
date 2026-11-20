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
package zio.sbt

import sbt.Keys._
import sbt._

import zio.sbt.scaladoc.ScaladocSnippets.Generated

/**
 * sbt 1.x counterpart of the `scala-3` [[ScaladocCheckCompat]]. sbt 1.x does
 * not cache task results, so these settings need no `Def.uncached`.
 */
private[sbt] object ScaladocCheckCompat {

  /**
   * The examples' classpath (the module's main classpath plus the libraries
   * added with `% ScaladocCheck`) and the mapping of compile errors back to the
   * Scaladoc lines.
   */
  def classpathAndPositionSettings(
    config: Configuration,
    generate: TaskKey[Seq[(File, Generated)]]
  ): Seq[Setting[_]] = Seq(
    config / dependencyClasspath :=
      ((Compile / fullClasspath).value ++ (config / managedClasspath).value).distinct,
    config / sourcePositionMappers := (config / sourcePositionMappers).value :+
      ScaladocCheckPlugin.positionMapper((LocalRootProject / baseDirectory).value, generate.value)
  )

  /** Compiles the examples of `config`. */
  def compileExamples(config: Configuration): Def.Initialize[Task[Unit]] =
    Def.task { val _ = (config / compile).value; () }
}
