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

import java.nio.file.{Files, Paths}

import scala.annotation.nowarn
import scala.sys.process._

import sbt.File

import zio._

@nowarn("msg=detected an interpolated expression")
object WebsiteUtils {

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

  def githubBadge(githubUser: String, githubRepo: String, projectName: String): String = {
    val githubBadge = s"https://img.shields.io/github/stars/$githubUser/$githubRepo?style=social"
    val repoUrl     = s"https://github.com/$githubUser/$githubRepo"
    s"[![$projectName]($githubBadge)]($repoUrl)"
  }

  def javadocBadge(groupId: String, artifactId: String): String = {
    val badge      = s"https://javadoc.io/badge2/$groupId/$artifactId/javadoc.svg"
    val javadocUrl = s"https://javadoc.io/doc/$groupId/$artifactId"
    s"[![javadoc]($badge)]($javadocUrl)"
  }

  def discord =
    "[![Chat on Discord!](https://img.shields.io/discord/629491597070827530?logo=discord)](https://discord.gg/2ccFBr4)"

  def ciBadge(githubUser: String, githubRepo: String, workflowName: String): String = {
    val ci = workflowName.replaceAll(" ", "%20")
    s"![CI Badge](https://github.com/$githubUser/$githubRepo/workflows/$ci/badge.svg)"
  }

  def snapshotBadge(groupId: String, artifact: String): String = {
    val badge =
      s"https://img.shields.io/nexus/s/https/oss.sonatype.org/$groupId/$artifact.svg?label=Sonatype%20Snapshot"
    val link = s"https://oss.sonatype.org/content/repositories/snapshots/${groupId.replace('.', '/')}/$artifact/"
    s"[![Sonatype Snapshots]($badge)]($link)"
  }

  def releaseBadge(groupId: String, artifact: String): String = {
    val badge = s"https://img.shields.io/nexus/r/https/oss.sonatype.org/$groupId/$artifact.svg?label=Sonatype%20Release"
    val link  = s"https://oss.sonatype.org/content/repositories/releases/${groupId.replace('.', '/')}/$artifact/"
    s"[![Sonatype Releases]($badge)]($link)"
  }

  sealed abstract class ProjectStage(val name: String, color: String) {
    val stagePage: String =
      "https://github.com/zio/zio/wiki/Project-Stages"
    def badge: String =
      s"https://img.shields.io/badge/Project%20Stage-${name.replace(" ", "%20") + '-' + color}.svg"
  }
  object ProjectStage {
    final case object ProductionReady extends ProjectStage(name = "Production Ready", "brightgreen")
    final case object Development     extends ProjectStage(name = "Development", "green")
    final case object Experimental    extends ProjectStage(name = "Experimental", "yellowgreen")
    final case object Research        extends ProjectStage(name = "Research", "yellow")
    final case object Concept         extends ProjectStage(name = "Concept", "orange")
    final case object Deprecated      extends ProjectStage(name = "Deprecated", "red")
  }

  def projectStageBadge(stage: ProjectStage): String =
    s"[![${stage.name}](${stage.badge})](${stage.stagePage})"

  def generateProjectBadges(
    projectStage: ProjectStage,
    groupId: String,
    artifactId: String,
    docsArtifactId: String,
    githubUser: String,
    githubRepo: String,
    projectName: String,
    ciWorkflowName: String
  ): String = {
    val stage    = projectStageBadge(projectStage)
    val ci       = ciBadge(githubUser, githubRepo, ciWorkflowName)
    val snapshot = snapshotBadge(groupId, artifactId)
    val github   = githubBadge(githubUser, githubRepo, projectName)

    releaseVersion(_ => ()) match {
      case Some(_) =>
        val release = releaseBadge(groupId, artifactId)
        val javadoc = javadocBadge(groupId, docsArtifactId)
        s"$stage $ci $release $snapshot $javadoc $github"
      case None =>
        s"$stage $ci $snapshot $github"
    }
  }

  def generateReadme(
    projectName: String,
    banner: String,
    introduction: String,
    documentation: String,
    codeOfConduct: String,
    contribution: String,
    support: String,
    license: String,
    acknowledgement: String,
    credits: String,
    maintainers: String
  ): Task[Unit] = {
    val commentSection =
      """|[//]: # (This file was autogenerated using `zio-sbt-website` plugin via `sbt generateReadme` command.)
         |[//]: # (So please do not edit it manually. Instead, change "docs/index.md" file or sbt setting keys)
         |[//]: # (e.g. "readmeDocumentation" and "readmeSupport".)
         |""".stripMargin
    val introductionSection    = s"\n# $projectName\n\n$introduction\n"
    val creditsSection         = if (credits.nonEmpty) s"\n## Credits\n\n$credits\n" else ""
    val supportSection         = s"\n## Support\n\n$support\n"
    val codeOfConductSection   = s"\n## Code of Conduct\n\n$codeOfConduct\n"
    val contributingSection    = s"\n## Contributing\n\n$contribution\n"
    val documentationSection   = s"\n## Documentation\n\n$documentation\n"
    val maintainersSection     = if (maintainers.nonEmpty) s"\n## Maintainers\n\n$maintainers\n" else ""
    val acknowledgementSection = if (acknowledgement.nonEmpty) s"\n## Acknowledgement\n\n$acknowledgement\n" else ""
    val licenseSection         = s"\n## License\n\n$license\n"
    val readme =
      commentSection + banner + introductionSection + documentationSection + contributingSection + codeOfConductSection +
        supportSection + maintainersSection + creditsSection + acknowledgementSection + licenseSection

    ZIO.attemptBlocking {
      Files.write(
        Paths.get("README.md"),
        readme.getBytes(StandardCharsets.UTF_8)
      )
      ()
    }
  }

  def releaseVersion(logger: String => Unit): Option[String] =
    try "git tag --sort=committerdate".!!.split("\n")
      .filter(_.startsWith("v"))
      .lastOption
      .map(_.tail) // without the leading v char
    catch {
      case _: Exception =>
        logger(
          s"Could not determine release version from git tags, will return 'None' instead.  This is most likely a result of this project not having a git repo initialized.  See previous log messages for more detail."
        )
        None
    }
}
