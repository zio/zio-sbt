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

import com.jsuereth.sbtpgp.SbtPgp.autoImport._
import com.typesafe.tools.mima.plugin.MimaPlugin
import de.heikoseeberger.sbtheader.HeaderPlugin
import org.scalafmt.sbt.ScalafmtPlugin
import sbt.Keys._
import sbt.nio.Keys.{ReloadOnSourceChanges, onChangedBuildSource}
import sbt.{Def, _}
import sbtdynver.DynVerPlugin
import scalafix.sbt.ScalafixPlugin

import zio.sbt.ZioSbtShared.autoImport.{banners, usefulTasksAndSettings, welcomeMessage}

object ZioSbtEcosystemPlugin extends AutoPlugin {

  override def trigger = allRequirements

  override def requires: Plugins =
    super.requires && HeaderPlugin && ScalafixPlugin && ScalafmtPlugin && MimaPlugin && DynVerPlugin && ZioSbtCrossbuildPlugin

  object autoImport extends ScalaCompilerSettings with MimaSettings {

    def addCommand(commandString: List[String], name: String, description: String): Seq[Setting[_]] = {
      val cCommand = Commands.ComposableCommand(commandString, name, description)
      addCommand(cCommand)
    }

    def addCommand(command: Commands.ComposableCommand): Seq[Setting[_]] =
      Seq(
        commands += command.toCommand,
        usefulTasksAndSettings += command.toItem
      )

    lazy val zioVersion: SettingKey[String] = settingKey[String]("ZIO version")

  }

  import autoImport.*

  private val defaultTasksAndSettings: ListMap[String, String] = Commands.ComposableCommand.makeHelp ++ ListMap(
//    "build"                                       -> "Lints source files then strictly compiles and runs tests.",
    "enableStrictCompile"  -> "Enables strict compilation e.g. warnings become errors.",
    "disableStrictCompile" -> "Disables strict compilation e.g. warnings are no longer treated as errors.",
    "~compile"             -> "Compiles all modules (file-watch enabled)"
  )

  override def projectSettings: Seq[Setting[_]] =
    Commands.settings ++ Tasks.settings ++ Seq(
      banners ++= {
        if (SharedTasks.isRoot.value) Seq(Banner.trueColor(s"${(ThisBuild / name).value} v.${version.value}"))
        else Seq.empty
      },
      usefulTasksAndSettings ++= {
        if (SharedTasks.isRoot.value) ZioSbtEcosystemPlugin.defaultTasksAndSettings.toSeq else Seq.empty
      },
      onLoadMessage := {
        if (SharedTasks.isRoot.value) welcomeMessage.init.value else ""
      }
    ) ++ MimaSettings.projectSettings

  override def buildSettings: Seq[Def.Setting[_]] = super.buildSettings ++ Seq(
    zioVersion := zioVersion.?.value.getOrElse(Versions.zioVersion)
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
      pgpPassphrase                      := sys.env.get("PGP_PASSPHRASE").map(_.toArray),
      pgpPublicRing                      := file("/tmp/public.asc"),
      pgpSecretRing                      := file("/tmp/secret.asc"),
      onChangedBuildSource               := ReloadOnSourceChanges,
      usefulTasksAndSettings / aggregate := false
    )
}
