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

  object autoImport {
    val ciDocsVersioningScheme: SettingKey[DocsVersioning] = settingKey[DocsVersioning]("Docs versioning style")
    val ciEnabledBranches: SettingKey[Seq[String]]         = settingKey[Seq[String]]("Publish branch for documentation")
    val ciGroupSimilarTests: SettingKey[Boolean]           =
      settingKey[Boolean]("Group similar test by their Java and Scala versions, default is false")
    val ciMatrixMaxParallel: SettingKey[Option[Int]] =
      settingKey[Option[Int]](
        "Set the maximum number of jobs that can run simultaneously when using a matrix job strategy, default is None"
      )
    val ciGenerateGithubWorkflow: TaskKey[Unit]        = taskKey[Unit]("Generate github workflow")
    val ciJvmOptions: SettingKey[Seq[String]]          = settingKey[Seq[String]]("JVM Options")
    val ciNodeOptions: SettingKey[Seq[String]]         = settingKey[Seq[String]]("NodeJS Options")
    val ciWorkflowEnv: SettingKey[Map[String, String]] =
      settingKey[Map[String, String]](
        "Environment variables set at the workflow level. Defaults to JDK_JAVA_OPTIONS (from " +
          "`ciJvmOptions`) and NODE_OPTIONS (from `ciNodeOptions`); assigning to this key replaces " +
          "that map entirely, which is how a build opts out of JDK_JAVA_OPTIONS in favour of, say, SBT_OPTS"
      )
    val ciWorkflowTriggers: SettingKey[Seq[Trigger]] =
      settingKey[Seq[Trigger]](
        "Events the generated workflow runs on. Defaults to workflow_dispatch, published releases, " +
          "pushes (to `ciEnabledBranches` if set, otherwise to every branch), and pull requests not " +
          "targeting gh-pages. Worth narrowing if a job's condition would let a manual run do " +
          "something it should not, such as release. Must not be empty: a workflow with no `on:` " +
          "block is rejected by GitHub"
      )
    val ciConcurrency: SettingKey[Option[Concurrency]] =
      settingKey[Option[Concurrency]](
        "Concurrency group for the generated workflow, or None to omit the block entirely. Defaults " +
          "to one run per branch with in-progress runs cancelled, keeping every run on the default " +
          "branch. `cancelInProgress` accepts an expression via `CancelInProgress.When(...)`, so a " +
          "workflow can cancel superseded pull request runs while letting releases finish"
      )
    val ciUpdateReadmeCondition: SettingKey[Option[Condition]] =
      settingKey[Option[Condition]]("condition to update readme")
    val ciTargetJavaVersions: SettingKey[Seq[String]] =
      settingKey[Seq[String]]("The default target Java versions for all modules, default is 17, 21, 25")
    val ciTargetMinJavaVersions: SettingKey[Map[String, String]] =
      SettingKey[Map[String, String]](
        "minimum target Java version for each module, default is an empty map which makes CI to use `ciAllTargetJavaVersions` to determine the minimum target Java version for all modules"
      )
    val ciTargetScalaVersions: SettingKey[Map[String, Seq[String]]] =
      settingKey[Map[String, Seq[String]]](
        "Scala versions used for testing each module, the default value is an empty map which omits the test job on CI"
      )
    val ciDefaultJavaVersion: SettingKey[String] =
      settingKey[String](
        "The default Java version which is used in CI, especially for releasing artifacts, defaults to 17. Note that this is just JDK version used for compilation. Artefact will be compiled with -target and -source flags specified by 'javaPlatform' setting or 'javaPlatform' parameter in 'stdSettings'"
      )
    val ciCheckGithubWorkflow: TaskKey[Unit] =
      taskKey[Unit]("Make sure if the ci.yml, auto-approve.yml, and auto-merge.yml files are up-to-date")
    val ciCheckArtifactsBuildSteps: SettingKey[Seq[Step]] =
      settingKey[Seq[Step]]("Workflow steps for checking artifact build process")
    val ciCheckWebsiteBuildProcess: SettingKey[Seq[Step]] =
      settingKey[Seq[Step]]("Workflow steps for checking website build process")
    val ciCheckArtifactsCompilationSteps: SettingKey[Seq[Step]] =
      settingKey[Seq[Step]]("Workflow steps for checking compilation of all codes")
    val ciCheckGithubWorkflowSteps: SettingKey[Seq[Step]] =
      settingKey[Seq[Step]]("Workflow steps for checking if the workflow is up to date")
    val ciPullRequestApprovalJobs: SettingKey[Seq[String]] =
      settingKey[Seq[String]]("Job IDs that need to pass before a pull request (PR) can be approved")
    val ciReleaseApprovalJobs: SettingKey[Seq[String]] =
      settingKey[Seq[String]]("Job IDs that need to pass before a new release.")
    val ciWorkflowTitle: SettingKey[String] =
      settingKey[String](
        "Name of the generated CI workflow, which also determines the file it is written to; " +
          "default is \"CI\", written to .github/workflows/ci.yml. Named `ciWorkflowTitle` rather " +
          "than `ciWorkflowName` so it does not collide with zio-sbt-website's key of that name"
      )
    val ciDependencyUpdateBots: SettingKey[Seq[DependencyBot]] =
      settingKey[Seq[DependencyBot]](
        "Bots whose PRs are auto-approved and auto-merged, e.g. `DependencyBot.Dependabot`, " +
          "`DependencyBot.Renovate`, `DependencyBot.ScalaSteward(githubAppName)`, or `DependencyBot.Custom(login)`; " +
          "default is Seq(Dependabot, Renovate, ScalaSteward(\"zio-scala-steward\")), matching zio/zio's bots"
      )
    val ciGenerateAutoApproveWorkflow: TaskKey[Unit] =
      taskKey[Unit]("Generate the github workflow that auto-approves dependency-bot PRs")
    val ciGenerateAutoMergeWorkflow: TaskKey[Unit] =
      taskKey[Unit]("Generate the github workflow that auto-merges dependency-bot PRs")
    val ciSwapSizeGB: SettingKey[Int]           = settingKey[Int]("Swap size, default is 0")
    val ciPublishSnapshots: SettingKey[Boolean] =
      settingKey[Boolean](
        "Whether the release job also runs on pushes to enabled branches, publishing SNAPSHOT artifacts via `sbt ci-release`; default is true"
      )
    val ciBackgroundJobs: SettingKey[Seq[String]] = settingKey[Seq[String]]("Background jobs")
    val ciBuildJobs: SettingKey[Seq[Job]]         = settingKey[Seq[Job]]("CI Build Jobs")
    val ciLintJobs: SettingKey[Seq[Job]]          = settingKey[Seq[Job]]("CI Lint Jobs")
    val ciTestJobs: SettingKey[Seq[Job]]          = settingKey[Seq[Job]]("CI Test Jobs")
    val ciUpdateReadmeJobs: SettingKey[Seq[Job]]  = settingKey[Seq[Job]]("CI Update Readme Jobs")
    val ciReleaseJobs: SettingKey[Seq[Job]]       = settingKey[Seq[Job]]("CI Release Jobs")
    val ciPostReleaseJobs: SettingKey[Seq[Job]]   = settingKey[Seq[Job]]("CI Post Release Jobs")

    // Neither a `val` nor a `def` for the shared `ScopeFilter` survives being referenced from two
    // separate `.all(...).value` call sites in the same `Def.setting {}` block: sbt 1.x's macro
    // rejects a `val` ("Could not find proxy for val filter"), and sbt 2.x's rejects a `def`
    // ("reference to method filter was used outside the scope where it was defined") - so the
    // filter expression is simply repeated inline at each call site instead.
    def targetScalaVersionsFor(projects: Project*): sbt.Def.Initialize[Map[String, Seq[String]]] =
      Def.setting {
        Keys.thisProject
          .all(ScopeFilter(inProjects(projects.map(p => LocalProject(p.id))*)))
          .value
          .map(_.id)
          .zip(Keys.crossScalaVersions.all(ScopeFilter(inProjects(projects.map(p => LocalProject(p.id))*))).value)
          .toMap
      }

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

  lazy val buildJobs: Def.Initialize[Seq[Job]] = Def.setting {
    val swapSizeGB                = ciSwapSizeGB.value
    val setSwapSpace              = SetSwapSpace.value
    val checkout                  = Checkout.value
    val javaVersion               = ciDefaultJavaVersion.value
    val checkAllCodeCompiles      = ciCheckArtifactsCompilationSteps.value
    val checkArtifactBuildProcess = ciCheckArtifactsBuildSteps.value
    val checkWebsiteBuildProcess  = ciCheckWebsiteBuildProcess.value

    Seq(
      Job(
        id = "build",
        name = "Build",
        continueOnError = true,
        steps = {
          (if (swapSizeGB > 0) Seq(setSwapSpace) else Seq.empty) ++
            Seq(
              checkout,
              SetupLibuv,
              SetupJava(javaVersion),
              SetupSBT,
              CacheDependencies
            ) ++ checkAllCodeCompiles ++ checkArtifactBuildProcess ++ checkWebsiteBuildProcess
        }
      )
    )
  }

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
          Seq(checkout, SetupLibuv, SetupJava(javaVersion), SetupSBT, CacheDependencies) ++ checkGithubWorkflow ++ Seq(
            lint
          )
      )
    )
  }

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
            SetupLibuv,
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
            SetupLibuv,
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
            SetupLibuv,
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
            SetupLibuv,
            SetupJava(javaVersion),
            SetupSBT,
            CacheDependencies,
            generateReadme,
            Step.SingleStep(
              name = "Commit Changes",
              run = Some("""|git config --local user.email "zio-assistant[bot]@users.noreply.github.com"
                            |git config --local user.name "ZIO Assistant"
                            |git add README.md
                            |git commit -m "Update README.md" || echo "No changes to commit"
                            |""".stripMargin)
            ),
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
                "GITHUB_TOKEN" -> "${{ secrets.GITHUB_TOKEN }}",
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
        condition = if (publishSnapshots) releaseOrSnapshotCondition else releaseCondition,
        steps = (if (swapSizeGB > 0) Seq(setSwapSpace) else Seq.empty) ++
          Seq(
            checkout,
            SetupLibuv,
            SetupJava(javaVersion),
            SetupSBT,
            CacheDependencies,
            release
          )
      )
    )
  }

  lazy val postReleaseJobs: Def.Initialize[Seq[Job]] = Def.setting {
    val swapSizeGB           = ciSwapSizeGB.value
    val setSwapSpace         = SetSwapSpace.value
    val checkout             = Checkout.value
    val javaVersion          = ciDefaultJavaVersion.value
    val publishToNpmRegistry = PublishToNpmRegistry.value

    Seq(
      Job(
        id = "release-docs",
        name = "Release Docs",
        need = Seq("release"),
        condition = releaseCondition,
        steps = (if (swapSizeGB > 0) Seq(setSwapSpace) else Seq.empty) ++
          Seq(
            Step.StepSequence(
              Seq(
                checkout,
                SetupLibuv,
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
        condition = releaseCondition,
        steps = Seq(
          checkout,
          // Unlike the auto-merge app-token fallback, this has no GITHUB_TOKEN path to fall back
          // to: the dispatch below targets zio/zio, a different repository, and GITHUB_TOKEN is
          // scoped to only the repository the workflow is running in. A minted app token works
          // only if that app is installed on zio/zio too; PAT_TOKEN remains the fallback for repos
          // relying on it (or where the app lacks that installation).
          SingleStep(
            name = "Generate Token",
            id = Some("generate-token"),
            condition = Some(Condition.Expression("secrets.APP_ID != ''")),
            uses = Some(ActionRef(V("zio/generate-github-app-token"))),
            parameters = Map(
              "app_id"          -> Json.Str("${{ secrets.APP_ID }}"),
              "app_private_key" -> Json.Str("${{ secrets.APP_PRIVATE_KEY }}")
            )
          ),
          SingleStep(
            name = "notify the main repo about the new release of docs package",
            env = Map("NOTIFY_TOKEN" -> "${{ steps.generate-token.outputs.token || secrets.PAT_TOKEN }}"),
            run = Some("""|PACKAGE_NAME=$(cat docs/package.json | grep '"name"' | awk -F'"' '{print $4}')
                          |PACKAGE_VERSION=$(npm view $PACKAGE_NAME version)
                          |curl -L \
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
                          |""".stripMargin)
          )
        )
      )
    )
  }

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

    s"""|# This file was autogenerated using `zio-sbt-ci` plugin via `sbt ciGenerateGithubWorkflow`
        |# task and should be included in the git repository. Please do not edit it manually.
        |
        |$yaml""".stripMargin
  }

  /**
   * Turns a workflow name into a file name.
   *
   * Anything that is not alphanumeric becomes a dash, so a name like
   * "Continuous Integration" yields `continuous-integration.yml` rather than a
   * file with a space in it.
   */
  private[sbt] def workflowFileName(workflowName: String): String = {
    val slug = workflowName.trim.toLowerCase.replaceAll("[^a-z0-9]+", "-").replaceAll("(^-)|(-$)", "")
    s"${if (slug.isEmpty) "ci" else slug}.yml"
  }

  def writeWorkflowFile(baseDir: File, workflow: Workflow, fileName: String): Unit =
    IO.write(baseDir / ".github" / "workflows" / fileName, renderWorkflow(workflow))

  @deprecated("Use the overload taking the build's base directory", "0.6.4")
  def writeWorkflowFile(workflow: Workflow, fileName: String): Unit =
    writeWorkflowFile(file("."), workflow, fileName)

  lazy val generateGithubWorkflowTask: Def.Initialize[Task[Unit]] =
    Def.task {
      val workflowName     = ciWorkflowTitle.value
      val buildJobs        = ciBuildJobs.value
      val lintJobs         = ciLintJobs.value
      val testJobs         = ciTestJobs.value
      val reportSuccessful = reportSuccessfulJobs.value
      val updateReadmeJobs = ciUpdateReadmeJobs.value
      val releaseJobs      = ciReleaseJobs.value
      val postReleaseJobs  = ciPostReleaseJobs.value
      val workflowEnv      = ciWorkflowEnv.value
      val triggers         = ciWorkflowTriggers.value
      val concurrency      = ciConcurrency.value

      // An empty trigger list encodes as no `on:` block at all, and GitHub rejects a workflow
      // without one. Better to say so here than to emit a file that silently never runs.
      if (triggers.isEmpty)
        sys.error(
          "`ciWorkflowTriggers` is empty, which would generate a workflow with no `on:` block. " +
            "GitHub requires at least one trigger."
        )

      val workflow = Workflow(
        name = workflowName,
        env = workflowEnv,
        concurrency = concurrency,
        triggers = triggers,
        jobs =
          buildJobs ++ lintJobs ++ testJobs ++ updateReadmeJobs ++ reportSuccessful ++ releaseJobs ++ postReleaseJobs
      )

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

  lazy val autoMergeWorkflow: Def.Initialize[Workflow] = Def.setting {
    val bots = ciDependencyUpdateBots.value.map(_.login)

    Workflow(
      name = "Auto-merge bot dependency PRs",
      triggers = dependencyBotPRTriggers,
      // `contents`/`pull-requests: write` are for the GITHUB_TOKEN fallback path (see
      // `mergeTokenEnv`); the app-token path doesn't need this block at all, the app's own
      // installation permissions govern what its token can do.
      permissions = Map("contents" -> "write", "pull-requests" -> "write"),
      jobs = Seq(
        Job(
          id = "auto-merge",
          name = "auto-merge",
          condition = Some(dependencyBotPRCondition(bots)),
          steps = Seq(
            Step.SingleStep(
              name = "Generate Token",
              id = Some("generate-token"),
              // Skipped (rather than left to fail on empty secrets) on repos that never
              // configured APP_ID/APP_PRIVATE_KEY, so no action run is wasted on the doomed API
              // calls that would otherwise make.
              condition = Some(Condition.Expression("secrets.APP_ID != ''")),
              uses = Some(ActionRef(V("zio/generate-github-app-token"))),
              parameters = Map(
                "app_id"          -> Json.Str("${{ secrets.APP_ID }}"),
                "app_private_key" -> Json.Str("${{ secrets.APP_PRIVATE_KEY }}")
              )
            ),
            Step.SingleStep(
              name = "Enable auto-merge for bot PR",
              condition = Some(Condition.Expression("github.event_name == 'pull_request_target'")),
              run = Some("gh pr merge --auto --squash ${{ github.event.number }}"),
              env = mergeTokenEnv
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

  lazy val generateAutoApproveWorkflowTask: Def.Initialize[Task[Unit]] =
    Def.task(writeWorkflowFile((ThisBuild / Keys.baseDirectory).value, autoApproveWorkflow.value, "auto-approve.yml"))

  lazy val generateAutoMergeWorkflowTask: Def.Initialize[Task[Unit]] =
    Def.task(writeWorkflowFile((ThisBuild / Keys.baseDirectory).value, autoMergeWorkflow.value, "auto-merge.yml"))

  override lazy val buildSettings: Seq[Setting[_]] =
    Seq(
      ciWorkflowTitle        := "CI",
      ciEnabledBranches      := Seq.empty,
      ciDependencyUpdateBots := Seq(
        DependencyBot.Dependabot,
        DependencyBot.Renovate,
        DependencyBot.ScalaSteward("zio-scala-steward")
      ),
      ciGenerateAutoApproveWorkflow := generateAutoApproveWorkflowTask.value,
      ciGenerateAutoMergeWorkflow   := generateAutoMergeWorkflowTask.value,
      ciGenerateGithubWorkflow      := Def
        .sequential(
          generateGithubWorkflowTask,
          generateAutoApproveWorkflowTask,
          generateAutoMergeWorkflowTask
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
      ciCheckWebsiteBuildProcess       := CheckWebsiteBuildProcess.value,
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
      ciReleaseJobs             := releaseJobs.value,
      ciPostReleaseJobs         := postReleaseJobs.value,
      ciPullRequestApprovalJobs := Def.setting {
        // The real job ids: a build that renames a job, or adds a second test job, needs the
        // aggregate job to wait on what actually exists.
        ciLintJobs.value.map(_.id) ++ ciTestJobs.value.map(_.id) ++ ciBuildJobs.value.map(_.id)
      }.value,
      ciReleaseApprovalJobs := Seq("ci")
    )

  abstract class DocsVersioning(val npmCommand: String)
  object DocsVersioning {
    object SemanticVersioning extends DocsVersioning("publishToNpm")
    object HashVersioning     extends DocsVersioning("publishHashverToNpm")
  }

  private val WorkflowsDir = ".github/workflows"

  /** Runs git, returning its exit code and stdout lines. */
  private def git(args: String*): (Int, Seq[String]) = {
    val out  = scala.collection.mutable.ListBuffer.empty[String]
    val code = Process("git" +: args) ! ProcessLogger(out += _, _ => ())
    (code, out.toList)
  }

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
          git("diff", "--name-only", "HEAD", "--", WorkflowsDir) match {
            case (0, files) => files
            case (code, _)  => sys.error(s"`git diff` exited with $code; cannot verify the workflows.")
          }
        else Seq.empty

      val untracked = git("ls-files", "--others", "--exclude-standard", "--", WorkflowsDir) match {
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

  def makePrefixJobs(backgroundJobs: Seq[String]): String =
    if (backgroundJobs.nonEmpty)
      backgroundJobs.mkString(" & ") + " & "
    else ""

  lazy val SetSwapSpace: Def.Initialize[SingleStep] =
    Def.setting {
      val swapSizeGB = ciSwapSizeGB.value

      Step.SingleStep(
        name = "Set Swap Space",
        uses = Some(ActionRef(V("pierotofy/set-swap-space"))),
        parameters = Map("swap-size-gb" -> Json.Num(swapSizeGB))
      )
    }

  lazy val Checkout: Def.Initialize[SingleStep] =
    Def.setting {
      Step.SingleStep(
        name = "Git Checkout",
        uses = Some(ActionRef(V("actions/checkout"))),
        parameters = Map("fetch-depth" -> Json.Str("0"))
      )
    }

  lazy val SetupLibuv: Step.SingleStep = Step.SingleStep(
    name = "Install libuv",
    run = Some("sudo apt-get update && sudo apt-get install -y libuv1-dev")
  )

  def SetupJava(version: String = "17"): Step.SingleStep = Step.SingleStep(
    name = "Setup Scala",
    uses = Some(ActionRef(V("actions/setup-java"))),
    parameters = Map(
      "distribution" -> Json.Str("corretto"),
      "java-version" -> Json.Str(version),
      "check-latest" -> Json.Bool(true)
    )
  )

  lazy val SetupSBT: Step.SingleStep = Step.SingleStep(
    name = "Setup SBT",
    uses = Some(ActionRef(V("sbt/setup-sbt")))
  )

  lazy val CacheDependencies: Step.SingleStep = Step.SingleStep(
    name = "Cache Dependencies",
    uses = Some(ActionRef(V("coursier/cache-action")))
  )

  lazy val CheckWebsiteBuildProcess: Def.Initialize[Seq[Step.SingleStep]] =
    Def.setting {
      val backgroundJobs = ciBackgroundJobs.value
      val prefixJobs     = makePrefixJobs(backgroundJobs)

      Seq(
        Step.SingleStep(
          name = "Check website build process",
          run = Some(prefixJobs + SbtCommand + " docs/clean; " + SbtCommand + " docs/buildWebsite")
        )
      )
    }

  lazy val Lint: Def.Initialize[Step.SingleStep] = Def.setting {
    val backgroundJobs = ciBackgroundJobs.value
    val prefixJobs     = makePrefixJobs(backgroundJobs)

    Step.SingleStep(
      name = "Lint",
      run = Some(prefixJobs + SbtCommand + " lint")
    )
  }

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

  val SetupNodeJs: Step.SingleStep = Step.SingleStep(
    name = "Setup NodeJs",
    uses = Some(ActionRef(V("actions/setup-node"))),
    parameters = Map(
      "node-version" -> Json.Str("24.12.0"),
      "registry-url" -> Json.Str("https://registry.npmjs.org")
    )
  )

  val PublishToNpmRegistry: Def.Initialize[SingleStep] = Def.setting {
    val backgroundJobs = ciBackgroundJobs.value
    val docsVersioning = autoImport.ciDocsVersioningScheme.value

    val prefixJobs = makePrefixJobs(backgroundJobs)

    Step.SingleStep(
      name = "Publish Docs to NPM Registry",
      run = Some(prefixJobs + SbtCommand + s" docs/${docsVersioning.npmCommand}")
    )
  }

  val GenerateReadme: Def.Initialize[SingleStep] = Def.setting {
    val backgroundJobs = ciBackgroundJobs.value

    val prefixJobs = makePrefixJobs(backgroundJobs)

    Step.SingleStep(
      name = "Generate Readme",
      run = Some(prefixJobs + SbtCommand + " docs/generateReadme")
    )
  }

}
