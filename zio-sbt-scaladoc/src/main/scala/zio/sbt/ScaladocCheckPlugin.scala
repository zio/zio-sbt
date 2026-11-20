/*
 * Copyright 2022-2026 dev.zio
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

import java.util.Optional

import sbt.Keys._
import sbt._
import sbt.internal.util.MessageOnlyException

import zio.sbt.scaladoc.ScaladocSnippets
import zio.sbt.scaladoc.ScaladocSnippets.{Generated, Platform}

/**
 * Compiles the code examples in the Scaladoc of every module of a build.
 *
 * Loading the plugin (`addSbtPlugin("dev.zio" % "zio-sbt-scaladoc" % ...)`)
 * switches it on in every module. `sbt +scaladocCheck` then compiles, in each
 * module and for each of its Scala versions, every multi-line `{{{ }}}` (or
 * ```` ```scala ````) block inside a `/** */` comment of the module's main
 * sources. `compile`, `test` and `publish` do not run it.
 *
 * How an example is compiled:
 *   - It is wrapped in an `object` of its own, in a file under the module's
 *     `target`, and compiled in the `ScaladocCheck` configuration, which sees
 *     the module's main classpath (its own classes and every library it
 *     depends on) and uses its `Compile / scalacOptions`, minus the flags that
 *     turn warnings into errors. A Scala.js or Scala Native module therefore
 *     compiles its examples with the Scala.js or Scala Native compiler.
 *   - The wrapper imports the packages that the source file's `package`
 *     clauses open, so an example sees what the code around it sees, without
 *     the package-private members. The file's own package is imported only
 *     when the example uses one of its names, so the import is never unused.
 *   - A compile error is reported at the Scaladoc line of the source file.
 *
 * The first lines of a block can be markers: `// scala-3-only`,
 * `// scala-2-only`, `// jvm-only`, `// js-only`, `// native-only`,
 * `// no-js`, `// no-native`, and `// no-check: <reason>` (the reason is
 * required). A line that looks like a marker but is not one of these fails the
 * check.
 *
 * Limitations:
 *   - One-line `{{{ x }}}` blocks and blocks outside `/** */` are not checked.
 *   - Examples are compiled, not run: a result shown in a comment is not
 *     checked.
 *   - On Scala.js and Scala Native, examples are compiled, not linked. A JDK
 *     class the platform lacks (`java.io.File` on Scala.js) is only reported
 *     by the linker, so the check does not catch it; a library the platform's
 *     module does not depend on, and the platform compiler's own checks, are
 *     caught.
 *   - An example sees only its own module and the modules it depends on. An
 *     example that needs another library gets it with
 *     `libraryDependencies += ... % ScaladocCheck`, which the published module
 *     does not depend on.
 *   - An example that only uses a `given` or an anonymous implicit of its
 *     package, or a name of its package that comes from a library jar, needs
 *     its own import.
 */
object ScaladocCheckPlugin extends AutoPlugin {

  override def trigger = allRequirements

  override def requires = plugins.JvmPlugin

  object autoImport {

    /**
     * The configuration the Scaladoc examples are compiled in. Add a library
     * that only the examples need with
     * `libraryDependencies += "org" %% "name" % "version" % ScaladocCheck`.
     */
    val ScaladocCheck = config("scaladoccheck").extend(Compile).hide

    @transient
    val scaladocCheck: TaskKey[Unit] =
      taskKey[Unit](
        "Compiles the code examples in this module's Scaladoc. `scaladocCheck / skip := true` turns it off for a module"
      )
  }

  import autoImport._

  @transient
  private[sbt] val scaladocCheckGenerate: TaskKey[Seq[(File, Generated)]] =
    taskKey[Seq[(File, Generated)]]("Writes one source file per Scaladoc example of this module")

  override def projectConfigurations: Seq[Configuration] = Seq(ScaladocCheck)

  override def projectSettings: Seq[Setting[_]] =
    inConfig(ScaladocCheck)(Defaults.configSettings) ++ Seq(
      ScaladocCheck / unmanagedSourceDirectories   := Nil,
      ScaladocCheck / unmanagedResourceDirectories := Nil,
      ScaladocCheck / scalacOptions                := exampleScalacOptions((Compile / scalacOptions).value),
      ScaladocCheck / bspEnabled                   := false,
      ScaladocCheck / sourceGenerators += Def.task(scaladocCheckGenerate.value.map(_._1)).taskValue,
      scaladocCheckGenerate := generateTask.value,
      scaladocCheck / skip  := false,
      scaladocCheck         := Def.taskDyn {
        if ((scaladocCheck / skip).value) Def.task(())
        else
          Def.taskDyn {
            if (scaladocCheckGenerate.value.isEmpty) Def.task(())
            else ScaladocCheckCompat.compileExamples(ScaladocCheck)
          }
      }.value
    ) ++ ScaladocCheckCompat.classpathAndPositionSettings(ScaladocCheck, scaladocCheckGenerate)

