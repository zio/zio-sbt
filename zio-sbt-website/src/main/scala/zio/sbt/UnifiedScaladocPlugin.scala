package zio.sbt

import sbt.Keys.*
import sbt.*
import sbtunidoc.ScalaUnidocPlugin

object UnifiedScaladocPlugin extends sbt.AutoPlugin {
  override def requires = ScalaUnidocPlugin

  override def trigger = noTrigger

  import ScalaUnidocPlugin.autoImport.*

  override def projectSettings: Seq[Setting[_]] =
    Seq(Compile / doc := (ScalaUnidoc / doc).value)
}
