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
import scala.sys.process._

import _root_.java.nio.file.{Files, Path, Paths}
import mdoc.MdocPlugin
import mdoc.MdocPlugin.autoImport._
import sbt.Keys._
import sbt.{Def, _}

import zio.Chunk
import zio.json._
import zio.json.ast.Json
import zio.sbt.WebsiteUtils.{readFile, removeYamlHeader}

/**
 * An sbt plugin that compiles the project documentation with mdoc into the
 * `docs` folder of the Docusaurus website (`websiteDir`), builds, previews and
 * installs that website, publishes the compiled `docs` folder to npm as a docs
 * package, and generates the project `README.md`. It requires `MdocPlugin` and
 * [[UnifiedScaladocPlugin]], and is enabled per project (it has no trigger of
 * its own).
 */
object WebsitePlugin extends sbt.AutoPlugin {

  /** The setting and task keys that the plugin adds to a project. */
  object autoImport {

    /**
     * Runs mdoc on the documentation sources and writes the result to
     * `mdocOut`, which the plugin sets to the `docs` folder inside
     * `websiteDir`. When the first argument is `--watch` (compared ignoring
     * case), mdoc runs in watch mode with `--no-livereload`; otherwise it runs
     * once.
     */
    val compileDocs: InputKey[Unit] = inputKey[Unit]("Compile docs")

    /**
     * Scaffolds the Docusaurus website into `websiteDir` with
     * `npx @zio.dev/create-zio-website` at the version
     * `createZioWebsiteVersion`, then runs `npm install` there. It logs a
     * warning and skips the installation when `websiteDir` already contains a
     * `package.json`; in both cases it creates the `docs` folder. When the
     * `@docusaurus/core` major version in the scaffold's `package.json` is
     * below 3, it adds an `overrides` entry to `package.json` that pins
     * `webpack` to `5.75.0`.
     */
    val installWebsite: TaskKey[Unit] = taskKey[Unit]("Install the website for the first time")

    /**
     * Runs `installWebsite` and `compileDocs`, then runs `npm install` in
     * `websiteDir` when it has no `node_modules` folder, and finally
     * `npm run build` there. It fails with "Failed to build the website!" when
     * the build command exits with a non-zero code, and fails with a message
     * that `websiteDir` contains no `package.json` when there is no
     * `node_modules` folder and no `package.json`.
     */
    val buildWebsite: TaskKey[Unit] = taskKey[Unit]("Build website (default output: website/build)")

    /**
     * Runs `compileDocs`, `compileDocs --watch` and the Docusaurus development
     * server (`npm run start` in `websiteDir`). It fails with "Failed to run
     * start command!" when the server command exits with a non-zero code.
     */
    val previewWebsite: TaskKey[Unit] = taskKey[Unit]("preview website")

    /**
     * Compiles the docs, scaffolds a new website under `target` from
     * `@zio.dev/create-zio-website` at the version `createZioWebsiteVersion`,
     * replaces its `docs` folder with the compiled docs, and runs `npm install`
     * and `npm run build` there. When `websiteDir` has no `package.json`, it
     * only compiles the docs and logs a message, because `buildWebsite` then
     * already builds on a freshly scaffolded site. It fails with "The docs do
     * not build on a fresh ZIO website (the one zio/zio builds them with)!"
     * when the build fails.
     */
    val checkDocsOnFreshWebsite: TaskKey[Unit] =
      taskKey[Unit]("Build the docs in a freshly scaffolded ZIO website, the way the ZIO website consumes them")

    /**
     * Runs `compileDocs`, then publishes the `docs` folder inside `websiteDir`
     * to the npm registry. It sets the package version to the one the
     * `docsVersioningScheme` setting selects, sets `repository.url` from the
     * browse URL of `scmInfo`, sets the npm `access` config to `public`, and
     * runs `npm publish`. The `npm publish` call carries a `--tag` flag only
     * for a prerelease or snapshot version. It fails with "scmInfo must be set
     * for npm provenance verification" when `scmInfo` is not set.
     */
    val publishToNpm: InputKey[Unit] = inputKey[Unit]("Publish website to the npm registry")

    /**
     * Runs `compileDocs`, then publishes the `docs` folder inside `websiteDir`
     * to npm under the `snapshot` dist-tag. The package version is the project
     * `version` with every `+` replaced by `--`. It fails with "scmInfo must be
     * set for npm provenance verification" when `scmInfo` is not set.
     */
    val publishSnapshotToNpm: InputKey[Unit] = inputKey[Unit]("Publish snapshot version of website to the npm registry")

