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
import scala.annotation.nowarn
import scala.sys.process._

import io.circe._
import io.circe.syntax._
import io.circe.yaml.Printer.{LineBreak, YamlVersion}
import sbt.{Def, io => _, _}

import zio.sbt.githubactions.Step.SingleStep
import zio.sbt.githubactions.{Job, Step, _}

object ZioSbtCiPlugin extends AutoPlugin {

  override def requires: Plugins =
    super.requires && ZioSbtEcosystemPlugin

  object autoImport {
    val ciDocsVersioning: SettingKey[DocsVersioning] = settingKey[DocsVersioning]("Docs versioning style")
    val ciEnabledBranches: SettingKey[Seq[String]]   = settingKey[Seq[String]]("Publish branch for documentation")
    val ciGroupSimilarTests: SettingKey[Boolean] =
      settingKey[Boolean]("Group similar test by their Java and Scala versions, default is false")
    val ciMatrixMaxParallel: SettingKey[Option[Int]] =
      settingKey[Option[Int]](
        "Set the maximum number of jobs that can run simultaneously when using a matrix job strategy, default is None"
      )
    val ciGenerateGithubWorkflow: TaskKey[Unit] = taskKey[Unit]("Generate github workflow")
    val ciJvmOptions: SettingKey[Seq[String]]   = settingKey[Seq[String]]("JVM Options")
    val ciUpdateReadmeCondition: SettingKey[Option[Condition]] =
      settingKey[Option[Condition]]("condition to update readme")
    val ciTargetJavaVersions: SettingKey[Map[String, String]] =
      SettingKey[Map[String, String]](
        "defines a map of projects to the targeted Java versions for those projects in the CI jobs, default is 8 for all projects."
      )
    val ciTargetScalaVersions: SettingKey[Map[String, Seq[String]]] =
      settingKey[Map[String, Seq[String]]](
        "Defines a map of projects to the targeted Scala versions for those projects in the CI jobs."
      )
    val ciDefaultTargetJavaVersion: SettingKey[String] =
      settingKey[String](
        "The default Java version which is used in CI, especially for releasing artifacts, defaults to 8"
      )
    val ciDefaultTargetJavaVersions: SettingKey[Seq[String]] =
      settingKey[Seq[String]]("The default target java versions for all sub projects, default is 8, 11, 17")
    val ciCheckGithubWorkflow: TaskKey[Unit] = taskKey[Unit]("Make sure if the ci.yml file is up-to-date")
    val ciCheckArtifactsBuildSteps: SettingKey[Seq[Step]] =
      settingKey[Seq[Step]]("Workflow steps for checking artifact build process")
    val ciCheckArtifactsCompilationSteps: SettingKey[Seq[Step]] =
      settingKey[Seq[Step]]("Workflow steps for checking compilation of all codes")
    val ciCheckGithubWorkflowSteps: SettingKey[Seq[Step]] =
      settingKey[Seq[Step]]("Workflow steps for checking if the workflow is up to date")
    val ciPullRequestApprovalJobs: SettingKey[Seq[String]] =
      settingKey[Seq[String]]("Job IDs that need to pass before a pull request (PR) can be approved")
    val ciWorkflowName: SettingKey[String]        = settingKey[String]("CI Workflow Name")
    val ciExtraTestSteps: SettingKey[Seq[Step]]   = settingKey[Seq[Step]]("Extra test steps")
    val ciSwapSizeGB: SettingKey[Int]             = settingKey[Int]("Swap size, default is 0")
    val ciBackgroundJobs: SettingKey[Seq[String]] = settingKey[Seq[String]]("Background jobs")
    val ciBuildJobs: SettingKey[Seq[Job]]         = settingKey[Seq[Job]]("CI Build Jobs")
    val ciLintJobs: SettingKey[Seq[Job]]          = settingKey[Seq[Job]]("CI Lint Jobs")
    val ciTestJobs: SettingKey[Seq[Job]]          = settingKey[Seq[Job]]("CI Test Jobs")
    val ciReleaseJobs: SettingKey[Seq[Job]]       = settingKey[Seq[Job]]("CI Release Jobs")
    val ciPostReleaseJobs: SettingKey[Seq[Job]]   = settingKey[Seq[Job]]("CI Post Release Jobs")
  }

  import autoImport.*

