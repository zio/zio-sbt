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
import sbtprojectmatrix.ProjectMatrixKeys.virtualAxes

object BuildAssertions {

  trait Keys {

    lazy val isProjectMatrix: SettingKey[Boolean] = settingKey[Boolean]("Use sbt-projectmatrix")
    lazy val isScalaJS: SettingKey[Boolean]       = settingKey[Boolean]("Is Scala.js project")
    lazy val isScalaJVM: SettingKey[Boolean]      = settingKey[Boolean]("Is JVM project")
    lazy val isScalaNative: SettingKey[Boolean]   = settingKey[Boolean]("Is Scala Native project")
    lazy val isScala2_12: SettingKey[Boolean]     = settingKey[Boolean]("Is Scala 2.12 project")
    lazy val isScala2_13: SettingKey[Boolean]     = settingKey[Boolean]("Is Scala 2.13 project")
    lazy val isScala3: SettingKey[Boolean]        = settingKey[Boolean]("Is Scala 3 project")

    lazy val isCrossbuildProject: SettingKey[Boolean] =
      settingKey[Boolean]("Check if the project is a crossbuild project (crossScalaVersions.size > 1)")

    lazy val hasCrossbuildProjects: SettingKey[Boolean] =
      settingKey[Boolean]("Check if the project has crossbuild projects")
  }

  object Keys extends Keys

  import Keys._

  def isProjectMatrixSetting: Setting[Boolean] = isProjectMatrix := virtualAxes.?.value.isDefined

  def requireJS[T](setting: => T): Def.Initialize[T] =
    Def.settingDyn {
      if (isScalaJS.value) Def.setting(setting)
      else sys.error("This setting requires Scala.js")
    }

  def isScalaJSSetting: Setting[Boolean] = isScalaJS := {
    thisProject.?.value.map(_.autoPlugins.exists(_.label == "org.scalajs.sbtplugin.ScalaJSPlugin")).getOrElse(false)
  }

  def requireJVM[T](setting: => T): Def.Initialize[T] =
    Def.settingDyn {
      if (isScalaJVM.value) Def.setting(setting)
      else sys.error("This setting requires JVM")
    }

  def isScalaJVMSetting: Setting[Boolean] = isScalaJVM := {
    !isScalaJS.value && !isScalaNative.value
  }

  def requireNative[T](setting: => T): Def.Initialize[T] =
    Def.settingDyn {
      if (isScalaNative.value) Def.setting(setting)
      else sys.error("This setting requires Scala Native")
    }

  def isScalaNativeSetting: Setting[Boolean] = isScalaNative := {
    thisProject.?.value
      .map(_.autoPlugins.exists(_.label == "scala.scalanative.sbtplugin.ScalaNativePlugin"))
      .getOrElse(false)
  }

  def isScala2_12Setting: Setting[Boolean] = isScala2_12 := Def.setting {
    CrossVersion.partialVersion(scalaVersion.value) match {
      case Some((2, 12)) => true
      case _             => false
    }
  }.value

  def isScala2_13Setting: Setting[Boolean] = isScala2_13 := Def.setting {
    CrossVersion.partialVersion(scalaVersion.value) match {
      case Some((2, 13)) => true
      case _             => false
    }
  }.value

  def isScala3Setting: Setting[Boolean] = isScala3 := Def.setting {
    CrossVersion.partialVersion(scalaVersion.value) match {
      case Some((3, _)) => true
      case _            => false
    }
  }.value

  def isCrossbuildProjectSetting: Def.Setting[Boolean] = isCrossbuildProject := crossScalaVersions.value.size > 1

  private val allAggregates =
    ScopeFilter(inAggregates(ThisProject))

  def hasCrossbuildProjectsSetting: Def.Setting[Boolean] = hasCrossbuildProjects := {
    val current = isCrossbuildProject.value
    current || isCrossbuildProject
      .all(allAggregates)
      .value
      .reduce(_ || _)
  }

  def contains2_12: Def.Initialize[Boolean] = Def.settingDyn {
    Def.setting {
      Def.setting {
        isScala2_12.value
      }.all(allAggregates)
        .value
        .reduce(_ || _)
    }
  }