    /**
     * Runs `compileDocs`, then publishes the `docs` folder inside `websiteDir`
     * to npm under the `hash` dist-tag. The package version is the current date
     * as `yyyy.MM.dd` followed by `-` and the git `HEAD` hash shortened by
     * `git rev-parse --short=12 HEAD` (12 characters, more when 12 are not
     * unique). It fails when the hash cannot be read from git, and with
     * "scmInfo must be set for npm provenance verification" when `scmInfo` is
     * not set.
     */
    val publishHashverToNpm: InputKey[Unit] = inputKey[Unit]("Publish hash version of website to the npm registry")

    /**
     * Runs `generateReadme`, then runs `git diff --exit-code`, and fails with
     * an error whose message starts with "The README.md file is not
     * up-to-date!" when that command exits with code 1. `git diff` compares the
     * working tree with the git index, so the check covers every unstaged
     * change to a tracked file, not only `README.md`; staged changes and
     * untracked files do not fail it.
     */
    val checkReadme: TaskKey[Unit] = taskKey[Unit]("Make sure if the README.md file is up-to-date")

    /**
     * Writes `README.md` in the sbt working directory. It first runs
     * `git fetch --tags` and fails with a message asking for `sbt reload` when
     * that fetch reports a new tag, then runs `compileDocs`, and builds the
     * README from the compiled `docs/index.md` (or `docs/index.mdx` when only
     * that exists) in `websiteDir` and the `readme*` settings.
     */
    val generateReadme: TaskKey[Unit] = taskKey[Unit]("Generate readme file")

    /**
     * A token string setting with no default. No task in the plugin reads it.
     */
    val npmToken: SettingKey[String] = settingKey[String]("NPM Token")

    /**
     * Extra library dependencies of the documentation project, appended to
     * `libraryDependencies`. The default is an empty `Seq`.
     */
    val docsDependencies: SettingKey[Seq[ModuleID]] = settingKey[Seq[ModuleID]]("documentation project dependencies")

    /**
     * The folder of the Docusaurus website. The default is the `website` folder
     * under the `baseDirectory` of the build root (`ThisBuild`).
     */
    val websiteDir: SettingKey[Path] = settingKey[Path]("Website directory")

    /**
     * The stage of the project (for example `ProjectStage.ProductionReady`),
     * shown as a badge in the `@PROJECT_BADGES@` mdoc variable. The plugin sets
     * no default.
     */
    val projectStage: SettingKey[ProjectStage] = settingKey[ProjectStage]("Project stage")

    /**
     * The human-readable project name, such as `ZIO SBT`, used in the badges
     * and as the README title. The plugin sets no default.
     */
    val projectName: SettingKey[String] = settingKey[String]("Project name e.g. ZIO SBT")

    /**
     * The name of the main module of the project, such as `zio-sbt`. The
     * default of `badgeArtifactId` is built from it. The plugin sets no
     * default.
     */
    val mainModuleName: SettingKey[String] = settingKey[String]("Main Module Name e.g. zio-sbt")

    /**
     * The artifact id used in the release, snapshot and javadoc badges of
     * `@PROJECT_BADGES@`. The default is `mainModuleName`, an underscore and
     * the Scala binary version. Override it for artifacts with other
     * coordinates, such as sbt plugins (`name_2.12_1.0`).
     */
    val badgeArtifactId: SettingKey[String] =
      settingKey[String](
        "Artifact id used in version badges (default: mainModuleName_scalaBinaryVersion). " +
          "Override it for artifacts with non-standard coordinates, e.g. sbt plugins (name_2.12_1.0)."
      )

    /**
     * The name of the GitHub Actions workflow whose status badge appears in
     * `@PROJECT_BADGES@`. The default is `CI`.
     */
    val ciWorkflowName: SettingKey[String] = settingKey[String]("CI Workflow Name")

    /**
     * A home page URL setting with no default. No task in the plugin reads it;
     * the README documentation link uses the sbt `homepage` setting instead.
     */
    val projectHomePage: SettingKey[String] = settingKey[String]("Project home page url e.g. https://zio.dev/zio-sbt")

    /**
     * Markdown placed at the top of the generated README, before the
     * introduction. The default is an empty string. A banner that is not blank
     * also removes the `# <projectName>` title unless `readmeShowTitle` is
     * `Some(true)`.
     */
    val readmeBanner: SettingKey[String] = settingKey[String]("Readme banner section")

