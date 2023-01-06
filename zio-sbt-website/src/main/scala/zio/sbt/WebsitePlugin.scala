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

import java.nio.file.{Path, Paths}

import scala.sys.process.*

import mdoc.MdocPlugin
import mdoc.MdocPlugin.autoImport.*
import sbt.Keys.*
import sbt.{Def, *}

import zio.sbt.WebsiteUtils.{ProjectStage, readFile, removeYamlHeader}

case class BadgeInfo(
  artifact: String,
  projectStage: ProjectStage
)

object WebsitePlugin extends sbt.AutoPlugin {

  object autoImport {
    val compileDocs: InputKey[Unit]                 = inputKey[Unit]("Compile docs")
    val installWebsite: TaskKey[Unit]               = taskKey[Unit]("Install the website for the first time")
    val buildWebsite: TaskKey[Unit]                 = taskKey[Unit]("Build website (default output: target/website/build)")
    val previewWebsite: TaskKey[Unit]               = taskKey[Unit]("preview website")
    val publishToNpm: InputKey[Unit]                = inputKey[Unit]("Publish website to the npm registry")
    val publishSnapshotToNpm: InputKey[Unit]        = inputKey[Unit]("Publish snapshot version of website to the npm registry")
    val publishHashverToNpm: InputKey[Unit]         = inputKey[Unit]("Publish hash version of website to the npm registry")
    val generateGithubWorkflow: TaskKey[Unit]       = taskKey[Unit]("Generate github workflow")
    val checkGithubWorkflow: TaskKey[Unit]          = taskKey[Unit]("Make sure the site.yml file is up-to-date")
    val generateReadme: TaskKey[Unit]               = taskKey[Unit]("Generate readme file")
    val npmToken: SettingKey[String]                = settingKey[String]("NPM Token")
    val docsDependencies: SettingKey[Seq[ModuleID]] = settingKey[Seq[ModuleID]]("documentation project dependencies")
    val websiteDir: SettingKey[Path]                = settingKey[Path]("Website directory")
    val docsPublishBranch: SettingKey[String]       = settingKey[String]("Publish branch for documentation")
    val badgeInfo: SettingKey[Option[BadgeInfo]] =
      settingKey[Option[BadgeInfo]]("Information necessary to create badge")
    val projectName: SettingKey[String]            = settingKey[String]("Project name e.g. ZIO SBT")
    val projectHomePage: SettingKey[String]        = settingKey[String]("Project home page url e.g. https://zio.dev/zio-sbt")
    val readmeBanner: SettingKey[String]           = settingKey[String]("Readme banner section")
    val readmeDocumentation: SettingKey[String]    = settingKey[String]("Readme documentation section")
    val readmeContribution: SettingKey[String]     = settingKey[String]("Readme contribution section")
    val readmeCodeOfConduct: SettingKey[String]    = settingKey[String]("Readme code of conduct")
    val readmeSupport: SettingKey[String]          = settingKey[String]("Readme support section")
    val readmeLicense: SettingKey[String]          = settingKey[String]("Readme license section")
    val readmeAcknowledgement: SettingKey[String]  = settingKey[String]("Acknowledgement section")
    val readmeCredits: SettingKey[String]          = settingKey[String]("Credits section")
    val readmeMaintainers: SettingKey[String]      = settingKey[String]("Maintainers section")
    val docsVersioning: SettingKey[DocsVersioning] = settingKey[DocsVersioning]("Docs versioning style")
    val sbtBuildOptions: SettingKey[List[String]]  = settingKey[List[String]]("SBT build options")

    val BadgeInfo = zio.sbt.BadgeInfo
    type BadgeInfo = zio.sbt.BadgeInfo

    val ProjectStage = zio.sbt.WebsiteUtils.ProjectStage
    type ProjectStage = zio.sbt.WebsiteUtils.ProjectStage

    val DocsVersioning = zio.sbt.WebsiteUtils.DocsVersioning
    type DocsVersioning = zio.sbt.WebsiteUtils.DocsVersioning
  }