  def contains2_13: Def.Initialize[Boolean] = Def.settingDyn {
    Def.setting {
      Def.setting {
        isScala2_13.value
      }.all(allAggregates)
        .value
        .reduce(_ || _)
    }
  }

  def contains3: Def.Initialize[Boolean] = Def.settingDyn {
    Def.setting {
      Def.setting {
        isScala3.value
      }.all(allAggregates)
        .value
        .reduce(_ || _)
    }
  }

  def containsJS: Def.Initialize[Boolean] = Def.settingDyn {
    Def.setting {
      Def.setting {
        isScalaJS.value
      }.all(allAggregates)
        .value
        .reduce(_ || _)
    }
  }

  def containsJS2_12: Def.Initialize[Boolean] = Def.settingDyn {
    Def.setting {
      Def.setting {
        isScalaJS.value && isScala2_12.value
      }.all(allAggregates)
        .value
        .reduce(_ || _)
    }
  }

  def containsJS2_13: Def.Initialize[Boolean] = Def.settingDyn {
    Def.setting {
      Def.setting {
        isScalaJS.value && isScala2_13.value
      }.all(allAggregates)
        .value
        .reduce(_ || _)
    }
  }

  def containsJS3: Def.Initialize[Boolean] = Def.settingDyn {
    Def.setting {
      Def.setting {
        isScalaJS.value && isScala3.value
      }.all(allAggregates)
        .value
        .reduce(_ || _)
    }
  }

  def containsJVM: Def.Initialize[Boolean] = Def.settingDyn {
    Def.setting {
      Def.setting {
        isScalaJVM.value
      }.all(allAggregates)
        .value
        .reduce(_ || _)
    }
  }

  def containsJVM2_12: Def.Initialize[Boolean] = Def.settingDyn {
    Def.setting {
      Def.setting {
        isScalaJVM.value && isScala2_12.value
      }.all(allAggregates)
        .value
        .reduce(_ || _)
    }
  }

  def containsJVM2_13: Def.Initialize[Boolean] = Def.settingDyn {
    Def.setting {
      Def.setting {
        isScalaJVM.value && isScala2_13.value
      }.all(allAggregates)
        .value
        .reduce(_ || _)
    }
  }

  def containsJVM3: Def.Initialize[Boolean] = Def.settingDyn {
    Def.setting {
      Def.setting {
        isScalaJVM.value && isScala3.value
      }.all(allAggregates)
        .value
        .reduce(_ || _)
    }
  }

  def containsNative: Def.Initialize[Boolean] = Def.settingDyn {
    Def.setting {
      Def.setting {
        isScalaNative.value
      }.all(allAggregates)
        .value
        .reduce(_ || _)
    }
  }

  def containsNative2_12: Def.Initialize[Boolean] = Def.settingDyn {
    Def.setting {
      Def.setting {
        isScalaNative.value && isScala2_12.value
      }.all(allAggregates)
        .value
        .reduce(_ || _)
    }
  }

  def containsNative2_13: Def.Initialize[Boolean] = Def.settingDyn {
    Def.setting {
      Def.setting {
        isScalaNative.value && isScala2_13.value
      }.all(allAggregates)
        .value
        .reduce(_ || _)
    }
  }

  def containsNative3: Def.Initialize[Boolean] = Def.settingDyn {
    Def.setting {
      Def.setting {
        isScalaNative.value && isScala3.value
      }.all(allAggregates)
        .value
        .reduce(_ || _)
    }
  }

  def scalaCrossVersionsCountsOnePerProject: Def.Initialize[Boolean] = Def.settingDyn {
    val result = crossScalaVersions.?.all(
      ScopeFilter(
        inAggregates(LocalRootProject),
        inConfigurations(Compile)
      )
    ).value
    Def.setting(result.map(_.forall(_.size <= 1)).reduce(_ && _))
  }

  def settings: Seq[Setting[_]] = Seq(
    isScalaJSSetting,
    isScalaJVMSetting,
    isScalaNativeSetting,
    isScala2_12Setting,
    isScala2_13Setting,
    isScala3Setting,
    isProjectMatrixSetting,
    isCrossbuildProjectSetting,
    hasCrossbuildProjectsSetting,
    hasCrossbuildProjects / aggregate := false
  )

