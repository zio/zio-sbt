package zio.sbt

import mdoc.MdocPlugin
import mdoc.MdocPlugin.autoImport.*
import sbt.*
import sbt.Keys.*

import java.nio.file.Paths
import scala.language.postfixOps

object WebsitePlugin extends sbt.AutoPlugin {

  object autoImport {
    val compileDocs = taskKey[Unit]("compile docs")
    val checkDocs = taskKey[String]("check docs")
    val installWebsite = taskKey[Unit]("install the website for the first time")
    val previewWebsite = taskKey[Unit]("preview website")
  }

  import autoImport.*

  override def requires = MdocPlugin

  override lazy val projectSettings =
    Seq(
      compileDocs := compileDocsTask.value,
      checkDocs := checkDocsTask.value,
      mdocOut := Paths.get("website/docs").toFile,
      installWebsite := installWebsiteTask.value,
      previewWebsite := previewWebsiteTask.value
    )

  lazy val previewWebsiteTask = Def
    .task {
      import zio.*

      val task =
        for {
          _ <- ZIO.attempt(compileDocsTask.value).forkDaemon
          _ <- ZIO.attempt(docusaurusServerTask.value)
        } yield ()

      Unsafe.unsafe { implicit unsafe =>
        Runtime.default.unsafe.run(task).getOrThrowFiberFailure()
      }
    }
    .dependsOn(mdoc.toTask(""))

  lazy val docusaurusServerTask = Def.task {
    import sys.process.*
    "yarn --cwd ./website run start" !
  }

  lazy val compileDocsTask = Def
    .taskDyn {
      val logger = streams.value.log
      logger.info("Compiling docs using mdoc ...")
      mdoc.toTask(" --watch --no-livereload")
    }

  lazy val checkDocsTask = Def
    .taskDyn {
      val logger = streams.value.log
      logger.info("Compiling docs using mdoc ...")
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
      task !

      s"mv ${normalizedName.value} website" !

      "rm website/.git/ -rvf" !
    }
}
