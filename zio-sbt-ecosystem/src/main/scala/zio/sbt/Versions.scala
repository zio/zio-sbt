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

import sbt.*

object Versions {
  val KindProjectorVersion = "0.13.3"
  val ScaluzziVersion      = "0.1.23"

  val scala3   = "3.3.1"
  val scala212 = "2.12.19"
  val scala213 = "2.13.12"

  val zioVersion = "2.0.17"

  lazy val betterMonadFor: ModuleID = "com.olegpy" %% "better-monadic-for" % "0.3.1"
}
