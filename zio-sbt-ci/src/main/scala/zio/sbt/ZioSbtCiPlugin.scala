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
import scala.language.experimental.macros
import scala.sys.process._

import sbt.{Def, io => _, _}

import zio.json._
import zio.json.ast.Json
import zio.json.yaml._
import zio.sbt.githubactions.Step.SingleStep
import zio.sbt.githubactions._

object ZioSbtCiPlugin extends AutoPlugin {
  override def requires = plugins.CorePlugin
  override def trigger  = allRequirements

  object autoImport {
    val ciDocsVersioningScheme: SettingKey[DocsVersioning] = settingKey[DocsVersioning]("Docs versioning style")
    val ciEnabledBranches: SettingKey[Seq[String]]         = settingKey[Seq[String]]("Publish branch for documentation")
    val ciGroupSimilarTests: SettingKey[Boolean]           =
      settingKey[Boolean]("Group similar test by their Java and Scala versions, default is false")
    val ciMatrixMaxParallel: SettingKey[Option[Int]] =
      settingKey[Option[Int]](
        "Set the maximum number of jobs that can run simultaneously when using a matrix job strategy, default is None"
      )
    val ciGenerateGithubWorkflow: TaskKey[Unit]                = taskKey[Unit]("Generate github workflow")
    val ciJvmOptions: SettingKey[Seq[String]]                  = settingKey[Seq[String]]("JVM Options")
    val ciNodeOptions: SettingKey[Seq[String]]                 = settingKey[Seq[String]]("NodeJS Options")
    val ciUpdateReadmeCondition: SettingKey[Option[Condition]] =
      settingKey[Option[Condition]]("condition to update readme")
    val ciTargetJavaVersions: SettingKey[Seq[String]] =
      settingKey[Seq[String]]("The default target Java versions for all modules, default is 11, 17, 21")
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
    val ciCacheSbtBuild: SettingKey[Boolean] =
      settingKey[Boolean](
        "Enable caching of SBT build artifacts (target directories) to speed up CI builds across workflow runs, default is true"
      )
    val ciCheckGithubWorkflow: TaskKey[Unit]              = taskKey[Unit]("Make sure if the ci.yml file is up-to-date")
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
    val ciWorkflowName: SettingKey[String]        = settingKey[String]("CI Workflow Name")
    val ciSwapSizeGB: SettingKey[Int]             = settingKey[Int]("Swap size, default is 0")
    val ciBackgroundJobs: SettingKey[Seq[String]] = settingKey[Seq[String]]("Background jobs")
    val ciBuildJobs: SettingKey[Seq[Job]]         = settingKey[Seq[Job]]("CI Build Jobs")
    val ciLintJobs: SettingKey[Seq[Job]]          = settingKey[Seq[Job]]("CI Lint Jobs")
    val ciTestJobs: SettingKey[Seq[Job]]          = settingKey[Seq[Job]]("CI Test Jobs")
    val ciUpdateReadmeJobs: SettingKey[Seq[Job]]  = settingKey[Seq[Job]]("CI Update Readme Jobs")
    val ciReleaseJobs: SettingKey[Seq[Job]]       = settingKey[Seq[Job]]("CI Release Jobs")
    val ciPostReleaseJobs: SettingKey[Seq[Job]]   = settingKey[Seq[Job]]("CI Post Release Jobs")

    def targetScalaVersionsFor(projects: Project*): sbt.Def.Initialize[Map[String, Seq[String]]] =
      macro CiTargetMap.macroMakeTargetScalaMapImpl

    def minTargetJavaVersionsFor(projects: Project*): sbt.Def.Initialize[Map[String, String]] =
      macro CiTargetMap.macroMakeJavaVersionMapImpl
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
    val cacheSbtBuild             = ciCacheSbtBuild.value

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
            ) ++
            (if (cacheSbtBuild) Seq(CacheSbtBuild(javaVersion)) else Seq.empty) ++
            checkAllCodeCompiles ++ checkArtifactBuildProcess ++ checkWebsiteBuildProcess
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
    val cacheSbtBuild       = ciCacheSbtBuild.value

