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
import scala.sys.process.{Process, ProcessLogger}

import sbt.{Def, io => _, _}

import zio.json._
import zio.json.ast.Json
import zio.json.yaml._
import zio.sbt.githubactions.Step.SingleStep
import zio.sbt.githubactions._

/**
 * An sbt plugin that generates GitHub Actions workflow files (the CI workflow,
 * whose file name comes from `ciWorkflowTitle`, plus `auto-approve.yml`,
 * `auto-merge.yml`, `regenerate-workflows.yml`, `deploy-preview.yml`,
 * `release-drafter.yml` and `scala-steward.yml`) and `.github/dependabot.yml`
 * from the `ci*` settings, with the `ciGenerateGithubWorkflow` task. The
 * `ciCheckGithubWorkflow` task fails when the committed files differ from what
 * the build generates.
 *
 * The plugin requires `plugins.CorePlugin` and is enabled automatically
 * wherever that plugin is.
 */
object ZioSbtCiPlugin extends AutoPlugin {
  override def requires = plugins.CorePlugin
  override def trigger  = allRequirements

  /**
   * How every generated step invokes sbt.
   *
   * `--no-colors` matters more in CI than it looks: with colour on, sbt wraps
   * its `[error]` and `[warn]` prefixes in ANSI escapes, so a run log cannot be
   * searched for them. `gh run view --log-failed | grep '[error]'` comes back
   * empty on a job that failed with sbt errors, and GitHub's own log search has
   * the same blind spot.
   */
  private val SbtCommand = "sbt --no-colors"

  /**
   * The setting keys, task keys and helper methods that the plugin makes
   * available in `build.sbt` without an import.
   */
  object autoImport {

    /**
     * How the docs package is versioned on npm. The `release-docs` job runs
     * `docs/publishToNpm` for `DocsVersioning.SemanticVersioning` and
     * `docs/publishHashverToNpm` for `DocsVersioning.HashVersioning`. Default
     * is `DocsVersioning.SemanticVersioning`.
     */
    val ciDocsVersioningScheme: SettingKey[DocsVersioning] = settingKey[DocsVersioning]("Docs versioning style")

    /**
     * The branches listed in the `push` trigger of the default
     * `ciWorkflowTriggers`. When the list is empty, which is the default, that
     * trigger has no branch filter and matches pushes to all branches. When
     * `ciReleaseDrafterBranch` is `None`, the first of them is the branch
     * release-drafter runs on, or `main` when the list is empty.
     */
    val ciEnabledBranches: SettingKey[Seq[String]] = settingKey[Seq[String]]("Publish branch for documentation")

    /**
     * Applies when `ciTargetScalaVersions` or `ciTargetMinJavaVersions` is not
     * empty. When true, the test job's matrix crosses the Java versions with
     * the distinct Scala versions, and each step runs `/test` for the modules
     * that cross-build that Scala version, leaving out the modules whose
     * minimum in `ciTargetMinJavaVersions` is above the matrix Java version.
     * When false, the matrix has one entry per module and Scala version; when
     * `ciTargetMinJavaVersions` is not empty, these entries go into one
     * `scala-project-java<N>` axis per Java version `N`, which leaves out the
     * modules whose minimum is above `N`. Default is false.
     */
    val ciGroupSimilarTests: SettingKey[Boolean] =
      settingKey[Boolean]("Group similar test by their Java and Scala versions, default is false")

    /**
     * The `max-parallel` limit of the test job's matrix strategy, which is the
     * number of matrix jobs that run at the same time. Default is `None`.
     */
    val ciMatrixMaxParallel: SettingKey[Option[Int]] =
      settingKey[Option[Int]](
        "Set the maximum number of jobs that can run simultaneously when using a matrix job strategy, default is None"
      )

    /**
     * Writes the CI workflow and then the other generated files, one generator
     * after another: `auto-approve.yml`, `auto-merge.yml`,
     * `regenerate-workflows.yml`, `deploy-preview.yml`, `release-drafter.yml`,
     * `scala-steward.yml` and `.github/dependabot.yml`. The `ciEnable*`
     * settings switch the optional ones on and off. It also writes
     * `.github/release-drafter.yml` when it is missing and
     * `ciEnableReleaseDrafter` is true, and `.scala-steward.conf` when it is
     * missing, `ciEnableScalaSteward` is true and `ciScalaStewardConfig`
     * renders content.
     */
    val ciGenerateGithubWorkflow: TaskKey[Unit] = taskKey[Unit]("Generate github workflow")

    /**
     * JVM options appended to `-XX:+PrintCommandLineFlags` in the
     * `JDK_JAVA_OPTIONS` variable of the default `ciWorkflowEnv`. Default is
     * empty.
     */
    val ciJvmOptions: SettingKey[Seq[String]] = settingKey[Seq[String]]("JVM Options")

    /**
     * Node.js options that the default `ciWorkflowEnv` joins with spaces into a
     * `NODE_OPTIONS` variable. The variable is left out when the list is empty,
     * which is the default.
     */
    val ciNodeOptions: SettingKey[Seq[String]] = settingKey[Seq[String]]("NodeJS Options")

    /**
     * The environment variables set at the workflow level of the CI workflow.
     * The default has `JDK_JAVA_OPTIONS`, made of `-XX:+PrintCommandLineFlags`
     * and `ciJvmOptions`, and `NODE_OPTIONS` from `ciNodeOptions` when that is
     * not empty. Assigning to this key replaces the whole map.
     */
    val ciWorkflowEnv: SettingKey[Map[String, String]] =
      settingKey[Map[String, String]](
        "Environment variables set at the workflow level. Defaults to JDK_JAVA_OPTIONS (from " +
          "`ciJvmOptions`) and NODE_OPTIONS (from `ciNodeOptions`); assigning to this key replaces " +
          "that map entirely, which is how a build opts out of JDK_JAVA_OPTIONS in favour of, say, SBT_OPTS"
      )

    /**
     * The events the CI workflow runs on. The default is `workflow_dispatch`, a
     * published release, a push (to the branches in `ciEnabledBranches`, or to
     * all branches when that list is empty) and a pull request that does not
     * target `gh-pages`. `ciGenerateGithubWorkflow` fails with an error when
     * the list is empty. When the workflow has a `release-docs` job, each
     * `workflow_dispatch` trigger that lacks it gets a `docs-only` input.
     */
    val ciWorkflowTriggers: SettingKey[Seq[Trigger]] =
      settingKey[Seq[Trigger]](
        "Events the generated workflow runs on. Defaults to workflow_dispatch, published releases, " +
          "pushes (to `ciEnabledBranches` if set, otherwise to every branch), and pull requests not " +
          "targeting gh-pages. Worth narrowing if a job's condition would let a manual run do " +
          "something it should not, such as release. Must not be empty: a workflow with no `on:` " +
          "block is rejected by GitHub. When the workflow has a `release-docs` job, every " +
          "`workflow_dispatch` trigger gets a `docs-only` input, which publishes a release's docs " +
          "without running the Maven release"
      )

    /**
     * The `concurrency` block of the CI workflow, or `None` to leave it out.
     * The default is `Some(Workflow.defaultConcurrency)`, whose group is one
     * per Git ref (branch, tag or pull request), except on the default branch
     * where every run is its own group, and which cancels in-progress runs.
     */
    val ciConcurrency: SettingKey[Option[Concurrency]] =
      settingKey[Option[Concurrency]](
        "Concurrency group for the generated workflow, or None to omit the block entirely. Defaults " +
          "to one run per branch with in-progress runs cancelled, keeping every run on the default " +
          "branch. `cancelInProgress` accepts an expression via `CancelInProgress.When(...)`, so a " +
          "workflow can cancel superseded pull request runs while letting releases finish"
      )

    /**
     * The workflow-level `permissions` of the CI workflow. The default is
     * `Some(Workflow.defaultPermissions)`, which grants `id-token: write` and
     * `contents: read`. `None` leaves the block out, so the repository's
     * default token permissions apply.
     */
    val ciWorkflowPermissions: SettingKey[Option[Map[String, String]]] =
      settingKey[Option[Map[String, String]]](
        "Workflow-level `permissions` of the generated CI workflow. Defaults to " +
          "`Some(Workflow.defaultPermissions)`. `None` omits the block entirely, so the workflow " +
          "runs with the repository's default token permissions - needed by a job that pushes " +
          "with GITHUB_TOKEN, such as one tagging a release, since the default block only grants " +
          "`contents: read`. This is distinct from `Some(Map.empty)`, which renders " +
          "`permissions: {}` and grants nothing"
      )

    /**
     * The `if` condition of the `update-readme` job. With `None`, the default,
     * the job runs when the event is a published release.
     */
    val ciUpdateReadmeCondition: SettingKey[Option[Condition]] =
      settingKey[Option[Condition]]("condition to update readme")

    /**
     * The Java versions in the `java` matrix of the test job. Default is
     * `Seq("17", "21", "25")`.
     */
    val ciTargetJavaVersions: SettingKey[Seq[String]] =
      settingKey[Seq[String]]("The default target Java versions for all modules, default is 17, 21, 25")

    /**
     * The minimum Java version of each module, keyed by project id. A module is
     * tested only on the Java versions from `ciTargetJavaVersions` that are at
     * least its minimum, and a module without an entry is tested on all of
     * them. Default is an empty map. `minTargetJavaVersionsFor` builds this map
     * from the `javaPlatform` setting of the given projects.
     */
    val ciTargetMinJavaVersions: SettingKey[Map[String, String]] =
      SettingKey[Map[String, String]](
        "minimum target Java version for each module, default is an empty map which makes CI to use `ciAllTargetJavaVersions` to determine the minimum target Java version for all modules"
      )

    /**
     * The Scala versions each module is tested with, keyed by project id.
     * `targetScalaVersionsFor` builds this map from the `crossScalaVersions` of
     * the given projects. When this map and `ciTargetMinJavaVersions` are both
     * empty, which is the default, the test job runs `sbt +test` on each Java
     * version.
     */
    val ciTargetScalaVersions: SettingKey[Map[String, Seq[String]]] =
      settingKey[Map[String, Seq[String]]](
        "Scala versions used for testing each module, the default value is an empty map which omits the test job on CI"
      )

    /**
     * The Java version that the `build`, `lint`, `update-readme`, `release` and
     * `release-docs` jobs install, with the Corretto distribution. Default is
     * "17". The test job uses its Java matrix instead.
     */
    val ciDefaultJavaVersion: SettingKey[String] =
      settingKey[String](
        "The default Java version which is used in CI, especially for releasing artifacts, defaults to 17. Note that this is just JDK version used for compilation. Artefact will be compiled with -target and -source flags specified by 'javaPlatform' setting or 'javaPlatform' parameter in 'stdSettings'"
      )

    /**
     * Runs `ciGenerateGithubWorkflow`, then fails when git reports a file under
     * `.github/workflows` or `.github/dependabot.yml` as changed since `HEAD`
     * or as untracked. It also fails when the directory is not a git
     * repository. The error message starts with "These workflow files are not
     * up to date:" and lists the files.
     */
    val ciCheckGithubWorkflow: TaskKey[Unit] =
      taskKey[Unit]("Make sure if the ci.yml, auto-approve.yml, and auto-merge.yml files are up-to-date")

    /**
     * The steps of the `build` job that check the artifacts build. The default
     * is one step, "Check artifacts build process", which runs
     * `sbt --no-colors +publishLocal`.
     */
    val ciCheckArtifactsBuildSteps: SettingKey[Seq[Step]] =
      settingKey[Seq[Step]]("Workflow steps for checking artifact build process")

    /**
     * The steps of the `build` job that check the website build. The default is
     * `CheckWebsiteBuildProcess`.
     */
    val ciCheckWebsiteBuildProcess: SettingKey[Seq[Step]] =
      settingKey[Seq[Step]]("Workflow steps for checking website build process")

    /**
     * When true, the `build` job gets the `NetlifyPreviewBuildSteps`, and
     * `ciGenerateGithubWorkflow` also writes `deploy-preview.yml`, which
     * deploys the website to Netlify with the `NETLIFY_AUTH_TOKEN` and
     * `NETLIFY_SITE_ID` secrets, following the CI workflow. When false,
     * `ciGenerateGithubWorkflow` deletes `deploy-preview.yml`.
     * `ciDeployPreviewMode` chooses when the deploy happens. Default is false.
     */
    val ciEnableDeployPreview: SettingKey[Boolean] =
      settingKey[Boolean](
        "When true, adds change-detection and website-artifact upload steps to the build job, " +
          "and makes `ciGenerateGithubWorkflow` also generate deploy-preview.yml, which deploys " +
          "website/build to Netlify via a workflow_run-triggered job after a successful CI run " +
          "on a pull request touching docs/ or website/. Requires the NETLIFY_AUTH_TOKEN and " +
          "NETLIFY_SITE_ID secrets to be configured on the repository. See `ciDeployPreviewMode` to " +
          "deploy on demand instead of on every successful run. Default is false"
      )

    /**
     * When true, the `build` job gets the `CheckScaladocExamples` step, which
     * runs `sbt --no-colors +scaladocCheck` and needs the zio-sbt-scaladoc
     * plugin in the build. Default is false.
     */
    val ciCheckScaladocExamples: SettingKey[Boolean] =
      settingKey[Boolean](
        "When true, adds a \"Check Scaladoc examples\" step to the build job that runs " +
          "`sbt +scaladocCheck`, which compiles the code examples in the Scaladoc of every module on " +
          "every Scala version and platform. Needs the zio-sbt-scaladoc plugin in project/plugins.sbt. " +
          "Default is false"
      )

    /**
     * Selects the workflow that `deploy-preview.yml` contains, and has an
     * effect only when `ciEnableDeployPreview` is true.
     * `DeployPreviewMode.Automatic` deploys after a successful pull request run
     * of the CI workflow that uploaded the website artifact.
     * `DeployPreviewMode.OnDemand` is triggered by an edit to an issue comment
     * as well as by completed runs of the CI workflow, and deploys on request
     * through that comment. Default is `DeployPreviewMode.Automatic`.
     */
    val ciDeployPreviewMode: SettingKey[DeployPreviewMode] =
      settingKey[DeployPreviewMode](
        "How deploy-preview.yml deploys the website preview, only used when `ciEnableDeployPreview` " +
          "is true. `DeployPreviewMode.Automatic` deploys after every successful CI run on a pull " +
          "request. `DeployPreviewMode.OnDemand` instead posts a comment with a \"Deploy preview\" " +
          "checkbox on the pull request and deploys only when someone with write access ticks it, " +
          "which saves Netlify credits. Default is `DeployPreviewMode.Automatic`"
      )

    /**
     * When true, `ciGenerateGithubWorkflow` writes `release-drafter.yml`, which
     * runs release-drafter/release-drafter on pushes to the release-drafter
     * branch, and writes `.github/release-drafter.yml` if that file does not
     * exist yet. A `release-drafter.yml` that already exists is overwritten.
     * When false, it deletes `release-drafter.yml` only if the file starts with
     * the autogenerated header, and leaves `.github/release-drafter.yml` alone.
     * Default is true.
     */
    val ciEnableReleaseDrafter: SettingKey[Boolean] =
      settingKey[Boolean](
        "When true, makes `ciGenerateGithubWorkflow` also generate release-drafter.yml, which runs " +
          "release-drafter/release-drafter on pushes to the release branch to maintain a draft " +
          "GitHub Release with auto-generated notes, and scaffolds .github/release-drafter.yml " +
          "(the action's own config file) the first time the feature is enabled, if it does not " +
          "already exist. Overwrites a pre-existing hand-maintained release-drafter.yml when " +
          "enabled. Turning it back off deletes a plugin-owned workflow file only (identified by " +
          "its autogenerated header), never a hand-written one, and not the scaffolded " +
          "config. Default is true"
      )

    /**
     * The categories written into `.github/release-drafter.yml` when it is
     * first generated. The default has four categories, each selected by one
     * label.
     */
    val ciReleaseDrafterCategories: SettingKey[Seq[ReleaseDrafterCategory]] =
      settingKey[Seq[ReleaseDrafterCategory]](
        "Categories written into the scaffolded .github/release-drafter.yml. Only takes effect the " +
          "first time the file is generated"
      )

    /**
     * The optional version-resolver block written into
     * `.github/release-drafter.yml` when it is first generated. Default is
     * `None`.
     */
    val ciReleaseDrafterVersionResolver: SettingKey[Option[ReleaseDrafterVersionResolver]] =
      settingKey[Option[ReleaseDrafterVersionResolver]](
        "Optional version-resolver block for the scaffolded .github/release-drafter.yml. Default is None"
      )

    /**
     * The autolabeler rules written into `.github/release-drafter.yml` when it
     * is first generated. Default is empty.
     */
    val ciReleaseDrafterAutolabeler: SettingKey[Seq[ReleaseDrafterAutolabelerRule]] =
      settingKey[Seq[ReleaseDrafterAutolabelerRule]](
        "Optional autolabeler rules for the scaffolded .github/release-drafter.yml. Default is empty"
      )

