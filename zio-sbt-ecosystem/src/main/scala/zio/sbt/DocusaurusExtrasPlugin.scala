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

import scala.sys.process._

import mdoc.DocusaurusPlugin.website
import sbt.Keys._
import sbt._
import sbt.complete.DefaultParsers._

object DocusaurusExtrasPlugin extends AutoPlugin {

  override def requires = mdoc.DocusaurusPlugin

  val docusaurus: InputKey[Unit] = inputKey[Unit]("docusaurus")

  val docusaurusImpl: Def.Initialize[InputTask[Unit]] = Def.inputTask[Unit] {

    val log               = streams.value.log
    val args: Seq[String] = spaceDelimited("<arg>").parsed

    val plogger = ProcessLogger(log.info(_), log.error(_))

    try {
      Process(List("yarn", "install"), cwd = website.value).!(plogger)
      Process(List("yarn", "run") ++ args, cwd = website.value).!(plogger)

    } catch {

      case e: Throwable =>
        log.error(s"Failed to execute docusaurus command.  Cause: ${e.getMessage}")
    } finally log.info(s"Docusaurus finished")

  }

  override def projectSettings: Seq[Setting[_]] =
    Seq(
      docusaurus := docusaurusImpl.evaluated
    )

}