  val docs: Def.Initialize[Seq[String]] = Def.settingDyn {
    val containsJS2_12                        = BuildAssertions.containsJS2_12.value
    val containsJS2_13                        = BuildAssertions.containsJS2_13.value
    val containsJS3                           = BuildAssertions.containsJS3.value
    val containsJVM2_12                       = BuildAssertions.containsJVM2_12.value
    val containsJVM2_13                       = BuildAssertions.containsJVM2_13.value
    val containsJVM3                          = BuildAssertions.containsJVM3.value
    val containsNative2_12                    = BuildAssertions.containsNative2_12.value
    val containsNative2_13                    = BuildAssertions.containsNative2_13.value
    val containsNative3                       = BuildAssertions.containsNative3.value
    val allScalaCrossVersions                 = ScalaVersions.allScalaCrossVersions.value.filter(_.size > 1).flatten
    val scalaCrossVersionsCountsOnePerProject = BuildAssertions.scalaCrossVersionsCountsOnePerProject.value

    val activeScala212 =
      ScalaVersions.allCurrentScalaVersions.value
        .filter(_.startsWith("2.12"))
        .toList
        .sorted
        .lastOption
        .getOrElse("2.12.x ")
    val activeScala213 =
      ScalaVersions.allCurrentScalaVersions.value
        .filter(_.startsWith("2.13"))
        .toList
        .sorted
        .lastOption
        .getOrElse("2.13.x ")
    val activeScala3 =
      ScalaVersions.allCurrentScalaVersions.value.filter(_.startsWith("3.")).toList.sorted.lastOption.getOrElse("3.x  ")

    Def.setting(
      Seq(
        s"""
           | \u001b[33m### This build contains crossbuild projects (multiple scala versions defined in crossScalaVersions)\u001b[0m
           | \u001b[33mVersions found: ${ScalaVersions.Keys.allScalaVersions.value.toSeq.sorted.mkString(", ")}\u001b
           | \u001b[33mNote: having only one scala version per project makes it easier to compile and test all without `++` switching, sbt-projectmatrix can help with that.\u001b
           | \u001b[33mSee: \u001b[36mhttps://github.com/sbt/sbt-projectmatrix?tab=readme-ov-file#sbt-projectmatrix\u001b
           | \u001b[33mthis is feature in sbt 2.x, see: \u001b[36mhttps://www.scala-sbt.org/2.x/docs/en/reference/cross-building-setup.html#project-matrix
        """.stripMargin.stripLeading()
      ).filter(_ => !scalaCrossVersionsCountsOnePerProject) ++
        Seq(
        // format: off
        s"""
          | ### Detected current Platforms, JDKs and Scala Versions ${if (!scalaCrossVersionsCountsOnePerProject) "( other builds " + (allScalaCrossVersions - scalaVersion.value).mkString(", ") + " )" else ""}
          | Current JDK ${if (JavaVersions.Keys.currentJDK.value < JavaVersions.Keys.javaPlatform.value) "\u001b[31m" else ""}${JavaVersions.Keys.currentJDK.value}\u001b[0m
          | Target JDK ${JavaVersions.Keys.javaPlatform.value} 
          | Scala   | ${activeScala212} | ${activeScala213} | ${activeScala3}  |
          |---------|---------|---------|--------|
          | JS      |   ${if (containsJS2_12) "✅" else "❌"}    |   ${if (containsJS2_13) "✅" else "❌"}    |   ${if (containsJS3) "✅" else "❌"}   |
          | JVM     |   ${if (containsJVM2_12) "✅" else "❌"}    |   ${if (containsJVM2_13) "✅" else "❌"}    |   ${if (containsJVM3) "✅" else "❌"}   |
          | Native  |   ${if (containsNative2_12) "✅" else "❌"}    |   ${if (containsNative2_13) "✅" else "❌"}    |   ${if (containsNative3) "✅" else "❌"}   |
        """.stripMargin.stripLeading()
        // format: on
        )
    )
  }

}
