/*
 * Copyright 2022 dev.zio
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

import java.nio.file.{Files, Path, Paths}

import scala.sys.process.*

import mdoc.MdocPlugin
import mdoc.MdocPlugin.autoImport.*
import sbt.Keys.*
import sbt.*

object WebsitePlugin extends sbt.AutoPlugin {

  object autoImport {
    val compileDocs: InputKey[Unit]                 = inputKey[Unit]("compile docs")
    val installWebsite: TaskKey[Unit]               = taskKey[Unit]("install the website for the first time")
    val previewWebsite: TaskKey[Unit]               = taskKey[Unit]("preview website")
    val publishToNpm: InputKey[Unit]                = inputKey[Unit]("publish website to the npm registry")
    val publishSnapshotToNpm: InputKey[Unit]        = inputKey[Unit]("publish snapshot version of website to the npm registry")
    val publishHashverToNpm: InputKey[Unit]         = inputKey[Unit]("publish hash version of website to the npm registry")
    val generateGithubWorkflow: TaskKey[Unit]       = taskKey[Unit]("generate github workflow")
    val generateReadme: TaskKey[Unit]               = taskKey[Unit]("generate readme file")
    val npmToken: SettingKey[String]                = settingKey[String]("npm token")
    val docsDependencies: SettingKey[Seq[ModuleID]] = settingKey[Seq[ModuleID]]("documentation project dependencies")
    val websiteDir: SettingKey[Path]                = settingKey[Path]("website directory")
  }

  import autoImport.*

  override def requires = MdocPlugin

  override lazy val projectSettings: Seq[Setting[_ <: Object]] =
    Seq(
      compileDocs            := compileDocsTask.evaluated,
      websiteDir             := Paths.get(target.value.getPath, "website"),
      mdocOut                := websiteDir.value.resolve("docs").toFile,
      installWebsite         := installWebsiteTask.value,
      previewWebsite         := previewWebsiteTask.value,
      publishToNpm           := publishWebsiteTask.value,
      publishSnapshotToNpm   := publishSnapshotToNpmTask.value,
      publishHashverToNpm    := publishHashverToNpmTask.value,
      generateGithubWorkflow := generateGithubWorkflowTask.value,
      generateReadme         := generateReadmeTask.value,
      docsDependencies       := Seq.empty,
      libraryDependencies ++= docsDependencies.value,
      mdocVariables ++= {
        Map(
          "VERSION"          -> releaseVersion(sLog.value.warn(_)).getOrElse(version.value),
          "RELEASE_VERSION"  -> releaseVersion(sLog.value.warn(_)).getOrElse("NOT RELEASED YET"),
          "SNAPSHOT_VERSION" -> version.value
        )
      }
    )

  def releaseVersion(logger: String => Unit): Option[String] =
    try "git tag --sort=committerdate".!!.split("\n").filter(_.startsWith("v")).lastOption.map(_.tail)
    catch {
      case _: Exception =>
        logger(
          s"Could not determine release version from git tags, will return 'None' instead.  This is most likely a result of this project not having a git repo initialized.  See previous log messages for more detail."
        )
        None
    }

  private def exit(exitCode: Int) = if (exitCode != 0) sys.exit(exitCode)

  lazy val previewWebsiteTask: Def.Initialize[Task[Unit]] = Def.task {
    import zio.*

    val task: Task[Unit] =
      for {
        _ <- ZIO.attempt(compileDocsTask.toTask(" --watch").value).forkDaemon
        _ <- ZIO.attempt(docusaurusServerTask.value)
      } yield ()

    Unsafe.unsafe { implicit unsafe =>
      Runtime.default.unsafe.run(task).getOrThrowFiberFailure()
    }
  }
    .dependsOn(compileDocsTask.toTask(""))

  lazy val docusaurusServerTask: Def.Initialize[Task[Unit]] =
    Def.task {
      exit(Process("npm run start", new File(s"${websiteDir.value}")).!)
    }

  lazy val compileDocsTask: Def.Initialize[InputTask[Unit]] =
    Def.inputTaskDyn {
      val parsed =
        sbt.complete.DefaultParsers.spaceDelimited("<arg>").parsed
      val watch =
        parsed.headOption.getOrElse("").equalsIgnoreCase("--watch")
      val logger = streams.value.log
      logger.info("Compiling docs using mdoc ...")

      if (watch)
        mdoc.toTask(" --watch --no-livereload")
      else
        mdoc.toTask("")
    }

  lazy val installWebsiteTask: Def.Initialize[Task[Unit]] =
    Def.task {
      val logger = streams.value.log

      exit(Process(s"rm ${target.value}/${normalizedName.value}-website -Rvf").!)

      val task: String =
        s"""|npx @zio.dev/create-zio-website@latest ${normalizedName.value}-website \\
            |  --description="${name.value}" \\
            |  --author="ZIO Contributors" \\
            |  --email="email@zio.dev" \\
            |  --license="Apache-2.0" \\
            |  --architecture=Linux""".stripMargin

      logger.info(s"installing website for ${normalizedName.value} ... \n$task")

      exit(Process(task, target.value).!)

      exit(Process(s"mv ${target.value}/${normalizedName.value}-website ${websiteDir.value}").!)

      exit(s"rm -rvf ${websiteDir.value.toString}/.git/".!)
    }

  lazy val publishWebsiteTask: Def.Initialize[Task[Unit]] =
    Def.task {
      val _ = compileDocs.toTask("").value

      val refinedNpmVersion = {
        val v = releaseVersion(streams.value.log.warn(_)).getOrElse(version.value)
        if (v.endsWith("-SNAPSHOT")) v.replace("+", "--") else v
      }

      exit(
        Process(
          s"npm version --new-version $refinedNpmVersion --no-git-tag-version",
          new File(s"${websiteDir.value.toString}/docs/")
        ).!
      )

      exit("npm config set access public".!)

      exit(Process("npm publish", new File(s"${websiteDir.value.toString}/docs/")).!)
    }

  private def hashVersion: String = {
    val hashPart = s"git rev-parse --short=12 HEAD".!!
    val datePart = java.time.LocalDate.now().toString.replace("-", ".")
    datePart + "-" + hashPart
  }

  lazy val publishHashverToNpmTask: Def.Initialize[Task[Unit]] =
    Def.task {
      val _ = compileDocs.toTask("").value

      exit(
        Process(
          s"npm version --new-version $hashVersion --no-git-tag-version",
          new File(s"${websiteDir.value.toString}/docs/")
        ).!
      )

      exit("npm config set access public".!)

      exit(Process("npm publish", new File(s"${websiteDir.value.toString}/docs/")).!)
    }

  lazy val publishSnapshotToNpmTask: Def.Initialize[Task[Unit]] =
    Def.task {
      val _ = compileDocs.toTask("").value

      val refinedVersion = version.value.replace("+", "--")

      exit(
        Process(
          s"npm version --new-version $refinedVersion --no-git-tag-version",
          new File(s"${websiteDir.value.toString}/docs/")
        ).!
      )

      exit("npm config set access public".!)

      exit(Process("npm publish", new File(s"${websiteDir.value.toString}/docs/")).!)
    }

  lazy val generateReadmeTask: Def.Initialize[Task[Unit]] = {
    Def.task {
      import zio.*

      val _ = compileDocs.toTask("").value

      Unsafe.unsafe { implicit unsafe =>
        Runtime.default.unsafe
          .run(WebsitePluginUtils.generateReadme(websiteDir.value.resolve("docs/index.md").toString))
          .getOrThrowFiberFailure()
      }
      val logger = streams.value.log

      logger.info("The new README.md file generated")
    }
  }

  lazy val generateGithubWorkflowTask: Def.Initialize[Task[Unit]] =
    Def.task {
      val template =
        s"""|# This file was autogenerated using `zio-sbt` via `sbt generateGithubWorkflow` 
            |# task and should be included in the git repository. Please do not edit 
            |# it manually. 
            |
            |name: website
            |
            |on:
            |  release:
            |    types: [ published ]
            |
            |jobs:
            |  publish-docs:
            |    runs-on: ubuntu-20.04
            |    timeout-minutes: 30
            |    steps:
            |      - uses: actions/checkout@v3.1.0
            |        with:
            |          fetch-depth: 0
            |      - name: Setup Scala and Java
            |        uses: actions/setup-java@v3.6.0
            |        with:
            |          distribution: temurin
            |          java-version: 17
            |          check-latest: true
            |      - uses: actions/setup-node@v3
            |        with:
            |          node-version: '16.x'
            |          registry-url: 'https://registry.npmjs.org'
            |      - name: Publishing Docs to NPM Registry
            |        run: sbt docs/publishToNpm
            |        env:
            |          NODE_AUTH_TOKEN: $${{ secrets.NPM_TOKEN }}
            |""".stripMargin

      IO.write(new File(".github/workflows/site.yml"), template)
    }

}

object WebsitePluginUtils {
  import zio.*

  import java.nio.charset.StandardCharsets

  def removeYamlHeader(markdown: String): String =
    markdown
      .split("\n")
      .dropWhile(_ == "---")
      .dropWhile(_ != "---")
      .dropWhile(_ == "---")
      .mkString("\n")

  def readFile(pathname: String): Task[String] =
    ZIO.attemptBlocking {
      val source = scala.io.Source.fromFile(new File(pathname))
      val result = source.getLines().mkString("\n")
      source.close()
      result
    }

  def generateReadme(sourcePath: String): Task[Unit] =
    for {
      template    <- readFile("README.template.md")
      mainContent <- readFile(sourcePath).map(md => removeYamlHeader(md))
      comment =
        """|[//]: # (This file was autogenerated using `zio-sbt-website` plugin via `sbt generateReadme` command.)
           |[//]: # (So please do not edit it manually. Instead, edit `README.template.md` file. This command will replace any)
           |[//]: # ({{ main_content }} template tag inside the `README.template.md` file with the main content of the)
           |[//]: # ("docs/index.md" file.)
           |""".stripMargin
      readme <- ZIO.succeed(comment + '\n' + template.replaceFirst("\\{\\{.*main_content.*}}", mainContent))
      _ <- ZIO.attemptBlocking(
             Files.write(
               Paths.get("README.md"),
               readme.getBytes(StandardCharsets.UTF_8)
             )
           )
    } yield ()

}
