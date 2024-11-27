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

import sbt.{ScalaVersion => _, _}

object Versions {
  val KindProjectorVersion = "0.13.3"
  val ScaluzziVersion      = "0.1.23"

  val Scala3   = ScalaVersion.Scala3
  val Scala212 = ScalaVersion.Scala212
  val Scala213 = ScalaVersion.Scala213

  val zioVersion = "2.1.13"

  lazy val betterMonadFor: ModuleID = "com.olegpy" %% "better-monadic-for" % "0.3.1"
}
