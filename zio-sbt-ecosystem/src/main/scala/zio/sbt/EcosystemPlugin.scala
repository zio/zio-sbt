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

import scala.collection.immutable.ListMap
import _root_.scalafix.sbt.ScalafixPlugin
import org.scalafmt.sbt.ScalafmtPlugin
import sbt.Keys.*
import sbt.{Def, *}
import sbtbuildinfo.BuildInfoPlugin
import sbtbuildinfo.BuildInfoPlugin.autoImport.*
import scalafix.sbt.ScalafixPlugin.autoImport.*

object EcosystemPlugin extends AutoPlugin {

  override def trigger = allRequirements

  override def requires: Plugins =
    super.requires && de.heikoseeberger.sbtheader.HeaderPlugin && ScalafixPlugin && ScalafmtPlugin && BuildInfoPlugin

  object autoImport {

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
      addCommand(cCommand)
    }

    def addCommand(command: Commands.ComposableCommand): Seq[Setting[_]] =
      Seq(
        commands += command.toCommand,
        usefulTasksAndSettings += command.toItem
      )

    object Defaults {
      val scala3     = "3.2.1"
      val scala211   = "2.11.12"
      val scala212   = "2.12.17"
      val scala213   = "2.13.10"
      val zioVersion = "2.0.6"
    }

    lazy val scala3: SettingKey[String]     = settingKey[String]("Scala 3 version")
    lazy val scala211: SettingKey[String]   = settingKey[String]("Scala 2.11 version")
    lazy val scala212: SettingKey[String]   = settingKey[String]("Scala 2.12 version")
    lazy val scala213: SettingKey[String]   = settingKey[String]("Scala 2.13 version")
    lazy val zioVersion: SettingKey[String] = settingKey[String]("ZIO version")

    val welcomeBannerEnabled: SettingKey[Boolean] =
      settingKey[Boolean]("Indicates whether or not to enable the welcome banner.")

    val usefulTasksAndSettings: SettingKey[Map[String, String]] = settingKey[Map[String, String]](
      "A map of useful tasks and settings that will be displayed as part of the welcome banner."
    )

  }

  import autoImport.*

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
      licenses               := List("Apache-2.0" -> new URL("http://www.apache.org/licenses/LICENSE-2.0.txt")),
      welcomeBannerEnabled   := true,
      usefulTasksAndSettings := defaultTasksAndSettings,
      scalacOptions          := ScalaCompilerSettings.stdScalacOptions(scalaVersion.value, !isSnapshot.value),
      libraryDependencies ++= {
        if (zioVersion.value.nonEmpty)
          Seq(
            "dev.zio" %% "zio"          % zioVersion.value,
            "dev.zio" %% "zio-test"     % zioVersion.value,
            "dev.zio" %% "zio-test-sbt" % zioVersion.value % Test
          )
        else Seq.empty
      },
      testFrameworks += new TestFramework("zio.test.sbt.ZTestFramework"),
      semanticdbEnabled := scalaVersion.value != scala3.value, // enable SemanticDB
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

  override def projectSettings: Seq[Setting[_]] =
    stdSettings ++ Tasks.settings ++ Commands.settings ++ welcomeMessage

  override def globalSettings: Seq[Def.Setting[_]] =
    super.globalSettings ++ Seq(
      scala3       := Defaults.scala3,
      scala211     := Defaults.scala211,
      scala212     := Defaults.scala212,
      scala213     := Defaults.scala213,
      zioVersion   := Defaults.zioVersion,
      scalaVersion := Defaults.scala213
    )
}