    /**
     * The `exclude-labels` written into `.github/release-drafter.yml` when it
     * is first generated. Default is `Seq("skip-changelog")`.
     */
    val ciReleaseDrafterExcludeLabels: SettingKey[Seq[String]] =
      settingKey[Seq[String]](
        "exclude-labels for the scaffolded .github/release-drafter.yml. Default is Seq(\"skip-changelog\")"
      )

    /**
     * The branch in the `push` trigger of `release-drafter.yml`. With `None`,
     * the default, it is the first of `ciEnabledBranches` when that is not
     * empty, and `main` otherwise.
     */
    val ciReleaseDrafterBranch: SettingKey[Option[Branch]] =
      settingKey[Option[Branch]](
        "Branch release-drafter.yml's push trigger runs on. None resolves at generation time to the " +
          "first of ciEnabledBranches if non-empty, else the literal branch \"main\""
      )

    /**
     * When true, `ciGenerateGithubWorkflow` writes `scala-steward.yml`, which
     * runs scala-steward-org/scala-steward-action on a schedule with the
     * `SCALA_STEWARD_GITHUB_APP_ID`, `SCALA_STEWARD_GITHUB_APP_INSTALLATION_ID`
     * and `SCALA_STEWARD_GITHUB_APP_PRIVATE_KEY` secrets. It also writes
     * `.scala-steward.conf` from `ciScalaStewardConfig` when that renders
     * content and the file does not exist yet. A `scala-steward.yml` that
     * already exists is overwritten. When false, it deletes `scala-steward.yml`
     * only if the file starts with the autogenerated header, and leaves
     * `.scala-steward.conf` alone. Default is true.
     */
    val ciEnableScalaSteward: SettingKey[Boolean] =
      settingKey[Boolean](
        "When true, makes `ciGenerateGithubWorkflow` also generate scala-steward.yml, which runs " +
          "scala-steward-org/scala-steward-action on a schedule to open dependency-update pull " +
          "requests, authenticating via a GitHub App through the SCALA_STEWARD_GITHUB_APP_ID, " +
          "SCALA_STEWARD_GITHUB_APP_INSTALLATION_ID and SCALA_STEWARD_GITHUB_APP_PRIVATE_KEY " +
          "secrets. Also scaffolds .scala-steward.conf from `ciScalaStewardConfig` the first " +
          "time that renders non-empty content, if the file does not already exist. Never " +
          "touches a pre-existing scala-steward.yml this plugin didn't generate itself, whether " +
          "enabling or disabling. Default is true"
      )

    /**
     * The cron expression of the `schedule` trigger in `scala-steward.yml`.
     * Default is "0 0 * * *".
     */
    val ciScalaStewardSchedule: SettingKey[String] =
      settingKey[String](
        "Cron schedule for scala-steward.yml's `schedule` trigger, default is \"0 0 * * *\" " +
          "(daily at midnight UTC), matching every ZIO-ecosystem repo surveyed"
      )

    /** The `timeout-minutes` of the `scala-steward` job. Default is 45. */
    val ciScalaStewardTimeoutMinutes: SettingKey[Int] =
      settingKey[Int]("`timeout-minutes` for the scala-steward job, default is 45")

    /**
     * The workflow-level `permissions` of `scala-steward.yml`. Default is
     * `Map("contents" -> "read")`. The step passes
     * `github-app-auth-only: true`, so it authenticates with the GitHub App
     * rather than `GITHUB_TOKEN`.
     */
    val ciScalaStewardPermissions: SettingKey[Map[String, String]] =
      settingKey[Map[String, String]](
        "Permissions block for scala-steward.yml, default is Map(\"contents\" -> \"read\") - the " +
          "action authenticates via the GitHub App's installation token (github-app-auth-only is " +
          "always true), not GITHUB_TOKEN, so no write scope is needed by default"
      )

    /**
     * Environment variables set on the Scala Steward step of
     * `scala-steward.yml`. Default is empty.
     */
    val ciScalaStewardWorkflowEnv: SettingKey[Map[String, String]] =
      settingKey[Map[String, String]](
        "Extra env vars for the scala-steward job, e.g. JVM heap/GC tuning; default is empty"
      )

    /**
     * The content of `.scala-steward.conf` at the build's base directory,
     * written only when `ciEnableScalaSteward` is true, the content renders
     * non-empty and the file does not exist yet. Default is
     * `ScalaStewardConfig()`, which renders nothing, so no file is written.
     */
    val ciScalaStewardConfig: SettingKey[ScalaStewardConfig] =
      settingKey[ScalaStewardConfig](
        "Settings scaffolded into .scala-steward.conf the first time it renders non-empty " +
          "content. Default is ScalaStewardConfig(), which renders to nothing and skips " +
          "scaffolding entirely"
      )

    /**
     * When true, `ciGenerateGithubWorkflow` writes `.github/dependabot.yml`
     * from `ciDependabotConfig`, overwriting an existing file. When false, it
     * deletes that file only if it starts with the autogenerated header.
     * Default is true.
     */
    val ciEnableDependabot: SettingKey[Boolean] =
      settingKey[Boolean](
        "When true, makes `ciGenerateGithubWorkflow` also generate .github/dependabot.yml from " +
          "`ciDependabotConfig`. Adopts (overwrites) a pre-existing hand-written dependabot.yml, " +
          "like the generated workflows; when false, only deletes a dependabot.yml this plugin " +
          "generated itself, identified by its autogenerated header. Default is true"
      )

    /**
     * When true, every `github-actions` update in the generated
     * `.github/dependabot.yml` excludes the workflows this plugin generates.
     * Their action versions come from the plugin, so a Dependabot bump of them
     * would be reverted by the next `ciGenerateGithubWorkflow`. Default is
     * true.
     */
    val ciDependabotExcludeGenerated: SettingKey[Boolean] =
      settingKey[Boolean](
        "When true, every `github-actions` update in the generated .github/dependabot.yml lists the " +
          "workflows generated by this plugin under `exclude-paths`, since their action versions " +
          "come from the plugin and `ciGenerateGithubWorkflow` would revert a Dependabot bump. " +
          "Default is true"
      )

    /**
     * When true, `ciGenerateGithubWorkflow` writes `regenerate-workflows.yml`,
     * which runs `ciGenerateGithubWorkflow` on pull requests opened by the
     * `ciDependencyUpdateBots` and pushes the changed files to the pull request
     * branch. The push uses the GitHub App token when the `APP_ID` secret is
     * set, and `GITHUB_TOKEN` otherwise. When false, it deletes the file only
     * if it starts with the autogenerated header. Default is true.
     */
    val ciEnableRegenerateWorkflows: SettingKey[Boolean] =
      settingKey[Boolean](
        "When true, makes `ciGenerateGithubWorkflow` also generate regenerate-workflows.yml, which " +
          "re-runs `ciGenerateGithubWorkflow` on pull requests opened by `ciDependencyUpdateBots` " +
          "and pushes the result to the PR branch, so a bot bump of zio-sbt-ci (or of anything else " +
          "the generated files depend on) does not leave `ciCheckGithubWorkflow` failing. Pushes " +
          "with the GitHub App token (APP_ID/APP_PRIVATE_KEY) so CI re-runs on the new commit. " +
          "Default is true"
      )

    /**
     * The content of the generated `.github/dependabot.yml`. Default is
     * `DependabotConfig()`.
     */
    val ciDependabotConfig: SettingKey[DependabotConfig] =
      settingKey[DependabotConfig](
        "Content of the generated .github/dependabot.yml. Default is DependabotConfig(), a single " +
          "weekly `github-actions` update at the repository root, matching the ZIO-ecosystem repos"
      )

    /**
     * The steps of the `build` job that check that all code compiles. The
     * default is one step, "Check all code compiles", which runs
     * `sbt --no-colors +Test/compile`, after the `ciBackgroundJobs` prefix.
     */
    val ciCheckArtifactsCompilationSteps: SettingKey[Seq[Step]] =
      settingKey[Seq[Step]]("Workflow steps for checking compilation of all codes")

    /**
     * The steps of the `lint` job that check the generated workflow files are
     * up to date. The default is one step, "Check if the site workflow is up to
     * date", which runs `sbt --no-colors ciCheckGithubWorkflow`, after the
     * `ciBackgroundJobs` prefix.
     */
    val ciCheckGithubWorkflowSteps: SettingKey[Seq[Step]] =
      settingKey[Seq[Step]]("Workflow steps for checking if the workflow is up to date")

    /**
     * The ids of the jobs that the `ci` report job waits for. The default is
     * the ids of the jobs in `ciLintJobs`, `ciTestJobs` and `ciBuildJobs`.
     */
    val ciPullRequestApprovalJobs: SettingKey[Seq[String]] =
      settingKey[Seq[String]]("Job IDs that need to pass before a pull request (PR) can be approved")

    /**
     * The ids of the jobs that the `release` job waits for. Default is
     * `Seq("ci")`.
     */
    val ciReleaseApprovalJobs: SettingKey[Seq[String]] =
      settingKey[Seq[String]]("Job IDs that need to pass before a new release.")

    /**
     * The name of the CI workflow. The file name derives from it: trimmed and
     * lower-cased, with each run of characters other than `a-z` and `0-9`
     * replaced by one dash and leading and trailing dashes removed (`ci` when
     * nothing is left), so "CI" gives `.github/workflows/ci.yml`. The
     * `deploy-preview.yml` workflow also uses it to name the workflow it
     * follows. Default is "CI".
     */
    val ciWorkflowTitle: SettingKey[String] =
      settingKey[String](
        "Name of the generated CI workflow, which also determines the file it is written to; " +
          "default is \"CI\", written to .github/workflows/ci.yml. Named `ciWorkflowTitle` rather " +
          "than `ciWorkflowName` so it does not collide with zio-sbt-website's key of that name"
      )

    /**
     * The bots whose pull requests `auto-approve.yml`, `auto-merge.yml` and
     * `regenerate-workflows.yml` act on. The default is
     * `DependencyBot.Dependabot`, `DependencyBot.Renovate`,
     * `DependencyBot.ScalaSteward("zio-scala-steward")` and
     * `DependencyBot.Custom("zio-assistant[bot]")`.
     */
    val ciDependencyUpdateBots: SettingKey[Seq[DependencyBot]] =
      settingKey[Seq[DependencyBot]](
        "Bots whose PRs are auto-approved and auto-merged, e.g. `DependencyBot.Dependabot`, " +
          "`DependencyBot.Renovate`, `DependencyBot.ScalaSteward(githubAppName)`, or `DependencyBot.Custom(login)`; " +
          "default is Seq(Dependabot, Renovate, ScalaSteward(\"zio-scala-steward\")), matching zio/zio's bots"
      )

    /**
     * Writes `auto-approve.yml`, the workflow that approves pull requests from
     * the `ciDependencyUpdateBots`.
     */
    val ciGenerateAutoApproveWorkflow: TaskKey[Unit] =
      taskKey[Unit]("Generate the github workflow that auto-approves dependency-bot PRs")

    /**
     * Writes `auto-merge.yml`, the workflow that merges pull requests from the
     * `ciDependencyUpdateBots`.
     */
    val ciGenerateAutoMergeWorkflow: TaskKey[Unit] =
      taskKey[Unit]("Generate the github workflow that auto-merges dependency-bot PRs")

    /**
     * The swap space size in GB. When it is above 0, the `build`, `lint`,
     * `test`, `update-readme`, `release` and `release-docs` jobs start with a
     * "Set Swap Space" step of that size. Default is 0.
     */
    val ciSwapSizeGB: SettingKey[Int] = settingKey[Int]("Swap size, default is 0")

    /**
     * When true, the `release` job also runs on a push to the repository's
     * default branch, where `sbt ci-release` publishes a SNAPSHOT. When false,
     * it runs only on a published release or a manual dispatch. Default is
     * true.
     */
    val ciPublishSnapshots: SettingKey[Boolean] =
      settingKey[Boolean](
        "Whether the release job also runs on pushes to enabled branches, publishing SNAPSHOT artifacts via `sbt ci-release`; default is true"
      )

    /**
     * Shell commands started in the background, joined with ` & `, before the
     * first sbt command of the default test, compile, `ciCheckGithubWorkflow`,
     * lint, website build, release, npm publish and readme steps. The
     * `+publishLocal` and `+scaladocCheck` steps do not get them. Default is
     * empty.
     */
    val ciBackgroundJobs: SettingKey[Seq[String]] = settingKey[Seq[String]]("Background jobs")

    /** The build jobs of the CI workflow. The default is `buildJobs`. */
    val ciBuildJobs: SettingKey[Seq[Job]] = settingKey[Seq[Job]]("CI Build Jobs")

    /** The lint jobs of the CI workflow. The default is `lintJobs`. */
    val ciLintJobs: SettingKey[Seq[Job]] = settingKey[Seq[Job]]("CI Lint Jobs")

    /** The test jobs of the CI workflow. The default is `testJobs`. */
    val ciTestJobs: SettingKey[Seq[Job]] = settingKey[Seq[Job]]("CI Test Jobs")

    /**
     * The jobs of the CI workflow that update the README. The default is
     * `updateReadmeJobs`.
     */
    val ciUpdateReadmeJobs: SettingKey[Seq[Job]] = settingKey[Seq[Job]]("CI Update Readme Jobs")

    /**
     * The job of the CI workflow that reports whether the required jobs passed.
     * The default is `reportSuccessfulJobs`, a single `ci` job that waits for
     * `ciPullRequestApprovalJobs`. `Seq.empty` leaves it out.
     */
    val ciReportSuccessfulJobs: SettingKey[Seq[Job]] =
      settingKey[Seq[Job]](
        "CI Report Successful Jobs: by default the single `ci` job that waits on " +
          "`ciPullRequestApprovalJobs`, for branch protection to require. Set to `Seq.empty` for a " +
          "workflow made up entirely of custom jobs that has no use for it"
      )

    /** The release jobs of the CI workflow. The default is `releaseJobs`. */
    val ciReleaseJobs: SettingKey[Seq[Job]] = settingKey[Seq[Job]]("CI Release Jobs")

    /**
     * The jobs of the CI workflow that run after the release. The default is
     * `postReleaseJobs`.
     */
    val ciPostReleaseJobs: SettingKey[Seq[Job]] = settingKey[Seq[Job]]("CI Post Release Jobs")

    // Neither a `val` nor a `def` for the shared `ScopeFilter` survives being referenced from two
    // separate `.all(...).value` call sites in the same `Def.setting {}` block: sbt 1.x's macro
    // rejects a `val` ("Could not find proxy for val filter"), and sbt 2.x's rejects a `def`
    // ("reference to method filter was used outside the scope where it was defined") - so the
    // filter expression is simply repeated inline at each call site instead.
    /**
     * Returns a map from the id of each given project to its
     * `crossScalaVersions`, for use as `ciTargetScalaVersions`.
     */
    def targetScalaVersionsFor(projects: Project*): sbt.Def.Initialize[Map[String, Seq[String]]] =
      Def.setting {
        Keys.thisProject
          .all(ScopeFilter(inProjects(projects.map(p => LocalProject(p.id))*)))
          .value
          .map(_.id)
          .zip(Keys.crossScalaVersions.all(ScopeFilter(inProjects(projects.map(p => LocalProject(p.id))*))).value)
          .toMap
      }

    /**
     * Returns a map from the id of each given project to the value of its
     * `javaPlatform` setting, for use as `ciTargetMinJavaVersions`.
     */
    def minTargetJavaVersionsFor(projects: Project*): sbt.Def.Initialize[Map[String, String]] = {
      // Referenced by label rather than importing `ZioSbtEcosystemPlugin` (which zio-sbt-ci does not
      // otherwise depend on): sbt resolves same-labelled settingKeys as the same key without
      // requiring a compile-time module dependency on whichever plugin first declared it. Defined
      // outside the `Def.setting {}` block - a plain `val` referenced across a `.value` call *inside*
      // that block fails both sbt 1.x's and sbt 2.x's macro (see comment on `targetScalaVersionsFor`
      // above), but capturing an outer val from an enclosing method scope is fine.
      val javaPlatformKey = SettingKey[String]("javaPlatform")
      Def.setting {
        Keys.thisProject
          .all(ScopeFilter(inProjects(projects.map(p => LocalProject(p.id))*)))
          .value
          .map(_.id)
          .zip(javaPlatformKey.all(ScopeFilter(inProjects(projects.map(p => LocalProject(p.id))*))).value)
          .toMap
      }
    }
  }

  import autoImport.*

