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

import sbt.Keys.*
import sbt.*
import sbtbuildinfo.BuildInfoPlugin.autoImport.{BuildInfoKey, buildInfoKeys, buildInfoPackage}
import sbtcrossproject.CrossPlugin.autoImport.crossProjectPlatform
import scalafix.sbt.ScalafixPlugin.autoImport.{scalafixDependencies, scalafixScalaBinaryVersion, scalafixSemanticdb}

import zio.sbt.Versions.*

trait ScalaCompilerSettings {

  private val baseSettings = Seq(
    "-language:postfixOps", // Added by @tusharmath
    "-deprecation",         // Emit warning and location for usages of deprecated APIs.
    "-encoding",
    "utf-8",                         // Specify character encoding used by source files.
    "-explaintypes",                 // Explain type errors in more detail.
    "-feature",                      // Emit warning and location for usages of features that should be imported explicitly.
    "-language:existentials",        // Existential types (besides wildcard types) can be written and inferred
    "-language:higherKinds",         // Allow higher-kinded types
    "-unchecked",                    // Enable additional warnings where generated code depends on assumptions.
    "-Xcheckinit",                   // Wrap field accessors to throw an exception on uninitialized access.
    "-Xlint:adapted-args",           // Warn if an argument list is modified to match the receiver.
    "-Xlint:constant",               // Evaluation of a constant arithmetic expression results in an error.
    "-Xlint:delayedinit-select",     // Selecting member of DelayedInit.
    "-Xlint:doc-detached",           // A Scaladoc comment appears to be detached from its element.
    "-Xlint:inaccessible",           // Warn about inaccessible types in method signatures.
    "-Xlint:missing-interpolator",   // A string literal appears to be missing an interpolator id.
    "-Xlint:nullary-unit",           // Warn when nullary methods return Unit.
    "-Xlint:option-implicit",        // Option.apply used implicit view.
    "-Xlint:package-object-classes", // Class or object defined in package object.
    "-Xlint:poly-implicit-overload", // Parameterized overloaded implicit methods are not visible as view bounds.
    "-Xlint:private-shadow",         // A private field (or class parameter) shadows a superclass field.
    "-Xlint:stars-align",            // Pattern sequence wildcard must align with sequence component.
    "-Xlint:type-parameter-shadow",  // A local type parameter shadows a type already in scope.
    // "-Xlint:unused",                 // TODO check if we still need -Wunused below
    "-Xlint:deprecation", // Enable linted deprecations.

    // "-Wunused:explicits",                        // Warn if an explicit parameter is unused.
    // "-Wunused:params",                           // Enable -Wunused:explicits,implicits.
    // "-Wunused:linted",
    "-Ybackend-parallelism",
    "8",                                         // Enable paralellisation — change to desired number!
    "-Ycache-plugin-class-loader:last-modified", // Enables caching of classloaders for compiler plugins
    "-Ycache-macro-class-loader:last-modified"   // and macro definitions. This can lead to performance improvements.

    // FIXME: Disabled because of scalac bug https://github.com/scala/bug/issues/11798
    //  "-Xlint:infer-any",                 // Warn when a type argument is inferred to be `Any`.
    //  "-Ywarn-infer-any",                 // Warn when a type argument is inferred to be `Any`.
    //  "-language:experimental.macros",   // Allow macro definition (besides implementation and application). Disabled, as this will significantly change in Scala 3
    //  "-language:implicitConversions",   // Allow definition of implicit functions called views. Disabled, as it might be dropped in Scala 3. Instead use extension methods (implemented as implicit class Wrapper(val inner: Foo) extends AnyVal {}
  )

//  private val scala3Settings: Seq[String] = Seq("-Xignore-scala2-macros", "-noindent")

  // RECOMMENDED SETTINGS: https://tpolecat.github.io/2017/04/25/scalac-flags.html
  val scala213Settings: Seq[String] = baseSettings ++ Seq(
    "-Xlint:nonlocal-return",    // A return statement used an exception for flow control.
    "-Xlint:implicit-not-found", // Check @implicitNotFound and @implicitAmbiguous messages.
    "-Xlint:serial",             // @SerialVersionUID on traits and non-serializable classes.
    "-Xlint:valpattern",         // Enable pattern checks in val definitions.
    "-Xlint:eta-zero",           // Warn on eta-expansion (rather than auto-application) of zero-ary method.
    "-Xlint:eta-sam",            // Warn on eta-expansion to meet a Java-defined functional interface that is not explicitly annotated with @FunctionalInterface.
    "-Wdead-code",               // Warn when dead code is identified.
    "-Wextra-implicit",          // Warn when more than one implicit parameter section is defined.
    "-Wmacros:after",            // Lints code before and after applying a macro
    "-Wnumeric-widen",           // Warn when numerics are widened.
    "-Woctal-literal",           // Warn on obsolete octal syntax.
    "-Wunused:imports",          // Warn if an import selector is not referenced.
    "-Wunused:patvars",          // Warn if a variable bound in a pattern is unused.
    "-Wunused:privates",         // Warn if a private member is unused.
    "-Wunused:locals",           // Warn if a local definition is unused.
    "-Wvalue-discard"            // Warn when non-Unit expression results are unused.
  )

