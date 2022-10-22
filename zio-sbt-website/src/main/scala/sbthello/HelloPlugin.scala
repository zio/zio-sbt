package sbthello

import sbt.*

object HelloPlugin extends AutoPlugin {
  val greeting = settingKey[String]("greeting")
  val hello    = taskKey[Unit]("say hello")

  override lazy val buildSettings =
    Seq(hello := helloTask.value)

  lazy val helloTask =
    Def.task {
      println("Hello!")
    }
}
