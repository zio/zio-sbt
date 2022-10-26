package zio.sbt

import sbt.{Def, *}

import scala.annotation.nowarn
import mdoc.MdocPlugin
import Keys.*
import MdocPlugin.autoImport.*

import java.nio.file.Paths

object WebsitePlugin extends sbt.AutoPlugin {

  object autoImport {
    val projectName = settingKey[String]("project name")
    val installWebsite = taskKey[Unit]("install website")
    val installSite = taskKey[Unit]("install website")
    val runMdoc = taskKey[Unit]("run mdoc")
    val taskA = taskKey[Unit]("run task A")
    val taskB = taskKey[Unit]("run task A")
    val taskC = taskKey[Unit]("run task C")
  }

  import autoImport._

  override def requires =
    MdocPlugin

  override lazy val projectSettings =
    Seq(
      installWebsite := installWebsiteTask.value,
      taskA := taskATask.value,
      taskB := taskBTask.value,
      runMdoc := runMdocTask.value,
      mdocOut := Paths.get("website/docs").toFile,
      mdocVariables := {
        mdocVariables.value ++
          Map(
            "VERSION" -> version.value,
            "PRERELEASE_VERSION" -> version.value,
            "SNAPSHOT_VERSION" -> version.value,
            "API_URL" -> "url"
          )
      },
      taskC := Def.sequential(taskA, taskB, runMdoc, installWebsite).value
    )

  lazy val taskATask =
    Def.task {
      println("running task A")
    }

  lazy val taskBTask =
    Def.task {
      println("running task B")
    }

  lazy val runMdocTask = Def
    .taskDyn {
      println("hello")
      mdoc.toTask("")
    }

  lazy val installWebsiteTask =
    Def.task {
      import sys.process._

      val task =
        s"""|npx @zio.dev/create-zio-website@latest website \\
            |  --description="Documentation of folan" \\
            |  --author="Milad Khajavi" \\
            |  --email="khajavi@gmail.com" \\
            |  --license="Apache-2.0" \\
            |  --architecture=Linux""".stripMargin

      println(s"executing the following task: \n$task")

      task !
    }
}
