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

import sbt.{JavaVersion => _, _}

import zio.sbt.ZioSbtShared.autoImport.{banners, usefulTasksAndSettings}

object ZioSbtCrossbuildPlugin extends AutoPlugin {

  override def trigger = allRequirements

  override def requires: Plugins = super.requires && ZioSbtShared

  object autoImport
      extends BuildAssertions.Keys
      with CompileTasks.Keys
      with TestTasks.Keys
      with ScalaVersions.Keys
      with ScalaPlatforms.Keys
      with JavaVersions.Keys {

    def zioSbtCrossbuildSettings: Seq[Setting[_]] =
      BuildAssertions.settings ++
        inConfig(Compile)(CompileTasks.settings) ++
        inConfig(Test)(CompileTasks.settings) ++
        inConfig(Test)(TestTasks.settings)
  }

  import autoImport.*

  override def projectSettings: Seq[Setting[_]] = zioSbtCrossbuildSettings ++ Seq(
    banners ++= {
      if (SharedTasks.isRoot.value) BuildAssertions.docs.value
      else Seq.empty
    },
    usefulTasksAndSettings ++= {
      if (SharedTasks.isRoot.value) {
        CompileTasks.docs.value ++ TestTasks.docs.value
      } else Seq.empty
    }
  )

  override def buildSettings: Seq[Def.Setting[_]] =
    super.buildSettings ++
      ScalaVersions.buildSettings ++
      JavaVersions.buildSettings

  override def globalSettings: Seq[Def.Setting[_]] =
    super.globalSettings ++
      ScalaVersions.globalSettings ++
      ScalaPlatforms.globalSettings ++
      JavaVersions.globalSettings
}
