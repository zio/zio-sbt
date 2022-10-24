package sbthello

import sbt.*

object PublishPlugin extends AutoPlugin {

  object autoImport {
    val npmToken             = settingKey[String]("npm token")
    val publishToNpmRegistry = taskKey[Unit]("publish to npm registry")
  }

  import autoImport.*

  override lazy val buildSettings =
    Seq(publishToNpmRegistry := helloTask.value)

  lazy val helloTask =
    Def.task {
      println(npmToken.value)
    }
}
