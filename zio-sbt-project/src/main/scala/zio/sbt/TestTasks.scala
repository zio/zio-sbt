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

object TestTasks {

  import BuildAssertions.Keys._

  trait Keys {

    lazy val test2_12: TaskKey[Unit] = taskKey[Unit]("Test all Scala 2.12")
    lazy val test2_13: TaskKey[Unit] = taskKey[Unit]("Test all Scala 2.13")
    lazy val test3: TaskKey[Unit]    = taskKey[Unit]("Test all Scala 3")

    lazy val testJS: TaskKey[Unit]     = taskKey[Unit]("Test all JS")
    lazy val testJS2_12: TaskKey[Unit] = taskKey[Unit]("Test all JS 2.12")
    lazy val testJS2_13: TaskKey[Unit] = taskKey[Unit]("Test all JS 2.13")
    lazy val testJS3: TaskKey[Unit]    = taskKey[Unit]("Test all JS 3")

    lazy val testJVM: TaskKey[Unit]     = taskKey[Unit]("Test all the JVM")
    lazy val testJVM2_12: TaskKey[Unit] = taskKey[Unit]("Test all JVM 2.12")
    lazy val testJVM2_13: TaskKey[Unit] = taskKey[Unit]("Test all JVM 2.13")
    lazy val testJVM3: TaskKey[Unit]    = taskKey[Unit]("Test all JVM 3")

    lazy val testNative: TaskKey[Unit]     = taskKey[Unit]("Test all Native")
    lazy val testNative2_12: TaskKey[Unit] = taskKey[Unit]("Test all Native 2.12")
    lazy val testNative2_13: TaskKey[Unit] = taskKey[Unit]("Test all Native 2.13")
    lazy val testNative3: TaskKey[Unit]    = taskKey[Unit]("Test all Native 3")

  }

  object Keys extends Keys

  def scalaVersionToSuffix(scalaVersion: String): String =
    CrossVersion.partialVersion(scalaVersion) match {
      case Some((3, _))  => "3"
      case Some((2, 13)) => "2_13"
      case Some((2, 12)) => "2_12"
      case _             => throw new Exception(s"Unsupported Scala version: $scalaVersion")
    }

  import Keys._

  def testOn(
    condition: Def.Initialize[Boolean],
    label: Option[Def.Initialize[Task[String]]] = None
  ): Def.Initialize[Task[Unit]] =
    Def.taskIf {
      if (condition.value) {
        val name = label.getOrElse(Def.task("")).value

        if (name.nonEmpty) sLog.value.info("Running tests for " + name)

        test.in(Scope.ThisScope).value
      } else ()
    }

  def test2_12Task: Def.Setting[Task[Unit]] = test2_12 := testOn(
    isScala2_12,
    Some(thisProject.?.map(_.map(_.id).getOrElse("")))
  ).value

  def test2_13Task: Def.Setting[Task[Unit]] = test2_13 := testOn(
    isScala2_13,
    Some(thisProject.?.map(_.map(_.id).getOrElse("")))
  ).value

  def test3Task: Def.Setting[Task[Unit]] = test3 := testOn(
    isScala3,
    Some(thisProject.?.map(_.map(_.id).getOrElse("")))
  ).value

  def testJSTask: Def.Setting[Task[Unit]] =
    testJS := testOn(isScalaJS, Some(thisProject.?.map(_.map(_.id).getOrElse("")))).value
  def testJS2_12Task: Def.Setting[Task[Unit]] = testJS2_12 := testOn(
    isScalaJS.zip(isScala2_12) { case (a, b) => a && b },
    Some(thisProject.?.map(_.map(_.id).getOrElse("")))
  ).value
  def testJS2_13Task: Def.Setting[Task[Unit]] = testJS2_13 := testOn(
    isScalaJS.zip(isScala2_13) { case (a, b) => a && b },
    Some(thisProject.?.map(_.map(_.id).getOrElse("")))
  ).value
  def testJS3Task: Def.Setting[Task[Unit]] = testJS3 := testOn(
    isScalaJS.zip(isScala3) { case (a, b) => a && b },
    Some(thisProject.?.map(_.map(_.id).getOrElse("")))
  ).value

