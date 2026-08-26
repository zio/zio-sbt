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

import explicitdeps.ExplicitDepsPlugin.autoImport._
import org.portablescala.sbtplatformdeps.PlatformDepsPlugin.autoImport._
import sbt.Keys._
import sbt._

/**
 * The `sbt-explicit-dependencies`/`sbt-platform-deps`-dependent half of [[ScalaCompilerSettings]],
 * split into a `scala-2.12`/`scala-3` cross-source pair because neither plugin has an sbt-2.x /
 * Scala-3 release yet - see the `scala-3` sibling of this file for what that axis loses.
 *
 * `enableZIO`'s body (not just the `%%%` operator alone) has to live in this cross-source split,
 * not behind a plain helper method taking `%%%`'s result as a parameter: `%%%`, like `.value`, is
 * itself a macro that only expands when it appears directly inside a recognized settings-DSL
 * expression (`libraryDependencies += ...`), not behind an ordinary function call.
 */
private[sbt] object PlatformCompat {
  def unusedCompileDependenciesFilterSettings: Seq[Setting[_]] = Seq(
    unusedCompileDependenciesFilter -= moduleFilter("org.scala-js", "scalajs-library")
  )

  // `Test / test`'s value type is `Unit` on this axis; see the `scala-3` sibling for why this
  // can't be a single shared expression.
  def skippedTestSetting: Setting[_] =
    Test / test := { val _ = (Test / compile).value; () }

  def enableZIO(enableStreaming: Boolean, enableTesting: Boolean): Seq[Def.Setting[_]] =
    Seq(libraryDependencies += "dev.zio" %%% "zio" % ZioSbtEcosystemPlugin.autoImport.zioVersion.value) ++
      (if (enableTesting)
         Seq(
           libraryDependencies ++= Seq(
             "dev.zio" %%% "zio-test"     % ZioSbtEcosystemPlugin.autoImport.zioVersion.value % Test,
             "dev.zio" %%% "zio-test-sbt" % ZioSbtEcosystemPlugin.autoImport.zioVersion.value % Test
           ),
           testFrameworks += new TestFramework("zio.test.sbt.ZTestFramework")
         )
       else Seq.empty) ++ {
        if (enableStreaming)
          libraryDependencies += "dev.zio" %%% "zio-streams" % ZioSbtEcosystemPlugin.autoImport.zioVersion.value
        else Seq.empty
      }
}
