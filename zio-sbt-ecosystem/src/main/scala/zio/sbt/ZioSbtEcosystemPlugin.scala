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

import com.jsuereth.sbtpgp.SbtPgp.autoImport.*
import de.heikoseeberger.sbtheader.HeaderPlugin
import org.scalafmt.sbt.ScalafmtPlugin
import sbt.Keys.*
import sbt.nio.Keys.{ReloadOnSourceChanges, onChangedBuildSource}
import sbt.{Def, *}
import sbtbuildinfo.BuildInfoPlugin
import scalafix.sbt.ScalafixPlugin

object ZioSbtEcosystemPlugin extends AutoPlugin {

  override def trigger = allRequirements

  override def requires: Plugins =
    super.requires && HeaderPlugin && ScalafixPlugin && ScalafmtPlugin && BuildInfoPlugin

  object autoImport extends ScalaCompilerSettings {

    def addCommand(commandString: List[String], name: String, description: String): Seq[Setting[_]] = {
      val cCommand = Commands.ComposableCommand(commandString, name, description)
      addCommand(cCommand)
    }

    def addCommand(command: Commands.ComposableCommand): Seq[Setting[_]] =
      Seq(
        commands += command.toCommand,
        usefulTasksAndSettings += command.toItem
      )

    lazy val scala3: SettingKey[String]   = settingKey[String]("Scala 3 version")
    lazy val scala211: SettingKey[String] = settingKey[String]("Scala 2.11 version")
    lazy val scala212: SettingKey[String] = settingKey[String]("Scala 2.12 version")
    lazy val scala213: SettingKey[String] = settingKey[String]("Scala 2.13 version")

    val welcomeBannerEnabled: SettingKey[Boolean] =
      settingKey[Boolean]("Indicates whether or not to enable the welcome banner.")

    val usefulTasksAndSettings: SettingKey[Map[String, String]] = settingKey[Map[String, String]](
      "A map of useful tasks and settings that will be displayed as part of the welcome banner."
    )

  }

  import autoImport.*

  private val defaultTasksAndSettings: Map[String, String] = Commands.ComposableCommand.makeHelp ++ ListMap(
//    "build"                                       -> "Lints source files then strictly compiles and runs tests.",
    "enableStrictCompile"                         -> "Enables strict compilation e.g. warnings become errors.",
    "disableStrictCompile"                        -> "Disables strict compilation e.g. warnings are no longer treated as errors.",
    "~compile"                                    -> "Compiles all modules (file-watch enabled)",
    "test"                                        -> "Runs all tests",
    """testOnly *.YourSpec -- -t \"YourLabel\"""" -> "Only runs tests with matching term e.g."
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
    Commands.settings ++ welcomeMessage ++ Seq(
      usefulTasksAndSettings := defaultTasksAndSettings,
      welcomeBannerEnabled   := true
    ) ++ Tasks.settings // ++ Tasks.settings

  override def buildSettings: Seq[Def.Setting[_]] = super.buildSettings ++ Seq(
    scala3             := Versions.scala3,
    scala211           := Versions.scala211,
    scala212           := Versions.scala212,
    scala213           := Versions.scala213,
    scalaVersion       := scala213.value,
    crossScalaVersions := Seq(scala211.value, scala212.value, scala213.value, scala3.value)
  )

  override def globalSettings: Seq[Def.Setting[_]] =
    super.globalSettings ++ Seq(
      licenses       := Seq(License.Apache2),
      organization   := "dev.zio",
      homepage       := Some(url(s"https://zio.dev/${normalizedName.value}")),
      normalizedName := (ThisBuild / name).value.toLowerCase.replaceAll(" ", "-"),
      scmInfo := Some(
        ScmInfo(
          homepage.value.get,
          s"scm:git:git@github.com:zio/${normalizedName}.git"
        )
      ),
      pgpPassphrase        := sys.env.get("PGP_PASSPHRASE").map(_.toArray),
      pgpPublicRing        := file("/tmp/public.asc"),
      pgpSecretRing        := file("/tmp/secret.asc"),
      onChangedBuildSource := ReloadOnSourceChanges
    )
}