    /**
     * Whether the generated README starts with a `# <projectName>` heading.
     * `None`, the default, shows the heading only when `readmeBanner` is blank.
     */
    val readmeShowTitle: SettingKey[Option[Boolean]] = settingKey[Option[Boolean]](
      "Whether the generated README starts with a '# <projectName>' heading. None (default): shown unless readmeBanner is set"
    )

    /**
     * The Documentation section of the generated README. The default is a link
     * to the project homepage built from `projectName` and the sbt `homepage`
     * setting, or `https://zio.dev/ecosystem/` when `homepage` is not set.
     */
    val readmeDocumentation: SettingKey[String] = settingKey[String]("Readme documentation section")

    /**
     * The Contributing section of the generated README. The default points to
     * the ZIO contributor's guide.
     */
    val readmeContribution: SettingKey[String] = settingKey[String]("Readme contribution section")

    /**
     * The Code of Conduct section of the generated README. The default links to
     * the ZIO code of conduct.
     */
    val readmeCodeOfConduct: SettingKey[String] = settingKey[String]("Readme code of conduct")

    /**
     * The Support section of the generated README. The default invites readers
     * to the ZIO Discord chat.
     */
    val readmeSupport: SettingKey[String] = settingKey[String]("Readme support section")

    /**
     * The License section of the generated README. The default is a link to the
     * `LICENSE` file.
     */
    val readmeLicense: SettingKey[String] = settingKey[String]("Readme license section")

    /**
     * The Acknowledgement section of the generated README. The default is an
     * empty string, which leaves the section out.
     */
    val readmeAcknowledgement: SettingKey[String] = settingKey[String]("Acknowledgement section")

    /**
     * The Credits section of the generated README. The default is an empty
     * string, which leaves the section out.
     */
    val readmeCredits: SettingKey[String] = settingKey[String]("Credits section")

    /**
     * The Maintainers section of the generated README. The default is an empty
     * string, which leaves the section out.
     */
    val readmeMaintainers: SettingKey[String] = settingKey[String]("Maintainers section")

    /**
     * The [[WebsitePlugin.VersioningScheme]] that selects the version of the
     * docs package. The default is `VersioningScheme.SemanticVersioning`.
     */
    val docsVersioningScheme: SettingKey[VersioningScheme] =
      settingKey[VersioningScheme]("Versioning scheme used for docs package")

    /**
     * The version of the docs package, computed from `docsVersioningScheme`.
     * The `publishToNpm` task computes the version again from
     * `docsVersioningScheme` instead of reading this setting.
     */
    val docsVersion: SettingKey[String] = settingKey[String]("Docs package version")

    /**
     * The version of the `@zio.dev/create-zio-website` npm package that
     * `installWebsite` and `checkDocsOnFreshWebsite` scaffold with. The default
     * is `0.1.0`.
     */
    val createZioWebsiteVersion: SettingKey[String] =
      settingKey[String]("Pinned version of @zio.dev/create-zio-website npm package")

    /**
     * The companion of `WebsiteUtils.ProjectStage`, re-exported so that
     * `ProjectStage.ProductionReady` and the other stages are available in
     * `build.sbt`.
     */
    val ProjectStage = zio.sbt.WebsiteUtils.ProjectStage

    /**
     * The type of the `projectStage` setting; an alias of
     * `WebsiteUtils.ProjectStage`.
     */
    type ProjectStage = zio.sbt.WebsiteUtils.ProjectStage
  }

  /**
   * How the version of the docs package is chosen. The `docsVersion` setting
   * reads it from `docsVersioningScheme`.
   */
  sealed trait VersioningScheme

  /** The two versioning schemes. */
  object VersioningScheme {

    /**
     * The version is the current date as `yyyy.MM.dd`, then `-` and the git
     * `HEAD` hash shortened by `git rev-parse --short=12 HEAD` (12 characters,
     * more when 12 are not unique). It falls back to the project `version`
     * (with a warning) when git cannot supply the hash.
     */
    final case object HashVersioning extends VersioningScheme

    /**
     * The version is the latest git tag that starts with `v`, without the `v`,
     * taken by the committer date of the tags. It falls back to the
     * `HashVersioning` version when the repository has no such tag.
     */
    final case object SemanticVersioning extends VersioningScheme
  }

  import autoImport.*

  override def requires: Plugins = MdocPlugin && UnifiedScaladocPlugin

