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
import sbt.{Def, _}
import sbtbuildinfo.BuildInfoPlugin.autoImport.{BuildInfoKey, buildInfoKeys, buildInfoPackage}
import sbtcrossproject.CrossPlugin.autoImport.{JVMPlatform, crossProjectPlatform}
import scalafix.sbt.ScalafixPlugin.autoImport.{scalafixDependencies, scalafixSemanticdb}

import zio.sbt.Versions._

trait ScalaCompilerSettings {

  private val stdOptions = Seq(
    "-deprecation",
    "-encoding",
    "utf8",
    "-feature",
    "-unchecked"
  )

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

  lazy val scala3Settings: Seq[Setting[_]] = Seq(
    scalacOptions --= {
      if (Keys.scalaBinaryVersion.value == "3")
        Seq("-Xfatal-warnings")
      else
        Seq() // "-Xprint:typer" from zio-config
    },
    Compile / doc / sources := {
      val old = (Compile / doc / sources).value
      if (Keys.scalaBinaryVersion.value == "3") {
        Nil
      } else {
        old
      }
    },
    Test / parallelExecution := {
      val old = (Test / parallelExecution).value
      if (Keys.scalaBinaryVersion.value == "3") {
        false
      } else {
        old
      }
    }
  )

  def extraOptions(scalaVersion: String, javaPlatform: String, optimize: Boolean): Seq[String] =
    CrossVersion.partialVersion(scalaVersion) match {
      case Some((3, _)) =>
        Seq(
          "-language:implicitConversions",
          "-Xignore-scala2-macros",
          "-noindent",
          s"-release:$javaPlatform"
        )
      case Some((2, 13)) =>
        Seq(
          "-Ywarn-unused:params,-implicits",
          s"-release:$javaPlatform"
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
      case Some((2, 12)) =>
        List("2.12", "2.12+", "2.12-2.13", "2.x")
      case Some((2, 13)) =>
        List("2.13", "2.12+", "2.13+", "2.12-2.13", "2.x")
      case Some((3, _)) =>
        List("dotty", "2.12+", "2.13+", "3.x")
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

  def stdSettings(
    name: Option[String] = None,
    packageName: Option[String] = None,
    javaPlatform: String = "11",
    enableKindProjector: Boolean = true,
    enableCrossProject: Boolean = false,
    enableScalafix: Boolean = true,
    turnCompilerWarningIntoErrors: Boolean = true
  ): Seq[Setting[_]] =
    (name.map(n => Keys.name := n) match {
      case Some(value) => Seq(value)
      case None        => Seq.empty
    }) ++
      Seq(
        ZioSbtEcosystemPlugin.autoImport.javaPlatform := javaPlatform,
        scalacOptions                                 := (
          scalacOptions.value ++
            stdOptions ++
            extraOptions(Keys.scalaVersion.value, javaPlatform, optimize = !isSnapshot.value) ++
            (
              if (turnCompilerWarningIntoErrors && sys.env.contains("CI")) Seq("-Xfatal-warnings")
              else Nil // to enable Scalafix locally
            )
        ).distinct,
        javacOptions := Seq("-source", javaPlatform, "-target", javaPlatform),
//      Compile / console / scalacOptions ~= {
//        _.filterNot(Set("-Xfatal-warnings"))
//      },
        libraryDependencies ++= {
          if (enableKindProjector && scalaBinaryVersion.value != "3") {
            Seq(
              compilerPlugin("org.typelevel" %% "kind-projector" % KindProjectorVersion cross CrossVersion.full)
            )
          } else Seq.empty
        },
        Test / parallelExecution := scalaBinaryVersion.value != "3",
        incOptions ~= (_.withLogRecompileOnMacro(false)),
        autoAPIMappings := true
      ) ++ PlatformCompat.unusedCompileDependenciesFilterSettings ++
      (if (enableCrossProject) crossProjectSettings else Seq.empty) ++ {
        packageName match {
          case Some(name) => buildInfoSettings(name)
          case None       => Seq.empty
        }
      } ++ scala3Settings ++ {
        if (enableScalafix) scalafixSettings else Seq.empty
      } ++ betterMonadicForSettings

  lazy val scalafixSettings: Seq[Def.Setting[_]] =
    Seq(
      semanticdbEnabled := Keys.scalaBinaryVersion.value != "3",
      semanticdbOptions += "-P:semanticdb:synthetics:on",
      semanticdbVersion := scalafixSemanticdb.revision, // use Scalafix compatible version
      ThisBuild / scalafixDependencies ++= List(
        "com.github.vovapolu" %% "scaluzzi" % ScaluzziVersion
      )
    )

  // TODO: Review if this works properly
  def scalaReflectTestSettings: List[Setting[_]] = List(
    libraryDependencies ++= {
      if (scalaBinaryVersion.value == "3")
        Seq("org.scala-lang" % "scala-reflect" % ZioSbtEcosystemPlugin.autoImport.scala213.value % Test)
      else
        Seq("org.scala-lang" % "scala-reflect" % scalaVersion.value % Test)
    }
  )

  def enableZIO(
    enableStreaming: Boolean = false,
    enableTesting: Boolean = true
  ): Seq[Def.Setting[_]] =
    PlatformCompat.enableZIO(enableStreaming, enableTesting)

  def buildInfoSettings(packageName: String): Seq[Setting[_]] =
    Seq(
      // Wrapped explicitly with `BuildInfoKey(...)` rather than relying on its implicit
      // `SettingKey[T] => BuildInfoKey` conversion applying automatically inside a `Seq[BuildInfoKey](...)`
      // literal: Scala 3 does not apply it there the way Scala 2 does.
      buildInfoKeys    := Seq[BuildInfoKey](
        BuildInfoKey(name),
        BuildInfoKey(version),
        BuildInfoKey(scalaVersion),
        BuildInfoKey(sbtVersion),
        BuildInfoKey(isSnapshot)
      ),
      buildInfoPackage := packageName
    )

  def macroExpansionSettings: Seq[Setting[_]] = Seq(
    addOptionsOn("2.13")("-Ymacro-annotations"),
    addDependenciesOn("2.12")(
      compilerPlugin(("org.scalamacros" % "paradise" % "2.1.1").cross(CrossVersion.full))
    )
  )

  def macroDefinitionSettings: Seq[Setting[_]] =
    Seq(
      scalacOptions += "-language:experimental.macros",
      libraryDependencies ++= {
        if (scalaBinaryVersion.value == "3") Seq()
        else
          Seq(
            "org.scala-lang" % "scala-reflect"  % scalaVersion.value % "provided",
            "org.scala-lang" % "scala-compiler" % scalaVersion.value % "provided"
          )
      }
    )

  def jsSettings: Seq[Setting[_]] = Seq(
    Test / fork := crossProjectPlatform.value == JVMPlatform // set fork to `true` on JVM to improve log readability, JS and Native need `false`
  )

//  def jsSettings_ = Seq(
//    libraryDependencies += "io.github.cquiroz" %%% "scala-java-time"      % "2.2.2",
//    libraryDependencies += "io.github.cquiroz" %%% "scala-java-time-tzdb" % "2.2.2"
//  )

  def nativeSettings: Seq[Setting[_]] = Seq(
    doc / skip              := true,
    Compile / doc / sources := Seq.empty,
    // `Test / test`'s own value type is `Unit` on sbt 1.x but `sbt.protocol.testing.TestResult` on
    // sbt 2.x, so the replacement value - not just an expression discarding to a common type, since
    // `:=`'s RHS must match the key's own declared type - has to come from the scala-2.12/scala-3
    // PlatformCompat split.
    PlatformCompat.skippedTestSetting,
    Test / fork             := crossProjectPlatform.value == JVMPlatform // set fork to `true` on JVM to improve log readability, JS and Native need `false`
  )

  lazy val scalajs: Seq[Setting[_]] =
    addOptionsOn("3")("-scalajs")

  def optionsOn(scalaBinaryVersions: String*)(options: String*): Def.Initialize[Seq[String]] =
    optionsOnOrElse(scalaBinaryVersions*)(options*)(Seq.empty*)

  def optionsOnExcept(scalaBinaryVersions: String*)(options: String*): Def.Initialize[Seq[String]] =
    optionsOn(Seq("2.12", "2.13", "3").diff(scalaBinaryVersions)*)(options*)

  def optionsOnOrElse(scalaBinaryVersions: String*)(defaults: String*)(
    orElse: String*
  ): Def.Initialize[Seq[String]] =
    Def.setting(
      if (scalaBinaryVersions.contains(scalaBinaryVersion.value)) defaults else orElse
    )

  def dependenciesOn(scalaBinaryVersions: String*)(modules: ModuleID*): Def.Initialize[Seq[ModuleID]] =
    dependenciesOnOrElse(scalaBinaryVersions*)(modules*)(Seq.empty*)

  def dependenciesOnExcept(scalaBinaryVersions: String*)(modules: ModuleID*): Def.Initialize[Seq[ModuleID]] =
    dependenciesOn(Seq("2.12", "2.13", "3").diff(scalaBinaryVersions)*)(modules*)

  def dependenciesOnOrElse(scalaBinaryVersions: String*)(defaultModules: ModuleID*)(
    orElse: ModuleID*
  ): Def.Initialize[Seq[ModuleID]] =
    Def.setting(
      if (scalaBinaryVersions.contains(scalaBinaryVersion.value)) defaultModules else orElse
    )

  def addDependenciesOn(scalaBinaryVersions: String*)(dependencies: ModuleID*): Def.Setting[Seq[ModuleID]] =
    addDependenciesOnOrElse(scalaBinaryVersions*)(dependencies*)(Seq.empty*)

  def addDependenciesOnExcept(scalaBinaryVersions: String*)(dependencies: ModuleID*): Def.Setting[Seq[ModuleID]] =
    libraryDependencies ++= dependenciesOn(Seq("2.12", "2.13", "3").diff(scalaBinaryVersions)*)(
      dependencies*
    ).value

  def addDependenciesOnOrElse(
    scalaBinaryVersions: String*
  )(default: ModuleID*)(orElse: ModuleID*): Def.Setting[Seq[ModuleID]] =
    libraryDependencies ++= dependenciesOnOrElse(scalaBinaryVersions*)(default*)(orElse*).value

  def addOptionsOn(scalaBinaryVersions: String*)(options: String*): Def.Setting[Task[Seq[String]]] =
    scalacOptions ++= optionsOn(scalaBinaryVersions*)(options*).value

  def addOptionsOnOrElse(
    scalaBinaryVersions: String*
  )(options: String*)(orElse: String*): Def.Setting[Task[Seq[String]]] =
    scalacOptions ++= optionsOnOrElse(scalaBinaryVersions*)(options*)(orElse*).value

  def addOptionsOnExcept(scalaBinaryVersions: String*)(options: String*): Def.Setting[Task[Seq[String]]] =
    addOptionsOn(Seq("2.12", "2.13", "3").diff(scalaBinaryVersions)*)(options*)

  private def betterMonadicForSettings =
    Seq(
      libraryDependencies ++= {
        CrossVersion.partialVersion(scalaVersion.value) match {
          case Some((2, _)) => Seq(compilerPlugin(betterMonadFor))
          case _            => Seq.empty
        }
      }
    )

}