  val scala212Settings: Seq[String] = baseSettings ++ Seq(
    "-explaintypes",
    "-Yrangepos",
    "-Xlint:_,-missing-interpolator,-type-parameter-shadow",
    "-Ywarn-numeric-widen",
    "-Ywarn-macros:after",
    "-Ywarn-unused:-implicits"
  )

//  @nowarn
//  private def stdScalacOptions(scalaVersion: String, optimize: Boolean): Seq[String] = {
//    val versionedSettings = CrossVersion.partialVersion(scalaVersion) match {
//      case Some((3, _))  => scala3Settings
//      case Some((2, 13)) => scala213Settings
//      case Some((2, 12)) => scala212Settings
//      case _             => Seq.empty
//    }
//
//    val strictCompilation = sys.props.get("enable.strict.compilation").map(_.toBoolean).getOrElse(false)
//
//    val s1 = if (sys.env.contains("CI") || strictCompilation) {
//      versionedSettings :+ "-Xfatal-warnings"
//    } else {
//      versionedSettings
//    }
//
//    val s2 =
//      if (optimize)
//        s1 :+ "-opt:l:inline"
//      else s1
//
//    s2.filterNot(_.contains("semanticdb:targetroot")) // The presence of this flag causes scalac to fail.
//  }

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

  lazy val scala3Settings: Seq[Setting[_]] = Seq(
    libraryDependencies ++= {
      val scala213Version = Keys.crossScalaVersions.value.find(_.startsWith("2.13")).getOrElse(Versions.scala213)
      if (Keys.scalaVersion.value.startsWith("3"))
        Seq(
          "com.github.ghik" % s"silencer-lib_$scala213Version" % SilencerVersion % Provided
        )
      else
        Seq.empty
    },
    scalacOptions ++= {
      if (Keys.scalaVersion.value.startsWith("3"))
        Seq("-noindent")
      else
        Seq()
    },
    scalacOptions --= {
      if (Keys.scalaVersion.value.startsWith("3"))
        Seq("-Xfatal-warnings")
      else
        Seq()
    },
    Compile / doc / sources := {
      val old = (Compile / doc / sources).value
      if (Keys.scalaVersion.value.startsWith("3")) {
        Nil
      } else {
        old
      }
    },
    Test / parallelExecution := {
      val old = (Test / parallelExecution).value
      if (Keys.scalaVersion.value.startsWith("3")) {
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
      "com.github.ghik" % "silencer-lib" % SilencerVersion % Provided cross CrossVersion.full,
      compilerPlugin("com.github.ghik" % "silencer-plugin" % SilencerVersion cross CrossVersion.full)
    )

  val kindProjectorModule: ModuleID =
    compilerPlugin("org.typelevel" %% "kind-projector" % KindProjectorVersion cross CrossVersion.full)

  def stdSettings(
    name: String,
    packageName: Option[String] = None,
    enableSilencer: Boolean = false,
    enableKindProjector: Boolean = false,
    enableCrossProject: Boolean = false
  ): Seq[Setting[_]] =
    Seq(
      Keys.name := name,
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
        "com.github.liancheng" %% "organize-imports" % OrganizeImportsVersion,
        "com.github.vovapolu"  %% "scaluzzi"         % ScaluzziVersion
      ),
      Test / parallelExecution := true,
      incOptions ~= (_.withLogRecompileOnMacro(false)),
      autoAPIMappings := true
      //      unusedCompileDependenciesFilter -= moduleFilter("org.scala-js", "scalajs-library")
    ) ++ (if (enableCrossProject) crossProjectSettings else Seq.empty) ++
      (packageName match {
        case Some(name) => buildInfoSettings(name)
        case None       => Seq.empty
      }) ++ scala3Settings

  def scalaReflectTestSettings(scala213Version: String): List[Setting[_]] = List(
    libraryDependencies ++= {
      if (scalaVersion.value.startsWith("3"))
        Seq("org.scala-lang" % "scala-reflect" % scala213Version % Test)
      else
        Seq("org.scala-lang" % "scala-reflect" % scalaVersion.value % Test)
    }
  )

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
      buildInfoKeys    := Seq[BuildInfoKey](name, version, scalaVersion, sbtVersion, isSnapshot),
      buildInfoPackage := packageName
    )

}
