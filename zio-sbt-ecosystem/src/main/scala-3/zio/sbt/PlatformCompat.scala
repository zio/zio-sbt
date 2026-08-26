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

import sbt.Keys._
import sbt._

/**
 * `scala-3` counterpart of the `scala-2.12` [[PlatformCompat]] - see that file for why this split
 * exists. Neither `sbt-explicit-dependencies` nor `sbt-platform-deps` has an sbt-2.x / Scala-3
 * release yet, so on this axis `stdSettings()` skips the unused-dependency filter tweak, and
 * `enableZIO()` depends on the plain (JVM-only) `%%` operator instead of `%%%`
 * (cross-platform JS/Native support is unavailable here until upstream catches up).
 */
private[sbt] object PlatformCompat {
  def unusedCompileDependenciesFilterSettings: Seq[Setting[_]] = Seq.empty

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