  import autoImport.*

  override def requires: Plugins = MdocPlugin && UnifiedScaladocPlugin

  private def artifactVersion(version: String): String = {
    val array = version.split('.')
    if (array.head != "3") {
      array.dropRight(1).mkString(".")
    } else "3"
  }

  override lazy val projectSettings: Seq[Setting[_ <: Object]] =
    Seq(
      compileDocs            := compileDocsTask.evaluated,
      websiteDir             := Paths.get(target.value.getPath, "website"),
      mdocOut                := websiteDir.value.resolve("docs").toFile,
      installWebsite         := installWebsiteTask.value,
      buildWebsite           := buildWebsiteTask.value,
      previewWebsite         := previewWebsiteTask.value,
      publishToNpm           := publishWebsiteTask.value,
      publishSnapshotToNpm   := publishSnapshotToNpmTask.value,
      publishHashverToNpm    := publishHashverToNpmTask.value,
      generateGithubWorkflow := generateGithubWorkflowTask.value,
      checkGithubWorkflow    := checkGithubWorkflowTask.value,
      generateReadme         := generateReadmeTask.value,
      badgeInfo              := None,
      docsDependencies       := Seq.empty,
      libraryDependencies ++= docsDependencies.value,
      mdocVariables ++= {
        Map(
          "VERSION"          -> WebsiteUtils.releaseVersion(sLog.value.warn(_)).getOrElse(version.value),
          "RELEASE_VERSION"  -> WebsiteUtils.releaseVersion(sLog.value.warn(_)).getOrElse("NOT RELEASED YET"),
          "SNAPSHOT_VERSION" -> version.value,
          "PROJECT_BADGES" -> {
            badgeInfo.value match {
              case Some(badge) =>
                WebsiteUtils.generateProjectBadges(
                  projectStage = badge.projectStage,
                  groupId = organization.value,
                  artifactId = badge.artifact,
                  docsArtifactId = moduleName.value + '_' + artifactVersion(scalaVersion.value),
                  githubUser = "zio",
                  githubRepo =
                    scmInfo.value.map(_.browseUrl.getPath.split('/').last).getOrElse("github repo not provided"),
                  projectName = projectName.value
                )
              case None => ""
            }
          }
        )
      },
      docsPublishBranch := "main",
      readmeDocumentation := readmeDocumentationSection(
        projectName.value,
        homepage.value.getOrElse(new URL(s"https://zio.dev/ecosystem/"))
      ),
      readmeContribution    := readmeContributionSection,
      readmeSupport         := readmeSupportSection,
      readmeLicense         := readmeLicenseSection,
      readmeAcknowledgement := "",
      readmeContribution    := readmeContributionSection,
      readmeCodeOfConduct   := readmeCodeOfConductSection,
      readmeCredits         := "",
      readmeBanner          := "",
      readmeMaintainers     := "",
      docsVersioning        := DocsVersioning.SemanticVersioning,
      sbtBuildOptions       := List.empty[String]
    )

  private def exit(exitCode: Int, errorMessage: String = "") = if (exitCode != 0) sys.error(errorMessage: String)

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
      val p = Process("npm run start", new File(s"${websiteDir.value}")).!
      exit(p, "Failed to run start command!")
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

  lazy val buildWebsiteTask: Def.Initialize[Task[Unit]] =
    Def.task {
      val _ = Def.sequential(installWebsiteTask, compileDocs.toTask("")).value

      val p = Process("npm run build", new File(s"${websiteDir.value}")).!
      exit(p, "Failed to build the website!")
    }

