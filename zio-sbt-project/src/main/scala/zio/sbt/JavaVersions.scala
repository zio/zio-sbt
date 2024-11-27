package zio.sbt

import sbt.Keys._
import sbt._

object JavaVersion {

  val `11` = "11"
  val `17` = "17"
  val `21` = "21"
}

object JavaVersions {

  trait Keys {

    lazy val javaPlatform: SettingKey[String] = settingKey[String]("java target platform, default is 11")
    lazy val currentJDK: SettingKey[String]   = settingKey[String]("current JDK version")

  }

  object Keys extends Keys

  import Keys._

  def buildSettings: Seq[Setting[_]] = Seq(
    javaPlatform := {
      val targetJVM       = javaPlatform.?.value.getOrElse(JavaVersion.`11`)
      val jdkNeedsUpgrade = currentJDK.value.toInt < targetJVM.toInt
      if (jdkNeedsUpgrade)
        sLog.value.warn(
          s"\u001b[33mJDK upgrade is required, target ($targetJVM) is higher than the current JDK version (${currentJDK.value}), compilation will fail!\u001b"
        )
      targetJVM
    },
    scalacOptions ++= Seq(s"-release:${javaPlatform.value}"),
    javacOptions ++= Seq("-source", javaPlatform.value, "-target", javaPlatform.value)
  )

  def globalSettings: Seq[Setting[_]] = Seq(
    currentJDK := sys.props("java.specification.version")
  )
}
