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

import scala.collection.mutable
import scala.sys.process.*

import _root_.java.nio.file.{Files, Path, Paths}
import mdoc.MdocPlugin
import mdoc.MdocPlugin.autoImport.*
import sbt.Keys.*
import sbt.{Def, *}

import zio.sbt.WebsiteUtils.{readFile, removeYamlHeader}

object WebsitePlugin extends sbt.AutoPlugin {

  object autoImport {
    val compileDocs: InputKey[Unit]                 = inputKey[Unit]("Compile docs")
    val installWebsite: TaskKey[Unit]               = taskKey[Unit]("Install the website for the first time")
    val buildWebsite: TaskKey[Unit]                 = taskKey[Unit]("Build website (default output: target/website/build)")
    val previewWebsite: TaskKey[Unit]               = taskKey[Unit]("preview website")
    val publishToNpm: InputKey[Unit]                = inputKey[Unit]("Publish website to the npm registry")
    val publishSnapshotToNpm: InputKey[Unit]        = inputKey[Unit]("Publish snapshot version of website to the npm registry")
    val publishHashverToNpm: InputKey[Unit]         = inputKey[Unit]("Publish hash version of website to the npm registry")
    val checkReadme: TaskKey[Unit]                  = taskKey[Unit]("Make sure if the README.md file is up-to-date")
    val generateReadme: TaskKey[Unit]               = taskKey[Unit]("Generate readme file")
    val npmToken: SettingKey[String]                = settingKey[String]("NPM Token")
    val docsDependencies: SettingKey[Seq[ModuleID]] = settingKey[Seq[ModuleID]]("documentation project dependencies")
    val websiteDir: SettingKey[Path]                = settingKey[Path]("Website directory")
    val projectStage: SettingKey[ProjectStage]      = settingKey[ProjectStage]("Project stage")
    val projectName: SettingKey[String]             = settingKey[String]("Project name e.g. ZIO SBT")
    val mainModuleName: SettingKey[String]          = settingKey[String]("Main Module Name e.g. zio-sbt")
    val ciWorkflowName: SettingKey[String]          = settingKey[String]("CI Workflow Name")
    val projectHomePage: SettingKey[String]         = settingKey[String]("Project home page url e.g. https://zio.dev/zio-sbt")
    val readmeBanner: SettingKey[String]            = settingKey[String]("Readme banner section")
    val readmeDocumentation: SettingKey[String]     = settingKey[String]("Readme documentation section")
    val readmeContribution: SettingKey[String]      = settingKey[String]("Readme contribution section")
    val readmeCodeOfConduct: SettingKey[String]     = settingKey[String]("Readme code of conduct")
    val readmeSupport: SettingKey[String]           = settingKey[String]("Readme support section")
    val readmeLicense: SettingKey[String]           = settingKey[String]("Readme license section")
    val readmeAcknowledgement: SettingKey[String]   = settingKey[String]("Acknowledgement section")
    val readmeCredits: SettingKey[String]           = settingKey[String]("Credits section")
    val readmeMaintainers: SettingKey[String]       = settingKey[String]("Maintainers section")
    val docsVersioningScheme: SettingKey[VersioningScheme] =
      settingKey[VersioningScheme]("Versioning scheme used for docs package")
    val docsVersion: SettingKey[String] = settingKey[String]("Docs package version")

    val ProjectStage = zio.sbt.WebsiteUtils.ProjectStage
    type ProjectStage = zio.sbt.WebsiteUtils.ProjectStage
  }

  sealed trait VersioningScheme
  object VersioningScheme {
    final case object HashVersioning     extends VersioningScheme
    final case object SemanticVersioning extends VersioningScheme
  }

  import autoImport.*

  override def requires: Plugins = MdocPlugin && UnifiedScaladocPlugin

