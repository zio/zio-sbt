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

package zio.sbt.githubactions

/**
 * One named `groups` entry of a Dependabot `updates` item: updates matching
 * `patterns` and/or `updateTypes` ("major"/"minor"/"patch") are bundled into a
 * single pull request instead of one per dependency.
 */
case class DependabotGroup(
  name: String,
  patterns: Seq[String] = Seq.empty,
  updateTypes: Seq[String] = Seq.empty
)

/**
 * One item of the `updates` list in `.github/dependabot.yml`.
 */
case class DependabotUpdate(
  ecosystem: String,
  directory: String = "/",
  interval: String = "weekly",
  openPullRequestsLimit: Option[Int] = None,
  groups: Seq[DependabotGroup] = Seq.empty
)

/**
 * Full `.github/dependabot.yml` document. The default is a single weekly
 * `github-actions` update at the repository root, which is byte-for-byte what
 * 13 of the 14 surveyed ZIO-ecosystem repos that have a `dependabot.yml` use.
 */
case class DependabotConfig(
  updates: Seq[DependabotUpdate] = Seq(DependabotUpdate("github-actions"))
)

object DependabotConfig {

  private def q(s: String): String =
    "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\""

  private def renderGroup(g: DependabotGroup): Seq[String] = {
    def list(key: String, values: Seq[String]): Seq[String] =
      if (values.isEmpty) Seq.empty else s"        $key:" +: values.map(v => s"          - ${q(v)}")

    s"      ${g.name}:" +: (list("patterns", g.patterns) ++ list("update-types", g.updateTypes))
  }

  private def renderUpdate(u: DependabotUpdate): Seq[String] =
    Seq(
      s"  - package-ecosystem: ${q(u.ecosystem)}",
      s"    directory: ${q(u.directory)}",
      "    schedule:",
      s"      interval: ${q(u.interval)}"
    ) ++
      u.openPullRequestsLimit.map(n => s"    open-pull-requests-limit: $n") ++
      (if (u.groups.isEmpty) Seq.empty else "    groups:" +: u.groups.flatMap(renderGroup))

  /**
   * Renders the YAML document (without any header comment) with a trailing
   * newline.
   */
  def render(config: DependabotConfig): String =
    ("version: 2" +: "updates:" +: config.updates.flatMap(renderUpdate)).mkString("", "\n", "\n")
}
