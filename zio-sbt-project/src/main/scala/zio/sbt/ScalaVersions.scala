/*
 * Copyright 2022-2023 dev.zio
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package zio.sbt

import sbt.Keys._
import sbt._

object ScalaVersion {
  val Scala3   = "3.3.4"
  val Scala212 = "2.12.20"
  val Scala213 = "2.13.15"
}

object ScalaVersions {

  trait Keys {

    lazy val scala212: SettingKey[String] = settingKey[String]("Scala 2.12 version")
    lazy val scala213: SettingKey[String] = settingKey[String]("Scala 2.13 version")
    lazy val scala3: SettingKey[String]   = settingKey[String]("Scala 3 version")

    lazy val defaultCrossScalaVersions: SettingKey[Seq[String]] =
      settingKey[Seq[String]]("Optional default cross scala versions")
    lazy val allScalaVersions: SettingKey[Set[String]] =
      settingKey[Set[String]]("All scala versions used in the whole project")

  }

  object Keys extends Keys

  import Keys._

  def allCurrentScalaVersions: Def.Initialize[Set[String]] = Def.setting {
    scalaVersion.?.all(
      ScopeFilter(
        inAggregates(LocalRootProject),
        inConfigurations(Compile)
      )
    ).value.flatten.toSet
  }

  def allScalaVersionsSetting: Def.Setting[Set[String]] = allScalaVersions := {
    allScalaCrossVersions.value.flatten ++ allCurrentScalaVersions.value
  }

  def allScalaCrossVersions: Def.Initialize[Set[Set[String]]] = Def.setting {
    crossScalaVersions.?.all(
      ScopeFilter(
        inAggregates(LocalRootProject),
        inConfigurations(Compile)
      )
    ).value.map(_.map(_.toSet)).flatten.toSet
  }

  def buildSettings: Seq[Setting[_]] = Seq(
    scala212 := ScalaVersion.Scala212,
    scala213 := ScalaVersion.Scala213,
    scala3   := ScalaVersion.Scala3
  )

  def globalSettings: Seq[Setting[_]] = Seq(
    allScalaVersionsSetting
  )

}