  override lazy val projectSettings: Seq[Setting[_ <: Object]] =
    Seq(
      compileDocs          := compileDocsTask.evaluated,
      websiteDir           := Paths.get(target.value.getPath, "website"),
      mdocOut              := websiteDir.value.resolve("docs").toFile,
      installWebsite       := installWebsiteTask.value,
      buildWebsite         := buildWebsiteTask.value,
      previewWebsite       := previewWebsiteTask.value,
      publishToNpm         := publishToNpmTask.value,
      publishSnapshotToNpm := publishSnapshotToNpmTask.value,
      publishHashverToNpm  := publishHashverToNpmTask.value,
      checkReadme          := checkReadmeTask.value,
      generateReadme       := generateReadmeTask.value,
      docsDependencies     := Seq.empty,
      libraryDependencies ++= docsDependencies.value,
      mdocVariables ++= {
        Map(
          "VERSION"          -> WebsiteUtils.releaseVersion(sLog.value.warn(_)).getOrElse(version.value),
          "RELEASE_VERSION"  -> WebsiteUtils.releaseVersion(sLog.value.warn(_)).getOrElse("NOT RELEASED YET"),
          "SNAPSHOT_VERSION" -> version.value,
          "PROJECT_BADGES" -> {
            WebsiteUtils.generateProjectBadges(
              projectStage = projectStage.value,
              groupId = organization.value,
              artifactId = mainModuleName.value + '_' + scalaBinaryVersion.value,
              docsArtifactId = moduleName.value + '_' + scalaBinaryVersion.value,
              githubUser = "zio",
              githubRepo = scmInfo.value.map(_.browseUrl.getPath.split('/').last).getOrElse("github repo not provided"),
              projectName = projectName.value,
              ciWorkflowName = ciWorkflowName.value
            )
          }
        )
      },
      readmeDocumentation := readmeDocumentationSection(
        projectName.value,
        homepage.value.getOrElse(url(s"https://zio.dev/ecosystem/"))
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
      ciWorkflowName        := "CI",
      docsVersioningScheme  := VersioningScheme.SemanticVersioning,
      docsVersion           := docsVersionTask.value
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

      val siteTarget = s"${target.value}/${normalizedName.value}-website"

      if (Files.exists(Paths.get(siteTarget)))
        exit(Process(s"rm $siteTarget -Rvf").!)

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

  lazy val publishWebsiteSemanticVersioningTask: Def.Initialize[Task[Unit]] =
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

  private val docsVersionTask: Def.Initialize[String] =
    Def.setting {
      val versioningScheme = docsVersioningScheme.value
      versioningScheme match {
        case VersioningScheme.HashVersioning =>
          hashVersion
        case VersioningScheme.SemanticVersioning =>
          WebsiteUtils.releaseVersion(sLog.value.warn(_)).getOrElse(hashVersion)
      }
    }

  lazy val publishToNpmTask: Def.Initialize[Task[Unit]] =
    Def.task {
      val _       = compileDocs.toTask("").value
      val version = docsVersionTask.value

      exit(
        Process(
          s"npm version --new-version $version --no-git-tag-version",
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
    val regex = """\(((?!http)\S*\.(png|jpg|md|scala|java)\b)\)""".r

    regex.replaceAllIn(markdown, '(' + prefix + _.group(1) + ')')
  }

  lazy val normalizedVersion: Def.Initialize[Task[String]] =
    Def.task(WebsiteUtils.releaseVersion(sLog.value.warn(_)).getOrElse(version.value))

  lazy val fetchLatestTag: Def.Initialize[Task[Unit]] = Def.task {
    import sys.process.*

    val stdout = new mutable.StringBuilder
    val stderr = new mutable.StringBuilder

    exit(
      "git fetch --tags" ! ProcessLogger(
        { out =>
          stdout.append(out)
          ()
        },
        { err =>
          stderr.append(err)
          ()
        }
      )
    )

    if (stderr.mkString.contains("new tag")) {
      throw new MessageOnlyException(
        s"""|New release detected so the ${version.value} is out of date. I need to reload settings to update the version.
            |To do so, please reload the sbt (`sbt reload`) and then try `sbt docs/generateReadme` again""".stripMargin
      )
    }
  }

  lazy val ignoreIndexSnapshotVersion: Def.Initialize[Task[Unit]] = Def.task {
    if (normalizedVersion.value.endsWith("-SNAPSHOT"))
      exit("sed -i.bak s/@VERSION@/<version>/g docs/index.md".!)
  }

  lazy val revertIndexChanges: Def.Initialize[Task[Unit]] = Def.task {
    if (normalizedVersion.value.endsWith("-SNAPSHOT")) {
      exit("rm docs/index.md".!)
      exit("mv docs/index.md.bak docs/index.md".!)
    }
  }

  lazy val generateReadmeTask: Def.Initialize[Task[Unit]] =
    Def.task {
      import zio.*

      val _ = Def
        .sequential(
          fetchLatestTag,
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

  lazy val checkReadmeTask: Def.Initialize[Task[Unit]] =
    Def.task {
      val _ = generateReadme.value

      if ("git diff --exit-code".! == 1) {
        sys.error(
          "The README.md file is not up-to-date!\n" +
            "Please run `sbt docs/generateReadme` and commit new changes."
        )
      }
    }

  def readmeDocumentationSection(projectName: String, projectHomepageUrl: URL): String =
    s"""Learn more on the [$projectName homepage]($projectHomepageUrl)!""".stripMargin

  def readmeContributionSection: String =
    """For the general guidelines, see ZIO [contributor's guide](https://zio.dev/contributor-guidelines).""".stripMargin

  def readmeCodeOfConductSection: String =
    """See the [Code of Conduct](https://zio.dev/code-of-conduct)""".stripMargin

  def readmeSupportSection: String =
    """|Come chat with us on [![Badge-Discord]][Link-Discord].
       |
       |[Badge-Discord]: https://img.shields.io/discord/629491597070827530?logo=discord "chat on discord"
       |[Link-Discord]: https://discord.gg/2ccFBr4 "Discord"""".stripMargin

  def readmeLicenseSection: String =
    """[License](LICENSE)""".stripMargin

}