  lazy val publishWebsiteTask: Def.Initialize[Task[Unit]] =
    Def.task {
      val _ = compileDocs.toTask("").value

      val refinedNpmVersion = {
        val v = WebsiteUtils.releaseVersion(streams.value.log.warn(_)).getOrElse(version.value)
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

  private def prefixUrlsWith(markdown: String, prefix: String): String = {
    val regex = """\((.+?.md)\)""".r

    regex.replaceAllIn(markdown, '(' + prefix + _.group(1) + ')')
  }

  lazy val normalizedVersion: Def.Initialize[Task[String]] =
    Def.task(WebsiteUtils.releaseVersion(sLog.value.warn(_)).getOrElse(version.value))

  lazy val ignoreIndexSnapshotVersion: Def.Initialize[Task[Unit]] = Def.task {
    if (normalizedVersion.value.endsWith("-SNAPSHOT"))
      exit("sed -i.bak s/@VERSION@/<version>/g docs/index.md".!)
  }

  lazy val revertIndexChanges: Def.Initialize[Task[Unit]] = Def.task {
    if (normalizedVersion.value.endsWith("-SNAPSHOT")) {
      exit("rm docs/index.md".!)
      exit("cp docs/index.md.bak docs/index.md".!)
    }
  }

  lazy val generateReadmeTask: Def.Initialize[Task[Unit]] = {
    Def.task {
      import zio.*

      val _ = Def
        .sequential(
          ignoreIndexSnapshotVersion,
          compileDocs.toTask(""),
          revertIndexChanges
        )
        .value

      Unsafe.unsafe { implicit unsafe =>
        Runtime.default.unsafe
          .run(
            readFile(websiteDir.value.resolve("docs/index.md").toString).map(md => removeYamlHeader(md).trim).flatMap {
              introduction =>
                WebsiteUtils.generateReadme(
                  projectName = projectName.value,
                  banner = readmeBanner.value,
                  introduction = prefixUrlsWith(introduction, "docs/").trim,
                  documentation = readmeDocumentation.value.trim,
                  codeOfConduct = readmeCodeOfConduct.value.trim,
                  contribution = readmeContribution.value.trim,
                  support = readmeSupport.value.trim,
                  license = readmeLicense.value.trim,
                  acknowledgement = readmeAcknowledgement.value.trim,
                  credits = readmeCredits.value.trim,
                  maintainers = readmeMaintainers.value.trim
                )
            }
          )
          .getOrThrowFiberFailure()
      }
      val logger = streams.value.log

      logger.info("The new README.md file generated")
    }
  }

  lazy val generateGithubWorkflowTask: Def.Initialize[Task[Unit]] =
    Def.task {
      val workflow = WebsiteUtils.websiteWorkflow(docsPublishBranch.value, sbtBuildOptions.value, docsVersioning.value)

      val template =
        s"""|# This file was autogenerated using `zio-sbt-website` via `sbt generateGithubWorkflow` 
            |# task and should be included in the git repository. Please do not edit it manually.
            |
            |$workflow""".stripMargin

      IO.write(new File(".github/workflows/site.yml"), template)
    }

  lazy val checkGithubWorkflowTask: Def.Initialize[Task[Unit]] =
    Def.task {
      val _ = generateGithubWorkflow.value

      if ("git diff --exit-code".! == 1) {
        sys.error(
          "The site.yml workflow is not up-to-date!\n" +
            "Please run `sbt docs/generateGithubWorkflow` and commit new changes."
        )
      }
    }

  def readmeDocumentationSection(projectName: String, projectHomepageUrl: URL): String =
    s"""Learn more on the [$projectName homepage]($projectHomepageUrl)!""".stripMargin

  def readmeContributionSection: String =
    """For the general guidelines, see ZIO [contributor's guide](https://zio.dev/about/contributing).""".stripMargin

  def readmeCodeOfConductSection: String =
    """See the [Code of Conduct](https://zio.dev/about/code-of-conduct)""".stripMargin

  def readmeSupportSection: String =
    """|Come chat with us on [![Badge-Discord]][Link-Discord].
       |
       |[Badge-Discord]: https://img.shields.io/discord/629491597070827530?logo=discord "chat on discord"
       |[Link-Discord]: https://discord.gg/2ccFBr4 "Discord"""".stripMargin

  def readmeLicenseSection: String =
    """[License](LICENSE)""".stripMargin

}
