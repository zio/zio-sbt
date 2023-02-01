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

import org.scalafmt.sbt.ScalafmtPlugin
import sbt.Keys.*
import sbt.{Def, *}
import sbtbuildinfo.BuildInfoPlugin
import sbtbuildinfo.BuildInfoPlugin.autoImport.*
import sbtcrossproject.CrossPlugin.autoImport.crossProjectPlatform
import scalafix.sbt.ScalafixPlugin
import scalafix.sbt.ScalafixPlugin.autoImport.*

object EcosystemPlugin extends AutoPlugin {

  override def trigger = allRequirements

  override def requires: Plugins =
    super.requires && de.heikoseeberger.sbtheader.HeaderPlugin && ScalafixPlugin && ScalafmtPlugin && BuildInfoPlugin

  object autoImport {

    private val stdOptions = Seq(
      "-deprecation",
      "-encoding",
      "UTF-8",
      "-feature",
      "-unchecked"
    ) ++ {
      if (sys.env.contains("CI")) {
        Seq("-Xfatal-warnings")
      } else {
        Nil // to enable Scalafix locally
      }
    }

    private val std2xOptions = Seq(
      "-language:higherKinds",
      "-language:existentials",
      "-explaintypes",
      "-Yrangepos",
      "-Xlint:_,-missing-interpolator,-type-parameter-shadow",
      "-Ywarn-numeric-widen",
      "-Ywarn-value-discard"
    )

    private def optimizerOptions(optimize: Boolean) =
      if (optimize)
        Seq(
          "-opt:l:inline",
          "-opt-inline-from:zio.internal.**"
        )
      else Nil

    def enableScala3(scala3Version: String, scala213Version: String): Seq[Setting[_]] = Seq(
      libraryDependencies ++= {
        if (scalaVersion.value == scala3Version)
          Seq("com.github.ghik" % s"silencer-lib_$scala213Version" % V.SilencerVersion % Provided)
        else
          Seq.empty
      },
      scalacOptions --= {
        if (scalaVersion.value == scala3Version)
          Seq("-Xfatal-warnings")
        else
          Seq()
      },
      Compile / doc / sources := {
        val old = (Compile / doc / sources).value
        if (scalaVersion.value == scala3Version) {
          Nil
        } else {
          old
        }
      },
      Test / parallelExecution := {
        val old = (Test / parallelExecution).value
        if (scalaVersion.value == scala3Version) {
          false
        } else {
          old
        }
      }
    )

    def extraOptions(scalaVersion: String, optimize: Boolean): Seq[String] =
      CrossVersion.partialVersion(scalaVersion) match {
        case Some((3, 0)) =>
          Seq(
            "-language:implicitConversions",
            "-Xignore-scala2-macros"
          )
        case Some((2, 13)) =>
          Seq(
            "-Ywarn-unused:params,-implicits"
          ) ++ std2xOptions ++ optimizerOptions(optimize)
        case Some((2, 12)) =>
          Seq(
            "-opt-warnings",
            "-Ywarn-extra-implicit",
            "-Ywarn-unused:_,imports",
            "-Ywarn-unused:imports",
            "-Ypartial-unification",
            "-Yno-adapted-args",
            "-Ywarn-inaccessible",
            "-Ywarn-infer-any",
            "-Ywarn-nullary-override",
            "-Ywarn-nullary-unit",
            "-Ywarn-unused:params,-implicits",
            "-Xfuture",
            "-Xsource:2.13",
            "-Xmax-classfile-name",
            "242"
          ) ++ std2xOptions ++ optimizerOptions(optimize)
        case Some((2, 11)) =>
          Seq(
            "-Ypartial-unification",
            "-Yno-adapted-args",
            "-Ywarn-inaccessible",
            "-Ywarn-infer-any",
            "-Ywarn-nullary-override",
            "-Ywarn-nullary-unit",
            "-Xexperimental",
            "-Ywarn-unused-import",
            "-Xfuture",
            "-Xsource:2.13",
            "-Xmax-classfile-name",
            "242"
          ) ++ std2xOptions
        case _ => Seq.empty
      }

    def platformSpecificSources(platform: String, conf: String, baseDirectory: File)(versions: String*): List[File] =
      for {
        platform <- List("shared", platform)
        version  <- "scala" :: versions.toList.map("scala-" + _)
        result    = baseDirectory.getParentFile / platform.toLowerCase / "src" / conf / version
        if result.exists
      } yield result

