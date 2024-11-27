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
import com.typesafe.tools.mima.plugin.MimaKeys._
import sbt.Keys._
import sbt._
import sbtdynver.DynVerPlugin.autoImport.previousStableVersion

import zio.sbt.BuildAssertions.Keys.isScalaJVM

trait MimaSettings {

  lazy val checkMima: TaskKey[Unit] = taskKey[Unit]("Checks binary compatibility against previous versions.")
}

object MimaSettings extends MimaSettings {

  def projectSettings: Seq[Setting[_]] =
    Def.settings(
      checkMima := {
        Def.taskIf {
          if (isScalaJVM.value && !((checkMima / skip).value || (publish / skip).value)) mimaReportBinaryIssues.value
          else ()
        }.value
      },
      mimaFailOnProblem := true,
      mimaPreviousArtifacts := (ThisBuild / previousStableVersion).value
        .map(organization.value %% moduleName.value % _)
        .fold(Set.empty[ModuleID])(Set(_))
    )

}