  /**
   * The default `build` job: it checks out the code, sets up Java
   * (`ciDefaultJavaVersion`), sbt and the dependency cache, then runs
   * `ciCheckArtifactsCompilationSteps`, `ciCheckArtifactsBuildSteps`, the
   * Scaladoc check when `ciCheckScaladocExamples` is true,
   * `ciCheckWebsiteBuildProcess`, and the `NetlifyPreviewBuildSteps` when
   * `ciEnableDeployPreview` is true. A swap-space step comes first when
   * `ciSwapSizeGB` is above 0.
   */
  lazy val buildJobs: Def.Initialize[Seq[Job]] = Def.setting {
    val swapSizeGB                = ciSwapSizeGB.value
    val setSwapSpace              = SetSwapSpace.value
    val checkout                  = Checkout.value
    val javaVersion               = ciDefaultJavaVersion.value
    val checkAllCodeCompiles      = ciCheckArtifactsCompilationSteps.value
    val checkArtifactBuildProcess = ciCheckArtifactsBuildSteps.value
    val checkWebsiteBuildProcess  = ciCheckWebsiteBuildProcess.value
    val checkScaladocExamples     =
      if (ciCheckScaladocExamples.value) Seq(CheckScaladocExamples) else Seq.empty
    val netlifyPreviewBuildSteps =
      if (ciEnableDeployPreview.value) NetlifyPreviewBuildSteps.value else Seq.empty

    Seq(
      Job(
        id = "build",
        name = "Build",
        steps = {
          (if (swapSizeGB > 0) Seq(setSwapSpace) else Seq.empty) ++
            Seq(
              checkout,
              SetupJava(javaVersion),
              SetupSBT,
              CacheDependencies
            ) ++ checkAllCodeCompiles ++ checkArtifactBuildProcess ++ checkScaladocExamples ++
            checkWebsiteBuildProcess ++ netlifyPreviewBuildSteps
        }
      )
    )
  }

  /**
   * The default `lint` job: it checks out the code, sets up Java
   * (`ciDefaultJavaVersion`), sbt and the dependency cache, then runs
   * `ciCheckGithubWorkflowSteps` and the `Lint` step. A swap-space step comes
   * first when `ciSwapSizeGB` is above 0.
   */
  lazy val lintJobs: Def.Initialize[Seq[Job]] = Def.setting {
    val checkout            = Checkout.value
    val swapSizeGB          = ciSwapSizeGB.value
    val setSwapSpace        = SetSwapSpace.value
    val javaVersion         = ciDefaultJavaVersion.value
    val checkGithubWorkflow = ciCheckGithubWorkflowSteps.value
    val lint                = Lint.value

    Seq(
      Job(
        id = "lint",
        name = "Lint",
        steps = (if (swapSizeGB > 0) Seq(setSwapSpace) else Seq.empty) ++
          Seq(checkout, SetupJava(javaVersion), SetupSBT, CacheDependencies) ++ checkGithubWorkflow ++ Seq(
            lint
          )
      )
    )
  }

  /**
   * The default `test` job, a matrix over the Java versions in
   * `ciTargetJavaVersions`. When `ciTargetScalaVersions` and
   * `ciTargetMinJavaVersions` are both empty, it runs `sbt +test`. Otherwise it
   * runs `/test` for the modules in `ciTargetScalaVersions` for each Scala
   * version, either grouped by Scala version or with one matrix entry per
   * module and version, according to `ciGroupSimilarTests`. The commands start
   * with the `ciBackgroundJobs` prefix, and the matrix is limited by
   * `ciMatrixMaxParallel`.
   */
  lazy val testJobs: Def.Initialize[Seq[Job]] = Def.setting {
    val groupSimilarTests  = ciGroupSimilarTests.value
    val scalaVersionMatrix = ciTargetScalaVersions.value
    val javaPlatforms      = autoImport.ciTargetJavaVersions.value
    val javaPlatformMatrix = ciTargetMinJavaVersions.value
    val matrixMaxParallel  = ciMatrixMaxParallel.value
    val swapSizeGB         = ciSwapSizeGB.value
    val setSwapSpace       = SetSwapSpace.value
    val checkout           = Checkout.value
    val backgroundJobs     = ciBackgroundJobs.value

    val prefixJobs = makePrefixJobs(backgroundJobs)

    val GroupTests = {
      def makeTests(scalaVersion: String) =
        s" ${scalaVersionMatrix.filter { case (_, versions) =>
            versions.contains(scalaVersion)
          }.map(e => e._1 + "/test").mkString(" ")}"

