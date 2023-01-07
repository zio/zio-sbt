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

trait Versions {

  object versions {

    val cassandraDriverVersion               = "4.14.1"
    val scala213Version                      = "2.13.10"
    val scala212Version                      = "2.12.17"
    val scala3Version                        = "3.2.1"
    val supportedScalaVersions: List[String] = List(scala213Version, scala212Version, scala3Version)
    val zio1xVersion                         = "1.0.15"
    val zio2xVersion                         = "2.0.4"

    private val versions: Map[String, String] = {
      import org.snakeyaml.engine.v2.api.{Load, LoadSettings}

      import java.util.{List => JList, Map => JMap}
      import scala.jdk.CollectionConverters._

      try {
        val doc = new Load(LoadSettings.builder().build())
          .loadFromReader(scala.io.Source.fromFile(".github/workflows/ci.yml").bufferedReader())
        val yaml = doc.asInstanceOf[JMap[String, JMap[String, JMap[String, JMap[String, JMap[String, JList[String]]]]]]]

        val list = yaml
          .getOrDefault("jobs", Map.empty.asJava)
          .getOrDefault("build", Map.empty.asJava)
          .getOrDefault("strategy", Map.empty.asJava)
          .getOrDefault("matrix", Map.empty.asJava)
          .getOrDefault("scala", List.empty.asJava)
          .asScala

        val m1 = list.filter(_.startsWith("2")).map(v => (v.split('.').take(2).mkString("."), v)).toMap
        val m2 = list
          .filter(_.startsWith("3"))
          .map("3.x" -> _)
          .toMap
        m1 ++ m2
      } catch {
        case _: java.io.FileNotFoundException => Map.empty[String, String]
      }
    }

    val Scala212: String = versions.getOrElse("2.12", scala212Version)
    val Scala213: String = versions.getOrElse("2.13", scala213Version)
    val Scala3: String   = versions.getOrElse("3.x", scala3Version)
  }
}

object V extends Versions
