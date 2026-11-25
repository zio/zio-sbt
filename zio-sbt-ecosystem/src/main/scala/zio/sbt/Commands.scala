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

import sbt.Keys._
import sbt._

/**
 * The sbt commands of the plugin (`fix`, `fmt`, `lint`, `prepare`,
 * `publish-all` and `quiet`) and the [[Commands.ComposableCommand]] type used
 * to build them.
 */
object Commands {

  /**
   * A named list of sbt command strings that can be combined with other
   * commands and turned into an sbt `Command`. Running it queues
   * `commandStrings` in order at the front of the remaining commands.
   *
   * @param commandStrings
   *   the sbt commands to run, in order
   * @param name
   *   the name of the resulting sbt command; empty when not set
   * @param description
   *   the help text of the resulting sbt command; empty when not set
   */
  final case class ComposableCommand(commandStrings: List[String], name: String = "", description: String = "") {
    self =>

    private def choose(name1: String, name2: String): String =
      if (name1.isEmpty) name2 else if (name2.isEmpty) name1 else name1

    /**
     * Returns a command whose strings are the strings of `other` followed by
     * the strings of this command. The name and description are those of this
     * command, or those of `other` when this command has none.
     */
    def before(other: ComposableCommand): ComposableCommand =
      ComposableCommand(
        other.commandStrings ++ commandStrings,
        choose(name, other.name),
        choose(description, other.description)
      )

    /**
     * Alias for `before`. Since the operator ends in a colon, `a <<: b` calls
     * `b.<<:(a)`, so the strings of `a` run before those of `b`.
     */
    def <<:(other: ComposableCommand): ComposableCommand = before(other)

    /**
     * Returns a command whose strings are the strings of this command followed
     * by `other`. The name and description are those of this command. Since the
     * operator ends in a colon, `"x" <<: c` calls `c.<<:("x")`, so `x` runs
     * after the strings of `c`.
     */
    def <<:(other: String): ComposableCommand = self <<: ComposableCommand(other :: Nil, name, description)

    /**
     * Returns a command whose strings are the strings of this command followed
     * by the strings of `other`. The name and description are those of `other`,
     * or those of this command when `other` has none.
     */
    def andThen(other: ComposableCommand): ComposableCommand =
      ComposableCommand(
        commandStrings ++ other.commandStrings,
        choose(other.name, name),
        choose(other.description, description)
      )

    /** Alias for `andThen`. */
    def >>(other: ComposableCommand): ComposableCommand = andThen(other)

    /**
     * Returns a command that runs `other` after the strings of this command,
     * keeping the name and description of this command.
     */
    def >>(other: String): ComposableCommand = self >> ComposableCommand(other :: Nil)

    /**
     * The sbt `Command` registered under `name`, with `description` as its help
     * text. Running it puts `commandStrings` at the front of the command queue.
     */
    lazy val toCommand: Command = Command.command(name, description, description)(toState)

    /**
     * Returns `state` with `commandStrings` put in front of its remaining
     * commands.
     */
    def toState(state: State): State = commandStrings ::: state

    /** Returns a copy of this command with the given name and description. */
    def describe(newName: String, newDesc: String): ComposableCommand = copy(name = newName, description = newDesc)

    /** Alias for `describe`. */
    def ??(newName: String, newDesc: String): ComposableCommand = describe(newName, newDesc)

    /**
     * The pair of `name` and `description`, as used in the welcome banner list.
     */
    val toItem: (String, String) = name -> description

  }

  /**
   * The predefined commands of the plugin and the constructors of
   * [[Commands.ComposableCommand]].
   */
  object ComposableCommand {

    /**
     * Returns an unnamed command that runs the given sbt command strings in
     * order.
     */
    def make(commands: String*): ComposableCommand = ComposableCommand(commands.toList)

    /** Runs `set welcomeBannerEnabled := true`. */
    val quietOff: ComposableCommand = make("set welcomeBannerEnabled := true")

    /** Runs `set welcomeBannerEnabled := false`. */
    val quietOn: ComposableCommand = make("set welcomeBannerEnabled := false")

//    val buildAll: ComposableCommand =
//      (quietOn >> "project /" >> "++build") ?? ("build-all", s"Builds all modules for all defined Scala cross versions.")

    /** Returns a command that runs `++` followed by the given Scala version. */
    def setScalaVersion(scalaVersion: String): ComposableCommand = make(s"++$scalaVersion")

