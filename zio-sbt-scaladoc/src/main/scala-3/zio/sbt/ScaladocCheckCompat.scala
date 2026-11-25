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

import sbt.Keys.*
import sbt.*

import zio.sbt.scaladoc.ScaladocSnippets.Generated

/**
 * The settings of [[ScaladocCheckPlugin]] that differ between sbt 1.x, which
 * loads the plugin built for Scala 2.12, and sbt 2.x, which loads the one built
 * for Scala 3. sbt 2.x caches task results, so its copy (in `scala-3`) wraps
 * the examples' `dependencyClasspath`, their `sourcePositionMappers` and their
 * compile in `Def.uncached`. sbt 1.x has no `Def.uncached`, so its copy (in
 * `scala-2.12`) leaves it out.
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
  ): Seq[Setting[?]] = Seq(
    config / dependencyClasspath := Def.uncached(
      ((Compile / fullClasspath).value ++ (config / managedClasspath).value).distinct
    ),
    config / sourcePositionMappers := Def.uncached(
      (config / sourcePositionMappers).value :+
        ScaladocCheckPlugin.positionMapper((LocalRootProject / baseDirectory).value, generate.value)
    )
  )

  /** Compiles the examples of `config`. */
  def compileExamples(config: Configuration): Def.Initialize[Task[Unit]] =
    Def.task(Def.uncached { val _ = (config / compile).value; () })
}
