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

/**
 * Helpers behind the `zio-sbt-website` plugin: the Markdown badges of the
 * `@PROJECT_BADGES@` mdoc variable, the `README.md` generator, and the git and
 * npm version helpers used by the publishing tasks.
 */
@nowarn("msg=detected an interpolated expression")
object WebsiteUtils {

  /**
   * The dist-tag `npm publish` must be given for `version`, if any.
   *
   * A version that ends with `-SNAPSHOT` or contains `--SNAPSHOT` gets the
   * `snapshot` tag. For another version that matches
   * `major.minor.patch-<letters>...`, npm refuses to publish without an
   * explicit `--tag`: the lower-cased letters (for example `rc`) are the tag
   * when `currentLatest` is a version without a `-` (a stable release), and
   * `latest` otherwise, so that a project that never released a stable version
   * keeps its docs visible to dependency bots. Any other version needs no tag
   * and gets `None`.
   */
  def npmPublishTag(version: String, currentLatest: Option[String]): Option[String] = {
    val prereleasePattern = """^\d+\.\d+\.\d+-([a-zA-Z]+).*""".r
    version match {
      // A snapshot must never take over `latest`; it goes under the `snapshot` tag, like `publishSnapshotToNpm`.
      case v if v.endsWith("-SNAPSHOT") || v.contains("--SNAPSHOT") => Some("snapshot")
      case prereleasePattern(tag)                                   =>
        val latestIsStable = currentLatest.exists(!_.contains("-"))
        Some(if (latestIsStable) tag.toLowerCase else "latest")
      case _ => None
    }
  }

  import java.nio.charset.StandardCharsets

  /**
   * Returns `markdown` without its leading YAML front matter. It splits the
   * text on `\n`, drops the leading `---` lines, then every line up to the next
   * `---` line, then the `---` lines there, and joins the rest with `\n`. Input
   * that contains no `---` line comes back as an empty string; input that
   * starts without front matter loses everything up to and including its first
   * `---` line (and any `---` lines right after it); and an empty front matter
   * (two `---` lines in a row at the start) also drops the body up to and
   * including its next `---` line, or the whole body when it has none.
   */
  def removeYamlHeader(markdown: String): String =
    markdown
      .split("\n")
      .dropWhile(_ == "---")
      .dropWhile(_ != "---")
      .dropWhile(_ == "---")
      .mkString("\n")

  /**
   * Reads the file at `pathname` as one string in which the lines are joined
   * with `\n`; the line break at the end of the last line is dropped. The read
   * runs on ZIO's blocking thread pool.
   */
  def readFile(pathname: String): Task[String] =
    ZIO.attemptBlocking {
      val source = scala.io.Source.fromFile(new File(pathname))
      val result = source.getLines().mkString("\n")
      source.close()
      result
    }

  /**
   * Returns a Markdown image, with `projectName` as its alt text, of the
   * shields.io GitHub stars badge of `githubUser/githubRepo`, linked to
   * `https://github.com/githubUser/githubRepo`.
   */
  def githubBadge(githubUser: String, githubRepo: String, projectName: String): String = {
    val githubBadge = s"https://img.shields.io/github/stars/$githubUser/$githubRepo?style=social"
    val repoUrl     = s"https://github.com/$githubUser/$githubRepo"
    s"[![$projectName]($githubBadge)]($repoUrl)"
  }

  /**
   * Returns a Markdown image of the javadoc.io badge of `groupId/artifactId`,
   * linked to `https://javadoc.io/doc/groupId/artifactId`.
   */
  def javadocBadge(groupId: String, artifactId: String): String = {
    val badge      = s"https://javadoc.io/badge2/$groupId/$artifactId/javadoc.svg"
    val javadocUrl = s"https://javadoc.io/doc/$groupId/$artifactId"
    s"[![javadoc]($badge)]($javadocUrl)"
  }

  /**
   * A Markdown image of the ZIO Discord chat badge, linked to
   * `https://discord.gg/2ccFBr4`.
   */
  def discord =
    "[![Chat on Discord!](https://img.shields.io/discord/629491597070827530?logo=discord)](https://discord.gg/2ccFBr4)"

  /**
   * Returns a Markdown image of the GitHub Actions status badge of the workflow
   * `workflowName` in `githubUser/githubRepo`. Each space in the workflow name
   * becomes `%20` in the badge URL.
   */
  def ciBadge(githubUser: String, githubRepo: String, workflowName: String): String = {
    val ci = workflowName.replaceAll(" ", "%20")
    s"![CI Badge](https://github.com/$githubUser/$githubRepo/workflows/$ci/badge.svg)"
  }

  /**
   * Returns a Markdown image of a shields.io badge, labelled
   * `Sonatype Snapshot`, that reads the version from the `maven-metadata.xml`
   * of `artifact` in the Sonatype Central snapshots repository. The badge links
   * to the directory of that artifact in the repository, where the dots of
   * `groupId` become slashes.
   */
  def snapshotBadge(groupId: String, artifact: String): String = {
    val repoUrl     = s"https://central.sonatype.com/repository/maven-snapshots/${groupId.replace('.', '/')}/$artifact"
    val metadataUrl = java.net.URLEncoder.encode(s"$repoUrl/maven-metadata.xml", "UTF-8")
    val badge       = s"https://img.shields.io/maven-metadata/v?metadataUrl=$metadataUrl&label=Sonatype%20Snapshot"
    s"[![Sonatype Snapshots]($badge)]($repoUrl/)"
  }