    Seq(
      Job(
        id = "lint",
        name = "Lint",
        steps = (if (swapSizeGB > 0) Seq(setSwapSpace) else Seq.empty) ++
          Seq(checkout, SetupLibuv, SetupJava(javaVersion), SetupSBT, CacheDependencies) ++
          (if (cacheSbtBuild) Seq(CacheSbtBuild(javaVersion)) else Seq.empty) ++
          checkGithubWorkflow ++ Seq(lint)
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
    val cacheSbtBuild      = ciCacheSbtBuild.value

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
              "scala" -> scalaVersionMatrix.values.flatten.toSet.toList
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
          ) ++
            (if (cacheSbtBuild) Seq(CacheSbtBuild("${{ matrix.java }}")) else Seq.empty) ++
            (if (javaPlatformMatrix.values.toSet.isEmpty) {
               scalaVersionMatrix.values.toSeq.flatten.distinct.map { scalaVersion: String =>
                 Step.SingleStep(
                   name = "Test",
                   condition = Some(Condition.Expression(s"matrix.scala == '$scalaVersion'")),
                   run = Some(
                     prefixJobs + "sbt ++${{ matrix.scala }}" + makeTests(
                       scalaVersion
                     )
                   )
                 )
               }
             } else {
               (for {
                 javaPlatform: String <- Set("11", "17", "21")
                 scalaVersion: String <- scalaVersionMatrix.values.toSeq.flatten.toSet
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
                         prefixJobs + "sbt ++${{ matrix.scala }}" ++ s" ${projects.map(_ + "/test ").mkString(" ")}"
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
            checkout
          ) ++
          (if (cacheSbtBuild) Seq(CacheSbtBuild("${{ matrix.java }}")) else Seq.empty) ++
          Seq(
            if (javaPlatformMatrix.values.toSet.isEmpty) {
              Step.SingleStep(
                name = "Test",
                run = Some(prefixJobs + "sbt ${{ matrix.scala-project }}/test")
              )
            } else {
              Step.StepSequence(
                Seq(
                  Step.SingleStep(
                    name = "Java 11 Tests",
                    condition = Some(Condition.Expression("matrix.java == '11'")),
                    run = Some(
                      prefixJobs + "sbt ${{ matrix.scala-project-java11 }}/test"
                    )
                  ),
                  Step.SingleStep(
                    name = "Java 17 Tests",
                    condition = Some(Condition.Expression("matrix.java == '17'")),
                    run = Some(
                      prefixJobs + "sbt ${{ matrix.scala-project-java17 }}/test"
                    )
                  ),
                  Step.SingleStep(
                    name = "Java 21 Tests",
                    condition = Some(Condition.Expression("matrix.java == '21'")),
                    run = Some(
                      prefixJobs + "sbt ${{ matrix.scala-project-java21 }}/test"
                    )
                  )
                )
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
            checkout
          ) ++
          (if (cacheSbtBuild) Seq(CacheSbtBuild("${{ matrix.java }}")) else Seq.empty) ++
          Seq(
            Step.SingleStep(
              name = "Test",
              run = Some(prefixJobs + "sbt +test")
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
        steps = Seq(
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
    val cacheSbtBuild         = ciCacheSbtBuild.value

    Seq(
      Job(
        id = "update-readme",
        name = "Update README",
        condition = updateReadmeCondition orElse Some(
          Condition.Expression("github.ref == format('refs/heads/{0}', github.event.repository.default_branch)")
        ),
        steps = (if (swapSizeGB > 0) Seq(setSwapSpace) else Seq.empty) ++
          Seq(
            checkout,
            SetupLibuv,
            SetupJava(javaVersion),
            SetupSBT,
            CacheDependencies
          ) ++
          (if (cacheSbtBuild) Seq(CacheSbtBuild(javaVersion)) else Seq.empty) ++
          Seq(
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
                "delete-branch"  -> Json.Bool(true),
                "body"           ->
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

  lazy val releaseJobs: Def.Initialize[Seq[Job]] = Def.setting {
    val swapSizeGB    = ciSwapSizeGB.value
    val setSwapSpace  = SetSwapSpace.value
    val checkout      = Checkout.value
    val javaVersion   = ciDefaultJavaVersion.value
    val release       = Release.value
    val jobs          = ciReleaseApprovalJobs.value
    val cacheSbtBuild = ciCacheSbtBuild.value

    Seq(
      Job(
        id = "release",
        name = "Release",
        need = jobs,
        condition = Some(Condition.Expression("github.event_name != 'pull_request'")),
        steps = (if (swapSizeGB > 0) Seq(setSwapSpace) else Seq.empty) ++
          Seq(
            checkout,
            SetupLibuv,
            SetupJava(javaVersion),
            SetupSBT,
            CacheDependencies
          ) ++
          (if (cacheSbtBuild) Seq(CacheSbtBuild(javaVersion)) else Seq.empty) ++
          Seq(release)
      )
    )
  }

  lazy val postReleaseJobs: Def.Initialize[Seq[Job]] = Def.setting {
    val swapSizeGB           = ciSwapSizeGB.value
    val setSwapSpace         = SetSwapSpace.value
    val checkout             = Checkout.value
    val javaVersion          = ciDefaultJavaVersion.value
    val publishToNpmRegistry = PublishToNpmRegistry.value
    val cacheSbtBuild        = ciCacheSbtBuild.value

    Seq(
      Job(
        id = "release-docs",
        name = "Release Docs",
        need = Seq("release"),
        condition = Some(
          Condition.Expression("github.event_name == 'release'") &&
            Condition.Expression("github.event.action == 'published'") || Condition.Expression(
              "github.event_name == 'workflow_dispatch'"
            )
        ),
        steps = (if (swapSizeGB > 0) Seq(setSwapSpace) else Seq.empty) ++
          Seq(
            Step.StepSequence(
              Seq(
                checkout,
                SetupLibuv,
                SetupJava(javaVersion),
                SetupSBT,
                CacheDependencies
              ) ++
                (if (cacheSbtBuild) Seq(CacheSbtBuild(javaVersion)) else Seq.empty) ++
                Seq(
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
        condition = Some(
          Condition.Expression("github.event_name == 'release'") &&
            Condition.Expression("github.event.action == 'published'")
        ),
        steps = Seq(
          checkout,
          SingleStep(
            name = "notify the main repo about the new release of docs package",
            run = Some("""|PACKAGE_NAME=$(cat docs/package.json | grep '"name"' | awk -F'"' '{print $4}')
                          |PACKAGE_VERSION=$(npm view $PACKAGE_NAME version)
                          |curl -L \
                          |  -X POST \
                          |  -H "Accept: application/vnd.github+json" \
                          |  -H "Authorization: token ${{ secrets.PAT_TOKEN }}"\
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

  lazy val generateGithubWorkflowTask: Def.Initialize[Task[Unit]] =
    Def.task {
      val workflowName     = ciWorkflowName.value
      val enabledBranches  = ciEnabledBranches.value
      val buildJobs        = ciBuildJobs.value
      val lintJobs         = ciLintJobs.value
      val testJobs         = ciTestJobs.value
      val reportSuccessful = reportSuccessfulJobs.value
      val updateReadmeJobs = ciUpdateReadmeJobs.value
      val releaseJobs      = ciReleaseJobs.value
      val postReleaseJobs  = ciPostReleaseJobs.value
      val jvmOptions       = Seq("-XX:+PrintCommandLineFlags") ++ ciJvmOptions.value
      val nodeOptions      = ciNodeOptions.value

      val jvmMap = Map(
        "JDK_JAVA_OPTIONS" -> jvmOptions.mkString(" ")
      )
      val nodeMap: Map[String, String] =
        if (nodeOptions.nonEmpty) Map("NODE_OPTIONS" -> nodeOptions.mkString(" ")) else Map.empty

      val workflowJson = Workflow(
        name = workflowName,
        env = jvmMap ++ nodeMap,
        triggers = Seq(
          Trigger.WorkflowDispatch(),
          Trigger.Release(Seq("published")),
          Trigger.Push(branches = enabledBranches.map(Branch.Named)),
          Trigger.PullRequest(ignoredBranches = Seq(Branch.Named("gh-pages")))
        ),
        jobs =
          buildJobs ++ lintJobs ++ testJobs ++ updateReadmeJobs ++ reportSuccessful ++ releaseJobs ++ postReleaseJobs
      ).toJsonAST.getOrElse(Json.Null)

      val workflow = workflowJson.toYaml(
        YamlOptions.default.copy(
          dropNulls = true,
          sequenceIndentation = 0,
          maxScalarWidth = None
        )
      ) match {
        case Right(yaml) => yaml
        case Left(err)   => throw new Exception(s"Failed to convert workflow to YAML: $err")
      }

      val template =
        s"""|# This file was autogenerated using `zio-sbt-ci` plugin via `sbt ciGenerateGithubWorkflow` 
            |# task and should be included in the git repository. Please do not edit it manually.
            |
            |$workflow""".stripMargin

      IO.write(new File(s".github/workflows/${ciWorkflowName.value.toLowerCase}.yml"), template)
    }

  override lazy val buildSettings: Seq[Setting[_]] =
    Seq(
      ciWorkflowName             := "CI",
      ciEnabledBranches          := Seq.empty,
      ciGenerateGithubWorkflow   := generateGithubWorkflowTask.value,
      ciDocsVersioningScheme     := DocsVersioning.SemanticVersioning,
      ciCheckGithubWorkflow      := checkGithubWorkflowTask.value,
      ciTargetScalaVersions      := Map.empty,
      ciTargetMinJavaVersions    := Map.empty,
      ciJvmOptions               := Seq.empty,
      ciNodeOptions              := Seq.empty,
      ciUpdateReadmeCondition    := None,
      ciGroupSimilarTests        := false,
      ciSwapSizeGB               := 0,
      ciCacheSbtBuild            := true,
      ciTargetJavaVersions       := Seq("11", "17", "21"),
      ciCheckArtifactsBuildSteps :=
        Seq(
          Step.SingleStep(
            name = "Check artifacts build process",
            run = Some("sbt +publishLocal")
          )
        ),
      ciCheckWebsiteBuildProcess       := CheckWebsiteBuildProcess.value,
      ciCheckArtifactsCompilationSteps := Seq(
        Step.SingleStep(
          name = "Check all code compiles",
          run = Some(makePrefixJobs(ciBackgroundJobs.value) + "sbt +Test/compile")
        )
      ),
      ciCheckGithubWorkflowSteps := Seq(
        Step.SingleStep(
          name = "Check if the site workflow is up to date",
          run = Some(
            makePrefixJobs(ciBackgroundJobs.value) + "sbt ciCheckGithubWorkflow"
          )
        )
      ),
      ciBackgroundJobs          := Seq.empty,
      ciMatrixMaxParallel       := None,
      ciDefaultJavaVersion      := "17",
      ciBuildJobs               := buildJobs.value,
      ciLintJobs                := lintJobs.value,
      ciTestJobs                := testJobs.value,
      ciUpdateReadmeJobs        := updateReadmeJobs.value,
      ciReleaseJobs             := releaseJobs.value,
      ciPostReleaseJobs         := postReleaseJobs.value,
      ciPullRequestApprovalJobs := Def.setting {
        val test = ciTestJobs.value.map(_ => "test")
        Seq("lint") ++ test ++ Seq("build")
      }.value,
      ciReleaseApprovalJobs := Seq("ci")
    )

  abstract class DocsVersioning(val npmCommand: String)
  object DocsVersioning {
    object SemanticVersioning extends DocsVersioning("publishToNpm")
    object HashVersioning     extends DocsVersioning("publishHashverToNpm")
  }

  lazy val checkGithubWorkflowTask: Def.Initialize[Task[Unit]] =
    Def.task {
      val _ = ciGenerateGithubWorkflow.value

      if ("git diff --exit-code".! == 1) {
        sys.error(
          "The ci.yml workflow is not up-to-date!\n" +
            "Please run `sbt ciGenerateGithubWorkflow` and commit new changes."
        )
      }
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

  /**
   * Creates a step to cache SBT build artifacts (target directories). This
   * caches compiled classes across workflow runs to speed up CI builds.
   */
  def CacheSbtBuild(javaVersion: String): Step.SingleStep = Step.SingleStep(
    name = "Cache SBT Build",
    uses = Some(ActionRef(V("actions/cache"))),
    parameters = Map(
      "path" -> Json.Str(
        """|target
           |project/target
           |project/project/target
           |**/target""".stripMargin
      ),
      "key" -> Json.Str(
        s"sbt-build-$${{ runner.os }}-jdk$javaVersion-$${{ hashFiles('**/*.sbt', 'project/build.properties', 'project/*.scala', 'project/*.sbt') }}-$${{ hashFiles('**/*.scala') }}"
      ),
      "restore-keys" -> Json.Str(
        s"""|sbt-build-$${{ runner.os }}-jdk$javaVersion-$${{ hashFiles('**/*.sbt', 'project/build.properties', 'project/*.scala', 'project/*.sbt') }}-
            |sbt-build-$${{ runner.os }}-jdk$javaVersion-""".stripMargin
      )
    )
  )

  lazy val CheckWebsiteBuildProcess: Def.Initialize[Seq[Step.SingleStep]] =
    Def.setting {
      val backgroundJobs = ciBackgroundJobs.value
      val prefixJobs     = makePrefixJobs(backgroundJobs)

      Seq(
        Step.SingleStep(
          name = "Check website build process",
          run = Some(prefixJobs + "sbt docs/clean; sbt docs/buildWebsite")
        )
      )
    }

  lazy val Lint: Def.Initialize[Step.SingleStep] = Def.setting {
    val backgroundJobs = ciBackgroundJobs.value
    val prefixJobs     = makePrefixJobs(backgroundJobs)

    Step.SingleStep(
      name = "Lint",
      run = Some(prefixJobs + "sbt lint")
    )
  }

  lazy val Release: Def.Initialize[SingleStep] = Def.setting {
    val backgroundJobs = ciBackgroundJobs.value

    val prefixJobs = makePrefixJobs(backgroundJobs)

    Step.SingleStep(
      name = "Release",
      run = Some(prefixJobs + "sbt ci-release"),
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
      run = Some(prefixJobs + s"sbt docs/${docsVersioning.npmCommand}")
    )
  }

  val GenerateReadme: Def.Initialize[SingleStep] = Def.setting {
    val backgroundJobs = ciBackgroundJobs.value

    val prefixJobs = makePrefixJobs(backgroundJobs)

    Step.SingleStep(
      name = "Generate Readme",
      run = Some(prefixJobs + "sbt docs/generateReadme")
    )
  }

  val CheckReadme: Def.Initialize[SingleStep] = Def.setting {
    val backgroundJobs = ciBackgroundJobs.value

    val prefixJobs = makePrefixJobs(backgroundJobs)

    Step.SingleStep(
      name = "Check if the README file is up to date",
      run = Some(prefixJobs + "sbt docs/checkReadme")
    )
  }

}