  private lazy val generateTask: Def.Initialize[Task[Seq[(File, Generated)]]] = Def.task {
    val log        = streams.value.log
    val root       = (LocalRootProject / baseDirectory).value
    val outDir     = (ScaladocCheck / sourceManaged).value / "scaladoccheck"
    val scalaMajor = CrossVersion.partialVersion(scalaVersion.value).fold(3)(_._1.toInt)
    val platform   = platformOf(libraryDependencies.value)
    // Each module's source list already holds exactly the shared, platform and version-specific
    // (scala-2, scala-3, ...) files for the current Scala version.
    val own     = read(root, (Compile / unmanagedSources).value)
    val visible = read(
      root,
      (Compile / unmanagedSources).all(ScopeFilter(inDependencies(ThisProject))).value.flatten.distinct
    )
    val generated =
      try ScaladocSnippets.generate(own, visible, scalaMajor, platform)
      catch { case e: IllegalArgumentException => throw new MessageOnlyException(e.getMessage) }
    val files = generated.map(g => (outDir / g.fileName, g))
    files.foreach { case (file, g) =>
      // Rewrite only changed wrappers, so incremental compilation recompiles only those.
      if (!file.exists || IO.read(file) != g.content) IO.write(file, g.content)
    }
    val names = generated.map(_.fileName).toSet
    // Drop the wrappers of examples that were removed or moved.
    Option(outDir.listFiles).toList.flatten.filterNot(f => names(f.getName)).foreach(IO.delete)
    if (generated.nonEmpty)
      log.info(
        s"scaladocCheck: ${generated.size} example(s) in ${thisProject.value.id} (${platform.name}, Scala ${scalaVersion.value})"
      )
    files
  }

  private def read(root: File, files: Seq[File]): Seq[(String, String)] =
    files.filter(_.getName.endsWith(".scala")).map { file =>
      (IO.relativize(root, file).getOrElse(file.getPath).replace('\\', '/'), IO.read(file))
    }

  /**
   * The platform a module compiles for, read from the runtime library that
   * sbt-scalajs or sbt-scala-native adds to its dependencies.
   */
  private[sbt] def platformOf(dependencies: Seq[ModuleID]): Platform =
    if (dependencies.exists(m => m.organization == "org.scala-js" && m.name.startsWith("scalajs-library")))
      Platform.Js
    else if (
      dependencies.exists(m =>
        m.organization == "org.scala-native" && Seq("nativelib", "javalib", "scalalib").exists(m.name.startsWith)
      )
    ) Platform.Native
    else Platform.Jvm

  private val fatalWarningFlags = Set("-Xfatal-warnings", "-Werror", "-Wconf:any:error", "-Wconf:any:e")

  /**
   * The module's compiler options for its examples: without the flags that turn
   * warnings into errors (an example's deprecation warning must not fail the
   * build) and without SemanticDB, whose output directory is the main code's.
   */
  private[sbt] def exampleScalacOptions(options: Seq[String]): Seq[String] = {
    def isSemanticdb(option: String) =
      option.startsWith("-P:semanticdb") || option.contains("semanticdb-scalac") || option == "-Xsemanticdb" ||
        option == "-Ysemanticdb"
    @annotation.tailrec
    def loop(rest: List[String], acc: List[String]): List[String] = rest match {
      case "-semanticdb-target" :: _ :: tail           => loop(tail, acc)
      case option :: tail if fatalWarningFlags(option) => loop(tail, acc)
      case option :: tail if isSemanticdb(option)      => loop(tail, acc)
      case option :: tail
          if option.startsWith("-Xplugin:") &&
            option.contains("semanticdb") =>
        loop(tail, acc)
      case option :: tail => loop(tail, option :: acc)
      case Nil            => acc.reverse
    }
    loop(options.toList, Nil)
  }

  /**
   * Maps a position in a generated wrapper to the Scaladoc line it came from;
   * `None` for any other position, which sbt then reports unchanged.
   */
  private[sbt] def positionMapper(
    root: File,
    generated: Seq[(File, Generated)]
  ): xsbti.Position => Option[xsbti.Position] = {
    val byName = generated.map { case (file, g) => file.getName -> g }.toMap
    (position: xsbti.Position) => {
      val path = toOption(position.sourceFile).map(_.getPath).orElse(toOption(position.sourcePath)).getOrElse("")
      val name = path.replace('\\', '/').split('/').last
      for {
        g                          <- byName.get(name)
        line                       <- toOption(position.line).map(_.intValue)
        (srcLine, column, content) <- g.mapPosition(line, toOption(position.pointer).map(_.intValue))
      } yield sourcePosition(root / g.snippet.sourcePath, srcLine, column, content)
    }
  }

  private def toOption[A](optional: Optional[A]): Option[A] =
    if (optional.isPresent) Some(optional.get) else None

  private def sourcePosition(file: File, lineNumber: Int, column: Option[Int], content: String): xsbti.Position =
    new xsbti.Position {
      override def line(): Optional[Integer]    = Optional.of(Integer.valueOf(lineNumber))
      override def lineContent(): String        = content
      override def offset(): Optional[Integer]  = Optional.empty[Integer]()
      override def pointer(): Optional[Integer] =
        column.fold(Optional.empty[Integer]())(c => Optional.of(Integer.valueOf(c)))
      override def pointerSpace(): Optional[String] =
        column.fold(Optional.empty[String]())(c =>
          Optional.of(content.take(c).map(ch => if (ch == '\t') '\t' else ' '))
        )
      override def sourcePath(): Optional[String] = Optional.of(file.getPath)
      override def sourceFile(): Optional[File]   = Optional.of(file)
    }
}