  lazy val buildJobs: Def.Initialize[Seq[Job]] = Def.setting {
    val swapSizeGB                = ciSwapSizeGB.value
    val setSwapSpace              = SetSwapSpace.value
    val checkout                  = Checkout.value
    val javaVersion               = ciDefaultTargetJavaVersion.value
    val checkAllCodeCompiles      = ciCheckArtifactsCompilationSteps.value
    val checkArtifactBuildProcess = ciCheckArtifactsBuildSteps.value
    val checkWebsiteBuildProcess  = CheckWebsiteBuildProcess.value

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
              CacheDependencies
            ) ++ checkAllCodeCompiles ++ checkArtifactBuildProcess ++ Seq(checkWebsiteBuildProcess)
        }
      )
    )
  }

  lazy val lintJobs: Def.Initialize[Seq[Job]] = Def.setting {
    val checkout            = Checkout.value
    val swapSizeGB          = ciSwapSizeGB.value
    val setSwapSpace        = SetSwapSpace.value
    val javaVersion         = ciDefaultTargetJavaVersion.value
    val checkGithubWorkflow = ciCheckGithubWorkflowSteps.value
    val lint                = Lint.value

    Seq(
      Job(
        id = "lint",
        name = "Lint",
        steps = (if (swapSizeGB > 0) Seq(setSwapSpace) else Seq.empty) ++
          Seq(checkout, SetupLibuv, SetupJava(javaVersion), CacheDependencies) ++ checkGithubWorkflow ++ Seq(lint)
      )
    )
  }

  lazy val testJobs: Def.Initialize[Seq[Job]] = Def.setting {
    val groupSimilarTests  = ciGroupSimilarTests.value
    val scalaVersionMatrix = ciTargetScalaVersions.value                  // TODO: rename
    val javaPlatforms      = autoImport.ciDefaultTargetJavaVersions.value // TODO: rename
    val javaPlatformMatrix = ciTargetJavaVersions.value                   // TODO: rename
    val matrixMaxParallel  = ciMatrixMaxParallel.value
    val swapSizeGB         = ciSwapSizeGB.value
    val setSwapSpace       = SetSwapSpace.value
    val checkout           = Checkout.value
    val backgroundJobs     = ciBackgroundJobs.value
    val buildOptions       = ciJvmOptions.value                           // TODO: rename
    val extraTestSteps     = ciExtraTestSteps.value                       // TODO: do we need this sbt setting, I don't think so!

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
            CacheDependencies,
            checkout
          ) ++ (if (javaPlatformMatrix.values.toSet.isEmpty) {
                  scalaVersionMatrix.values.toSeq.flatten.distinct.map { scalaVersion: String =>
                    Step.SingleStep(
                      name = "Test",
                      condition = Some(Condition.Expression(s"matrix.scala == '$scalaVersion'")),
                      run = Some(
                        prefixJobs + s"sbt ${buildOptions.mkString(" ")} " ++ "++${{ matrix.scala }}" + makeTests(
                          scalaVersion
                        )
                      )
                    )
                  }
                } else {
                  (for {
                    javaPlatform: String <- Set("8", "11", "17")
                    scalaVersion: String <- scalaVersionMatrix.values.toSeq.flatten.toSet
                    projects =
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
                            prefixJobs + s"sbt ${buildOptions
                              .mkString(" ")} " ++ "++${{ matrix.scala }}" ++ s" ${projects.map(_ + "/test ").mkString(" ")}"
                          )
                        )
                      )
                    else Seq.empty).flatten.toSeq
                })
        } ++ extraTestSteps
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
            CacheDependencies,
            checkout,
            if (javaPlatformMatrix.values.toSet.isEmpty) {
              Step.SingleStep(
                name = "Test",
                run = Some(prefixJobs + s"sbt ${buildOptions.mkString(" ")} " ++ "${{ matrix.scala-project }}/test")
              )
            } else {
              Step.StepSequence(
                Seq(
                  Step.SingleStep(
                    name = "Java 8 Tests",
                    condition = Some(Condition.Expression("matrix.java == '8'")),
                    run = Some(
                      prefixJobs + s"sbt ${buildOptions.mkString(" ")} " ++ "${{ matrix.scala-project-java8 }}/test"
                    )
                  ),
                  Step.SingleStep(
                    name = "Java 11 Tests",
                    condition = Some(Condition.Expression("matrix.java == '11'")),
                    run = Some(
                      prefixJobs + s"sbt ${buildOptions.mkString(" ")} " ++ "${{ matrix.scala-project-java11 }}/test"
                    )
                  ),
                  Step.SingleStep(
                    name = "Java 17 Tests",
                    condition = Some(Condition.Expression("matrix.java == '17'")),
                    run = Some(
                      prefixJobs + s"sbt ${buildOptions.mkString(" ")} " ++ "${{ matrix.scala-project-java17 }}/test"
                    )
                  )
                )
              )

            }
          ) ++ extraTestSteps
      )

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

  lazy val releaseJobs: Def.Initialize[Seq[Job]] = Def.setting {
    val swapSizeGB   = ciSwapSizeGB.value
    val setSwapSpace = SetSwapSpace.value
    val checkout     = Checkout.value
    val javaVersion  = ciDefaultTargetJavaVersion.value
    val release      = Release.value

    Seq(
      Job(
        id = "release",
        name = "Release",
        need = Seq("build", "lint", "test"),
        condition = Some(Condition.Expression("github.event_name != 'pull_request'")),
        steps = (if (swapSizeGB > 0) Seq(setSwapSpace) else Seq.empty) ++
          Seq(
            checkout,
            SetupLibuv,
            SetupJava(javaVersion),
            CacheDependencies,
            release
          )
      )
    )
  }

  lazy val postReleaseJobs: Def.Initialize[Seq[Job]] = Def.setting {
    val swapSizeGB            = ciSwapSizeGB.value
    val setSwapSpace          = SetSwapSpace.value
    val checkout              = Checkout.value
    val javaVersion           = ciDefaultTargetJavaVersion.value
    val publishToNpmRegistry  = PublishToNpmRegistry.value
    val updateReadmeCondition = autoImport.ciUpdateReadmeCondition.value
    val generateReadme        = GenerateReadme.value

    Seq(
      Job(
        id = "publish-docs",
        name = "Publish Docs",
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
                CacheDependencies,
                SetupNodeJs,
                publishToNpmRegistry
              )
            )
          )
      ),
      Job(
        id = "generate-readme",
        name = "Generate README",
        need = Seq("release"),
        condition = updateReadmeCondition orElse Some(
          Condition.Expression("github.event_name == 'push'") ||
            Condition.Expression("github.event_name == 'release'") &&
            Condition.Expression("github.event.action == 'published'")
        ),
        steps = (if (swapSizeGB > 0) Seq(setSwapSpace) else Seq.empty) ++
          Seq(
            Step.SingleStep(
              name = "Git Checkout",
              uses = Some(ActionRef("actions/checkout@v3.3.0")),
              parameters = Map(
                "ref"         -> "${{ github.head_ref }}".asJson,
                "fetch-depth" -> "0".asJson
              )
            ),
            SetupLibuv,
            SetupJava(javaVersion),
            CacheDependencies,
            generateReadme,
            Step.SingleStep(
              name = "Commit Changes",
              run = Some("""|git config --local user.email "github-actions[bot]@users.noreply.github.com"
                            |git config --local user.name "github-actions[bot]"
                            |git add README.md
                            |git commit -m "Update README.md" || echo "No changes to commit"
                            |""".stripMargin)
            ),
            Step.SingleStep(
              name = "Create Pull Request",
              uses = Some(ActionRef("peter-evans/create-pull-request@v4.2.3")),
              parameters = Map(
                "title"          -> "Update README.md".asJson,
                "commit-message" -> "Update README.md".asJson,
                "branch"         -> "zio-sbt-website/update-readme".asJson,
                "delete-branch"  -> true.asJson,
                "body" ->
                  """|Autogenerated changes after running the `sbt docs/generateReadme` command of the [zio-sbt-website](https://zio.dev/zio-sbt) plugin.
                     |
                     |I will automatically update the README.md file whenever there is new change for README.md, e.g.
                     |  - After each release, I will update the version in the installation section.
                     |  - After any changes to the "docs/index.md" file, I will update the README.md file accordingly.""".stripMargin.asJson
              )
            )
          )
      )
    )
  }

  lazy val generateGithubWorkflowTask: Def.Initialize[Task[Unit]] =
    Def.task {
      val workflow = websiteWorkflow(
        buildJobs = buildJobs.value,
        lintJobs = lintJobs.value,
        testJobs = testJobs.value,
        reportSuccessfulJobs = reportSuccessfulJobs.value,
        releaseJobs = releaseJobs.value,
        postReleaseJobs = postReleaseJobs.value,
        workflowName = ciWorkflowName.value,
        ciEnabledBranches = ciEnabledBranches.value
      )

      val template =
        s"""|# This file was autogenerated using `zio-sbt-ci` plugin via `sbt ciGenerateGithubWorkflow` 
            |# task and should be included in the git repository. Please do not edit it manually.
            |
            |$workflow""".stripMargin

      IO.write(new File(s".github/workflows/${ciWorkflowName.value.toLowerCase}.yml"), template)
    }

  override def trigger = noTrigger

  override lazy val buildSettings: Seq[Setting[_]] = {
    Seq(
      ciWorkflowName              := "CI",
      ciEnabledBranches           := Seq.empty,
      ciGenerateGithubWorkflow    := generateGithubWorkflowTask.value,
      ciDocsVersioning            := DocsVersioning.SemanticVersioning,
      ciCheckGithubWorkflow       := checkGithubWorkflowTask.value,
      ciTargetScalaVersions       := Map.empty,
      ciTargetJavaVersions        := Map.empty,
      ciJvmOptions                := List.empty[String],
      ciUpdateReadmeCondition     := None,
      ciGroupSimilarTests         := false,
      ciExtraTestSteps            := Seq.empty,
      ciSwapSizeGB                := 0,
      ciDefaultTargetJavaVersions := Seq("8", "11", "17"),
      ciCheckArtifactsBuildSteps :=
        Seq(
          Step.SingleStep(
            name = "Check artifacts build process",
            run = Some(s"sbt ${ciJvmOptions.value.mkString(" ")} +publishLocal")
          )
        ),
      ciCheckArtifactsCompilationSteps := Seq(
        Step.SingleStep(
          name = "Check all code compiles",
          run = Some(makePrefixJobs(ciBackgroundJobs.value) + s"sbt ${ciJvmOptions.value.mkString(" ")} +Test/compile")
        )
      ),
      ciCheckGithubWorkflowSteps := Seq(
        Step.SingleStep(
          name = "Check if the site workflow is up to date",
          run = Some(
            makePrefixJobs(ciBackgroundJobs.value) + s"sbt ${ciJvmOptions.value.mkString(" ")} ciCheckGithubWorkflow"
          )
        )
      ),
      ciBackgroundJobs           := Seq.empty,
      ciMatrixMaxParallel        := None,
      ciDefaultTargetJavaVersion := "8",
      ciBuildJobs                := buildJobs.value,
      ciLintJobs                 := lintJobs.value,
      ciTestJobs                 := testJobs.value,
      ciReleaseJobs              := releaseJobs.value,
      ciPostReleaseJobs          := postReleaseJobs.value,
      ciPullRequestApprovalJobs  := Seq("lint", "test", "build")
    )
  }

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
        uses = Some(ActionRef("pierotofy/set-swap-space@master")),
        parameters = Map("swap-size-gb" -> swapSizeGB.asJson)
      )
    }

  lazy val Checkout: Def.Initialize[SingleStep] =
    Def.setting {
      Step.SingleStep(
        name = "Git Checkout",
        uses = Some(ActionRef("actions/checkout@v3.3.0")),
        parameters = Map("fetch-depth" -> "0".asJson)
      )
    }

  lazy val SetupLibuv: Step.SingleStep = Step.SingleStep(
    name = "Install libuv",
    run = Some("sudo apt-get update && sudo apt-get install -y libuv1-dev")
  )

  def SetupJava(version: String = "8"): Step.SingleStep = Step.SingleStep(
    name = "Setup Scala",
    uses = Some(ActionRef("actions/setup-java@v3.10.0")),
    parameters = Map(
      "distribution" -> "temurin".asJson,
      "java-version" -> version.asJson,
      "check-latest" -> true.asJson
    )
  )

  lazy val CacheDependencies: Step.SingleStep = Step.SingleStep(
    name = "Cache Dependencies",
    uses = Some(ActionRef("coursier/cache-action@v6"))
  )

  lazy val CheckWebsiteBuildProcess: Def.Initialize[Step.SingleStep] =
    Def.setting {
      val backgroundJobs = ciBackgroundJobs.value
      val prefixJobs     = makePrefixJobs(backgroundJobs)
      val buildOptions   = ciJvmOptions.value

      Step.SingleStep(
        name = "Check website build process",
        run = Some(prefixJobs + s"sbt docs/clean; sbt ${buildOptions.mkString(" ")} docs/buildWebsite")
      )
    }

  lazy val Lint: Def.Initialize[Step.SingleStep] = Def.setting {
    val backgroundJobs = ciBackgroundJobs.value
    val prefixJobs     = makePrefixJobs(backgroundJobs)
    val buildOptions   = ciJvmOptions.value

    Step.SingleStep(
      name = "Lint",
      run = Some(prefixJobs + s"sbt ${buildOptions.mkString(" ")} lint")
    )
  }

  lazy val Release: Def.Initialize[SingleStep] = Def.setting {
    val backgroundJobs = ciBackgroundJobs.value
    val buildOptions   = ciJvmOptions.value

    val prefixJobs = makePrefixJobs(backgroundJobs)

    Step.SingleStep(
      name = "Release",
      run = Some(prefixJobs + s"sbt ${buildOptions.mkString(" ")} ci-release"),
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
    uses = Some(ActionRef("actions/setup-node@v3")),
    parameters = Map(
      "node-version" -> "16.x".asJson,
      "registry-url" -> "https://registry.npmjs.org".asJson
    )
  )

  val PublishToNpmRegistry: Def.Initialize[SingleStep] = Def.setting {
    val backgroundJobs = ciBackgroundJobs.value
    val buildOptions   = ciJvmOptions.value
    val docsVersioning = autoImport.ciDocsVersioning.value

    val prefixJobs = makePrefixJobs(backgroundJobs)

    Step.SingleStep(
      name = "Publish Docs to NPM Registry",
      run = Some(prefixJobs + s"sbt ${buildOptions.mkString(" ")} docs/${docsVersioning.npmCommand}"),
      env = Map("NODE_AUTH_TOKEN" -> "${{ secrets.NPM_TOKEN }}")
    )
  }

  val GenerateReadme: Def.Initialize[SingleStep] = Def.setting {
    val backgroundJobs = ciBackgroundJobs.value
    val buildOptions   = ciJvmOptions.value

    val prefixJobs = makePrefixJobs(backgroundJobs)

    Step.SingleStep(
      name = "Generate Readme",
      run = Some(prefixJobs + s"sbt ${buildOptions.mkString(" ")} docs/generateReadme")
    )
  }

  val CheckReadme: Def.Initialize[SingleStep] = Def.setting {
    val backgroundJobs = ciBackgroundJobs.value
    val buildOptions   = ciJvmOptions.value

    val prefixJobs = makePrefixJobs(backgroundJobs)

    Step.SingleStep(
      name = "Check if the README file is up to date",
      run = Some(prefixJobs + s"sbt ${buildOptions.mkString(" ")} docs/checkReadme")
    )
  }

  @nowarn("msg=detected an interpolated expression")
  def websiteWorkflow(
    workflowName: String,
    buildJobs: Seq[Job] = Seq.empty,
    lintJobs: Seq[Job] = Seq.empty,
    testJobs: Seq[Job] = Seq.empty,
    reportSuccessfulJobs: Seq[Job] = Seq.empty,
    releaseJobs: Seq[Job] = Seq.empty,
    postReleaseJobs: Seq[Job] = Seq.empty,
    ciEnabledBranches: Seq[String] = Seq("main")
  ): String =
    yaml
      .Printer(
        preserveOrder = true,
        dropNullKeys = true,
        splitLines = false,
        lineBreak = LineBreak.Unix,
        version = YamlVersion.Auto
      )
      .pretty(
        Workflow(
          name = workflowName,
          env = Map(
            // JDK_JAVA_OPTIONS is _the_ env. variable to use for modern Java
            "JDK_JAVA_OPTIONS" -> "-XX:+PrintCommandLineFlags -Xmx6G -Xss4M -XX:+UseG1GC",
            // For Java 8 only (sadly, it is not modern enough for JDK_JAVA_OPTIONS)
            "JVM_OPTS"     -> "-XX:+PrintCommandLineFlags -Xmx6G -Xss4M -XX:+UseG1GC",
            "NODE_OPTIONS" -> "--max_old_space_size=6144"
          ),
          triggers = Seq(
            Trigger.WorkflowDispatch(),
            Trigger.Release(Seq("published")),
            Trigger.Push(branches = ciEnabledBranches.map(Branch.Named)),
            Trigger.PullRequest()
          ),
          jobs = buildJobs ++ lintJobs ++ testJobs ++ reportSuccessfulJobs ++ releaseJobs ++ postReleaseJobs
        ).asJson
      )
}
