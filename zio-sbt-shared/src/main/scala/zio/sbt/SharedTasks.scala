package zio.sbt

import sbt.Keys._
import sbt._

object SharedTasks {

  val isRoot: Def.Initialize[Boolean] = Def.settingDyn {
    val r = thisProjectRef.?.value.fold(true)(_.project == loadedBuild.value.units(loadedBuild.value.root).root)
    Def.setting(r)
  }
}
