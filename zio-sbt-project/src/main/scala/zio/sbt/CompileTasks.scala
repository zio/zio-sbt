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

object CompileTasks {

  import BuildAssertions.Keys._

  trait Keys {

    lazy val compile2_12: TaskKey[Unit] = taskKey[Unit]("Compile all Scala 2.12")
    lazy val compile2_13: TaskKey[Unit] = taskKey[Unit]("Compile all Scala 2.13")
    lazy val compile3: TaskKey[Unit]    = taskKey[Unit]("Compile all Scala 3")

    lazy val compileJS: TaskKey[Unit]     = taskKey[Unit]("Compile all JS projects")
    lazy val compileJS2_12: TaskKey[Unit] = taskKey[Unit]("Compile all JS 2.12 projects")
    lazy val compileJS2_13: TaskKey[Unit] = taskKey[Unit]("Compile all JS 2.13 projects")
    lazy val compileJS3: TaskKey[Unit]    = taskKey[Unit]("Compile all JS 3 projects")

    lazy val compileJVM: TaskKey[Unit]     = taskKey[Unit]("Compile all the JVM projects")
    lazy val compileJVM2_12: TaskKey[Unit] = taskKey[Unit]("Compile all JVM 2.12 projects")
    lazy val compileJVM2_13: TaskKey[Unit] = taskKey[Unit]("Compile all JVM 2.13 projects")
    lazy val compileJVM3: TaskKey[Unit]    = taskKey[Unit]("Compile all JVM 3 projects")

    lazy val compileNative: TaskKey[Unit]     = taskKey[Unit]("Compile all Native projects")
    lazy val compileNative2_12: TaskKey[Unit] = taskKey[Unit]("Compile all Native 2.12 projects")
    lazy val compileNative2_13: TaskKey[Unit] = taskKey[Unit]("Compile all Native 2.13 projects")
    lazy val compileNative3: TaskKey[Unit]    = taskKey[Unit]("Compile all Native 3 projects")

  }

  object Keys extends Keys

  import Keys._

  def compileOn(
    condition: Def.Initialize[Boolean],
    label: Option[Def.Initialize[Task[String]]] = None
  ): Def.Initialize[Task[Unit]] =
    Def.taskIf {
      if (condition.value) {
        val name = label.getOrElse(Def.task("")).value
        if (name.nonEmpty) sLog.value.info("Compiling " + name)
        val _ = (Scope.ThisScope / compile).value
      } else ()
    }

  def compile2_12Task: Def.Setting[Task[Unit]] = compile2_12 := compileOn(
    isScala2_12,
    Some(thisProject.?.map(_.map(_.id).getOrElse("")))
  ).value

  def compile2_13Task: Def.Setting[Task[Unit]] = compile2_13 := compileOn(
    isScala2_13,
    Some(thisProject.?.map(_.map(_.id).getOrElse("")))
  ).value

  def compile3Task: Def.Setting[Task[Unit]] = compile3 := compileOn(
    isScala3,
    Some(thisProject.?.map(_.map(_.id).getOrElse("")))
  ).value

  def compileJSTask: Def.Setting[Task[Unit]] =
    compileJS := compileOn(isScalaJS, Some(thisProject.?.map(_.map(_.id).getOrElse("")))).value
  def compileJS2_12Task: Def.Setting[Task[Unit]] = compileJS2_12 := compileOn(
    isScalaJS.zip(isScala2_12) { case (a, b) => a && b },
    Some(thisProject.?.map(_.map(_.id).getOrElse("")))
  ).value
  def compileJS2_13Task: Def.Setting[Task[Unit]] = compileJS2_13 := compileOn(
    isScalaJS.zip(isScala2_13) { case (a, b) => a && b },
    Some(thisProject.?.map(_.map(_.id).getOrElse("")))
  ).value
  def compileJS3Task: Def.Setting[Task[Unit]] = compileJS3 := compileOn(
    isScalaJS.zip(isScala3) { case (a, b) => a && b },
    Some(thisProject.?.map(_.map(_.id).getOrElse("")))
  ).value

  def compileJVMTask: Def.Setting[Task[Unit]] =
    compileJVM := compileOn(isScalaJVM, Some(thisProject.?.map(_.map(_.id).getOrElse("")))).value
  def compileJVM2_12Task: Def.Setting[Task[Unit]] = compileJVM2_12 := compileOn(
    isScalaJVM.zip(isScala2_12) { case (a, b) => a && b },
    Some(thisProject.?.map(_.map(_.id).getOrElse("")))
  ).value
  def compileJVM2_13Task: Def.Setting[Task[Unit]] = compileJVM2_13 := compileOn(
    isScalaJVM.zip(isScala2_13) { case (a, b) => a && b },
    Some(thisProject.?.map(_.map(_.id).getOrElse("")))
  ).value
  def compileJVM3Task: Def.Setting[Task[Unit]] = compileJVM3 := compileOn(
    isScalaJVM.zip(isScala3) { case (a, b) => a && b },
    Some(thisProject.?.map(_.map(_.id).getOrElse("")))
  ).value