  override lazy val projectSettings: Seq[Setting[_]] =
    Seq(
      compileDocs             := compileDocsTask.evaluated,
      websiteDir              := Paths.get((ThisBuild / baseDirectory).value.getPath, "website"),
      mdocOut                 := websiteDir.value.resolve("docs").toFile,
      installWebsite          := installWebsiteTask.value,
      buildWebsite            := buildWebsiteTask.value,
      checkDocsOnFreshWebsite := checkDocsOnFreshWebsiteTask.value,
      previewWebsite          := previewWebsiteTask.value,
      publishToNpm            := publishToNpmTask.value,
      publishSnapshotToNpm    := publishSnapshotToNpmTask.value,
      publishHashverToNpm     := publishHashverToNpmTask.value,
      checkReadme             := checkReadmeTask.value,
      generateReadme          := generateReadmeTask.value,
      // mdoc only treats a limited set of extensions as markdown by default, so without
      // this, docs/index.mdx would be copied verbatim, leaving @VERSION@ and
      // @PROJECT_BADGES@ unsubstituted. `--markdown-extensions` replaces the defaults and
      // must be passed once per extension (a comma-separated value is parsed as a single
      // literal extension that matches no files), so include all desired extensions here.
      mdocExtraArguments ++= Seq(
        "--markdown-extensions",
        "md",
        "--markdown-extensions",
        "html",
        "--markdown-extensions",
        "mdx"
      ),
      docsDependencies := Seq.empty,
      badgeArtifactId  := mainModuleName.value + '_' + scalaBinaryVersion.value,
      libraryDependencies ++= docsDependencies.value,
      mdocVariables ++= {
        Map(
          "VERSION"          -> WebsiteUtils.releaseVersion(sLog.value.warn(_)).getOrElse(version.value),
          "RELEASE_VERSION"  -> WebsiteUtils.releaseVersion(sLog.value.warn(_)).getOrElse("NOT RELEASED YET"),
          "SNAPSHOT_VERSION" -> version.value,
          "PROJECT_BADGES"   -> {
            WebsiteUtils.generateProjectBadges(
              projectStage = projectStage.value,
              groupId = organization.value,
              artifactId = badgeArtifactId.value,
              // The javadoc badge must point at a published artifact. The docs module
              // itself (moduleName here) is typically not published, so use the same
              // published coordinate as the release badge.
              docsArtifactId = badgeArtifactId.value,
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
        homepage.value.map(_.toString).getOrElse("https://zio.dev/ecosystem/")
      ),
      readmeContribution      := readmeContributionSection,
      readmeSupport           := readmeSupportSection,
      readmeLicense           := readmeLicenseSection,
      readmeAcknowledgement   := "",
      readmeContribution      := readmeContributionSection,
      readmeCodeOfConduct     := readmeCodeOfConductSection,
      readmeCredits           := "",
      readmeBanner            := "",
      readmeShowTitle         := None,
      readmeMaintainers       := "",
      ciWorkflowName          := "CI",
      docsVersioningScheme    := VersioningScheme.SemanticVersioning,
      docsVersion             := docsVersionTask.value,
      createZioWebsiteVersion := "0.1.0"
    ) ++ WebsitePluginCompat.runMdocInServerSettings

  private def exit(exitCode: Int, errorMessage: String = "") = if (exitCode != 0) sys.error(errorMessage: String)

  // Docs index may be either index.md or index.mdx (e.g. when the page needs
  // JSX components like Tabs); mdoc preserves the extension on output.
  private def resolveIndexFile(docsDir: Path): Path = {
    val md  = docsDir.resolve("index.md")
    val mdx = docsDir.resolve("index.mdx")
    if (!Files.exists(md) && Files.exists(mdx)) mdx else md
  }

  /**
   * The implementation of `previewWebsite`.
   */
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

  /**
   * Runs `npm run start` in `websiteDir`. It fails with "Failed to run start
   * command!" when the command exits with a non-zero code.
   */
  lazy val docusaurusServerTask: Def.Initialize[Task[Unit]] =
    Def.task {
      val p = Process("npm run start", new File(s"${websiteDir.value}")).!
      exit(p, "Failed to run start command!")
    }

  /**
   * The implementation of `compileDocs`. It logs "Compiling docs using mdoc
   * ..." and runs the `mdoc` task with the arguments ` --watch --no-livereload`
   * when the first input argument is `--watch` (ignoring case), and with no
   * arguments otherwise.
   */
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

  /**
   * The implementation of `installWebsite`.
   */
  lazy val installWebsiteTask: Def.Initialize[Task[Unit]] =
    Def.task {
      val logger = streams.value.log

      val websiteDirPath = websiteDir.value

      // Keyed on package.json, not on the directory itself. `mdocOut` writes generated docs to
      // `website/docs`, so a repository that commits that output -- or simply ran `compileDocs`
      // first -- has a `website/` directory containing no Docusaurus scaffold at all. Treating
      // that as "already installed" leaves nothing for `npm install` to read, which is the
      // failure reported in #749.
      if (Files.exists(websiteDirPath.resolve("package.json"))) {
        logger.warn(
          s"""|
              |Website directory already exists, skipping installation: $websiteDirPath
              |Remove it manually and rerun installWebsite if you want a fresh install.
              |""".stripMargin
        )
      } else {
        val siteTarget = s"${target.value}/${normalizedName.value}-website"

        if (Files.exists(Paths.get(siteTarget)))
          exit(Process(s"rm $siteTarget -Rvf").!)

        val task: String =
          scaffoldCommand(createZioWebsiteVersion.value, s"${normalizedName.value}-website", name.value)

        logger.info(s"installing website for ${normalizedName.value} ... \n$task")

        exit(Process(task, target.value).!)

        // `mv` moves the source *into* an existing destination directory rather than becoming
        // it, so a pre-existing `website/` (holding mdoc output, say) would end up with a nested
        // `website/<name>-website`. Copy the scaffold's contents in instead, which merges with
        // whatever is already there.
        if (Files.exists(websiteDirPath)) {
          exit(Process(s"cp -a $siteTarget/. ${websiteDirPath}/").!)
          exit(Process(s"rm -rf $siteTarget").!)
        } else
          exit(Process(s"mv $siteTarget ${websiteDirPath}").!)

        exit(s"rm -rvf ${websiteDirPath.toString}/.git/".!)

        // Docusaurus 2.x uses ProgressPlugin options that webpack >=5.76 removed, so a site
        // scaffolded on 2.x must pin webpack to 5.75.0 or `npm run build` breaks.
        //
        // The pin is applied only when the scaffold really is on 2.x. Docusaurus 3.x depends on
        // webpack ^5.95.0, where pinning 5.75.0 is unsatisfiable and fails `npm install` with
        // ERESOLVE. Keying the override off the scaffolded version lets the pin retire itself
        // when create-zio-website moves to Docusaurus 3, instead of turning into a broken
        // constraint for every project that scaffolds a new site.
        val pkgJsonFile    = new File(s"${websiteDirPath}/package.json")
        val pkgJsonContent = IO.read(pkgJsonFile)
        val pkgJson        = pkgJsonContent.fromJson[Json.Obj] match {
          case Right(obj) => obj
          case Left(err)  => sys.error(s"Failed to parse package.json: $err")
        }

        if (docusaurusMajorOf(pkgJson).exists(_ < 3)) {
          logger.info("Docusaurus 2.x scaffold detected, pinning webpack to 5.75.0.")
          val patched =
            Json
              .Obj(pkgJson.fields :+ ("overrides" -> Json.Obj(Chunk("webpack" -> Json.Str("5.75.0")))))
              .toJsonPretty + "\n"
          IO.write(pkgJsonFile, patched)
        }

        exit(Process("npm install", new File(s"${websiteDirPath}")).!)
      }

      val _ = Files.createDirectories(websiteDirPath.resolve("docs"))
    }

  private def scaffoldCommand(createZioWebsiteVersion: String, siteName: String, description: String): String =
    s"""|npx @zio.dev/create-zio-website@$createZioWebsiteVersion $siteName \\
        |  --description="$description" \\
        |  --author="ZIO Contributors" \\
        |  --email="email@zio.dev" \\
        |  --license="Apache-2.0" \\
        |  --architecture=Linux""".stripMargin

  // The ZIO website (zio/zio) builds every library's published docs with the current Docusaurus,
  // which rejects things an older, committed `website/` only warns about -- two docs sharing an
  // id, say (`mux.md` next to `mux.mdx`). A library whose own site is committed and pinned to an
  // old Docusaurus therefore passes its CI and then breaks the ZIO website's. Build the docs in a
  // site scaffolded from the pinned create-zio-website instead, which is what consumers see.
  /**
   * The implementation of `checkDocsOnFreshWebsite`.
   */
  lazy val checkDocsOnFreshWebsiteTask: Def.Initialize[Task[Unit]] =
    Def.task {
      val logger     = streams.value.log
      val _          = compileDocs.toTask("").value
      val committed  = Files.exists(websiteDir.value.resolve("package.json"))
      val siteName   = s"${normalizedName.value}-fresh-website"
      val scratch    = target.value / siteName
      val command    = scaffoldCommand(createZioWebsiteVersion.value, siteName, name.value)
      val workingDir = target.value
      val docsOut    = mdocOut.value

      // Without a committed site, `buildWebsite` already scaffolds a fresh one and builds on it.
      if (!committed)
        logger.info("No committed website found; `buildWebsite` already builds on a fresh scaffold, skipping.")
      else {
        if (scratch.exists) IO.delete(scratch)

        logger.info(s"scaffolding a fresh website to check the docs against ... \n$command")
        exit(Process(command, workingDir).!, "Failed to scaffold the fresh website!")

        // The scaffold ships its own `docs/`; swap it for this project's compiled docs.
        IO.delete(scratch / "docs")
        IO.copyDirectory(docsOut, scratch / "docs")

        exit(Process("npm install", scratch).!, "Failed to install the fresh website's dependencies!")
        exit(
          Process("npm run build", scratch).!,
          "The docs do not build on a fresh ZIO website (the one zio/zio builds them with)!"
        )
      }
    }

  /**
   * The implementation of `buildWebsite`.
   */
  lazy val buildWebsiteTask: Def.Initialize[Task[Unit]] =
    Def.task {
      val _ = Def.sequential(installWebsiteTask, compileDocs.toTask("")).value

      // The website directory may already exist (e.g. committed to the repository) so
      // installWebsiteTask skips installation, but its dependencies are not versioned.
      if (!Files.exists(websiteDir.value.resolve("node_modules"))) {
        // Say what is actually wrong. `npm install` reports only "Could not read package.json",
        // which gives no hint that the real problem is a website directory without a scaffold.
        if (!Files.exists(websiteDir.value.resolve("package.json")))
          sys.error(
            s"""|${websiteDir.value} exists but contains no package.json, so there is no website to build.
                |
                |This usually means the directory holds only generated output -- `mdocOut` writes
                |compiled docs to `website/docs` -- rather than a Docusaurus site. Add `website/docs`
                |to .gitignore so the generated output is not committed, then rerun `installWebsite`.
                |""".stripMargin
          )

        exit(
          Process("npm install", new File(s"${websiteDir.value}")).!,
          "Failed to install website dependencies!"
        )
      }

      val p = Process("npm run build", new File(s"${websiteDir.value}")).!
      exit(p, "Failed to build the website!")
    }

  /**
   * Runs `compileDocs`, then publishes the `docs` folder inside `websiteDir` to
   * npm. The package version is the latest git tag that starts with `v`,
   * without the `v`, or the project `version` when there is no such tag, with
   * every `+` replaced by `--` when the result ends with `-SNAPSHOT`. No
   * setting in the plugin is assigned this task.
   */
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

      val docsDir = new File(s"${websiteDir.value.toString}/docs/")
      exit(Process(s"npm publish ${npmPublishFlag(docsDir, refinedNpmVersion)}".trim, docsDir).!)
    }

  private val docsVersionTask: Def.Initialize[String] =
    Def.setting {
      val logger           = sLog.value
      val projectVersion   = version.value
      val versioningScheme = docsVersioningScheme.value

      // This setting is evaluated at project load, so it must not blow up when
      // git is unavailable (e.g. a source tarball or a scripted test sandbox).
      def hashOrProjectVersion: String =
        hashVersion.getOrElse {
          logger.warn(
            "Could not determine the hash version from git, falling back to the project version. " +
              "This is most likely because git is not installed, this project has no git repo initialized, " +
              "or the repo has no commits yet."
          )
          projectVersion
        }

      versioningScheme match {
        case VersioningScheme.HashVersioning =>
          hashOrProjectVersion
        case VersioningScheme.SemanticVersioning =>
          WebsiteUtils.releaseVersion(logger.warn(_)).getOrElse(hashOrProjectVersion)
      }
    }

  /**
   * The `--tag` flag for `npm publish` of `version`, or an empty string.
   */
  private def npmPublishFlag(docsDir: File, version: String): String = {
    // Sideline a prerelease under its own dist-tag only when a stable release
    // already holds `latest`. For a project that has never released a stable
    // version the prerelease *is* the current release, and parking it under
    // `rc` freezes `latest` on an older version - which hides the docs from
    // dependency bots, since Renovate's respectLatest will not go past `latest`.
    // npm refuses a prerelease without `--tag`, so that case passes `--tag latest`.
    val packageName: String =
      """"name"\s*:\s*"([^"]+)"""".r
        .findFirstMatchIn(IO.read(new File(docsDir, "package.json")))
        .map(_.group(1))
        .getOrElse(sys.error(s"Could not read the package name from ${docsDir / "package.json"}"))

    val currentLatest: Option[String] = {
      val out  = new mutable.StringBuilder
      val code = Process(s"npm view $packageName dist-tags.latest", docsDir)
        .!(ProcessLogger(line => { out.append(line); () }, _ => ()))
      Some(out.toString.trim).filter(_.nonEmpty && code == 0)
    }
    WebsiteUtils.npmPublishTag(version, currentLatest).map(tag => s"--tag $tag").getOrElse("")
  }

  /**
   * The implementation of `publishToNpm`.
   */
  lazy val publishToNpmTask: Def.Initialize[Task[Unit]] = Def.task {
    val _             = compileDocs.toTask("").value
    val version       = docsVersionTask.value
    val docsDir: File = new File(websiteDir.value.toString, "docs")

    // Get repository URL from scmInfo
    val repoUrl: String = scmInfo.value
      .map(_.browseUrl.toString)
      .getOrElse(sys.error("scmInfo must be set for npm provenance verification"))

    val npmTag = npmPublishFlag(docsDir, version)

    exit(Process(s"npm version $version --no-git-tag-version", docsDir).!)
    exit(Process(s"npm pkg set repository.url=$repoUrl", docsDir).!)
    exit("npm config set access public".!)
    exit(Process(s"npm publish $npmTag".trim, docsDir).!)
  }

  /**
   * The major version of `@docusaurus/core` declared under `dependencies` in a
   * scaffolded site's package.json: the first run of digits in the declared
   * version string, so `^3.1.0` gives 3. Returns None when `dependencies` is
   * missing or is not an object, when its `@docusaurus/core` entry is missing
   * or is not a string, or when that string contains no digits or its first run
   * of digits does not fit in an `Int`.
   */
  private[sbt] def docusaurusMajorOf(pkgJson: Json.Obj): Option[Int] = {
    def field(obj: Json.Obj, key: String): Option[Json] =
      obj.fields.collectFirst { case (k, v) if k == key => v }

    for {
      deps    <- field(pkgJson, "dependencies").collect { case o: Json.Obj => o }
      version <- field(deps, "@docusaurus/core").collect { case Json.Str(v) => v }
      digits   = version.dropWhile(!_.isDigit).takeWhile(_.isDigit)
      major   <- scala.util.Try(digits.toInt).toOption
    } yield major
  }

  private def hashVersion: Option[String] =
    try {
      val hashPart = s"git rev-parse --short=12 HEAD".!!.trim
      val datePart = java.time.LocalDate.now().toString.replace("-", ".")
      Some(datePart + "-" + hashPart)
    } catch {
      // Not a git repository, or a repository without any commit yet.
      case scala.util.control.NonFatal(_) => None
    }

  /**
   * The implementation of `publishHashverToNpm`.
   */
  lazy val publishHashverToNpmTask: Def.Initialize[Task[Unit]] =
    Def.task {
      val _             = compileDocs.toTask("").value
      val docsDir: File = new File(websiteDir.value.toString, "docs")

      // Get repository URL from scmInfo
      val repoUrl: String = scmInfo.value
        .map(_.browseUrl.toString)
        .getOrElse(sys.error("scmInfo must be set for npm provenance verification"))

      val hashVer = hashVersion.getOrElse(
        sys.error(
          "Could not determine the hash version from git. This is most likely because git is not installed, " +
            "this project has no git repo initialized, or the repo has no commits yet."
        )
      )

      exit(Process(s"npm version $hashVer --no-git-tag-version", docsDir).!)
      exit(Process(s"npm pkg set repository.url=$repoUrl", docsDir).!)
      exit("npm config set access public".!)
      exit(Process("npm publish --tag hash", docsDir).!)
    }

  /**
   * The implementation of `publishSnapshotToNpm`.
   */
  lazy val publishSnapshotToNpmTask: Def.Initialize[Task[Unit]] =
    Def.task {
      val _             = compileDocs.toTask("").value
      val docsDir: File = new File(websiteDir.value.toString, "docs")

      // Get repository URL from scmInfo
      val repoUrl: String = scmInfo.value
        .map(_.browseUrl.toString)
        .getOrElse(sys.error("scmInfo must be set for npm provenance verification"))

      // Replace + with -- for npm compatibility, snapshot versions contain hyphens
      val refinedVersion = version.value.replace("+", "--")

      exit(Process(s"npm version $refinedVersion --no-git-tag-version", docsDir).!)
      exit(Process(s"npm pkg set repository.url=$repoUrl", docsDir).!)
      exit("npm config set access public".!)
      exit(Process("npm publish --tag snapshot", docsDir).!)
    }

  private def prefixUrlsWith(markdown: String, prefix: String): String = {
    val regex = """\(((?!http)\S*\.(png|jpg|mdx?|scala|java)\b)\)""".r

    regex.replaceAllIn(markdown, '(' + prefix + _.group(1) + ')')
  }

  /**
   * Returns the version of the latest git tag that starts with `v`, without the
   * `v`, or the project `version` when no such tag can be found.
   */
  lazy val normalizedVersion: Def.Initialize[Task[String]] =
    Def.task(WebsiteUtils.releaseVersion(sLog.value.warn(_)).getOrElse(version.value))

  /**
   * Runs `git fetch --tags` and fails with a `sbt.MessageOnlyException` that
   * asks for `sbt reload` when the command reports a new tag on its standard
   * error. It fails with an error when the command exits with a non-zero code.
   */
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

  /**
   * When `normalizedVersion` ends with `-SNAPSHOT`, replaces `@VERSION@` with
   * `<version>` in `docs/index.md` (or `docs/index.mdx` when only that file
   * exists), relative to the sbt working directory, and keeps the original as a
   * `.bak` file next to it. Does nothing for other versions.
   */
  lazy val ignoreIndexSnapshotVersion: Def.Initialize[Task[Unit]] = Def.task {
    if (normalizedVersion.value.endsWith("-SNAPSHOT")) {
      val index = resolveIndexFile(Paths.get("docs"))
      exit(s"sed -i.bak s/@VERSION@/<version>/g $index".!)
    }
  }

  /**
   * When `normalizedVersion` ends with `-SNAPSHOT`, deletes `docs/index.md` (or
   * `docs/index.mdx` when only that file exists) and moves its `.bak` copy back
   * in its place. It undoes `ignoreIndexSnapshotVersion` and does nothing for
   * other versions.
   */
  lazy val revertIndexChanges: Def.Initialize[Task[Unit]] = Def.task {
    if (normalizedVersion.value.endsWith("-SNAPSHOT")) {
      val index = resolveIndexFile(Paths.get("docs"))
      exit(s"rm $index".!)
      exit(s"mv $index.bak $index".!)
    }
  }

  /**
   * The implementation of `generateReadme`.
   */
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
            readFile(resolveIndexFile(websiteDir.value.resolve("docs")).toString)
              .map(md => removeYamlHeader(md).trim)
              .flatMap { introduction =>
                WebsiteUtils.generateReadme(
                  projectName = projectName.value,
                  banner = readmeBanner.value,
                  showTitle = readmeShowTitle.value,
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

  /**
   * The implementation of `checkReadme`.
   */
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

  // `projectHomepageUrl` is a String, not `URL`/`URI`: `homepage`'s value type differs between sbt
  // lines (`Option[java.net.URL]` on sbt 1.x, `Option[java.net.URI]` on sbt 2.x), and this method
  // only ever interpolates it into markdown, so taking the pre-rendered String sidesteps that
  // divergence entirely instead of needing per-axis source.
  /**
   * Returns the default Documentation section of the README: a Markdown
   * sentence that links `projectName` to `projectHomepageUrl`.
   */
  def readmeDocumentationSection(projectName: String, projectHomepageUrl: String): String =
    s"""Learn more on the [$projectName homepage]($projectHomepageUrl)!""".stripMargin

  /**
   * Returns the default Contributing section of the README, which points to the
   * ZIO contributor's guide.
   */
  def readmeContributionSection: String =
    """For the general guidelines, see ZIO [contributor's guide](https://zio.dev/contributor-guidelines).""".stripMargin

  /**
   * Returns the default Code of Conduct section of the README, which links to
   * the ZIO code of conduct.
   */
  def readmeCodeOfConductSection: String =
    """See the [Code of Conduct](https://zio.dev/code-of-conduct)""".stripMargin

  /**
   * Returns the default Support section of the README, which invites readers to
   * the ZIO Discord chat and defines the Markdown link references for its
   * badge.
   */
  def readmeSupportSection: String =
    """|Come chat with us on [![Badge-Discord]][Link-Discord].
       |
       |[Badge-Discord]: https://img.shields.io/discord/629491597070827530?logo=discord "chat on discord"
       |[Link-Discord]: https://discord.gg/2ccFBr4 "Discord"""".stripMargin

  /**
   * Returns the default License section of the README, a link to the `LICENSE`
   * file.
   */
  def readmeLicenseSection: String =
    """[License](LICENSE)""".stripMargin

}