    def crossPlatformSources(scalaVer: String, platform: String, conf: String, baseDir: File): List[File] = {
      val versions = CrossVersion.partialVersion(scalaVer) match {
        case Some((2, 11)) =>
          List("2.11", "2.11+", "2.11-2.12", "2.x")
        case Some((2, 12)) =>
          List("2.12", "2.11+", "2.12+", "2.11-2.12", "2.12-2.13", "2.x")
        case Some((2, 13)) =>
          List("2.13", "2.11+", "2.12+", "2.13+", "2.12-2.13", "2.x")
        case Some((3, 0)) =>
          List("dotty", "2.11+", "2.12+", "2.13+", "3.x")
        case _ =>
          List()
      }
      platformSpecificSources(platform, conf, baseDir)(versions: _*)
    }

    lazy val crossProjectSettings: Seq[Setting[Seq[File]]] = Seq(
      Compile / unmanagedSourceDirectories ++= {
        crossPlatformSources(
          scalaVersion.value,
          crossProjectPlatform.value.identifier,
          "main",
          baseDirectory.value
        )
      },
      Test / unmanagedSourceDirectories ++= {
        crossPlatformSources(
          scalaVersion.value,
          crossProjectPlatform.value.identifier,
          "test",
          baseDirectory.value
        )
      }
    )

    val silencerModules: Seq[ModuleID] =
      Seq(
        "com.github.ghik" % "silencer-lib" % V.SilencerVersion % Provided cross CrossVersion.full,
        compilerPlugin("com.github.ghik" % "silencer-plugin" % V.SilencerVersion cross CrossVersion.full)
      )

    val kindProjectorModule: ModuleID =
      compilerPlugin("org.typelevel" %% "kind-projector" % V.KindProjectorVersion cross CrossVersion.full)

    def stdSettings(
      name: String,
      packageName: String,
      scalaVersion: String,
      crossScalaVersions: Seq[String],
      enableSilencer: Boolean = false,
      enableKindProjector: Boolean = false,
      enableCrossProject: Boolean = false
    ): Seq[Setting[_]] =
      Seq(
        Keys.name               := name,
        Keys.scalaVersion       := scalaVersion,
        Keys.crossScalaVersions := crossScalaVersions,
        scalacOptions ++= stdOptions ++ extraOptions(Keys.scalaVersion.value, optimize = !isSnapshot.value),
        Compile / console / scalacOptions ~= {
          _.filterNot(Set("-Xfatal-warnings"))
        },
        libraryDependencies ++= {
          if (!Keys.scalaVersion.value.startsWith("3")) {
            (if (enableSilencer) silencerModules else Seq.empty) ++
              (if (enableKindProjector) Seq(kindProjectorModule) else Seq.empty)
          } else Seq.empty
        },
        semanticdbEnabled := !Keys.scalaVersion.value.startsWith("3"),
        semanticdbOptions += "-P:semanticdb:synthetics:on",
        semanticdbVersion                      := scalafixSemanticdb.revision, // use Scalafix compatible version
        ThisBuild / scalafixScalaBinaryVersion := CrossVersion.binaryScalaVersion(Keys.scalaVersion.value),
        ThisBuild / scalafixDependencies ++= List(
          "com.github.liancheng" %% "organize-imports" % V.OrganizeImportsVersion,
          "com.github.vovapolu"  %% "scaluzzi"         % V.ScaluzziVersion
        ),
        Test / parallelExecution := true,
        incOptions ~= (_.withLogRecompileOnMacro(false)),
        autoAPIMappings := true
        //      unusedCompileDependenciesFilter -= moduleFilter("org.scala-js", "scalajs-library")
      ) ++ (if (enableCrossProject) crossProjectSettings else Seq.empty) ++ buildInfoSettings(packageName) ++ {
        crossScalaVersions.find(_.startsWith("3")) match {
          case Some(version) =>
            enableScala3(version, crossScalaVersions.find(_.startsWith("2.13")).getOrElse(Defaults.scala213))
          case None => Seq.empty
        }
      }
//    val scalaReflectTestSettings: List[Setting[_]] = List(
//      libraryDependencies ++= {
//        if (scalaVersion.value == Scala3)
//          Seq("org.scala-lang" % "scala-reflect" % Scala213           % Test)
//        else
//          Seq("org.scala-lang" % "scala-reflect" % scalaVersion.value % Test)
//      }
//    )

    def enableZIO(zioVersion: String, enableTesting: Boolean = false): Seq[Def.Setting[_]] =
      Seq(libraryDependencies += "dev.zio" %% "zio" % zioVersion) ++
        (if (enableTesting)
           Seq(
             libraryDependencies ++= Seq(
               "dev.zio" %% "zio-test"     % zioVersion,
               "dev.zio" %% "zio-test-sbt" % zioVersion % Test
             ),
             testFrameworks += new TestFramework("zio.test.sbt.ZTestFramework")
           )
         else Seq.empty)

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

//    def addCommand(commandString: List[String], name: String, description: String): Seq[Setting[_]] = {
//      val cCommand = Commands.ComposableCommand(commandString, name, description)
//      addCommand(cCommand)
//    }

//    def addCommand(command: Commands.ComposableCommand): Seq[Setting[_]] =
//      Seq(
//        commands += command.toCommand,
//        usefulTasksAndSettings += command.toItem
//      )

