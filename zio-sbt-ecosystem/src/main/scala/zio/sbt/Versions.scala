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

import sbt._

/**
 * The default versions that the plugin uses for Scala, ZIO and the compiler
 * plugins and scalafix rules it adds.
 */
object Versions {

  /**
   * The version of the `kind-projector` compiler plugin that `stdSettings` adds
   * on Scala 2.
   */
  val KindProjectorVersion = "0.13.4"

  /**
   * The version of the `scaluzzi` scalafix rules that `scalafixSettings` adds
   * to `scalafixDependencies`.
   */
  val ScaluzziVersion = "0.1.23"

  /** The default value of the `scala3` key. */
  val scala3 = "3.9.0"

  /** The default value of the `scala212` key. */
  val scala212 = "2.12.21"

  /**
   * The default value of the `scala213` key, which is also the default
   * `scalaVersion`.
   */
  val scala213 = "2.13.18"

  /**
   * The default value of the `zioVersion` key, used by `enableZIO` for the ZIO
   * dependencies.
   */
  val zioVersion = "2.1.22"

  /**
   * The `better-monadic-for` compiler plugin module that `stdSettings` adds on
   * Scala 2.
   */
  lazy val betterMonadFor: ModuleID = "com.olegpy" %% "better-monadic-for" % "0.3.1"
}
