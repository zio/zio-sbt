package zio.sbt

import sbt._

sealed trait ScalaPlatform {
  def asString: String = this match {
    case ScalaPlatform.JS     => "JS"
    case ScalaPlatform.JVM    => "JVM"
    case ScalaPlatform.Native => "Native"
  }
}
object ScalaPlatform {
  case object JS     extends ScalaPlatform
  case object JVM    extends ScalaPlatform
  case object Native extends ScalaPlatform
}

object ScalaPlatforms {

  trait Keys {

    lazy val allScalaPlatforms: SettingKey[Set[ScalaPlatform]] =
      settingKey[Set[ScalaPlatform]]("All Scala platforms used in the whole project")

  }

  object Keys extends Keys

  import Keys._

  def allScalaPlatformsSetting: Def.Setting[Set[ScalaPlatform]] = allScalaPlatforms := {
    Def.setting {
      Set(
        BuildAssertions.Keys.isScalaJS.value     -> ScalaPlatform.JS,
        BuildAssertions.Keys.isScalaJVM.value    -> ScalaPlatform.JVM,
        BuildAssertions.Keys.isScalaNative.value -> ScalaPlatform.Native
      ).collect { case (true, p) =>
        p
      }
    }.all(
      ScopeFilter(
        inAggregates(LocalRootProject),
        inConfigurations(Compile)
      )
    ).value
      .flatten
      .toSet
  }

  def globalSettings: Seq[Setting[_]] = Seq(
    allScalaPlatformsSetting
  )
}