  def testJVMTask: Def.Setting[Task[Unit]] =
    testJVM := testOn(isScalaJVM, Some(thisProject.?.map(_.map(_.id).getOrElse("")))).value
  def testJVM2_12Task: Def.Setting[Task[Unit]] = testJVM2_12 := testOn(
    isScalaJVM.zip(isScala2_12) { case (a, b) => a && b },
    Some(thisProject.?.map(_.map(_.id).getOrElse("")))
  ).value
  def testJVM2_13Task: Def.Setting[Task[Unit]] = testJVM2_13 := testOn(
    isScalaJVM.zip(isScala2_13) { case (a, b) => a && b },
    Some(thisProject.?.map(_.map(_.id).getOrElse("")))
  ).value
  def testJVM3Task: Def.Setting[Task[Unit]] = testJVM3 := testOn(
    isScalaJVM.zip(isScala3) { case (a, b) => a && b },
    Some(thisProject.?.map(_.map(_.id).getOrElse("")))
  ).value

  def testNativeTask: Def.Setting[Task[Unit]] =
    testNative := testOn(isScalaNative, Some(thisProject.?.map(_.map(_.id).getOrElse("")))).value
  def testNative2_12Task: Def.Setting[Task[Unit]] = testNative2_12 := testOn(
    isScalaNative.zip(isScala2_12) { case (a, b) => a && b },
    Some(thisProject.?.map(_.map(_.id).getOrElse("")))
  ).value
  def testNative2_13Task: Def.Setting[Task[Unit]] = testNative2_13 := testOn(
    isScalaNative.zip(isScala2_13) { case (a, b) => a && b },
    Some(thisProject.?.map(_.map(_.id).getOrElse("")))
  ).value
  def testNative3Task: Def.Setting[Task[Unit]] = testNative3 := testOn(
    isScalaNative.zip(isScala3) { case (a, b) => a && b },
    Some(thisProject.?.map(_.map(_.id).getOrElse("")))
  ).value

  def settings: Seq[Setting[Task[Unit]]] = Seq(
    test2_12Task,
    test2_13Task,
    test3Task,
    testJSTask,
    testJS2_12Task,
    testJS2_13Task,
    testJS3Task,
    testJVMTask,
    testJVM2_12Task,
    testJVM2_13Task,
    testJVM3Task,
    testNativeTask,
    testNative2_12Task,
    testNative2_13Task,
    testNative3Task
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
      Seq("test" -> "Test all projects") ++
        Seq(
          if (contains2_12) Seq(test2_12Task.key.key.label -> test2_12Task.key.key.description.getOrElse(""))
          else Seq.empty,
          if (contains2_13) Seq(test2_13Task.key.key.label -> test2_13Task.key.key.description.getOrElse(""))
          else Seq.empty,
          if (contains3) Seq(test3Task.key.key.label -> test3Task.key.key.description.getOrElse("")) else Seq.empty,
          if (containsJS) Seq(testJSTask.key.key.label -> testJSTask.key.key.description.getOrElse("")) else Seq.empty,
          if (containsJS2_12) Seq(testJS2_12Task.key.key.label -> testJS2_12Task.key.key.description.getOrElse(""))
          else Seq.empty,
          if (containsJS2_13) Seq(testJS2_13Task.key.key.label -> testJS2_13Task.key.key.description.getOrElse(""))
          else Seq.empty,
          if (containsJS3) Seq(testJS3Task.key.key.label -> testJS3Task.key.key.description.getOrElse(""))
          else Seq.empty,
          if (containsJVM) Seq(testJVMTask.key.key.label -> testJVMTask.key.key.description.getOrElse(""))
          else Seq.empty,
          if (containsJVM2_12) Seq(testJVM2_12Task.key.key.label -> testJVM2_12Task.key.key.description.getOrElse(""))
          else Seq.empty,
          if (containsJVM2_13) Seq(testJVM2_13Task.key.key.label -> testJVM2_13Task.key.key.description.getOrElse(""))
          else Seq.empty,
          if (containsJVM3) Seq(testJVM3Task.key.key.label -> testJVM3Task.key.key.description.getOrElse(""))
          else Seq.empty,
          if (containsNative) Seq(testNativeTask.key.key.label -> testNativeTask.key.key.description.getOrElse(""))
          else Seq.empty,
          if (containsNative2_12)
            Seq(testNative2_12Task.key.key.label -> testNative2_12Task.key.key.description.getOrElse(""))
          else Seq.empty,
          if (containsNative2_13)
            Seq(testNative2_13Task.key.key.label -> testNative2_13Task.key.key.description.getOrElse(""))
          else Seq.empty,
          if (containsNative3) Seq(testNative3Task.key.key.label -> testNative3Task.key.key.description.getOrElse(""))
          else Seq.empty
        ).flatten
        ++ Seq(
          """testOnly *.YourSpec -- -t \"YourLabel\"""" -> "Only runs tests with matching term e.g."
        )
    )
  }
}
