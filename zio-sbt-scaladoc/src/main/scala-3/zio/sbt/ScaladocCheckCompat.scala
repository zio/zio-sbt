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
 * sbt 2.x caches task results, which needs a way to serialize them. A
 * classpath, a function and a compile analysis have none, so these settings opt
 * out of the cache with `Def.uncached`, which sbt 1.x does not have (see the
 * `scala-2.12` [[ScaladocCheckCompat]]). `Def.uncached` has to appear directly
 * in the setting, like `.value`, so the settings themselves live here.
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
