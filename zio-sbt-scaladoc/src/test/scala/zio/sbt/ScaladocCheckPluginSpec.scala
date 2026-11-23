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

import sbt._

import zio.sbt.scaladoc.ScaladocSnippets.Platform
import zio.test._

object ScaladocCheckPluginSpec extends ZIOSpecDefault {

  def spec: Spec[TestEnvironment, Any] = suite("ScaladocCheckPlugin")(
    test("exampleScalacOptions drops the fatal-warning and SemanticDB flags and keeps the rest") {
      val options = Seq(
        "-deprecation",
        "-Xfatal-warnings",
        "-Werror",
        "-Xplugin:/cache/scalajs-compiler_2.13.18-1.23.0.jar",
        "-Xplugin:/cache/semanticdb-scalac_2.13.18-4.13.0.jar",
        "-P:semanticdb:targetroot:/out/classes",
        "-Xsemanticdb",
        "-semanticdb-target",
        "/out/classes",
        "-Xmax-classfile-name",
        "242"
      )
      assertTrue(
        ScaladocCheckPlugin.exampleScalacOptions(options) == Seq(
          "-deprecation",
          "-Xplugin:/cache/scalajs-compiler_2.13.18-1.23.0.jar",
          "-Xmax-classfile-name",
          "242"
        )
      )
    },
    test("platformOf reads the platform from the runtime library sbt-scalajs or sbt-scala-native adds") {
      assertTrue(
        ScaladocCheckPlugin.platformOf(Seq("dev.zio" %% "zio" % "2.1.27")) == Platform.Jvm,
        ScaladocCheckPlugin.platformOf(Seq("org.scala-js" % "scalajs-library_2.13" % "1.23.0")) == Platform.Js,
        ScaladocCheckPlugin.platformOf(Seq("org.scala-native" %% "nativelib" % "0.5.12")) == Platform.Native
      )
    }
  )
}