    object Defaults {
      val scala3   = "3.2.1"
      val scala211 = "2.11.12"
      val scala212 = "2.12.17"
      val scala213 = "2.13.10"
    }

    lazy val scala3: SettingKey[String]   = settingKey[String]("Scala 3 version")
    lazy val scala211: SettingKey[String] = settingKey[String]("Scala 2.11 version")
    lazy val scala212: SettingKey[String] = settingKey[String]("Scala 2.12 version")
    lazy val scala213: SettingKey[String] = settingKey[String]("Scala 2.13 version")

//    val welcomeBannerEnabled: SettingKey[Boolean] =
//      settingKey[Boolean]("Indicates whether or not to enable the welcome banner.")

//    val usefulTasksAndSettings: SettingKey[Map[String, String]] = settingKey[Map[String, String]](
//      "A map of useful tasks and settings that will be displayed as part of the welcome banner."
//    )

  }

  import autoImport.*

//  private val defaultTasksAndSettings: Map[String, String] = Commands.ComposableCommand.makeHelp ++ ListMap(
//    "build"                                       -> "Lints source files then strictly compiles and runs tests.",
//    "enableStrictCompile"                         -> "Enables strict compilation e.g. warnings become errors.",
//    "disableStrictCompile"                        -> "Disables strict compilation e.g. warnings are no longer treated as errors.",
//    "~compile"                                    -> "Compiles all modules (file-watch enabled)",
//    "test"                                        -> "Runs all tests",
//    """testOnly *.YourSpec -- -t \"YourLabel\"""" -> "Only runs tests with matching term e.g."
//  )

//  def stdSettings: Seq[Setting[_]] = Seq.empty
//    Seq(
//      licenses               := List("Apache-2.0" -> new URL("http://www.apache.org/licenses/LICENSE-2.0.txt")),
//      welcomeBannerEnabled   := true,
//      usefulTasksAndSettings := defaultTasksAndSettings,
//      scalacOptions          := ScalaCompilerSettings.stdScalacOptions(scalaVersion.value, !isSnapshot.value),
//      semanticdbEnabled      := scalaVersion.value != scala3.value, // enable SemanticDB
//      semanticdbOptions += "-P:semanticdb:synthetics:on",
//      semanticdbVersion                      := scalafixSemanticdb.revision, // use Scalafix compatible version
//      ThisBuild / scalafixScalaBinaryVersion := CrossVersion.binaryScalaVersion(scalaVersion.value),
//      ThisBuild / scalafixDependencies ++= List(
//        "com.github.liancheng" %% "organize-imports" % "0.6.0",
//        "com.github.vovapolu"  %% "scaluzzi"         % "0.1.23"
//      ),
//      Test / parallelExecution := !sys.env.contains("CI"),
//      incOptions ~= (_.withLogRecompileOnMacro(false)),
//      autoAPIMappings := true
//    )

//  def welcomeMessage: Setting[String] =
//    onLoadMessage := {
//      if (welcomeBannerEnabled.value) {
//        import scala.Console
//
//        val maxLen = usefulTasksAndSettings.value.keys.map(_.length).max
//
//        def normalizedPadding(s: String) = " " * (maxLen - s.length)
//
//        def item(text: String): String = s"${Console.GREEN}> ${Console.CYAN}$text${Console.RESET}"
//
//        s"""|${Banner.trueColor(s"${name.value} v.${version.value}")}
//            |Useful sbt tasks:
//            |${usefulTasksAndSettings.value.map { case (task, description) =>
//          s"${item(task)} ${normalizedPadding(task)}${description}"
//        }
//          .mkString("\n")}
//      """.stripMargin
//
//      } else ""
//    }

//  override def projectSettings: Seq[Setting[_]] =
//    stdSettings
//    ++ Tasks.settings ++ Commands.settings ++ welcomeMessage

  override def globalSettings: Seq[Def.Setting[_]] =
    super.globalSettings ++ Seq(
      scala3       := Defaults.scala3,
      scala211     := Defaults.scala211,
      scala212     := Defaults.scala212,
      scala213     := Defaults.scala213,
      scalaVersion := Defaults.scala213,
      licenses     := List("Apache-2.0" -> url("http://www.apache.org/licenses/LICENSE-2.0"))
    )
}
