package zio.sbt

import mdoc.MdocPlugin
import mdoc.MdocPlugin.autoImport.*
import sbt.*
import sbt.Keys.*

import java.nio.file.Paths
import scala.language.postfixOps

object WebsitePlugin extends sbt.AutoPlugin {

  object autoImport {
    val compileDocs = inputKey[Unit]("compile docs")
    val installWebsite = taskKey[Unit]("install the website for the first time")
    val previewWebsite = taskKey[Unit]("preview website")
    val publishWebsite = inputKey[Unit]("publish website to the npm registry")
    val npmToken = settingKey[String]("npm token")
    val generateGithubWorkflow = taskKey[Unit]("generate github workflow")
  }

  import autoImport.*

  override def requires = MdocPlugin

  override lazy val projectSettings =
    Seq(
      compileDocs := compileDocsTask.evaluated,
      mdocOut := Paths.get("website/docs").toFile,
      installWebsite := installWebsiteTask.value,
      previewWebsite := previewWebsiteTask.value,
      publishWebsite := publishWebsiteTask.value,
      generateGithubWorkflow := generateGithubWorkflowTask.value
    )

  private def exit(exitCode: Int) =
    if (exitCode != 0) sys.exit(exitCode)

  lazy val previewWebsiteTask = Def
    .task {
      import zio.*

      val task =
        for {
          _ <- ZIO.attempt(compileDocsTask.toTask(" --watch").value).forkDaemon
          _ <- ZIO.attempt(docusaurusServerTask.value)
        } yield ()

      Unsafe.unsafe { implicit unsafe =>
        Runtime.default.unsafe.run(task).getOrThrowFiberFailure()
      }
    }
    .dependsOn(compileDocsTask.toTask(""))

  lazy val docusaurusServerTask = Def.task {
    import sys.process.*
    exit("yarn --cwd ./website run start" !)
  }

  lazy val compileDocsTask =
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

  lazy val installWebsiteTask =
    Def.task {
      import sys.process.*
      val logger = streams.value.log

      val task =
        s"""|npx @zio.dev/create-zio-website@latest ${normalizedName.value} \\
            |  --description="${name.value}" \\
            |  --author="ZIO Contributors" \\
            |  --email="email@zio.dev" \\
            |  --license="Apache-2.0" \\
            |  --architecture=Linux""".stripMargin

      logger.info(s"installing website for ${normalizedName.value} ... \n$task")
      exit(task !)

      exit(s"mv ${normalizedName.value} website" !)

      exit("rm website/.git/ -rvf" !)
    }

  lazy val publishWebsiteTask =
    Def.task {
      import sys.process.*

        
      val version =
        ("git tag --sort=committerdate" !!).split("\n").last
        .replace("docs-", "")

      exit(Process(s"npm version $version", new File("website/docs/")) !)

      exit("npm config set access public" !)

      exit(Process("npm publish", new File("website/docs/")).!)
      
    }

  lazy val generateGithubWorkflowTask =
    Def.task {
      val template = {
        """
         |name: docs
         |
         |on:
         |  push:
         |    branches: [main]
         |    tags: ["*"]
         |
         |jobs:
         |  publish:
         |    runs-on: ubuntu-20.04
         |    timeout-minutes: 30
         |    steps:
         |      - uses: actions/checkout@v3.1.0
         |        with:
         |          fetch-depth: 0
         |      - name: Print Latest Tag For Debugging Purposes
         |        run: git tag --sort=committerdate | tail -1
         |      - uses: olafurpg/setup-scala@v13
         |      - name: Compile zio-sbt
         |        run: |
         |          git clone https://github.com/khajavi/zio-sbt.git
         |          cd zio-sbt
         |          sbt website/publishLocal
         |      - name: Compile Project's Documentation
         |        run: sbt compileDocs
         |      - uses: actions/setup-node@v3
         |        with:
         |          node-version: '16.x'
         |          registry-url: 'https://registry.npmjs.org'
         |      - name: Publishing Docs to NPM Registry
         |        run: sbt publishWebsite
         |        env:
         |          NODE_AUTH_TOKEN: ${{ secrets.NPM_TOKEN }}
         |
         |""".stripMargin
      }
      
      IO.write(new File(".github/workflows/docs.yml"), template)
    }

}
