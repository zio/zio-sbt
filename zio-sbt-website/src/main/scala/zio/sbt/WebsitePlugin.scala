package zio.sbt

import mdoc.MdocPlugin
import mdoc.MdocPlugin.autoImport.*
import sbt.{Def, *}

import java.nio.file.Paths

object WebsitePlugin extends sbt.AutoPlugin {

  object autoImport {
    val projectName = settingKey[String]("project name")
    val installWebsiteTheme = taskKey[Unit]("install website theme")
    val compileDocs = taskKey[Unit]("compile docs")
    val installWebsite = taskKey[Unit]("install the website for the first time")
    val docusaurusServer = taskKey[Unit]("run docusaurus")
    val previewWebsite = taskKey[Unit]("preview website")
  }

  import autoImport.*

  override def requires =
    MdocPlugin

  override lazy val projectSettings =
    Seq(
      installWebsiteTheme := installWebsiteThemeTask.value,
      compileDocs := compileDocsTask.value,
      mdocOut := Paths.get("website/docs").toFile,
      installWebsite := Def.sequential(installWebsiteTheme, compileDocs).value,
      docusaurusServer := docusaurusServerTask.value,
      previewWebsite := previewWebsiteTask.value
    )

  lazy val previewWebsiteTask = Def.task {
    import zio.*

    val task = ZIO.scoped {
      for {
        _ <- ZIO.attempt(compileDocsTask.value).debug.forkScoped
        _ <- ZIO.attempt(docusaurusServerTask.value)
      } yield ()
    }

    Unsafe.unsafe { implicit unsafe =>
      Runtime.default.unsafe.run(task).getOrThrowFiberFailure()
    }
  }

  lazy val docusaurusServerTask = Def.task {
    import sys.process.*
    "yarn --cwd ./website run start" !
  }

  lazy val compileDocsTask = Def
    .taskDyn {
      println("Compiling docs using mdoc ...")
      mdoc.toTask(" --watch --no-livereload")
    }

  lazy val installWebsiteThemeTask =
    Def.task {
      import sys.process.*

      val task =
        s"""|npx @zio.dev/create-zio-website@latest website \\
            |  --description="Documentation of folan" \\
            |  --author="Milad Khajavi" \\
            |  --email="khajavi@gmail.com" \\
            |  --license="Apache-2.0" \\
            |  --architecture=Linux""".stripMargin

      println(s"executing the following task: \n$task")

      task !

      "rm website/.git/ -rvf" !
    }
}