      Job(
        id = "test",
        name = "Test",
        strategy = Some(
          Strategy(
            matrix = Map(
              "java"  -> javaPlatforms.toList,
              "scala" -> scalaVersionMatrix.values.toSeq.flatten.distinct.toList
            ),
            maxParallel = matrixMaxParallel,
            failFast = false
          )
        ),
        steps = {
          (if (swapSizeGB > 0) Seq(setSwapSpace) else Seq.empty) ++ Seq(
            SetupJava("${{ matrix.java }}"),
            SetupSBT,
            CacheDependencies,
            checkout
          ) ++ (if (javaPlatformMatrix.values.toSet.isEmpty) {
                  scalaVersionMatrix.values.toSeq.flatten.distinct.map { (scalaVersion: String) =>
                    Step.SingleStep(
                      name = "Test",
                      condition = Some(Condition.Expression(s"matrix.scala == '$scalaVersion'")),
                      run = Some(
                        prefixJobs + SbtCommand + " ++${{ matrix.scala }}" + makeTests(
                          scalaVersion
                        )
                      )
                    )
                  }
                } else {
                  (for {
                    javaPlatform: String <- javaPlatforms
                    scalaVersion: String <- scalaVersionMatrix.values.toSeq.flatten.distinct
                    projects              =
                      scalaVersionMatrix.filterKeys { p =>
                        javaPlatformMatrix.getOrElse(p, javaPlatform).toInt <= javaPlatform.toInt
                      }.filter { case (_, versions) =>
                        versions.contains(scalaVersion)
                      }.keys
                  } yield
                    if (projects.nonEmpty)
                      Seq(
                        Step.SingleStep(
                          name = "Test",
                          condition = Some(
                            Condition.Expression(s"matrix.java == '$javaPlatform'") && Condition.Expression(
                              s"matrix.scala == '$scalaVersion'"
                            )
                          ),
                          run = Some(
                            prefixJobs + SbtCommand + " ++${{ matrix.scala }}" ++ s" ${projects.map(_ + "/test ").mkString(" ")}"
                          )
                        )
                      )
                    else Seq.empty).flatten.toSeq
                })
        }
      )
    }

    val FlattenTests =
      Job(
        id = "test",
        name = "Test",
        strategy = Some(
          Strategy(
            matrix = Map(
              "java" -> javaPlatforms.toList
            ) ++
              (if (javaPlatformMatrix.isEmpty) {
                 Map("scala-project" -> scalaVersionMatrix.flatMap { case (moduleName, versions) =>
                   versions.map { version =>
                     s"++$version $moduleName"
                   }
                 }.toList)
               } else {
                 def generateScalaProjectJavaPlatform(javaPlatform: String) =
                   s"scala-project-java$javaPlatform" -> scalaVersionMatrix.filterKeys { p =>
                     javaPlatformMatrix.getOrElse(p, javaPlatform).toInt <= javaPlatform.toInt
                   }.flatMap { case (moduleName, versions) =>
                     versions.map { version =>
                       s"++$version $moduleName"
                     }
                   }.toList

                 javaPlatforms.map(jp => generateScalaProjectJavaPlatform(jp))
               }),
            maxParallel = matrixMaxParallel,
            failFast = false
          )
        ),
        steps = (if (swapSizeGB > 0) Seq(setSwapSpace) else Seq.empty) ++
          Seq(
            SetupJava("${{ matrix.java }}"),
            SetupSBT,
            CacheDependencies,
            checkout,
            if (javaPlatformMatrix.values.toSet.isEmpty) {
              Step.SingleStep(
                name = "Test",
                run = Some(prefixJobs + SbtCommand + " ${{ matrix.scala-project }}/test")
              )
            } else {
              Step.StepSequence(
                javaPlatforms.map { javaPlatform =>
                  Step.SingleStep(
                    name = s"Java $javaPlatform Tests",
                    condition = Some(Condition.Expression(s"matrix.java == '$javaPlatform'")),
                    run = Some(
                      prefixJobs + SbtCommand + s" $${{ matrix.scala-project-java$javaPlatform }}/test"
                    )
                  )
                }
              )

            }
          )
      )

    val DefaultTestStrategy =
      Job(
        id = "test",
        name = "Test",
        strategy = Some(
          Strategy(
            matrix = Map("java" -> javaPlatforms.toList),
            maxParallel = matrixMaxParallel,
            failFast = false
          )
        ),
        steps = (if (swapSizeGB > 0) Seq(setSwapSpace) else Seq.empty) ++
          Seq(
            SetupJava("${{ matrix.java }}"),
            SetupSBT,
            CacheDependencies,
            checkout,
            Step.SingleStep(
              name = "Test",
              run = Some(prefixJobs + SbtCommand + " +test")
            )
          )
      )

    if (javaPlatformMatrix.isEmpty && scalaVersionMatrix.isEmpty)
      Seq(DefaultTestStrategy)
    else
      Seq(if (groupSimilarTests) GroupTests else FlattenTests)
  }

  /**
   * The default `ci` job, which needs the jobs in `ciPullRequestApprovalJobs`
   * and runs even when one of them failed. Its "Report Failed CI" step exits
   * with status 1 when any needed job failed or was cancelled, and its "Report
   * Successful CI" step prints "ci passed".
   */
  lazy val reportSuccessfulJobs: Def.Initialize[Seq[Job]] = Def.setting {
    val pullRequestApprovalJobs = ciPullRequestApprovalJobs.value

    Seq(
      Job(
        id = "ci",
        name = "ci",
        need = pullRequestApprovalJobs,
        // Runs even when a job it needs has failed.
        //
        // Without this the job is skipped, and GitHub reports a skipped job to branch protection as
        // a success. Since this is the job repositories are told to require, the single required
        // check would pass precisely when something upstream had broken - and a pull request with a
        // failing lint or test job would merge. That is not hypothetical: it is how #694 landed on
        // main with a red Lint job.
        condition = Some(Condition.Function("always()")),
        steps = Seq(
          // Only `failure` and `cancelled` count. An upstream job that was skipped was usually
          // skipped on purpose, by its own condition, and should not fail the gate.
          SingleStep(
            name = "Report Failed CI",
            condition = Some(
              Condition.Expression(
                "contains(needs.*.result, 'failure') || contains(needs.*.result, 'cancelled')"
              )
            ),
            run = Some("echo \"ci failed: one or more required jobs did not succeed\"; exit 1")
          ),
          SingleStep(
            name = "Report Successful CI",
            run = Some("echo \"ci passed\"")
          )
        )
      )
    )
  }

  /**
   * The default `update-readme` job. It runs `sbt docs/generateReadme` (the
   * `GenerateReadme` step), then opens a pull request that changes only
   * `README.md` on the branch `zio-sbt-website/update-readme` with a GitHub App
   * token, approves it and enables auto-merge, or squash-merges it directly
   * when enabling auto-merge fails. It runs under `ciUpdateReadmeCondition`, or
   * on a published release when that is `None`.
   */
  lazy val updateReadmeJobs: Def.Initialize[Seq[Job]] = Def.setting {
    val swapSizeGB            = ciSwapSizeGB.value
    val setSwapSpace          = SetSwapSpace.value
    val checkout              = Checkout.value
    val javaVersion           = ciDefaultJavaVersion.value
    val updateReadmeCondition = autoImport.ciUpdateReadmeCondition.value
    val generateReadme        = GenerateReadme.value

    Seq(
      Job(
        id = "update-readme",
        name = "Update README",
        // The workflow-level `permissions` block only grants `id-token`/`contents: read`, so
        // without this, `Approve PR`/`Enable Auto-Merge` below fail with "Resource not
        // accessible by integration": a job-level `permissions` block replaces the workflow's
        // grants entirely rather than adding to them, so `contents: read` has to be repeated
        // here for the checkout step - the job never pushes with GITHUB_TOKEN, the actual
        // commit/PR push uses the separate GitHub App token from `Generate Token` below.
        permissions = Map("contents" -> "read", "pull-requests" -> "write"),
        condition = updateReadmeCondition orElse Some(
          Condition.Expression("github.event_name == 'release'") &&
            Condition.Expression("github.event.action == 'published'")
        ),
        steps = (if (swapSizeGB > 0) Seq(setSwapSpace) else Seq.empty) ++
          Seq(
            checkout,
            SetupJava(javaVersion),
            SetupSBT,
            CacheDependencies,
            generateReadme,
            // Deliberately no `git commit` step here. Release events check out the tag (detached
            // HEAD), and create-pull-request treats an already-committed HEAD as its working base,
            // finds zero commits to carry over, and reports "not ahead of base" without opening a
            // PR. Leave README.md as an uncommitted change and let the action commit it.
            Step.SingleStep(
              name = "Generate Token",
              id = Some("generate-token"),
              uses = Some(ActionRef(V("zio/generate-github-app-token"))),
              parameters = Map(
                "app_id"          -> Json.Str("${{ secrets.APP_ID }}"),
                "app_private_key" -> Json.Str("${{ secrets.APP_PRIVATE_KEY }}")
              )
            ),
            Step.SingleStep(
              name = "Create Pull Request",
              id = Some("cpr"),
              uses = Some(ActionRef(V("peter-evans/create-pull-request"))),
              parameters = Map(
                "title"          -> Json.Str("Update README.md"),
                "commit-message" -> Json.Str("Update README.md"),
                "branch"         -> Json.Str("zio-sbt-website/update-readme"),
                // Scope the pull request to the file this job is about.
                //
                // `generateReadme` sequences `compileDocs`, which writes mdoc output to
                // `website/docs`. Without add-paths, create-pull-request commits everything left
                // in the working tree, so the generated PR carries that output too. On such a PR
                // the build job then fails: `installWebsite` finds a `website/` directory and
                // skips scaffolding, leaving no package.json for `npm install`.
                "add-paths" -> Json.Str("README.md"),
                // A `release`-triggered run checks out the tag, leaving HEAD detached, and
                // `create-pull-request` can't infer a base branch from a detached HEAD without
                // this — without it the step fails with "the 'base' input must be supplied".
                "base"          -> Json.Str("${{ github.event.repository.default_branch }}"),
                "delete-branch" -> Json.Bool(true),
                "body"          ->
                  Json.Str(
                    """|Autogenerated changes after running the `sbt docs/generateReadme` command of the [zio-sbt-website](https://zio.dev/zio-sbt) plugin.
                       |
                       |I will automatically update the README.md file whenever there is new change for README.md, e.g.
                       |  - After each release, I will update the version in the installation section.
                       |  - After any changes to the "docs/index.md" file, I will update the README.md file accordingly.""".stripMargin
                  ),
                "token" -> Json.Str("${{ steps.generate-token.outputs.token }}")
              )
            ),
            Step.SingleStep(
              name = "Approve PR",
              condition = Some(Condition.Expression("steps.cpr.outputs.pull-request-number")),
              env = Map(
                "GITHUB_TOKEN" -> "${{ secrets.GITHUB_TOKEN }}",
                "PR_URL"       -> "${{ steps.cpr.outputs.pull-request-url }}"
              ),
              run = Some("gh pr review \"$PR_URL\" --approve")
            ),
            Step.SingleStep(
              name = "Enable Auto-Merge",
              condition = Some(Condition.Expression("steps.cpr.outputs.pull-request-number")),
              env = Map(
                // The App token, not GITHUB_TOKEN: merging (and enabling auto-merge) needs
                // `contents: write`, which this job deliberately does not grant (see `permissions`
                // above). `Approve PR` keeps GITHUB_TOKEN: the PR is authored by the App's bot
                // identity, and a PR's author cannot approve it.
                "GITHUB_TOKEN" -> "${{ steps.generate-token.outputs.token }}",
                "PR_URL"       -> "${{ steps.cpr.outputs.pull-request-url }}"
              ),
              run = Some("gh pr merge --auto --squash \"$PR_URL\" || gh pr merge --squash \"$PR_URL\"")
            )
          )
      )
    )
  }

  private val releaseCondition = Some(
    Condition.Expression("github.event_name == 'release'") &&
      Condition.Expression("github.event.action == 'published'") || Condition.Expression(
        "github.event_name == 'workflow_dispatch'"
      )
  )

  // Snapshots are only published from the repository's default branch: the
  // workflow's push trigger covers ciEnabledBranches, but when that setting is
  // empty the trigger matches all branches, and snapshots must not be
  // published from feature branches. On a default-branch push HEAD is
  // untagged, so `sbt ci-release` publishes a SNAPSHOT.
  private val releaseOrSnapshotCondition =
    releaseCondition.map(
      _ || (Condition.Expression("github.event_name == 'push'") &&
        Condition.Expression("github.ref == format('refs/heads/{0}', github.event.repository.default_branch)"))
    )

  // A manual run with `docs-only` set to `true` publishes a release's docs without running the
  // Maven release. Maven Central rejects a version it already has, so once `release` has published,
  // every later run of it fails with "already exists" - and `release-docs`, which needs it, is
  // skipped for good. A release whose docs step failed (say, npm was not set up yet) could then only
  // get its docs by cutting a new version. See zio/zio-sbt#869.
  //
  // The input is read through `github.event.inputs` rather than the `inputs` context: the latter is
  // type-checked by actionlint against the declared inputs, which a build's own
  // `ciWorkflowTriggers` may not declare. For any event other than `workflow_dispatch` the value is
  // empty, so the comparisons below hold or fail the same way they would without the input.
  private[sbt] val DocsOnlyInputKey = "docs-only"

  private[sbt] val docsOnlyInput: Input = Input(
    key = DocsOnlyInputKey,
    description = "Set to true to skip the Maven release and only publish the docs to npm and notify zio/zio, " +
      "for a version Maven Central already has",
    required = false,
    defaultValue = "false"
  )

  private val docsOnlyRequested: Condition =
    Condition.Expression("github.event_name == 'workflow_dispatch'") &&
      Condition.Expression(s"github.event.inputs.$DocsOnlyInputKey == 'true'")

  private val docsOnlyNotRequested: Condition =
    Condition.Expression(s"github.event.inputs.$DocsOnlyInputKey != 'true'")

  /**
   * Adds the `docs-only` input to every `workflow_dispatch` trigger of the CI
   * workflow that lacks it, so the "Run workflow" form always offers the switch
   * the release jobs check for - including in a build that sets its own
   * `ciWorkflowTriggers`. Does nothing when the workflow has no `release-docs`
   * job for the switch to run.
   */
  private[sbt] def withDocsOnlyInput(triggers: Seq[Trigger], jobs: Seq[Job]): Seq[Trigger] =
    if (!jobs.exists(_.id == "release-docs")) triggers
    else
      triggers.map {
        case dispatch: Trigger.WorkflowDispatch if !dispatch.inputs.exists(_.key == DocsOnlyInputKey) =>
          Trigger.WorkflowDispatch(dispatch.inputs :+ docsOnlyInput)
        case other => other
      }

  /**
   * The default `release` job, which needs the jobs in `ciReleaseApprovalJobs`
   * and runs the `Release` step (`sbt ci-release`). It runs on a published
   * release or a manual dispatch, and also on a push to the default branch when
   * `ciPublishSnapshots` is true. It is skipped on a manual dispatch that sets
   * the `docs-only` input to `true`.
   */
  lazy val releaseJobs: Def.Initialize[Seq[Job]] = Def.setting {
    val swapSizeGB       = ciSwapSizeGB.value
    val setSwapSpace     = SetSwapSpace.value
    val checkout         = Checkout.value
    val javaVersion      = ciDefaultJavaVersion.value
    val release          = Release.value
    val jobs             = ciReleaseApprovalJobs.value
    val publishSnapshots = ciPublishSnapshots.value

    Seq(
      Job(
        id = "release",
        name = "Release",
        need = jobs,
        // Skipped on a docs-only run: the version is already on Maven Central.
        condition = (if (publishSnapshots) releaseOrSnapshotCondition else releaseCondition)
          .map(_ && docsOnlyNotRequested),
        steps = (if (swapSizeGB > 0) Seq(setSwapSpace) else Seq.empty) ++
          Seq(
            checkout,
            SetupJava(javaVersion),
            SetupSBT,
            CacheDependencies,
            release
          )
      )
    )
  }

  /**
   * The default jobs that run after a release. `release-docs` needs `release`:
   * it sets up Java, sbt and Node.js and publishes the docs to npm with
   * `PublishToNpmRegistry`, on a published release or a manual dispatch when
   * `release` succeeded, or on a manual dispatch with `docs-only` set to
   * `true`. It does not run on the pushes that publish a SNAPSHOT.
   * `notify-docs-release` then sends an `update-docs` dispatch event to the
   * zio/zio repository with the docs package name and version, when
   * `release-docs` succeeded.
   */
  lazy val postReleaseJobs: Def.Initialize[Seq[Job]] = Def.setting {
    val swapSizeGB           = ciSwapSizeGB.value
    val setSwapSpace         = SetSwapSpace.value
    val checkout             = Checkout.value
    val javaVersion          = ciDefaultJavaVersion.value
    val publishToNpmRegistry = PublishToNpmRegistry.value
    val docsVersioning       = ciDocsVersioningScheme.value

    Seq(
      Job(
        id = "release-docs",
        name = "Release Docs",
        need = Seq("release"),
        // Without a status function GitHub skips this job whenever `release` did not succeed,
        // which is also what a docs-only run does to `release` on purpose. `!cancelled()` lets the
        // condition decide instead: run after a successful release, or on a docs-only run. A
        // release that failed still keeps the docs back, and `releaseCondition` still keeps them
        // off snapshot pushes.
        condition = releaseCondition.map(
          Condition.Expression("!cancelled()") && _ &&
            (Condition.Expression("needs.release.result == 'success'") || docsOnlyRequested)
        ),
        steps = (if (swapSizeGB > 0) Seq(setSwapSpace) else Seq.empty) ++
          Seq(
            Step.StepSequence(
              Seq(
                checkout,
                SetupJava(javaVersion),
                SetupSBT,
                CacheDependencies,
                SetupNodeJs,
                publishToNpmRegistry
              )
            )
          )
      ),
      Job(
        id = "notify-docs-release",
        name = "Notify Docs Release",
        need = Seq("release-docs"),
        // The implicit `success()` checks every job upstream, not only `release-docs`, so on a
        // docs-only run the skipped `release` would skip this job too. Ask for the docs result
        // directly.
        condition = releaseCondition.map(
          Condition.Expression("!cancelled()") && _ &&
            Condition.Expression("needs.release-docs.result == 'success'")
        ),
        steps = Seq(
          checkout,
          // Unlike the auto-merge app-token fallback, this has no GITHUB_TOKEN path to fall back
          // to: the dispatch below targets zio/zio, a different repository, and GITHUB_TOKEN is
          // scoped to only the repository the workflow is running in. A minted app token works
          // only if that app is installed on zio/zio too; PAT_TOKEN remains the fallback for repos
          // relying on it (or where the app lacks that installation).
          //
          // github_repo must be zio/zio: the action defaults it to the current repository and
          // mints a token restricted to that repository only, which GitHub rejects on the
          // zio/zio dispatch endpoint with 403 "Resource not accessible by integration". Scoping
          // to zio/zio (rather than using the action's `unscoped_token` output, which covers every
          // repository the installation can reach) keeps the token least-privilege.
          checkAppTokenStep,
          SingleStep(
            name = "Generate Token",
            id = Some("generate-token"),
            condition = Some(appTokenConfigured),
            uses = Some(ActionRef(V("zio/generate-github-app-token"))),
            parameters = Map(
              "app_id"          -> Json.Str("${{ secrets.APP_ID }}"),
              "app_private_key" -> Json.Str("${{ secrets.APP_PRIVATE_KEY }}"),
              "github_repo"     -> Json.Str("zio/zio")
            )
          ),
          // --fail-with-body (not -f, which discards the response) so an HTTP 4xx/5xx fails the
          // job and GitHub's error message is still printed, instead of curl exiting 0.
          SingleStep(
            name = "notify the main repo about the new release of docs package",
            // The tag goes through env rather than straight into the script, so its text is never
            // parsed as shell.
            env = Map("NOTIFY_TOKEN" -> "${{ steps.generate-token.outputs.token || secrets.PAT_TOKEN }}") ++
              (if (docsVersioning == DocsVersioning.SemanticVersioning)
                 Map("RELEASE_TAG" -> "${{ github.event.release.tag_name }}")
               else Map.empty),
            run = Some(
              """|PACKAGE_NAME=$(cat docs/package.json | grep '"name"' | awk -F'"' '{print $4}')
                 |""".stripMargin +
                notifyPackageVersion(docsVersioning) +
                """|curl -sSL --fail-with-body \
                   |  -X POST \
                   |  -H "Accept: application/vnd.github+json" \
                   |  -H "Authorization: token $NOTIFY_TOKEN"\
                   |    https://api.github.com/repos/zio/zio/dispatches \
                   |    -d '{
                   |          "event_type":"update-docs",
                   |          "client_payload":{
                   |            "package_name":"'"${PACKAGE_NAME}"'",
                   |            "package_version": "'"${PACKAGE_VERSION}"'"
                   |          }
                   |        }'
                   |""".stripMargin
            )
          )
        )
      )
    )
  }

  /**
   * The shell lines of the docs notify step that set `PACKAGE_VERSION`, the
   * version zio/zio's `update-docs` job installs with `yarn add`.
   *
   * With semantic versioning that is the version `publishToNpm` just published:
   * the release's tag, or on a manual docs-only run the latest `v` tag, the
   * same rule zio-sbt-website's `releaseVersion` uses. It is not read back with
   * `npm view <package> version`: right after a publish the registry can take
   * over a minute to list the new version, so that returned the previous one
   * and zio/zio opened no update (zio-schema v1.9.2, zio-logging v2.5.4). The
   * step then waits until npm serves that exact version, since `yarn add` fails
   * on one npm does not list yet, and fails after five minutes rather than
   * notify for a version that never arrived.
   *
   * Hash versioning publishes under the `hash` dist-tag, not `latest`, so it
   * keeps reading the `latest` version as before.
   */
  private[sbt] def notifyPackageVersion(docsVersioning: DocsVersioning): String =
    if (docsVersioning == DocsVersioning.SemanticVersioning)
      """|TAG="${RELEASE_TAG:-$(git tag --sort=committerdate | grep '^v' | tail -1 || true)}"
         |if [ -z "$TAG" ]; then
         |  echo "::error::No release tag to take the docs package version from"
         |  exit 1
         |fi
         |PACKAGE_VERSION="${TAG#v}"
         |for attempt in $(seq 1 30); do
         |  if [ "$(npm view "$PACKAGE_NAME@$PACKAGE_VERSION" version --prefer-online 2>/dev/null)" = "$PACKAGE_VERSION" ]; then
         |    break
         |  fi
         |  if [ "$attempt" = 30 ]; then
         |    echo "::error::$PACKAGE_NAME@$PACKAGE_VERSION is not on npm after 5 minutes"
         |    exit 1
         |  fi
         |  echo "Waiting for npm to list $PACKAGE_NAME@$PACKAGE_VERSION ($attempt/30)"
         |  sleep 10
         |done
         |""".stripMargin
    else
      """|PACKAGE_VERSION=$(npm view $PACKAGE_NAME version)
         |""".stripMargin

  // Shared between renderWorkflow (which writes the full header) and the release-drafter
  // enable/disable guards (which only need to recognize it) - a single source of truth so the
  // two never drift out of sync.
  private val AutogeneratedHeaderPrefix = "# This file was autogenerated using `zio-sbt-ci` plugin"

  /**
   * Renders the workflow as YAML, preceded by a header comment that says the
   * file was autogenerated by `ciGenerateGithubWorkflow`. Throws an `Exception`
   * when the conversion to YAML fails.
   */
  def renderWorkflow(workflow: Workflow): String = {
    val workflowJson = workflow.toJsonAST.getOrElse(Json.Null)

    val yaml = workflowJson.toYaml(
      YamlOptions.default.copy(
        dropNulls = true,
        sequenceIndentation = 0,
        maxScalarWidth = None
      )
    ) match {
      case Right(yaml) => yaml
      case Left(err)   => throw new Exception(s"Failed to convert workflow to YAML: $err")
    }

    s"""|$AutogeneratedHeaderPrefix via `sbt ciGenerateGithubWorkflow`
        |# task and should be included in the git repository. Please do not edit it manually.
        |
        |$yaml""".stripMargin
  }

  private val DependabotConfigFile = ".github/dependabot.yml"

  /**
   * Renders the Dependabot configuration as the text of
   * `.github/dependabot.yml`, preceded by the autogenerated header comment.
   */
  def renderDependabotConfig(config: DependabotConfig): String =
    s"""|$AutogeneratedHeaderPrefix via `sbt ciGenerateGithubWorkflow`
        |# task and should be included in the git repository. Please do not edit it manually.
        |
        |${DependabotConfig.render(config)}""".stripMargin

  /**
   * Renders the release-drafter configuration as YAML, without the
   * autogenerated header. Throws an `Exception` when the conversion to YAML
   * fails.
   */
  def renderReleaseDrafterConfig(config: ReleaseDrafterConfig): String =
    config.toJsonAST
      .getOrElse(Json.Null)
      .toYaml(
        YamlOptions.default.copy(dropNulls = true, sequenceIndentation = 0, maxScalarWidth = None)
      ) match {
      case Right(yaml) => yaml
      case Left(err)   => throw new Exception(s"Failed to convert release-drafter config to YAML: $err")
    }

  /**
   * Turns a workflow name into a file name.
   *
   * The name is trimmed and lower-cased, each run of characters other than
   * `a-z` and `0-9` becomes one dash, and leading and trailing dashes are
   * removed, so a name like "Continuous Integration" yields
   * `continuous-integration.yml`. A name with nothing left yields `ci.yml`.
   */
  private[sbt] def workflowFileName(workflowName: String): String = {
    val slug = workflowName.trim.toLowerCase.replaceAll("[^a-z0-9]+", "-").replaceAll("(^-)|(-$)", "")
    s"${if (slug.isEmpty) "ci" else slug}.yml"
  }

  /**
   * Writes the rendered workflow to `.github/workflows/<fileName>` under
   * `baseDir`. When the file is new, it is also marked intent-to-add in git.
   */
  def writeWorkflowFile(baseDir: File, workflow: Workflow, fileName: String): Unit =
    writeGeneratedFile(baseDir, baseDir / ".github" / "workflows" / fileName, renderWorkflow(workflow))

  /**
   * Writes a generated file, and registers it with git when it is new.
   *
   * Scala Steward's post-update hook runs `ciGenerateGithubWorkflow` and then
   * commits with `git commit --all`, which only stages files git already knows
   * about. A file the task creates for the first time would otherwise be left
   * out of the "Regenerate GitHub Actions workflow" commit, and the PR would
   * fail `ciCheckGithubWorkflow`. Marking it intent-to-add makes `--all` pick
   * it up, and is harmless for anyone running the task by hand.
   */
  private def writeGeneratedFile(baseDir: File, target: File, content: String): Unit = {
    val isNew = !target.exists
    IO.write(target, content)
    if (isNew) markIntentToAdd(baseDir, target)
  }

  // Best effort: not a git repository, git missing or the path ignored are all fine to skip.
  private def markIntentToAdd(baseDir: File, target: File): Unit =
    try {
      val _ = Process(Seq("git", "add", "--intent-to-add", "--", target.getAbsolutePath), baseDir) !
        ProcessLogger(_ => (), _ => ())
    } catch { case _: java.io.IOException => () }

  /**
   * Writes the workflow under the current directory (`file(".")`). Deprecated:
   * use the overload that takes the build's base directory.
   */
  @deprecated("Use the overload taking the build's base directory", "0.6.4")
  def writeWorkflowFile(workflow: Workflow, fileName: String): Unit =
    writeWorkflowFile(file("."), workflow, fileName)

  /**
   * The task behind the CI workflow part of `ciGenerateGithubWorkflow`. It
   * joins the jobs of `ciBuildJobs`, `ciLintJobs`, `ciTestJobs`,
   * `ciUpdateReadmeJobs`, `ciReportSuccessfulJobs`, `ciReleaseJobs` and
   * `ciPostReleaseJobs` into one workflow, using `ciWorkflowEnv`,
   * `ciWorkflowTriggers`, `ciConcurrency` and `ciWorkflowPermissions`, and
   * writes it to the file named after `ciWorkflowTitle`. It fails with an error
   * when `ciWorkflowTriggers` is empty.
   */
  lazy val generateGithubWorkflowTask: Def.Initialize[Task[Unit]] =
    Def.task {
      val workflowName     = ciWorkflowTitle.value
      val buildJobs        = ciBuildJobs.value
      val lintJobs         = ciLintJobs.value
      val testJobs         = ciTestJobs.value
      val reportSuccessful = ciReportSuccessfulJobs.value
      val updateReadmeJobs = ciUpdateReadmeJobs.value
      val releaseJobs      = ciReleaseJobs.value
      val postReleaseJobs  = ciPostReleaseJobs.value
      val workflowEnv      = ciWorkflowEnv.value
      val triggers         = ciWorkflowTriggers.value
      val concurrency      = ciConcurrency.value
      val permissions      = ciWorkflowPermissions.value

      // An empty trigger list encodes as no `on:` block at all, and GitHub rejects a workflow
      // without one. Better to say so here than to emit a file that silently never runs.
      if (triggers.isEmpty)
        sys.error(
          "`ciWorkflowTriggers` is empty, which would generate a workflow with no `on:` block. " +
            "GitHub requires at least one trigger."
        )

      val jobs =
        buildJobs ++ lintJobs ++ testJobs ++ updateReadmeJobs ++ reportSuccessful ++ releaseJobs ++ postReleaseJobs

      val workflow = {
        val base = Workflow(
          name = workflowName,
          env = workflowEnv,
          concurrency = concurrency,
          triggers = withDocsOnlyInput(triggers, jobs),
          permissions = permissions.getOrElse(Workflow.defaultPermissions),
          jobs = jobs
        )

        if (permissions.isEmpty) base.withoutPermissions else base
      }

      writeWorkflowFile((ThisBuild / Keys.baseDirectory).value, workflow, workflowFileName(workflowName))
    }

  private def dependencyBotPRCondition(bots: Seq[String]): Condition = {
    val botConditions: Seq[Condition] =
      bots.map(bot => Condition.Expression(s"github.event.pull_request.user.login == '$bot'"))

    (Condition.Expression("github.event_name == 'workflow_dispatch'") +: botConditions).reduce(_ || _)
  }

  private def backfillBotPRsScript(bots: Seq[String], applyCommand: String): String = {
    val actors = bots.map(bot => s""""$bot"""").mkString(" ")

    s"""|set -euo pipefail
        |for actor in $actors; do
        |  gh pr list \\
        |    --author "$$actor" \\
        |    --state open \\
        |    --json number \\
        |    --jq '.[].number' | \\
        |  xargs -I{} $applyCommand {}
        |done
        |""".stripMargin
  }

  // `gh pr merge --auto` only waits for checks that branch protection *requires*; on a branch
  // with none it merges at once, before CI has even finished (see zio/zio-process#700). So when
  // the base branch requires checks - via classic branch protection or a ruleset, both readable
  // with the job's token - this hands the PR to GitHub's native auto-merge and exits at once.
  // Otherwise it waits for CI itself and merges directly, failing the job - leaving the PR open -
  // on a red check. Waiting is the fallback only: the app token expires after an hour, so a slow
  // CI run used to outlive it and the loop spun on "0 checks" until the job timed out.
  // The bot workflows' own jobs are excluded, or this would wait on itself forever. Checks for
  // the new head commit register a few seconds after the push, so "no checks reported yet" is
  // treated as pending rather than as success; any other `gh pr checks` error fails the job.
  private def mergeWhenCiPassesScript(ownJobs: Seq[String]): String = {
    val excluded = ownJobs.map(job => s""".name != "$job"""").mkString(" and ")

    s"""|set -euo pipefail
        |protected=$$(gh api "repos/$$GH_REPO/branches/$$BASE" \\
        |  --jq '.protection.required_status_checks.contexts | length')
        |ruleset=$$(gh api "repos/$$GH_REPO/rules/branches/$$BASE" \\
        |  --jq '[.[] | select(.type == "required_status_checks")] | length')
        |if [ $$((protected + ruleset)) -gt 0 ]; then
        |  echo "$$BASE requires status checks, enabling auto-merge"
        |  gh pr merge --auto --squash "$$PR"
        |  exit 0
        |fi
        |echo "$$BASE has no required status checks, waiting for CI before merging"
        |while true; do
        |  if ! checks=$$(gh pr checks "$$PR" --json name,bucket \\
        |    --jq '[.[] | select($excluded)]' 2>&1); then
        |    case "$$checks" in
        |      *"no checks reported"*) checks='[]' ;;
        |      *) echo "$$checks" >&2; exit 1 ;;
        |    esac
        |  fi
        |  checks=$${checks:-[]}
        |  total=$$(jq length <<< "$$checks")
        |  pending=$$(jq '[.[] | select(.bucket == "pending")] | length' <<< "$$checks")
        |  failed=$$(jq '[.[] | select(.bucket == "fail" or .bucket == "cancel")] | length' <<< "$$checks")
        |  if [ "$$failed" -gt 0 ]; then
        |    echo "CI failed, not merging:"
        |    jq -r '.[] | select(.bucket == "fail" or .bucket == "cancel") | .name' <<< "$$checks"
        |    exit 1
        |  fi
        |  if [ "$$total" -gt 0 ] && [ "$$pending" -eq 0 ]; then
        |    break
        |  fi
        |  echo "Waiting for CI ($$total checks, $$pending pending)..."
        |  sleep 30
        |done
        |gh pr merge --squash "$$PR"
        |""".stripMargin
  }

  private val dependencyBotPRTriggers: Seq[Trigger] = Seq(
    Trigger.PullRequestTarget(types = Seq("opened", "reopened", "synchronize", "ready_for_review")),
    Trigger.WorkflowDispatch()
  )

  private val botTokenEnv: Map[String, String] = Map(
    "GH_TOKEN" -> "${{ secrets.GITHUB_TOKEN }}",
    "GH_REPO"  -> "${{ github.repository }}"
  )

  // GITHUB_TOKEN can never be granted the "workflows" scope GitHub's `enablePullRequestAutoMerge`
  // mutation needs for a PR that touches .github/workflows/** - no `permissions:` grant fixes
  // that, `actions: write` included, despite the error message asking for a "workflows"
  // permission that isn't a valid permissions key at all (see #744). A GitHub App token can hold
  // that scope, so the "Generate Token" step below mints one and this falls back to GITHUB_TOKEN
  // when no app is configured: `steps.generate-token.outputs.token` is empty both when that step
  // is skipped (no APP_ID secret) and when it fails, so `||` reaches the fallback either way.
  // GITHUB_TOKEN alone still auto-merges ordinary, non-workflow-touching dependency PRs fine -
  // only PRs that touch workflow files need the app's scope.
  private val mergeTokenEnv: Map[String, String] = Map(
    "GH_TOKEN" -> "${{ steps.generate-token.outputs.token || secrets.GITHUB_TOKEN }}",
    "GH_REPO"  -> "${{ github.repository }}"
  )

  // GitHub rejects the `secrets` context in any `if:` - job or step - with "Unrecognized
  // named-value: 'secrets'", failing the *entire* workflow file (every job, every trigger), not
  // just the step that used it. `secrets` is readable in `env:`/`run:` though, so this step reads
  // it there and exposes the result as a step output, which `if:` conditions may reference freely.
  private val checkAppTokenStep: Step.SingleStep =
    Step.SingleStep(
      name = "Check App Token",
      id = Some("check-app-token"),
      env = Map("APP_ID" -> "${{ secrets.APP_ID }}"),
      run = Some(
        """|if [ -n "$APP_ID" ]; then
           |  echo "configured=true" >> "$GITHUB_OUTPUT"
           |else
           |  echo "configured=false" >> "$GITHUB_OUTPUT"
           |fi
           |""".stripMargin
      )
    )

  private val appTokenConfigured: Condition =
    Condition.Expression("steps.check-app-token.outputs.configured == 'true'")

  /**
   * The `auto-approve.yml` workflow. On pull requests (and a manual dispatch),
   * its `auto-approve-bot-prs` job approves pull requests opened by the
   * `ciDependencyUpdateBots`; on a manual dispatch it approves the open pull
   * requests of those bots.
   */
  lazy val autoApproveWorkflow: Def.Initialize[Workflow] = Def.setting {
    val bots = ciDependencyUpdateBots.value.map(_.login)

    Workflow(
      name = "Auto-approve bot dependency PRs",
      triggers = dependencyBotPRTriggers,
      permissions = Map("pull-requests" -> "write"),
      jobs = Seq(
        Job(
          id = "auto-approve-bot-prs",
          name = "auto-approve-bot-prs",
          condition = Some(dependencyBotPRCondition(bots)),
          steps = Seq(
            Step.SingleStep(
              name = "Approve PR",
              condition = Some(Condition.Expression("github.event_name == 'pull_request_target'")),
              run = Some("gh pr review --approve ${{ github.event.number }}"),
              env = botTokenEnv
            ),
            Step.SingleStep(
              name = "Backfill auto-approve for existing bot PRs",
              condition = Some(Condition.Expression("github.event_name == 'workflow_dispatch'")),
              run = Some(backfillBotPRsScript(bots, "gh pr review --approve")),
              env = botTokenEnv
            )
          )
        )
      )
    )
  }

  /**
   * The `auto-merge.yml` workflow. Its `auto-merge` job handles pull requests
   * opened by the `ciDependencyUpdateBots`: it enables auto-merge when the base
   * branch requires status checks, and otherwise waits for the other checks to
   * finish and merges the pull request with squash, or fails the job and leaves
   * the pull request open when a check failed or was cancelled. On a manual
   * dispatch it enables auto-merge on the open pull requests of those bots. It
   * uses a GitHub App token when the `APP_ID` secret is set, and `GITHUB_TOKEN`
   * otherwise.
   */
  lazy val autoMergeWorkflow: Def.Initialize[Workflow] = Def.setting {
    val bots = ciDependencyUpdateBots.value.map(_.login)

    Workflow(
      name = "Auto-merge bot dependency PRs",
      triggers = dependencyBotPRTriggers,
      // `contents`/`pull-requests: write` are for the GITHUB_TOKEN fallback path (see
      // `mergeTokenEnv`); the app-token path doesn't need this block at all, the app's own
      // installation permissions govern what its token can do.
      permissions = Map("contents" -> "write", "pull-requests" -> "write", "checks" -> "read"),
      jobs = Seq(
        Job(
          id = "auto-merge",
          name = "auto-merge",
          condition = Some(dependencyBotPRCondition(bots)),
          jobTimeout = Some(120),
          steps = Seq(
            checkAppTokenStep,
            Step.SingleStep(
              name = "Generate Token",
              id = Some("generate-token"),
              // Skipped (rather than left to fail on empty secrets) on repos that never
              // configured APP_ID/APP_PRIVATE_KEY, so no action run is wasted on the doomed API
              // calls that would otherwise make.
              condition = Some(appTokenConfigured),
              uses = Some(ActionRef(V("zio/generate-github-app-token"))),
              parameters = Map(
                "app_id"          -> Json.Str("${{ secrets.APP_ID }}"),
                "app_private_key" -> Json.Str("${{ secrets.APP_PRIVATE_KEY }}")
              )
            ),
            Step.SingleStep(
              name = "Merge bot PR once CI passes",
              condition = Some(Condition.Expression("github.event_name == 'pull_request_target'")),
              run = Some(mergeWhenCiPassesScript(Seq("auto-merge", "auto-approve-bot-prs"))),
              env = mergeTokenEnv ++ Map(
                "PR"   -> "${{ github.event.number }}",
                "BASE" -> "${{ github.event.pull_request.base.ref }}"
              )
            ),
            Step.SingleStep(
              name = "Backfill auto-merge for existing bot PRs",
              condition = Some(Condition.Expression("github.event_name == 'workflow_dispatch'")),
              run = Some(backfillBotPRsScript(bots, "gh pr merge --auto --squash")),
              env = mergeTokenEnv
            )
          )
        )
      )
    )
  }

  /** The task that writes `autoApproveWorkflow` to `auto-approve.yml`. */
  lazy val generateAutoApproveWorkflowTask: Def.Initialize[Task[Unit]] =
    Def.task(writeWorkflowFile((ThisBuild / Keys.baseDirectory).value, autoApproveWorkflow.value, "auto-approve.yml"))

  /** The task that writes `autoMergeWorkflow` to `auto-merge.yml`. */
  lazy val generateAutoMergeWorkflowTask: Def.Initialize[Task[Unit]] =
    Def.task(writeWorkflowFile((ThisBuild / Keys.baseDirectory).value, autoMergeWorkflow.value, "auto-merge.yml"))

  // Bot PRs routinely change inputs of the generated files - Scala Steward bumping `zio-sbt-ci`
  // itself, Dependabot bumping an action pin - which leaves `ciCheckGithubWorkflow` failing in
  // Lint with nobody to fix it, so auto-merge never fires. This regenerates and pushes the fix
  // to the PR branch. `pull_request` (not `_target`) so the PR's own build definition is what
  // gets regenerated; bot PRs are same-repo branches, so repo secrets are available (Dependabot
  // reads the separate Dependabot secrets store - see the docs).
  /**
   * The `regenerate-workflows.yml` workflow. On pull requests from the
   * `ciDependencyUpdateBots` whose branch is in the same repository, its
   * `regenerate` job runs `sbt ciGenerateGithubWorkflow` and, when files under
   * `.github` changed, commits them as "chore(ci): regenerate GitHub workflows"
   * and pushes to the pull request branch.
   */
  lazy val regenerateWorkflowsWorkflow: Def.Initialize[Workflow] = Def.setting {
    val bots     = ciDependencyUpdateBots.value.map(_.login)
    val checkout = Step.SingleStep(
      name = "Git Checkout",
      uses = Some(ActionRef(V("actions/checkout"))),
      parameters = Map(
        "ref"         -> Json.Str("${{ github.head_ref }}"),
        "fetch-depth" -> Json.Str("0"),
        // A push made with GITHUB_TOKEN does not trigger other workflows, which would leave CI
        // (and so auto-merge) waiting forever on the regenerated commit; the App token does.
        "token" -> Json.Str("${{ steps.generate-token.outputs.token || secrets.GITHUB_TOKEN }}")
      )
    )

    val botConditions: Seq[Condition] =
      bots.map(bot => Condition.Expression(s"github.event.pull_request.user.login == '$bot'"))
    val botCondition = botConditions.reduce(_ || _)

    Workflow(
      name = "Regenerate CI workflows for bot PRs",
      triggers = Seq(Trigger.PullRequest(ignoredBranches = Seq(Branch.Named("gh-pages")))),
      permissions = Map("contents" -> "read"),
      concurrency = Some(Concurrency("regenerate-ci-workflows-${{ github.head_ref }}")),
      jobs = Seq(
        Job(
          id = "regenerate",
          name = "regenerate",
          permissions = Map("contents" -> "write"),
          condition = Some(
            botCondition &&
              Condition.Expression("github.event.pull_request.head.repo.full_name == github.repository")
          ),
          steps = Seq(
            checkAppTokenStep,
            Step.SingleStep(
              name = "Generate Token",
              id = Some("generate-token"),
              condition = Some(appTokenConfigured),
              uses = Some(ActionRef(V("zio/generate-github-app-token"))),
              parameters = Map(
                "app_id"          -> Json.Str("${{ secrets.APP_ID }}"),
                "app_private_key" -> Json.Str("${{ secrets.APP_PRIVATE_KEY }}")
              )
            ),
            checkout,
            SetupJava(),
            SetupSBT,
            CacheDependencies,
            Step.SingleStep(
              name = "Regenerate GitHub workflow files",
              run = Some(SbtCommand + " ciGenerateGithubWorkflow")
            ),
            Step.SingleStep(
              name = "Commit and push the regenerated files if changed",
              env = Map("HEAD_REF" -> "${{ github.head_ref }}"),
              run = Some(
                """|set -euo pipefail
                   |if git diff --quiet -- .github && [ -z "$(git ls-files --others --exclude-standard -- .github)" ]; then
                   |  echo "Generated files already up to date; nothing to do."
                   |  exit 0
                   |fi
                   |git config user.name "github-actions[bot]"
                   |git config user.email "41898282+github-actions[bot]@users.noreply.github.com"
                   |git add .github
                   |git commit -m "chore(ci): regenerate GitHub workflows"
                   |git push origin "HEAD:$HEAD_REF"
                   |""".stripMargin
              )
            )
          )
        )
      )
    )
  }

  /**
   * The task that writes `regenerateWorkflowsWorkflow` to
   * `regenerate-workflows.yml` when `ciEnableRegenerateWorkflows` is true. When
   * it is false, the task deletes the file if it starts with the autogenerated
   * header.
   */
  lazy val generateRegenerateWorkflowsWorkflowTask: Def.Initialize[Task[Unit]] =
    Def.task {
      val baseDir  = (ThisBuild / Keys.baseDirectory).value
      val enabled  = ciEnableRegenerateWorkflows.value
      val workflow = regenerateWorkflowsWorkflow.value

      // Same ownership rule as release-drafter.yml: when disabled, only delete a copy this
      // plugin generated itself.
      val workflowFile = baseDir / ".github" / "workflows" / "regenerate-workflows.yml"
      val weOwn        = !workflowFile.exists || IO.read(workflowFile).startsWith(AutogeneratedHeaderPrefix)

      if (enabled) writeWorkflowFile(baseDir, workflow, "regenerate-workflows.yml")
      else if (workflowFile.exists && weOwn) IO.delete(workflowFile)
    }

  /**
   * The `deploy-preview.yml` workflow: the automatic variant for
   * `DeployPreviewMode.Automatic` and the on-demand variant for
   * `DeployPreviewMode.OnDemand`, as set by `ciDeployPreviewMode`.
   */
  lazy val netlifyDeployPreviewWorkflow: Def.Initialize[Workflow] = Def.setting {
    val automatic = automaticDeployPreviewWorkflow.value
    val onDemand  = onDemandDeployPreviewWorkflow.value

    ciDeployPreviewMode.value match {
      case DeployPreviewMode.Automatic => automatic
      case DeployPreviewMode.OnDemand  => onDemand
    }
  }

  private lazy val automaticDeployPreviewWorkflow: Def.Initialize[Workflow] = Def.setting {
    val ciWorkflowName = ciWorkflowTitle.value

    val deployPreviewCondition =
      Condition.Expression("github.event.workflow_run.event == 'pull_request'") &&
        Condition.Expression("github.event.workflow_run.conclusion == 'success'")

    // The build job only uploads these artifacts when the website is actually installed (see
    // `websiteInstalled` in `NetlifyPreviewBuildSteps`); this workflow triggers on every
    // successful pull-request CI run regardless. `continueOnError` on the two downloads turns a
    // missing artifact into a soft failure instead of a hard one, and every later step is then
    // gated on both downloads having actually succeeded, so a repo with no website installed gets
    // a job with two skipped-looking (continued-past) steps and nothing else - no deploy attempt,
    // no comment - rather than a failing required check.
    val artifactsDownloaded =
      Condition.Expression("steps.download-website.outcome == 'success'") &&
        Condition.Expression("steps.download-pr-metadata.outcome == 'success'")

    Workflow(
      name = "Deploy Preview",
      triggers = Seq(Trigger.WorkflowRun(workflows = Seq(ciWorkflowName), types = Seq("completed"))),
      permissions = Map(
        "actions"       -> "read",
        "contents"      -> "read",
        "deployments"   -> "write",
        "issues"        -> "write",
        "pull-requests" -> "write",
        "statuses"      -> "write"
      ),
      jobs = Seq(
        Job(
          id = "deploy-preview",
          name = "deploy-preview",
          condition = Some(deployPreviewCondition),
          steps = Seq(
            Step.SingleStep(
              name = "Download Website Artifact",
              id = Some("download-website"),
              continueOnError = true,
              uses = Some(ActionRef(V("actions/download-artifact"))),
              parameters = Map(
                "name"         -> Json.Str("website-artifact"),
                "path"         -> Json.Str("website-artifact"),
                "run-id"       -> Json.Str("${{ github.event.workflow_run.id }}"),
                "github-token" -> Json.Str("${{ secrets.GITHUB_TOKEN }}")
              )
            ),
            Step.SingleStep(
              name = "Download PR Metadata",
              id = Some("download-pr-metadata"),
              continueOnError = true,
              uses = Some(ActionRef(V("actions/download-artifact"))),
              parameters = Map(
                "name"         -> Json.Str("pr-metadata"),
                "path"         -> Json.Str("pr-metadata"),
                "run-id"       -> Json.Str("${{ github.event.workflow_run.id }}"),
                "github-token" -> Json.Str("${{ secrets.GITHUB_TOKEN }}")
              )
            ),
            Step.SingleStep(
              name = "Read PR Number",
              id = Some("pr"),
              condition = Some(artifactsDownloaded),
              run = Some("echo \"number=$(cat pr-metadata/pr-number.txt)\" >> \"$GITHUB_OUTPUT\"")
            ),
            Step.SingleStep(
              name = "Compute Deployment Metadata",
              id = Some("meta"),
              condition = Some(artifactsDownloaded),
              run = Some(
                """|echo "time=$(date -u +'%Y-%m-%d %H:%M:%S UTC')" >> "$GITHUB_OUTPUT"
                   |sha="${{ github.event.workflow_run.head_sha }}"
                   |echo "short_sha=${sha:0:7}" >> "$GITHUB_OUTPUT"
                   |""".stripMargin
              )
            ),
            Step.SingleStep(
              name = "Deploy Preview to Netlify",
              id = Some("netlify-deploy"),
              condition = Some(artifactsDownloaded),
              uses = Some(ActionRef(V("nwtgck/actions-netlify"))),
              parameters = Map(
                "publish-dir"                 -> Json.Str("./website-artifact"),
                "github-token"                -> Json.Str("${{ secrets.GITHUB_TOKEN }}"),
                "deploy-message"              -> Json.Str("PR #${{ steps.pr.outputs.number }}"),
                "alias"                       -> Json.Str("pr-${{ steps.pr.outputs.number }}"),
                "enable-pull-request-comment" -> Json.Bool(false),
                "enable-commit-comment"       -> Json.Bool(false)
              ),
              env = Map(
                "NETLIFY_AUTH_TOKEN" -> "${{ secrets.NETLIFY_AUTH_TOKEN }}",
                "NETLIFY_SITE_ID"    -> "${{ secrets.NETLIFY_SITE_ID }}"
              )
            ),
            // A plain re-deploy (e.g. a follow-up push to the same PR) used to overwrite the one
            // sticky comment in place, so pushing twice silently discarded the first deploy's
            // record. Posting a fresh comment per deploy keeps that history, but leaves stale
            // comments cluttering the thread - so before posting the new one, every earlier
            // preview comment (tagged by the same marker) gets collapsed as outdated instead of
            // deleted, keeping the audit trail without the noise.
            Step.SingleStep(
              name = "Mark previous preview comments as outdated",
              condition = Some(artifactsDownloaded),
              uses = Some(ActionRef(V("actions/github-script"))),
              parameters = Map(
                "script" -> Json.Str(
                  """|const comments = await github.paginate(github.rest.issues.listComments, {
                     |  owner: context.repo.owner,
                     |  repo: context.repo.repo,
                     |  issue_number: ${{ steps.pr.outputs.number }},
                     |  per_page: 100,
                     |});
                     |const stale = comments.filter(
                     |  (c) => c.user.type === 'Bot' && c.body.includes('<!-- netlify-preview-comment -->')
                     |);
                     |for (const comment of stale) {
                     |  await github.graphql(
                     |    `mutation($id: ID!) {
                     |      minimizeComment(input: { subjectId: $id, classifier: OUTDATED }) {
                     |        minimizedComment { isMinimized }
                     |      }
                     |    }`,
                     |    { id: comment.node_id }
                     |  );
                     |}
                     |""".stripMargin
                )
              )
            ),
            Step.SingleStep(
              name = "Post Preview URL Comment",
              condition = Some(artifactsDownloaded),
              uses = Some(ActionRef(V("peter-evans/create-or-update-comment"))),
              parameters = Map(
                "issue-number" -> Json.Str("${{ steps.pr.outputs.number }}"),
                "body"         -> Json.Str(
                  """|<!-- netlify-preview-comment -->
                     |🚀 New preview deployed to Netlify: ${{ steps.netlify-deploy.outputs.deploy-url }}
                     |
                     |🕒 Deployed at: ${{ steps.meta.outputs.time }}
                     |📌 Commit: [`${{ steps.meta.outputs.short_sha }}`](https://github.com/${{ github.repository }}/commit/${{ github.event.workflow_run.head_sha }})""".stripMargin
                )
              )
            )
          )
        )
      )
    )
  }

  // The on-demand variant of deploy-preview.yml (`DeployPreviewMode.OnDemand`). Same file name and
  // workflow name as the automatic variant, so switching modes just rewrites that one file.
  //
  // It has two jobs, told apart by the event that started the run:
  //  - `post-preview-comment` (after a successful pull-request CI run) creates or resets the single
  //    bot comment holding an unticked "Deploy preview" checkbox. Nothing is deployed.
  //  - `deploy-preview` (when that comment is edited with the box ticked) deploys the website built
  //    by the CI run of the commit named in the comment, then rewrites the comment with the URL.
  //
  // The bot comment carries two hidden markers: one to find it again, one with the full commit sha
  // it was posted for, so a later tick deploys exactly that commit and never a newer one.
  private lazy val onDemandDeployPreviewWorkflow: Def.Initialize[Workflow] = Def.setting {
    val ciWorkflowName = ciWorkflowTitle.value

    val commentMarker = "<!-- netlify-preview-comment -->"

    val postCommentCondition =
      Condition.Expression("github.event_name == 'workflow_run'") &&
        Condition.Expression("github.event.workflow_run.event == 'pull_request'") &&
        Condition.Expression("github.event.workflow_run.conclusion == 'success'")

    // Only a bot-authored preview comment whose checkbox was just ticked may start a deployment;
    // the permission of whoever ticked it is checked in the first step.
    val deployCondition =
      Condition.Expression("github.event_name == 'issue_comment'") &&
        Condition.Expression("github.event.issue.pull_request") &&
        Condition.Expression("github.event.comment.user.type == 'Bot'") &&
        Condition.Expression(s"contains(github.event.comment.body, '$commentMarker')") &&
        Condition.Expression("contains(github.event.comment.body, '- [x]')")

    // Same soft-failure pattern as the automatic mode: both artifacts are only uploaded when the
    // website is installed and docs/ or website/ changed, so a missing one means "nothing to
    // preview" and must not turn into a failing check or a pointless comment.
    val artifactsDownloaded =
      Condition.Expression("steps.download-website.outcome == 'success'") &&
        Condition.Expression("steps.download-pr-metadata.outcome == 'success'")

    Workflow(
      name = "Deploy Preview",
      triggers = Seq(
        Trigger.WorkflowRun(workflows = Seq(ciWorkflowName), types = Seq("completed")),
        Trigger.IssueComment(types = Seq("edited"))
      ),
      permissions = Map(
        "actions"       -> "read",
        "contents"      -> "read",
        "deployments"   -> "write",
        "issues"        -> "write",
        "pull-requests" -> "write",
        "statuses"      -> "write"
      ),
      // The workflow-level default group would cancel a deployment when an unrelated event for the
      // same ref arrives; the deploy job has its own per-PR group instead.
      concurrency = None,
      jobs = Seq(
        Job(
          id = "post-preview-comment",
          name = "post-preview-comment",
          condition = Some(postCommentCondition),
          steps = Seq(
            Step.SingleStep(
              name = "Download Website Artifact",
              id = Some("download-website"),
              continueOnError = true,
              uses = Some(ActionRef(V("actions/download-artifact"))),
              parameters = Map(
                "name"         -> Json.Str("website-artifact"),
                "path"         -> Json.Str("website-artifact"),
                "run-id"       -> Json.Str("${{ github.event.workflow_run.id }}"),
                "github-token" -> Json.Str("${{ secrets.GITHUB_TOKEN }}")
              )
            ),
            Step.SingleStep(
              name = "Download PR Metadata",
              id = Some("download-pr-metadata"),
              continueOnError = true,
              uses = Some(ActionRef(V("actions/download-artifact"))),
              parameters = Map(
                "name"         -> Json.Str("pr-metadata"),
                "path"         -> Json.Str("pr-metadata"),
                "run-id"       -> Json.Str("${{ github.event.workflow_run.id }}"),
                "github-token" -> Json.Str("${{ secrets.GITHUB_TOKEN }}")
              )
            ),
            Step.SingleStep(
              name = "Read PR Number",
              id = Some("pr"),
              condition = Some(artifactsDownloaded),
              run = Some("echo \"number=$(cat pr-metadata/pr-number.txt)\" >> \"$GITHUB_OUTPUT\"")
            ),
            Step.SingleStep(
              name = "Create or Reset Preview Comment",
              condition = Some(artifactsDownloaded),
              uses = Some(ActionRef(V("actions/github-script"))),
              env = Map(
                "PR_NUMBER" -> "${{ steps.pr.outputs.number }}",
                "HEAD_SHA"  -> "${{ github.event.workflow_run.head_sha }}"
              ),
              parameters = Map(
                "script" -> Json.Str(
                  """|const issue_number = parseInt(process.env.PR_NUMBER, 10);
                     |const sha = process.env.HEAD_SHA;
                     |const body = [
                     |  '<!-- netlify-preview-comment -->',
                     |  `<!-- preview-sha:${sha} -->`,
                     |  '### 🌐 Website preview',
                     |  '',
                     |  `- [ ] **Deploy preview** for commit [\`${sha.slice(0, 7)}\`](https://github.com/${context.repo.owner}/${context.repo.repo}/commit/${sha})`,
                     |  '',
                     |  'Tick the checkbox to deploy this commit to Netlify. Previews are not deployed automatically: after a new push, tick the box again.',
                     |].join('\n');
                     |
                     |const comments = await github.paginate(github.rest.issues.listComments, {
                     |  owner: context.repo.owner,
                     |  repo: context.repo.repo,
                     |  issue_number,
                     |  per_page: 100,
                     |});
                     |const existing = comments.find(
                     |  (c) => c.user.type === 'Bot' && c.body.includes('<!-- netlify-preview-comment -->')
                     |);
                     |
                     |if (existing) {
                     |  await github.rest.issues.updateComment({
                     |    owner: context.repo.owner,
                     |    repo: context.repo.repo,
                     |    comment_id: existing.id,
                     |    body,
                     |  });
                     |} else {
                     |  await github.rest.issues.createComment({
                     |    owner: context.repo.owner,
                     |    repo: context.repo.repo,
                     |    issue_number,
                     |    body,
                     |  });
                     |}
                     |""".stripMargin
                )
              )
            )
          )
        ),
        Job(
          id = "deploy-preview",
          name = "deploy-preview",
          condition = Some(deployCondition),
          // One deployment at a time per PR, but never cancel one that is already running: the
          // second tick simply waits for its turn.
          concurrency = Some(
            Concurrency(
              group = "deploy-preview-${{ github.event.issue.number }}",
              cancelInProgress = CancelInProgress.Never
            )
          ),
          steps = Seq(
            Step.SingleStep(
              name = "Check Permission of the Requester",
              uses = Some(ActionRef(V("actions/github-script"))),
              parameters = Map(
                "script" -> Json.Str(
                  """|const { data } = await github.rest.repos.getCollaboratorPermissionLevel({
                     |  owner: context.repo.owner,
                     |  repo: context.repo.repo,
                     |  username: context.payload.sender.login,
                     |});
                     |if (!['admin', 'maintain', 'write'].includes(data.permission)) {
                     |  core.setFailed(`${context.payload.sender.login} has no write access, refusing to deploy preview.`);
                     |}
                     |""".stripMargin
                )
              )
            ),
            // A 👍 on the comment tells the requester the tick was received. GitHub keeps a single
            // reaction per user, so on a re-tick (status 200: already there) it is removed and added
            // again to acknowledge this request too. Purely cosmetic, so it never blocks the deploy.
            Step.SingleStep(
              name = "Acknowledge Request",
              continueOnError = true,
              uses = Some(ActionRef(V("actions/github-script"))),
              parameters = Map(
                "script" -> Json.Str(
                  """|const params = {
                     |  owner: context.repo.owner,
                     |  repo: context.repo.repo,
                     |  comment_id: context.payload.comment.id,
                     |};
                     |const { status, data } = await github.rest.reactions.createForIssueComment({ ...params, content: '+1' });
                     |if (status === 200) {
                     |  await github.rest.reactions.deleteForIssueComment({ ...params, reaction_id: data.id });
                     |  await github.rest.reactions.createForIssueComment({ ...params, content: '+1' });
                     |}
                     |""".stripMargin
                )
              )
            ),
            Step.SingleStep(
              name = "Find CI Run of the Commit",
              id = Some("ci"),
              uses = Some(ActionRef(V("actions/github-script"))),
              env = Map("COMMENT_BODY" -> "${{ github.event.comment.body }}"),
              parameters = Map(
                "script" -> Json.Str(
                  """|const match = process.env.COMMENT_BODY.match(/<!-- preview-sha:([0-9a-f]{40}) -->/);
                     |if (!match) {
                     |  core.setFailed('Cannot find the commit sha in the preview comment.');
                     |  return;
                     |}
                     |const sha = match[1];
                     |const { data } = await github.rest.actions.listWorkflowRuns({
                     |  owner: context.repo.owner,
                     |  repo: context.repo.repo,
                     |  workflow_id: 'ci.yml',
                     |  head_sha: sha,
                     |  event: 'pull_request',
                     |  status: 'success',
                     |  per_page: 1,
                     |});
                     |if (data.workflow_runs.length === 0) {
                     |  core.setFailed(`No successful CI run found for commit ${sha}.`);
                     |  return;
                     |}
                     |core.setOutput('sha', sha);
                     |core.setOutput('run_id', data.workflow_runs[0].id);
                     |""".stripMargin
                )
              )
            ),
            Step.SingleStep(
              name = "Download Website Artifact",
              uses = Some(ActionRef(V("actions/download-artifact"))),
              parameters = Map(
                "name"         -> Json.Str("website-artifact"),
                "path"         -> Json.Str("website-artifact"),
                "run-id"       -> Json.Str("${{ steps.ci.outputs.run_id }}"),
                "github-token" -> Json.Str("${{ secrets.GITHUB_TOKEN }}")
              )
            ),
            Step.SingleStep(
              name = "Deploy Preview to Netlify",
              id = Some("netlify-deploy"),
              uses = Some(ActionRef(V("nwtgck/actions-netlify"))),
              parameters = Map(
                "publish-dir"                 -> Json.Str("./website-artifact"),
                "github-token"                -> Json.Str("${{ secrets.GITHUB_TOKEN }}"),
                "deploy-message"              -> Json.Str("PR #${{ github.event.issue.number }}"),
                "alias"                       -> Json.Str("pr-${{ github.event.issue.number }}"),
                "enable-pull-request-comment" -> Json.Bool(false),
                "enable-commit-comment"       -> Json.Bool(false)
              ),
              env = Map(
                "NETLIFY_AUTH_TOKEN" -> "${{ secrets.NETLIFY_AUTH_TOKEN }}",
                "NETLIFY_SITE_ID"    -> "${{ secrets.NETLIFY_SITE_ID }}"
              )
            ),
            Step.SingleStep(
              name = "Update Preview Comment With Deploy URL",
              uses = Some(ActionRef(V("actions/github-script"))),
              env = Map(
                "SHA"        -> "${{ steps.ci.outputs.sha }}",
                "DEPLOY_URL" -> "${{ steps.netlify-deploy.outputs.deploy-url }}"
              ),
              parameters = Map(
                "script" -> Json.Str(
                  """|const sha = process.env.SHA;
                     |const time = new Date().toISOString().replace('T', ' ').slice(0, 19) + ' UTC';
                     |const body = [
                     |  '<!-- netlify-preview-comment -->',
                     |  `<!-- preview-sha:${sha} -->`,
                     |  '### 🌐 Website preview',
                     |  '',
                     |  `🚀 Preview deployed to Netlify: ${process.env.DEPLOY_URL}`,
                     |  '',
                     |  `🕒 Deployed at: ${time}`,
                     |  `📌 Commit: [\`${sha.slice(0, 7)}\`](https://github.com/${context.repo.owner}/${context.repo.repo}/commit/${sha})`,
                     |  '',
                     |  '- [ ] **Redeploy preview** (tick to deploy this commit again)',
                     |  '',
                     |  'Previews are not deployed automatically: after a new push, tick the box again.',
                     |].join('\n');
                     |await github.rest.issues.updateComment({
                     |  owner: context.repo.owner,
                     |  repo: context.repo.repo,
                     |  comment_id: context.payload.comment.id,
                     |  body,
                     |});
                     |""".stripMargin
                )
              )
            ),
            Step.SingleStep(
              name = "Reset Checkbox on Failure",
              condition = Some(Condition.Expression("failure()")),
              uses = Some(ActionRef(V("actions/github-script"))),
              env = Map(
                "COMMENT_BODY" -> "${{ github.event.comment.body }}",
                "RUN_URL"      -> "${{ github.server_url }}/${{ github.repository }}/actions/runs/${{ github.run_id }}"
              ),
              parameters = Map(
                "script" -> Json.Str(
                  """|const body = process.env.COMMENT_BODY
                     |  .replace('- [x]', '- [ ]')
                     |  .split('\n')
                     |  .filter((l) => !l.startsWith('⚠️'))
                     |  .join('\n')
                     |  .trimEnd() + `\n\n⚠️ Preview deployment failed, see [workflow run](${process.env.RUN_URL}). Tick the box to retry.`;
                     |await github.rest.issues.updateComment({
                     |  owner: context.repo.owner,
                     |  repo: context.repo.repo,
                     |  comment_id: context.payload.comment.id,
                     |  body,
                     |});
                     |""".stripMargin
                )
              )
            )
          )
        )
      )
    )
  }

  /**
   * The task that writes `netlifyDeployPreviewWorkflow` to `deploy-preview.yml`
   * when `ciEnableDeployPreview` is true, and deletes that file otherwise.
   */
  lazy val generateNetlifyDeployPreviewWorkflowTask: Def.Initialize[Task[Unit]] =
    Def.task {
      val baseDir  = (ThisBuild / Keys.baseDirectory).value
      val enabled  = ciEnableDeployPreview.value
      val workflow = netlifyDeployPreviewWorkflow.value

      if (enabled)
        writeWorkflowFile(baseDir, workflow, "deploy-preview.yml")
      else
        IO.delete(baseDir / ".github" / "workflows" / "deploy-preview.yml")
    }

  /**
   * The `release-drafter.yml` workflow. It runs release-drafter/release-drafter
   * in the `update_release_draft` job on pushes to the branch from
   * `ciReleaseDrafterBranch` (or its fallback), with `contents: write` and
   * `pull-requests: read` permissions.
   */
  lazy val releaseDrafterWorkflow: Def.Initialize[Workflow] = Def.setting {
    // GitHub Actions' `on: push: branches:` list takes literal branch names, not runtime
    // expressions - unlike `if:` conditions elsewhere in this plugin, it cannot reference
    // `github.event.repository.default_branch`. So an explicit fallback branch name is needed
    // here, unlike e.g. `releaseOrSnapshotCondition`'s use of that expression.
    val branch: Branch = ciReleaseDrafterBranch.value.getOrElse {
      ciEnabledBranches.value.headOption.map(Branch.Named).getOrElse(Branch.Named("main"))
    }

    Workflow(
      name = "Release Drafter",
      triggers = Seq(Trigger.Push(branches = Seq(branch))),
      // Least-privilege and explicit, per decision 3 - the community norm (surveyed repos set no
      // `permissions:` block at all) relies on the repo's default token permissions, which newer
      // repos increasingly default to read-only, causing a 403 creating the draft release.
      permissions = Map("contents" -> "write", "pull-requests" -> "read"),
      jobs = Seq(
        Job(
          // Matches release-drafter's own README example and every surveyed repo's job id, so a
          // repo migrating from a hand-maintained file to this generator sees the same job name
          // in its Actions run history.
          id = "update_release_draft",
          name = "update_release_draft",
          steps = Seq(
            Step.SingleStep(
              name = "Update Release Draft",
              uses = Some(ActionRef(V("release-drafter/release-drafter"))),
              env = Map("GITHUB_TOKEN" -> "${{ secrets.GITHUB_TOKEN }}")
            )
          )
        )
      )
    )
  }

  /**
   * The release-drafter configuration built from `ciReleaseDrafterCategories`,
   * `ciReleaseDrafterExcludeLabels`, `ciReleaseDrafterAutolabeler` and
   * `ciReleaseDrafterVersionResolver`.
   */
  lazy val releaseDrafterConfig: Def.Initialize[ReleaseDrafterConfig] = Def.setting {
    ReleaseDrafterConfig(
      categories = ciReleaseDrafterCategories.value,
      excludeLabels = ciReleaseDrafterExcludeLabels.value,
      autolabeler = ciReleaseDrafterAutolabeler.value,
      versionResolver = ciReleaseDrafterVersionResolver.value
    )
  }

  /**
   * The task behind `ciEnableReleaseDrafter`: when true it writes
   * `release-drafter.yml` and, if missing, `.github/release-drafter.yml`; when
   * false it deletes `release-drafter.yml` if it starts with the autogenerated
   * header.
   */
  lazy val generateReleaseDrafterWorkflowTask: Def.Initialize[Task[Unit]] =
    Def.task {
      val baseDir  = (ThisBuild / Keys.baseDirectory).value
      val enabled  = ciEnableReleaseDrafter.value
      val workflow = releaseDrafterWorkflow.value
      val config   = releaseDrafterConfig.value

      // `release-drafter.yml` is a filename many repos already hand-maintain before adopting
      // this plugin (release-drafter's own README convention). ciEnableReleaseDrafter defaults
      // to true, so when enabled the plugin adopts (overwrites) a pre-existing hand-maintained
      // copy, exactly like ci.yml and the other generated workflows - otherwise those repos would
      // never get the generated workflow by default. When disabled, it only deletes a copy it
      // created itself, identified by the same "autogenerated" header every generated workflow
      // carries, so turning the feature off never removes a hand-written file.
      val workflowFile     = baseDir / ".github" / "workflows" / "release-drafter.yml"
      val weOwnTheWorkflow = !workflowFile.exists || IO.read(workflowFile).startsWith(AutogeneratedHeaderPrefix)

      if (enabled) {
        writeWorkflowFile(baseDir, workflow, "release-drafter.yml")

        val configFile = baseDir / ".github" / "release-drafter.yml"
        if (!configFile.exists) writeGeneratedFile(baseDir, configFile, renderReleaseDrafterConfig(config))
      } else {
        if (workflowFile.exists && weOwnTheWorkflow) IO.delete(workflowFile)
        // .github/release-drafter.yml (the config) is deliberately left alone here - see §5.2/decision 5.
      }
    }

  /**
   * The `scala-steward.yml` workflow. Its `scala-steward` job runs
   * scala-steward-org/scala-steward-action on the `schedule` of
   * `ciScalaStewardSchedule` and on a manual dispatch, with the permissions,
   * timeout and environment from `ciScalaStewardPermissions`,
   * `ciScalaStewardTimeoutMinutes` and `ciScalaStewardWorkflowEnv`.
   */
  lazy val scalaStewardWorkflow: Def.Initialize[Workflow] = Def.setting {
    val schedule = ciScalaStewardSchedule.value
    val timeout  = ciScalaStewardTimeoutMinutes.value
    val perms    = ciScalaStewardPermissions.value
    val env      = ciScalaStewardWorkflowEnv.value

    Workflow(
      name = "Scala Steward",
      triggers = Seq(Trigger.Schedule(Seq(schedule)), Trigger.WorkflowDispatch()),
      permissions = perms,
      jobs = Seq(
        Job(
          id = "scala-steward",
          name = "Scala Steward",
          jobTimeout = Some(timeout),
          steps = Seq(
            Step.SingleStep(
              name = "Scala Steward",
              uses = Some(ActionRef(V("scala-steward-org/scala-steward-action"))),
              env = env,
              parameters = Map(
                "github-app-id"              -> Json.Str("${{ secrets.SCALA_STEWARD_GITHUB_APP_ID }}"),
                "github-app-installation-id" ->
                  Json.Str("${{ secrets.SCALA_STEWARD_GITHUB_APP_INSTALLATION_ID }}"),
                "github-app-key"       -> Json.Str("${{ secrets.SCALA_STEWARD_GITHUB_APP_PRIVATE_KEY }}"),
                "github-app-auth-only" -> Json.Bool(true)
              )
            )
          )
        )
      )
    )
  }

  /**
   * The task behind `ciEnableScalaSteward`: when true it writes
   * `scala-steward.yml` and, if `ciScalaStewardConfig` renders content and
   * `.scala-steward.conf` is missing, that file; when false it deletes
   * `scala-steward.yml` if it starts with the autogenerated header.
   */
  lazy val generateScalaStewardWorkflowTask: Def.Initialize[Task[Unit]] =
    Def.task {
      val baseDir  = (ThisBuild / Keys.baseDirectory).value
      val enabled  = ciEnableScalaSteward.value
      val workflow = scalaStewardWorkflow.value
      val config   = ciScalaStewardConfig.value

      // Same rule as release-drafter.yml (see generateReleaseDrafterWorkflowTask): adopt a
      // pre-existing scala-steward.yml when enabled, but when disabled only delete a copy this
      // plugin generated itself.
      val workflowFile     = baseDir / ".github" / "workflows" / "scala-steward.yml"
      val weOwnTheWorkflow = !workflowFile.exists || IO.read(workflowFile).startsWith(AutogeneratedHeaderPrefix)

      if (enabled) {
        writeWorkflowFile(baseDir, workflow, "scala-steward.yml")

        // At the repo root, not .github/ - matching every ZIO-ecosystem repo's actual layout
        // (verified directly, not from the action's own doc default).
        val configFile = baseDir / ".scala-steward.conf"
        ScalaStewardConfig.render(config).foreach { rendered =>
          if (!configFile.exists) writeGeneratedFile(baseDir, configFile, rendered)
        }
      } else {
        if (workflowFile.exists && weOwnTheWorkflow) IO.delete(workflowFile)
        // .scala-steward.conf is deliberately left alone here, always - same as
        // .github/release-drafter.yml in generateReleaseDrafterWorkflowTask.
      }
    }

  /**
   * The task behind `ciEnableDependabot`: when true it writes
   * `.github/dependabot.yml` from `ciDependabotConfig`, adding the generated
   * workflows to the `exclude-paths` of each `github-actions` update when
   * `ciDependabotExcludeGenerated` is true; when false it deletes that file if
   * it starts with the autogenerated header.
   */
  lazy val generateDependabotConfigTask: Def.Initialize[Task[Unit]] =
    Def.task {
      val baseDir          = (ThisBuild / Keys.baseDirectory).value
      val enabled          = ciEnableDependabot.value
      val excludeGenerated = ciDependabotExcludeGenerated.value
      val configured       = ciDependabotConfig.value

      // Same ownership rule as the generated workflows: adopt a pre-existing file when enabled,
      // but when disabled only delete a copy this plugin generated itself.
      val file  = baseDir / DependabotConfigFile
      val weOwn = !file.exists || IO.read(file).startsWith(AutogeneratedHeaderPrefix)

      // Runs after every workflow has been written, so the headers identify exactly the generated ones.
      lazy val generatedWorkflows =
        (baseDir / WorkflowsDir * "*.yml")
          .get()
          .filter(f => IO.read(f).startsWith(AutogeneratedHeaderPrefix))
          .map(f => s"$WorkflowsDir/${f.getName}")
          .sorted

      val config =
        if (!excludeGenerated) configured
        else
          configured.copy(updates = configured.updates.map { u =>
            if (u.ecosystem == "github-actions") u.copy(excludePaths = u.excludePaths ++ generatedWorkflows)
            else u
          })

      if (enabled) writeGeneratedFile(baseDir, file, renderDependabotConfig(config))
      else if (file.exists && weOwn) IO.delete(file)
    }

  override lazy val buildSettings: Seq[Setting[_]] =
    Seq(
      ciWorkflowTitle        := "CI",
      ciEnabledBranches      := Seq.empty,
      ciDependencyUpdateBots := Seq(
        DependencyBot.Dependabot,
        DependencyBot.Renovate,
        DependencyBot.ScalaSteward("zio-scala-steward"),
        DependencyBot.Custom("zio-assistant[bot]")
      ),
      ciGenerateAutoApproveWorkflow := generateAutoApproveWorkflowTask.value,
      ciGenerateAutoMergeWorkflow   := generateAutoMergeWorkflowTask.value,
      ciGenerateGithubWorkflow      := Def
        .sequential(
          generateGithubWorkflowTask,
          generateAutoApproveWorkflowTask,
          generateAutoMergeWorkflowTask,
          generateRegenerateWorkflowsWorkflowTask,
          generateNetlifyDeployPreviewWorkflowTask,
          generateReleaseDrafterWorkflowTask,
          generateScalaStewardWorkflowTask,
          generateDependabotConfigTask
        )
        .value,
      ciDocsVersioningScheme  := DocsVersioning.SemanticVersioning,
      ciCheckGithubWorkflow   := checkGithubWorkflowTask.value,
      ciTargetScalaVersions   := Map.empty,
      ciTargetMinJavaVersions := Map.empty,
      ciJvmOptions            := Seq.empty,
      ciNodeOptions           := Seq.empty,
      ciWorkflowEnv           := {
        val jvmOptions  = Seq("-XX:+PrintCommandLineFlags") ++ ciJvmOptions.value
        val nodeOptions = ciNodeOptions.value

        Map("JDK_JAVA_OPTIONS" -> jvmOptions.mkString(" ")) ++
          (if (nodeOptions.nonEmpty) Map("NODE_OPTIONS" -> nodeOptions.mkString(" ")) else Map.empty)
      },
      ciWorkflowTriggers := Seq(
        Trigger.WorkflowDispatch(),
        Trigger.Release(Seq("published")),
        Trigger.Push(branches = ciEnabledBranches.value.map(Branch.Named)),
        Trigger.PullRequest(ignoredBranches = Seq(Branch.Named("gh-pages")))
      ),
      ciConcurrency              := Some(Workflow.defaultConcurrency),
      ciWorkflowPermissions      := Some(Workflow.defaultPermissions),
      ciUpdateReadmeCondition    := None,
      ciGroupSimilarTests        := false,
      ciSwapSizeGB               := 0,
      ciTargetJavaVersions       := Seq("17", "21", "25"),
      ciCheckArtifactsBuildSteps :=
        Seq(
          Step.SingleStep(
            name = "Check artifacts build process",
            run = Some(SbtCommand + " +publishLocal")
          )
        ),
      ciCheckWebsiteBuildProcess := CheckWebsiteBuildProcess.value,
      ciCheckScaladocExamples    := false,
      ciEnableDeployPreview      := false,
      ciDeployPreviewMode        := DeployPreviewMode.Automatic,
      ciEnableReleaseDrafter     := true,
      ciReleaseDrafterCategories := Seq(
        ReleaseDrafterCategory("🚀 Features", Seq("feature")),
        ReleaseDrafterCategory("🐛 Bug Fixes", Seq("bug")),
        ReleaseDrafterCategory("🧰 Maintenance", Seq("build")),
        ReleaseDrafterCategory("🌱 Dependency Updates", Seq("dependency-update"))
      ),
      ciReleaseDrafterVersionResolver  := None,
      ciReleaseDrafterAutolabeler      := Seq.empty,
      ciReleaseDrafterExcludeLabels    := Seq("skip-changelog"),
      ciReleaseDrafterBranch           := None,
      ciEnableScalaSteward             := true,
      ciScalaStewardSchedule           := "0 0 * * *",
      ciScalaStewardTimeoutMinutes     := 45,
      ciScalaStewardPermissions        := Map("contents" -> "read"),
      ciScalaStewardWorkflowEnv        := Map.empty,
      ciScalaStewardConfig             := ScalaStewardConfig(),
      ciEnableDependabot               := true,
      ciDependabotExcludeGenerated     := true,
      ciEnableRegenerateWorkflows      := true,
      ciDependabotConfig               := DependabotConfig(),
      ciCheckArtifactsCompilationSteps := Seq(
        Step.SingleStep(
          name = "Check all code compiles",
          run = Some(makePrefixJobs(ciBackgroundJobs.value) + SbtCommand + " +Test/compile")
        )
      ),
      ciCheckGithubWorkflowSteps := Seq(
        Step.SingleStep(
          name = "Check if the site workflow is up to date",
          run = Some(
            makePrefixJobs(ciBackgroundJobs.value) + SbtCommand + " ciCheckGithubWorkflow"
          )
        )
      ),
      ciBackgroundJobs          := Seq.empty,
      ciMatrixMaxParallel       := None,
      ciDefaultJavaVersion      := "17",
      ciPublishSnapshots        := true,
      ciBuildJobs               := buildJobs.value,
      ciLintJobs                := lintJobs.value,
      ciTestJobs                := testJobs.value,
      ciUpdateReadmeJobs        := updateReadmeJobs.value,
      ciReportSuccessfulJobs    := reportSuccessfulJobs.value,
      ciReleaseJobs             := releaseJobs.value,
      ciPostReleaseJobs         := postReleaseJobs.value,
      ciPullRequestApprovalJobs := Def.setting {
        // The real job ids: a build that renames a job, or adds a second test job, needs the
        // aggregate job to wait on what actually exists.
        ciLintJobs.value.map(_.id) ++ ciTestJobs.value.map(_.id) ++ ciBuildJobs.value.map(_.id)
      }.value,
      ciReleaseApprovalJobs := Seq("ci")
    )

  /**
   * The way the docs package is versioned on npm. `npmCommand` is the sbt task,
   * run as `docs/<npmCommand>`, that publishes it.
   */
  abstract class DocsVersioning(val npmCommand: String)

  /** The available docs versioning schemes. */
  object DocsVersioning {

    /** Publishes the docs with the `publishToNpm` task. */
    object SemanticVersioning extends DocsVersioning("publishToNpm")

    /** Publishes the docs with the `publishHashverToNpm` task. */
    object HashVersioning extends DocsVersioning("publishHashverToNpm")
  }

  private val WorkflowsDir = ".github/workflows"

  /** Runs git, returning its exit code and stdout lines. */
  private def git(args: String*): (Int, Seq[String]) = {
    val out  = scala.collection.mutable.ListBuffer.empty[String]
    val code = Process("git" +: args) ! ProcessLogger(out += _, _ => ())
    (code, out.toList)
  }

  /**
   * The task behind `ciCheckGithubWorkflow`. It runs
   * `ciGenerateGithubWorkflow`, then fails with an error that lists the files
   * under `.github/workflows` and `.github/dependabot.yml` that git reports as
   * changed since `HEAD` or untracked. It also fails when the directory is not
   * a git repository, or when `git diff` or `git ls-files` exits with a
   * non-zero code.
   */
  lazy val checkGithubWorkflowTask: Def.Initialize[Task[Unit]] =
    Def.task {
      val _ = ciGenerateGithubWorkflow.value

      // Regeneration has just overwritten the workflow files, so anything git still reports as
      // changed is a difference between what the build produces and what was committed.
      //
      // The comparison is deliberately scoped to the workflow directory: a whole-tree `git diff`
      // reports every unrelated edit in the working copy as workflow drift, which is a confusing
      // failure for anyone running `sbt lint` locally with work in progress.
      if (git("rev-parse", "--git-dir")._1 != 0)
        sys.error(
          "`ciCheckGithubWorkflow` needs a git repository to compare the generated workflows " +
            "against, and none was found."
        )

      // Comparing against HEAD rather than the index, so a staged-but-uncommitted edit still counts.
      // A repository with no commits yet has no HEAD; there everything is simply untracked.
      val modified =
        if (git("rev-parse", "--verify", "--quiet", "HEAD")._1 == 0)
          git("diff", "--name-only", "HEAD", "--", WorkflowsDir, DependabotConfigFile) match {
            case (0, files) => files
            case (code, _)  => sys.error(s"`git diff` exited with $code; cannot verify the workflows.")
          }
        else Seq.empty

      val untracked =
        git("ls-files", "--others", "--exclude-standard", "--", WorkflowsDir, DependabotConfigFile) match {
          case (0, files) => files
          case (code, _)  => sys.error(s"`git ls-files` exited with $code; cannot verify the workflows.")
        }

      val stale = (modified ++ untracked).map(_.trim).filter(_.nonEmpty).distinct.sorted

      if (stale.nonEmpty)
        sys.error(
          stale.mkString("These workflow files are not up to date:\n  ", "\n  ", "\n") +
            "Please run `sbt ciGenerateGithubWorkflow` and commit the result."
        )
    }

  /**
   * Returns the background jobs joined with ` & ` and followed by ` & ` (for
   * example `a & b & `), or an empty string when there are none. The result
   * goes in front of a step's sbt command.
   */
  def makePrefixJobs(backgroundJobs: Seq[String]): String =
    if (backgroundJobs.nonEmpty)
      backgroundJobs.mkString(" & ") + " & "
    else ""

  /**
   * The "Set Swap Space" step, which uses pierotofy/set-swap-space with
   * `swap-size-gb` set to `ciSwapSizeGB`.
   */
  lazy val SetSwapSpace: Def.Initialize[SingleStep] =
    Def.setting {
      val swapSizeGB = ciSwapSizeGB.value

      Step.SingleStep(
        name = "Set Swap Space",
        uses = Some(ActionRef(V("pierotofy/set-swap-space"))),
        parameters = Map("swap-size-gb" -> Json.Num(swapSizeGB))
      )
    }

  /**
   * The "Git Checkout" step, which uses actions/checkout with `fetch-depth` set
   * to 0.
   */
  lazy val Checkout: Def.Initialize[SingleStep] =
    Def.setting {
      Step.SingleStep(
        name = "Git Checkout",
        uses = Some(ActionRef(V("actions/checkout"))),
        parameters = Map("fetch-depth" -> Json.Str("0"))
      )
    }

  /**
   * The "Install libuv" step, which runs
   * `sudo apt-get update && sudo apt-get install -y libuv1-dev`. Deprecated:
   * the generated jobs no longer include it.
   */
  @deprecated(
    "No longer added to any generated job: Scala Native does not need libuv, and the apt install made CI depend on the Ubuntu mirror. Remove it from custom jobs.",
    "0.8.9"
  )
  lazy val SetupLibuv: Step.SingleStep = Step.SingleStep(
    name = "Install libuv",
    run = Some("sudo apt-get update && sudo apt-get install -y libuv1-dev")
  )

  /**
   * Returns a step, named "Setup Scala", that uses actions/setup-java to
   * install the given Java version from the `corretto` distribution, with
   * `check-latest` set to true. The default version is "17".
   */
  def SetupJava(version: String = "17"): Step.SingleStep = Step.SingleStep(
    name = "Setup Scala",
    uses = Some(ActionRef(V("actions/setup-java"))),
    parameters = Map(
      "distribution" -> Json.Str("corretto"),
      "java-version" -> Json.Str(version),
      "check-latest" -> Json.Bool(true)
    )
  )

  /** The "Setup SBT" step, which uses sbt/setup-sbt. */
  lazy val SetupSBT: Step.SingleStep = Step.SingleStep(
    name = "Setup SBT",
    uses = Some(ActionRef(V("sbt/setup-sbt")))
  )

  /** The "Cache Dependencies" step, which uses coursier/cache-action. */
  lazy val CacheDependencies: Step.SingleStep = Step.SingleStep(
    name = "Cache Dependencies",
    uses = Some(ActionRef(V("coursier/cache-action")))
  )

  /**
   * Two steps that check the website build: "Check website build process" runs
   * `sbt --no-colors docs/clean; sbt --no-colors docs/buildWebsite`, and "Check
   * docs build on a fresh ZIO website" runs
   * `sbt --no-colors docs/checkDocsOnFreshWebsite`. Both start with the
   * `ciBackgroundJobs` prefix.
   */
  lazy val CheckWebsiteBuildProcess: Def.Initialize[Seq[Step.SingleStep]] =
    Def.setting {
      val backgroundJobs = ciBackgroundJobs.value
      val prefixJobs     = makePrefixJobs(backgroundJobs)

      Seq(
        Step.SingleStep(
          name = "Check website build process",
          run = Some(prefixJobs + SbtCommand + " docs/clean; " + SbtCommand + " docs/buildWebsite")
        ),
        // A committed `website/` is built with whatever Docusaurus it pins, which can be too old
        // to reject what the ZIO website's (zio/zio) current Docusaurus rejects -- duplicate doc
        // ids, for one. This builds the same docs on a fresh scaffold, as zio/zio does, so the
        // failure shows up here instead of in a dependency-bump PR there. A no-op when there is
        // no committed website, since `docs/buildWebsite` then already builds on a fresh one.
        Step.SingleStep(
          name = "Check docs build on a fresh ZIO website",
          run = Some(prefixJobs + SbtCommand + " docs/checkDocsOnFreshWebsite")
        )
      )
    }

  // Only meaningful on a pull_request run: a push or release has no base to diff against, and the
  // artifact this feeds (`website-artifact`) is only ever consumed by a pull-request-triggered
  // deploy-preview run.
  private val isPullRequest: Condition = Condition.Expression("github.event_name == 'pull_request'")

  private val docsOrWebsiteChanged: Condition =
    Condition.Expression("steps.detect-docs-changes.outputs.changed == 'true'")

  // `website/package.json` is the exact signal `WebsitePlugin.installWebsiteTask` itself keys off
  // of to decide whether a Docusaurus site is actually scaffolded there, as opposed to a
  // `website/` directory holding nothing but `mdocOut`'s generated `website/docs` output (see its
  // comment referencing #749). `docusaurus.config.js` is checked too, so a `website/` with some
  // other, non-Docusaurus package.json isn't mistaken for an installed site.
  private val websiteInstalled: Condition =
    Condition.Expression("steps.check-website-installed.outputs.installed == 'true'")

  /**
   * The steps that prepare a Netlify deploy preview in the `build` job. On pull
   * request runs they check whether `website/package.json` and
   * `website/docusaurus.config.js` exist. When both exist, which means the
   * website is installed, they detect changes under `docs/` or `website/`,
   * upload `./website/build` as the `website-artifact` artifact when those
   * paths changed, and upload the pull request number as the `pr-metadata`
   * artifact.
   */
  lazy val NetlifyPreviewBuildSteps: Def.Initialize[Seq[Step.SingleStep]] =
    Def.setting {
      Seq(
        Step.SingleStep(
          name = "Check website is installed",
          id = Some("check-website-installed"),
          condition = Some(isPullRequest),
          run = Some(
            """|if [ -f website/package.json ] && [ -f website/docusaurus.config.js ]; then
               |  echo "installed=true" >> "$GITHUB_OUTPUT"
               |else
               |  echo "installed=false" >> "$GITHUB_OUTPUT"
               |fi
               |""".stripMargin
          )
        ),
        Step.SingleStep(
          name = "Detect docs/website changes",
          id = Some("detect-docs-changes"),
          condition = Some(isPullRequest && websiteInstalled),
          run = Some(
            // A continuation line whose first non-space character is `|` is misrendered by the
            // YAML block-scalar writer (the leading `|` and its indentation are silently
            // dropped), so the `git diff | grep` pipeline stays on one line rather than
            // continuing onto the next.
            """|BASE_SHA=${{ github.event.pull_request.base.sha }}
               |git fetch origin "$BASE_SHA" --depth=1
               |if git diff --name-only "$BASE_SHA" ${{ github.sha }} | grep -E '^(docs/|website/)' > /dev/null; then
               |  echo "changed=true" >> "$GITHUB_OUTPUT"
               |else
               |  echo "changed=false" >> "$GITHUB_OUTPUT"
               |fi
               |""".stripMargin
          )
        ),
        Step.SingleStep(
          name = "Upload website build artifact",
          condition = Some(isPullRequest && websiteInstalled && docsOrWebsiteChanged),
          uses = Some(ActionRef(V("actions/upload-artifact"))),
          parameters = Map(
            "name"           -> Json.Str("website-artifact"),
            "path"           -> Json.Str("./website/build"),
            "retention-days" -> Json.Num(30),
            "overwrite"      -> Json.Bool(true)
          )
        ),
        Step.SingleStep(
          name = "Save PR number",
          condition = Some(isPullRequest && websiteInstalled),
          run = Some("mkdir -p pr-metadata && echo ${{ github.event.pull_request.number }} > pr-metadata/pr-number.txt")
        ),
        Step.SingleStep(
          name = "Upload PR metadata",
          condition = Some(isPullRequest && websiteInstalled),
          uses = Some(ActionRef(V("actions/upload-artifact"))),
          parameters = Map(
            "name" -> Json.Str("pr-metadata"),
            "path" -> Json.Str("pr-metadata/pr-number.txt")
          )
        )
      )
    }

  // Compiles the Scaladoc examples of every module (zio-sbt-scaladoc's `scaladocCheck`) on every Scala version.
  /**
   * The "Check Scaladoc examples" step, which runs
   * `sbt --no-colors +scaladocCheck`.
   */
  val CheckScaladocExamples: Step.SingleStep = Step.SingleStep(
    name = "Check Scaladoc examples",
    run = Some(SbtCommand + " +scaladocCheck")
  )

  /**
   * The "Lint" step, which runs `sbt --no-colors lint` after the
   * `ciBackgroundJobs` prefix.
   */
  lazy val Lint: Def.Initialize[Step.SingleStep] = Def.setting {
    val backgroundJobs = ciBackgroundJobs.value
    val prefixJobs     = makePrefixJobs(backgroundJobs)

    Step.SingleStep(
      name = "Lint",
      run = Some(prefixJobs + SbtCommand + " lint")
    )
  }

  /**
   * The "Release" step, which runs `sbt --no-colors ci-release` after the
   * `ciBackgroundJobs` prefix, with the `PGP_PASSPHRASE`, `PGP_SECRET`,
   * `SONATYPE_PASSWORD` and `SONATYPE_USERNAME` environment variables taken
   * from the repository secrets of the same names.
   */
  lazy val Release: Def.Initialize[SingleStep] = Def.setting {
    val backgroundJobs = ciBackgroundJobs.value

    val prefixJobs = makePrefixJobs(backgroundJobs)

    Step.SingleStep(
      name = "Release",
      run = Some(prefixJobs + SbtCommand + " ci-release"),
      env = Map(
        "PGP_PASSPHRASE"    -> "${{ secrets.PGP_PASSPHRASE }}",
        "PGP_SECRET"        -> "${{ secrets.PGP_SECRET }}",
        "SONATYPE_PASSWORD" -> "${{ secrets.SONATYPE_PASSWORD }}",
        "SONATYPE_USERNAME" -> "${{ secrets.SONATYPE_USERNAME }}"
      )
    )
  }

  /**
   * The "Setup NodeJs" step, which uses actions/setup-node with Node.js version
   * "24.22.1" and the npm registry `https://registry.npmjs.org`.
   */
  val SetupNodeJs: Step.SingleStep = Step.SingleStep(
    name = "Setup NodeJs",
    uses = Some(ActionRef(V("actions/setup-node"))),
    parameters = Map(
      "node-version" -> Json.Str("24.22.1"),
      "registry-url" -> Json.Str("https://registry.npmjs.org")
    )
  )

  /**
   * The "Publish Docs to NPM Registry" step, which runs
   * `sbt --no-colors docs/<npmCommand>` for the `npmCommand` of
   * `ciDocsVersioningScheme`, after the `ciBackgroundJobs` prefix.
   */
  val PublishToNpmRegistry: Def.Initialize[SingleStep] = Def.setting {
    val backgroundJobs = ciBackgroundJobs.value
    val docsVersioning = autoImport.ciDocsVersioningScheme.value

    val prefixJobs = makePrefixJobs(backgroundJobs)

    Step.SingleStep(
      name = "Publish Docs to NPM Registry",
      run = Some(prefixJobs + SbtCommand + s" docs/${docsVersioning.npmCommand}")
    )
  }

  /**
   * The "Generate Readme" step, which runs
   * `sbt --no-colors docs/generateReadme` after the `ciBackgroundJobs` prefix.
   */
  val GenerateReadme: Def.Initialize[SingleStep] = Def.setting {
    val backgroundJobs = ciBackgroundJobs.value

    val prefixJobs = makePrefixJobs(backgroundJobs)

    Step.SingleStep(
      name = "Generate Readme",
      run = Some(prefixJobs + SbtCommand + " docs/generateReadme")
    )
  }

}