    /**
     * Returns a command that runs `Compile / scalafix` and then
     * `Test / scalafix`, each with `args` appended and the result trimmed.
     */
    def scalafix(args: String = ""): ComposableCommand =
      ComposableCommand.make(s"Compile / scalafix ${args}".trim()) >> s"Test / scalafix ${args}".trim()

    /**
     * The `fix` command: runs `quietOn`, then `Compile / scalafix` and
     * `Test / scalafix`.
     */
    val fix: ComposableCommand =
      quietOn >> scalafix() ?? ("fix", "Fixes source files using using scalafix")

    /**
     * Runs `quietOn`, then `Compile / scalafix --check` and
     * `Test / scalafix --check`. It has no name, so it is not registered as a
     * command by itself.
     */
    val fixLint: ComposableCommand =
      quietOn >> scalafix("--check")

    /**
     * The `fmt` command: runs `quietOn`, then `scalafmtSbt`, `+scalafmt` and
     * `+Test / scalafmt`.
     */
    val fmt: ComposableCommand =
      (quietOn >> "scalafmtSbt" >> "+scalafmt" >> "+Test / scalafmt") ?? ("fmt", "Formats source files using scalafmt.")

    // TODO: have the correct license headers
    /**
     * The `lint` command: runs `quietOn`, `enableStrictCompile`,
     * `+scalafmtSbtCheck`, `+scalafmtCheckAll`, the commands of `fixLint` and
     * `disableStrictCompile`, in this order.
     */
    val lint: ComposableCommand = {
      quietOn >>
        "enableStrictCompile" >>
        "+scalafmtSbtCheck" >>
        "+scalafmtCheckAll" >>
//        "+headerCheckAll" >>
        fixLint >>
        "disableStrictCompile"
    } ?? (
      "lint",
      "Verifies that all source files are properly formatted and have had all scalafix rules applied."
    )

    // TODO: adding missing license headers
    /**
     * The `prepare` command: runs `quietOn`, then `+scalafmtSbt`, `+scalafmt`,
     * `+Test / scalafmt` and the commands of `fix`.
     */
    val prepare: ComposableCommand = {
      quietOn >>
//        "+headerCreateAll" >>
        "+scalafmtSbt" >>
        "+scalafmt" >>
        "+Test / scalafmt" >>
        fix
    } ??
      ("prepare", "Prepares sources by applying scalafmt and running scalafix.")

    /**
     * The `publish-all` command: runs `quietOn`, `project /` and
     * `+publishSigned`.
     */
    val publishAll: ComposableCommand =
      (quietOn >> "project /" >> "+publishSigned") ??
        ("publish-all", "Signs and publishes all artifacts to Maven Central.")

    /**
     * The `site` command: runs `quietOn`, `docs/clean` and
     * `docs/docusaurusCreateSite`. It is not registered by `Commands.settings`.
     */
    val site: ComposableCommand =
      (quietOn >> "docs/clean" >> "docs/docusaurusCreateSite") ??
        ("site", "Builds the documentation microsite.")

    /**
     * The name-to-description entries for `lint`, `fmt`, `fix`, `prepare` and
     * `publish-all`, plus an entry for `quiet`. The plugin includes them in the
     * initial value of `usefulTasksAndSettings`.
     */
    val makeHelp: ListMap[String, String] = ListMap(
      lint.toItem,
      fmt.toItem,
      fix.toItem,
      prepare.toItem,
//      buildAll.toItem,
      prepare.toItem,
      "quiet" -> "`quite on` mutes the welcome banner whilst `quiet off` un-mutes it.",
      publishAll.toItem
    )

  }

  import ComposableCommand.*

  /**
   * The `quiet` command. `quiet on`, `quiet true` and `quiet 1` run `quietOn`
   * and print "Welcome banner is off."; `quiet off`, `quiet false` and
   * `quiet 0` run `quietOff` and print "Welcome banner is on.". The argument is
   * trimmed and lower-cased first; any other argument prints
   * `Invalid 'quiet' argument: <argument>` and fails the state.
   */
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

  /**
   * Settings that register the `fix`, `fmt`, `quiet`, `lint`, `prepare` and
   * `publish-all` commands.
   */
  lazy val settings: Seq[Setting[_]] = Seq(
    commands ++= Seq(
      fix.toCommand,
      fmt.toCommand,
      quiet,
      lint.toCommand,
      prepare.toCommand,
//      buildAll.toCommand,
      publishAll.toCommand
    )
  )
}
