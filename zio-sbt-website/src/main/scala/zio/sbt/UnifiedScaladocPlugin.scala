/*
 * Copyright 2022 dev.zio
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
