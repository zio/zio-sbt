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
import sbt.Keys._
import sbt._

/**
 * Platform-dependent settings for [[ScalaCompilerSettings]]. The `scala-2.12`
 * and `scala-3` source directories each hold a copy of this object: the
 * `scala-2.12` copy adds ZIO dependencies with the cross-platform `%%%`
 * operator of `sbt-platform-deps`, and the `scala-3` copy uses the plain `%%`
 * operator.
 */
private[sbt] object PlatformCompat {
  def unusedCompileDependenciesFilterSettings: Seq[Setting[_]] = Seq(
    unusedCompileDependenciesFilter -= moduleFilter("org.scala-js", "scalajs-library")
  )

  // `Test / test`'s value type is `sbt.protocol.testing.TestResult` on this axis (was `Unit`
  // under sbt 1.x); `Passed` matches the original intent of treating a compiled-but-not-run test
  // suite as a non-failure.
  def skippedTestSetting: Setting[_] =
    Test / test := {
      val _ = (Test / compile).value
      sbt.protocol.testing.TestResult.Passed
    }

  def enableZIO(enableStreaming: Boolean, enableTesting: Boolean): Seq[Def.Setting[_]] =
    Seq(libraryDependencies += "dev.zio" %% "zio" % ZioSbtEcosystemPlugin.autoImport.zioVersion.value) ++
      (if (enableTesting)
         Seq(
           libraryDependencies ++= Seq(
             "dev.zio" %% "zio-test"     % ZioSbtEcosystemPlugin.autoImport.zioVersion.value % Test,
             "dev.zio" %% "zio-test-sbt" % ZioSbtEcosystemPlugin.autoImport.zioVersion.value % Test
           ),
           testFrameworks += new TestFramework("zio.test.sbt.ZTestFramework")
         )
       else Seq.empty) ++ {
        if (enableStreaming)
          libraryDependencies += "dev.zio" %% "zio-streams" % ZioSbtEcosystemPlugin.autoImport.zioVersion.value
        else Seq.empty
      }
}