  /**
   * Returns a Markdown image of the shields.io Maven Central version badge of
   * `groupId/artifact`, labelled `Sonatype Release`, linked to the artifact
   * page on `central.sonatype.com`.
   */
  def releaseBadge(groupId: String, artifact: String): String = {
    val badge = s"https://img.shields.io/maven-central/v/$groupId/$artifact.svg?label=Sonatype%20Release"
    val link  = s"https://central.sonatype.com/artifact/$groupId/$artifact"
    s"[![Sonatype Releases]($badge)]($link)"
  }

  /**
   * A stage of a project's life cycle, with the `name` that appears on its
   * badge. `badge` is the URL of the badge image, and `stagePage` is the page
   * that explains the stages.
   */
  sealed abstract class ProjectStage(val name: String, color: String) {

    /**
     * The URL of the ZIO project stages wiki page.
     */
    val stagePage: String =
      "https://github.com/zio/zio/wiki/Project-Stages"

    /**
     * The URL of the shields.io badge image `Project Stage-<name>-<color>`,
     * with spaces in the name written as `%20`.
     */
    def badge: String =
      s"https://img.shields.io/badge/Project%20Stage-${name.replace(" ", "%20") + '-' + color}.svg"
  }

  /**
   * The six stages a project can be in: `ProductionReady`, `Development`,
   * `Experimental`, `Research`, `Concept` and `Deprecated`, each with its own
   * badge colour.
   */
  object ProjectStage {

    /**
     * The `Production Ready` stage, with a bright green badge.
     */
    final case object ProductionReady extends ProjectStage(name = "Production Ready", "brightgreen")

    /**
     * The `Development` stage, with a green badge.
     */
    final case object Development extends ProjectStage(name = "Development", "green")

    /**
     * The `Experimental` stage, with a yellow-green badge.
     */
    final case object Experimental extends ProjectStage(name = "Experimental", "yellowgreen")

    /**
     * The `Research` stage, with a yellow badge.
     */
    final case object Research extends ProjectStage(name = "Research", "yellow")

    /**
     * The `Concept` stage, with an orange badge.
     */
    final case object Concept extends ProjectStage(name = "Concept", "orange")

    /**
     * The `Deprecated` stage, with a red badge.
     */
    final case object Deprecated extends ProjectStage(name = "Deprecated", "red")
  }

  /**
   * Returns a Markdown image of the badge of `stage`, with the stage name as
   * alt text, linked to the stage's `stagePage`.
   */
  def projectStageBadge(stage: ProjectStage): String =
    s"[![${stage.name}](${stage.badge})](${stage.stagePage})"

  /**
   * Returns the space-separated Markdown badges of a project: stage, CI,
   * Sonatype release, Sonatype snapshot, javadoc and GitHub stars, in that
   * order. The release and javadoc badges are included only when
   * `releaseVersion` finds a release tag in git; the javadoc badge uses
   * `docsArtifactId`, and the release and snapshot badges use `artifactId`.
   */
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

  /**
   * Writes `README.md` in the current working directory. The file starts with a
   * comment that says it was generated, then holds the banner, the
   * introduction, and the Documentation, Contributing, Code of Conduct and
   * Support sections, followed by the Maintainers, Credits and Acknowledgement
   * sections when their text is not empty, and finally the License section. The
   * `# projectName` title is added before the introduction when `showTitle` is
   * `Some(true)`, or when it is `None` and `banner` is blank.
   */
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
    maintainers: String,
    showTitle: Option[Boolean] = None
  ): Task[Unit] = {
    val commentSection =
      """|[//]: # (This file was autogenerated using `zio-sbt-website` plugin via `sbt generateReadme` command.)
         |[//]: # (So please do not edit it manually. Instead, change "docs/index.md" file or sbt setting keys)
         |[//]: # (e.g. "readmeDocumentation" and "readmeSupport".)
         |""".stripMargin
    // A banner (usually a logo) already shows the project name, so by default skip the "# <projectName>" title.
    val introductionSection =
      if (showTitle.getOrElse(banner.trim.isEmpty)) s"\n# $projectName\n\n$introduction\n" else s"\n$introduction\n"
    val creditsSection         = if (credits.nonEmpty) s"\n## Credits\n\n$credits\n" else ""
    val supportSection         = s"\n## Support\n\n$support\n"
    val codeOfConductSection   = s"\n## Code of Conduct\n\n$codeOfConduct\n"
    val contributingSection    = s"\n## Contributing\n\n$contribution\n"
    val documentationSection   = s"\n## Documentation\n\n$documentation\n"
    val maintainersSection     = if (maintainers.nonEmpty) s"\n## Maintainers\n\n$maintainers\n" else ""
    val acknowledgementSection = if (acknowledgement.nonEmpty) s"\n## Acknowledgement\n\n$acknowledgement\n" else ""
    val licenseSection         = s"\n## License\n\n$license\n"
    val readme                 =
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

  /**
   * Returns the latest release version from git: the last tag, in the order of
   * `git tag --sort=committerdate`, that starts with `v`, without that `v`.
   * Returns `None` when there is no such tag. When the git command fails, for
   * example when the directory is not a git repository, it passes a message to
   * `logger` and returns `None`.
   */
  def releaseVersion(logger: String => Unit): Option[String] =
    try
      "git tag --sort=committerdate".!!.split("\n")
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
