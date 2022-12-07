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

import java.nio.file.{Path, Paths}

import scala.sys.process.*

import mdoc.MdocPlugin
import mdoc.MdocPlugin.autoImport.*
import sbt.Keys.*
import sbt.*

import zio.sbt.WebsiteUtils.ProjectStage

case class BadgeInfo(
  projectName: String,
  artifact: String,
  projectStage: ProjectStage
)

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
    val badgeInfo: SettingKey[Option[BadgeInfo]] =
      settingKey[Option[BadgeInfo]]("information necessary to create badge")

    val BadgeInfo = zio.sbt.BadgeInfo
    type BadgeInfo = zio.sbt.BadgeInfo

    val ProjectStage = zio.sbt.WebsiteUtils.ProjectStage
    type ProjectStage = zio.sbt.WebsiteUtils.ProjectStage
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
      badgeInfo              := None,
      docsDependencies       := Seq.empty,
      libraryDependencies ++= docsDependencies.value,
      mdocVariables ++= {
        Map(
          "VERSION"          -> releaseVersion(sLog.value.warn(_)).getOrElse(version.value),
          "RELEASE_VERSION"  -> releaseVersion(sLog.value.warn(_)).getOrElse("NOT RELEASED YET"),
          "SNAPSHOT_VERSION" -> version.value,
          "PROJECT_BADGES" -> {
            badgeInfo.value match {
              case Some(badge) =>
                WebsiteUtils.generateProjectBadges(
                  projectStage = badge.projectStage,
                  groupId = organization.value,
                  artifact = badge.artifact,
                  githubUser = "zio",
                  githubRepo =
                    scmInfo.value.map(_.browseUrl.getPath.split('/').last).getOrElse("github repo not provided"),
                  projectName = badge.projectName
                )
              case None => ""
            }
          }
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
          .run(WebsiteUtils.generateReadme(websiteDir.value.resolve("docs/index.md").toString))
          .getOrThrowFiberFailure()
      }
      val logger = streams.value.log

      logger.info("The new README.md file generated")
    }
  }

  lazy val generateGithubWorkflowTask: Def.Initialize[Task[Unit]] =
    Def.task {
      val template =
        s"""|# This file was autogenerated using `zio-sbt-website` via `sbt generateGithubWorkflow` 
            |# task and should be included in the git repository. Please do not edit it manually.
            |
            |${WebsiteUtils.websiteWorkflow}
            |""".stripMargin

      IO.write(new File(".github/workflows/site.yml"), template)
    }

}
