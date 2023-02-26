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

import explicitdeps.ExplicitDepsPlugin.autoImport._
import sbt.Keys.*
import sbt.*
import sbtbuildinfo.BuildInfoPlugin.autoImport.{BuildInfoKey, buildInfoKeys, buildInfoPackage}
import sbtcrossproject.CrossPlugin.autoImport.crossProjectPlatform
import scalafix.sbt.ScalafixPlugin.autoImport.{scalafixDependencies, scalafixScalaBinaryVersion, scalafixSemanticdb}

import zio.sbt.Versions.*

trait ScalaCompilerSettings {

  private val stdOptions = Seq(
    "-deprecation",
    "-encoding",
    "UTF-8",
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
    crossScalaVersions += ZioSbtEcosystemPlugin.autoImport.scala3.value,
    scalacOptions ++= {
      if (Keys.scalaBinaryVersion.value == "3")
        Seq("-noindent")
      else
        Seq()
    },
    scalacOptions --= {
      if (Keys.scalaBinaryVersion.value == "3")
        Seq("-Xfatal-warnings")
      else
        Seq()
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
          "-release",
          javaPlatform
        )
      case Some((2, 13)) =>
        Seq(
          "-Ywarn-unused:params,-implicits",
          "-release",
          javaPlatform,
          s"-target:$javaPlatform"
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
      case Some((3, _)) =>
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
      "com.github.ghik" % "silencer-lib" % SilencerVersion % Provided cross CrossVersion.full,
      compilerPlugin("com.github.ghik" % "silencer-plugin" % SilencerVersion cross CrossVersion.full)
    )

  def stdSettings(
    name: String,
    packageName: Option[String] = None,
    javaPlatform: String = "8",
    enableSilencer: Boolean = true,
    enableKindProjector: Boolean = true,
    enableCrossProject: Boolean = false,
    turnCompilerWarningIntoErrors: Boolean = true
  ): Seq[Setting[_]] =
    Seq(
      Keys.name                                     := name,
      ZioSbtEcosystemPlugin.autoImport.javaPlatform := javaPlatform,
      scalacOptions := stdOptions ++ extraOptions(
        Keys.scalaVersion.value,
        javaPlatform,
        optimize = !isSnapshot.value
      ) ++ {
        if (turnCompilerWarningIntoErrors && sys.env.contains("CI"))
          Seq("-Xfatal-warnings")
        else
          Nil // to enable Scalafix locally
      },
      javacOptions := Seq("-source", javaPlatform, "-target", javaPlatform),
//      Compile / console / scalacOptions ~= {
//        _.filterNot(Set("-Xfatal-warnings"))
//      },
      libraryDependencies ++= {
        if (enableSilencer) {
          if (scalaBinaryVersion.value == "3")
            Seq(
              "com.github.ghik" % s"silencer-lib_${ZioSbtEcosystemPlugin.autoImport.scala213.value}" % SilencerVersion % Provided
            )
          else
            Seq(
              "com.github.ghik" % "silencer-lib" % SilencerVersion % Provided cross CrossVersion.full,
              compilerPlugin("com.github.ghik" % "silencer-plugin" % SilencerVersion cross CrossVersion.full)
            )
        } else Seq.empty
      },
      libraryDependencies ++= {
        if (enableKindProjector && scalaBinaryVersion.value != "3") {
          Seq(
            compilerPlugin("org.typelevel" %% "kind-projector" % KindProjectorVersion cross CrossVersion.full)
          )
        } else Seq.empty
      },
      semanticdbEnabled := Keys.scalaBinaryVersion.value != "3",
      semanticdbOptions += "-P:semanticdb:synthetics:on",
      semanticdbVersion                      := scalafixSemanticdb.revision, // use Scalafix compatible version
      ThisBuild / scalafixScalaBinaryVersion := CrossVersion.binaryScalaVersion(Keys.scalaVersion.value),
      ThisBuild / scalafixDependencies ++= List(
        "com.github.liancheng" %% "organize-imports" % OrganizeImportsVersion,
        "com.github.vovapolu"  %% "scaluzzi"         % ScaluzziVersion
      ),
      Test / parallelExecution := scalaBinaryVersion.value != "3",
      incOptions ~= (_.withLogRecompileOnMacro(false)),
      autoAPIMappings := true,
      unusedCompileDependenciesFilter -= moduleFilter("org.scala-js", "scalajs-library")
    ) ++ (if (enableCrossProject) crossProjectSettings else Seq.empty) ++
      (packageName match {
        case Some(name) => buildInfoSettings(name)
        case None       => Seq.empty
      })

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

  def buildInfoSettings(packageName: String): Seq[Setting[_ <: Object]] =
    Seq(
      buildInfoKeys    := Seq[BuildInfoKey](name, version, scalaVersion, sbtVersion, isSnapshot),
      buildInfoPackage := packageName
    )

  def macroDefinitionSettings: Seq[Setting[_ <: Equals]] =
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

  def jsSettings: Seq[Setting[_]] = Seq()

  def nativeSettings: Seq[Setting[_]] = Seq(
    Test / test             := { val _ = (Test / compile).value; () },
    doc / skip              := true,
    Compile / doc / sources := Seq.empty
  )
}
