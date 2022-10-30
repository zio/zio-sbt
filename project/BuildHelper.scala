import sbt._
import Keys._
import sbtbuildinfo._
import BuildInfoKeys._

object BuildHelper {
  def buildInfoSettings(packageName: String) = Seq(
    buildInfoKeys := Seq[BuildInfoKey](name, version, scalaVersion, sbtVersion, isSnapshot),
    buildInfoPackage := packageName
  )
}
