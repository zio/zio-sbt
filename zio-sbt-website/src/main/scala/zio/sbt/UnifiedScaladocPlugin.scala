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
import sbtunidoc.ScalaUnidocPlugin

/**
 * An sbt plugin that sets `Compile / doc` to the `ScalaUnidoc / doc` task of
 * sbt-unidoc, so that the Scaladoc of a project is built for its aggregated
 * sources. It requires `ScalaUnidocPlugin` and is enabled per project (it has
 * no trigger of its own).
 */
object UnifiedScaladocPlugin extends sbt.AutoPlugin {
  override def requires = ScalaUnidocPlugin

  override def trigger = noTrigger

  import ScalaUnidocPlugin.autoImport._

  override def projectSettings: Seq[Setting[_]] =
    Seq(Compile / doc := (ScalaUnidoc / doc).value)
}
