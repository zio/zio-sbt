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

import scala.collection.immutable.ListMap

import _root_.scalafix.sbt.ScalafixPlugin
import org.scalafmt.sbt.ScalafmtPlugin
import sbt.Keys._
import sbt._
import sbtbuildinfo.BuildInfoPlugin
import sbtbuildinfo.BuildInfoPlugin.autoImport._
import scalafix.sbt.ScalafixPlugin.autoImport._

object ZioEcosystemProjectPlugin extends AutoPlugin {

  override def trigger = allRequirements

  override def requires: Plugins =
    super.requires && de.heikoseeberger.sbtheader.HeaderPlugin && ScalafixPlugin && ScalafmtPlugin && BuildInfoPlugin

  object autoImport extends Versions {

    sealed trait ZIOSeries {
      def version: String
    }

    object ZIOSeries {

      case object Series1X extends ZIOSeries {
        override val version: String = versions.zio1xVersion
      }

      case object Series2X extends ZIOSeries {
        override val version: String = versions.zio2xVersion
      }

    }

    def buildInfoSettings(packageName: String): Seq[Setting[_ <: Object]] =
      Seq(
        buildInfoKeys := Seq[BuildInfoKey](
          name,
          version,
          scalaVersion,
          sbtVersion,
          isSnapshot
        ),
        buildInfoPackage := packageName
      )

    def addCommand(commandString: List[String], name: String, description: String): Seq[Setting[_]] = {
      val cCommand = Commands.ComposableCommand(commandString, name, description)
      Seq(
        commands += cCommand.toCommand,
        usefulTasksAndSettings += cCommand.toItem
      )
    }

    val zioSeries: SettingKey[ZIOSeries] = settingKey[ZIOSeries]("Indicates whether to use ZIO 2.x or ZIO 1.x.")

    val needsZio: SettingKey[Boolean] = settingKey[Boolean]("Indicates whether or not the project needs ZIO libraries.")

    val welcomeBannerEnabled: SettingKey[Boolean] =
      settingKey[Boolean]("Indicates whether or not to enable the welcome banner.")

    val usefulTasksAndSettings: SettingKey[Map[String, String]] = settingKey[Map[String, String]](
      "A map of useful tasks and settings that will be displayed as part of the welcome banner."
    )

  }

  // Keep this consistent with the version in .core-tests/shared/src/test/scala/REPLSpec.scala
  makeReplSettings {
    """|import zio._
       |import zio.console._
       |import zio.duration._
       |import zio.Runtime.default._
       |implicit class RunSyntax[A](io: ZIO[ZEnv, Any, A]){ def unsafeRun: A = Runtime.default.unsafeRun(io.provideLayer(ZEnv.live)) }
    """.stripMargin
  }

  // Keep this consistent with the version in .streams-tests/shared/src/test/scala/StreamREPLSpec.scala
  makeReplSettings {
    """|import zio._
       |import zio.console._
       |import zio.duration._
       |import zio.stream._
       |import zio.Runtime.default._
       |implicit class RunSyntax[A](io: ZIO[ZEnv, Any, A]){ def unsafeRun: A = Runtime.default.unsafeRun(io.provideLayer(ZEnv.live)) }
    """.stripMargin
  }

  private def makeReplSettings(initialCommandsStr: String) =
    Seq(
      // In the repl most warnings are useless or worse.
      // This is intentionally := as it's more direct to enumerate the few
      // options we do want than to try to subtract off the ones we don't.
      // One of -Ydelambdafy:inline or -Yrepl-class-based must be given to
      // avoid deadlocking on parallel operations, see
      //   https://issues.scala-lang.org/browse/SI-9076
      Compile / console / scalacOptions := Seq(
        "-Ypartial-unification",
        "-language:higherKinds",
        "-language:existentials",
        "-Yno-adapted-args",
        "-Xsource:2.13",
        "-Yrepl-class-based"
      ),
      Compile / console / initialCommands := initialCommandsStr
    )

  import autoImport._

  private val defaultTasksAndSettings: Map[String, String] = Commands.ComposableCommand.makeHelp ++ ListMap(
    "build"                                       -> "Lints source files then strictly compiles and runs tests.",
    "enableStrictCompile"                         -> "Enables strict compilation e.g. warnings become errors.",
    "disableStrictCompile"                        -> "Disables strict compilation e.g. warnings are no longer treated as errors.",
    "~compile"                                    -> "Compiles all modules (file-watch enabled)",
    "test"                                        -> "Runs all tests",
    """testOnly *.YourSpec -- -t \"YourLabel\"""" -> "Only runs tests with matching term e.g."
  )

  def stdSettings: Seq[Setting[_]] =
    Seq(
      crossScalaVersions     := Seq(versions.Scala212, versions.Scala213, versions.Scala3),
      scalaVersion           := versions.Scala213,
      licenses               := List("Apache-2.0" -> new URL("http://www.apache.org/licenses/LICENSE-2.0.txt")),
      zioSeries              := ZIOSeries.Series2X,
      needsZio               := true,
      welcomeBannerEnabled   := true,
      usefulTasksAndSettings := defaultTasksAndSettings,
      scalacOptions          := ScalaCompilerSettings.stdScalacOptions(scalaVersion.value, !isSnapshot.value),
      libraryDependencies ++= {
        if (needsZio.value)
          Seq(
            "dev.zio" %% "zio"          % zioSeries.value.version,
            "dev.zio" %% "zio-test"     % zioSeries.value.version,
            "dev.zio" %% "zio-test-sbt" % zioSeries.value.version % Test
          )
        else Seq.empty
      },
      testFrameworks += new TestFramework("zio.test.sbt.ZTestFramework"),
      semanticdbEnabled := scalaVersion.value != versions.Scala3, // enable SemanticDB
      semanticdbOptions += "-P:semanticdb:synthetics:on",
      semanticdbVersion                      := scalafixSemanticdb.revision, // use Scalafix compatible version
      ThisBuild / scalafixScalaBinaryVersion := CrossVersion.binaryScalaVersion(scalaVersion.value),
      ThisBuild / scalafixDependencies ++= List(
        "com.github.liancheng" %% "organize-imports" % "0.6.0",
        "com.github.vovapolu"  %% "scaluzzi"         % "0.1.23"
      ),
      Test / parallelExecution := !sys.env.contains("CI"),
      incOptions ~= (_.withLogRecompileOnMacro(false)),
      autoAPIMappings := true
    )

  def welcomeMessage: Setting[String] =
    onLoadMessage := {
      if (welcomeBannerEnabled.value) {
        import scala.Console

        val maxLen = usefulTasksAndSettings.value.keys.map(_.length).max

        def normalizedPadding(s: String) = " " * (maxLen - s.length)

        def item(text: String): String = s"${Console.GREEN}> ${Console.CYAN}$text${Console.RESET}"

        s"""|${Banner.trueColor(s"${name.value} v.${version.value}")}
            |Useful sbt tasks:
            |${usefulTasksAndSettings.value.map { case (task, description) =>
          s"${item(task)} ${normalizedPadding(task)}${description}"
        }
          .mkString("\n")}
      """.stripMargin

      } else ""
    }

  override def projectSettings: Seq[Setting[_]] = stdSettings ++ Tasks.settings ++ Commands.settings ++ welcomeMessage
}
