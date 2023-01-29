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

import sbt.Keys.*
import sbt.*

object Commands {

  final case class ComposableCommand(commandStrings: List[String], name: String = "", description: String = "") {
    self =>

    private def choose(name1: String, name2: String): String =
      if (name1.isEmpty) name2 else if (name2.isEmpty) name1 else name1

    def before(other: ComposableCommand): ComposableCommand =
      ComposableCommand(
        other.commandStrings ++ commandStrings,
        choose(name, other.name),
        choose(description, other.description)
      )

    def <<:(other: ComposableCommand): ComposableCommand = before(other)

    def <<:(other: String): ComposableCommand = self <<: ComposableCommand(other :: Nil, name, description)

    def andThen(other: ComposableCommand): ComposableCommand =
      ComposableCommand(
        commandStrings ++ other.commandStrings,
        choose(other.name, name),
        choose(other.description, description)
      )

    def >>(other: ComposableCommand): ComposableCommand = andThen(other)

    def >>(other: String): ComposableCommand = self >> ComposableCommand(other :: Nil)

    lazy val toCommand: Command = Command.command(name, description, description)(toState)

    def toState(state: State): State = commandStrings ::: state

    def describe(newName: String, newDesc: String): ComposableCommand = copy(name = newName, description = newDesc)

    def ??(newName: String, newDesc: String): ComposableCommand = describe(newName, newDesc)

    val toItem: (String, String) = name -> description

  }

  object ComposableCommand {

    def make(commands: String*): ComposableCommand = ComposableCommand(commands.toList)

    val quietOff: ComposableCommand = make("set welcomeBannerEnabled := true")

    val quietOn: ComposableCommand = make("set welcomeBannerEnabled := false")

    val buildAll: ComposableCommand =
      quietOn >> "project /" >> "++build" >> quietOff ?? ("build-all", s"Builds all modules for all defined Scala cross versions.")

    def setScalaVersion(scalaVersion: String): ComposableCommand = make(s"++$scalaVersion")

    def scalafix(args: String = ""): ComposableCommand =
      ComposableCommand.make(s"scalafix ${args}".trim()) >> s"Test / scalafix ${args}".trim()

    val fix: ComposableCommand =
      quietOn >> scalafix() >> quietOff ?? ("fix", "Fixes source files using using scalafix")

    val fixLint: ComposableCommand =
      quietOn >> scalafix("--check") >> quietOff

    val fmt: ComposableCommand =
      quietOn >> "scalafmtSbt" >> "+scalafmt" >> "+Test / scalafmt" >> quietOff ?? ("fmt", "Formats source files using scalafmt.")

    val lint: ComposableCommand =
      quietOn >> "enableStrictCompile" >> "+scalafmtSbtCheck" >> "+scalafmtCheckAll" >> "+headerCheckAll" >> fixLint >> "disableStrictCompile" >> quietOff ?? ("lint", "Verifies that all source files are properly formatted, have the correct license headers and have had all scalafix rules applied.")

    val prepare: ComposableCommand =
      quietOn >> "+headerCreateAll" >> "+scalafmtSbt" >> "+scalafmt" >> "+Test / scalafmt" >> fix >> quietOff ?? ("prepare", "Prepares sources by applying scalafmt, adding missing license headers and running scalafix.")

    val publishAll: ComposableCommand =
      quietOn >> "project /" >> "+publishSigned" >> quietOff ?? ("publish-all", "Signs and publishes all artifacts to Maven Central.")

    val site: ComposableCommand =
      quietOn >> "docs/clean" >> "docs/docusaurusCreateSite" >> quietOff ?? ("site", "Builds the documentation microsite.")

    val makeHelp: ListMap[String, String] = ListMap(
      lint.toItem,
      fmt.toItem,
      fix.toItem,
      prepare.toItem,
      buildAll.toItem,
      site.toItem,
      prepare.toItem,
      "quiet" -> "`quite on` mutes the welcome banner whilst `quiet off` un-mutes it.",
      publishAll.toItem
    )

  }

  import ComposableCommand.*

  val quiet: Command = Command.single("quiet") { (state, arg) =>
    arg.trim.toLowerCase() match {
      case "on" | "true" | "1" =>
        println("Welcome banner is off.")
        quietOn.toState(state)
      case "off" | "false" | "0" =>
        println("Welcome banner is on.")
        quietOff.toState(state)
      case invalid =>
        println(s"Invalid 'quiet' argument: ${invalid}")
        state.fail
    }

  }

  lazy val settings: Seq[Setting[_]] = Seq(
    commands ++= Seq(
      fix.toCommand,
      fmt.toCommand,
      quiet,
      lint.toCommand,
      prepare.toCommand,
      buildAll.toCommand,
      publishAll.toCommand,
      site.toCommand
    )
  )
}
