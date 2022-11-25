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

trait Versions {

  object versions {

    val cassandraDriverVersion               = "4.14.1"
    val scala213Version                      = "2.13.8"
    val scala212Version                      = "2.12.16"
    val scala3Version                        = "3.1.3"
    val supportedScalaVersions: List[String] = List(scala213Version, scala212Version, scala3Version)
    val zio1xVersion                         = "1.0.15"
    val zio2xVersion                         = "2.0.4"

    private val versions: Map[String, String] = {
      import org.snakeyaml.engine.v2.api.{Load, LoadSettings}

      import java.util.{List => JList, Map => JMap}
      import scala.jdk.CollectionConverters._

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
      list.map(v => (v.split('.').take(2).mkString("."), v)).toMap
    }

    val Scala212: String = versions.getOrElse("2.12", scala212Version)
    val Scala213: String = versions.getOrElse("2.13", scala213Version)
    val Scala3: String   = versions.getOrElse("3.1", scala3Version)
  }
}

object V extends Versions