  def compileNativeTask: Def.Setting[Task[Unit]] =
    compileNative := compileOn(isScalaNative, Some(thisProject.?.map(_.map(_.id).getOrElse("")))).value
  def compileNative2_12Task: Def.Setting[Task[Unit]] = compileNative2_12 := compileOn(
    isScalaNative.zip(isScala2_12) { case (a, b) => a && b },
    Some(thisProject.?.map(_.map(_.id).getOrElse("")))
  ).value
  def compileNative2_13Task: Def.Setting[Task[Unit]] = compileNative2_13 := compileOn(
    isScalaNative.zip(isScala2_13) { case (a, b) => a && b },
    Some(thisProject.?.map(_.map(_.id).getOrElse("")))
  ).value
  def compileNative3Task: Def.Setting[Task[Unit]] = compileNative3 := compileOn(
    isScalaNative.zip(isScala3) { case (a, b) => a && b },
    Some(thisProject.?.map(_.map(_.id).getOrElse("")))
  ).value

  def settings: Seq[Setting[Task[Unit]]] = Seq(
    compile2_12Task,
    compile2_13Task,
    compile3Task,
    compileJSTask,
    compileJS2_12Task,
    compileJS2_13Task,
    compileJS3Task,
    compileJVMTask,
    compileJVM2_12Task,
    compileJVM2_13Task,
    compileJVM3Task,
    compileNativeTask,
    compileNative2_12Task,
    compileNative2_13Task,
    compileNative3Task
  )

  val docs: Def.Initialize[Seq[(String, String)]] = Def.settingDyn {
    val contains2_12       = BuildAssertions.contains2_12.value
    val contains2_13       = BuildAssertions.contains2_13.value
    val contains3          = BuildAssertions.contains3.value
    val containsJS         = BuildAssertions.containsJS.value
    val containsJS2_12     = BuildAssertions.containsJS2_12.value
    val containsJS2_13     = BuildAssertions.containsJS2_13.value
    val containsJS3        = BuildAssertions.containsJS3.value
    val containsJVM        = BuildAssertions.containsJVM.value
    val containsJVM2_12    = BuildAssertions.containsJVM2_12.value
    val containsJVM2_13    = BuildAssertions.containsJVM2_13.value
    val containsJVM3       = BuildAssertions.containsJVM3.value
    val containsNative     = BuildAssertions.containsNative.value
    val containsNative2_12 = BuildAssertions.containsNative2_12.value
    val containsNative2_13 = BuildAssertions.containsNative2_13.value
    val containsNative3    = BuildAssertions.containsNative3.value

    Def.setting(
      Seq(
        Seq("compile" -> "Compile all projects"),
        if (contains2_12) Seq(compile2_12Task.key.key.label -> compile2_12Task.key.key.description.getOrElse(""))
        else Seq.empty,
        if (contains2_13) Seq(compile2_13Task.key.key.label -> compile2_13Task.key.key.description.getOrElse(""))
        else Seq.empty,
        if (contains3) Seq(compile3Task.key.key.label -> compile3Task.key.key.description.getOrElse(""))
        else Seq.empty,
        if (containsJS) Seq(compileJSTask.key.key.label -> compileJSTask.key.key.description.getOrElse(""))
        else Seq.empty,
        if (containsJS2_12) Seq(compileJS2_12Task.key.key.label -> compileJS2_12Task.key.key.description.getOrElse(""))
        else Seq.empty,
        if (containsJS2_13) Seq(compileJS2_13Task.key.key.label -> compileJS2_13Task.key.key.description.getOrElse(""))
        else Seq.empty,
        if (containsJS3) Seq(compileJS3Task.key.key.label -> compileJS3Task.key.key.description.getOrElse(""))
        else Seq.empty,
        if (containsJVM)
          Seq(compileJVMTask.key.key.label -> compileJVMTask.key.key.description.getOrElse(""))
        else Seq.empty,
        if (containsJVM2_12)
          Seq(compileJVM2_12Task.key.key.label -> compileJVM2_12Task.key.key.description.getOrElse(""))
        else Seq.empty,
        if (containsJVM2_13)
          Seq(compileJVM2_13Task.key.key.label -> compileJVM2_13Task.key.key.description.getOrElse(""))
        else Seq.empty,
        if (containsJVM3) Seq(compileJVM3Task.key.key.label -> compileJVM3Task.key.key.description.getOrElse(""))
        else Seq.empty,
        if (containsNative)
          Seq(compileNativeTask.key.key.label -> compileNativeTask.key.key.description.getOrElse(""))
        else Seq.empty,
        if (containsNative2_12)
          Seq(compileNative2_12Task.key.key.label -> compileNative2_12Task.key.key.description.getOrElse(""))
        else Seq.empty,
        if (containsNative2_13)
          Seq(compileNative2_13Task.key.key.label -> compileNative2_13Task.key.key.description.getOrElse(""))
        else Seq.empty,
        if (containsNative3)
          Seq(compileNative3Task.key.key.label -> compileNative3Task.key.key.description.getOrElse(""))
        else Seq.empty
      ).flatten
    )
  }

}
