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
 * One dependency filter used by `updates.pin` / `updates.allow` /
 * `updates.ignore` in the scaffolded `.scala-steward.conf`.
 * `artifactId`/`version` are optional: an entry with only `groupId` matches
 * every artifact in that group. `pin`/`ignore` conventionally also give
 * `version` (a prefix to lock to, or exclude); `allow` conventionally does not.
 */
case class ScalaStewardDependency(
  groupId: String,
  artifactId: Option[String] = None,
  version: Option[String] = None
)

/**
 * One `pullRequests.grouping` match criterion. `version` is expected to be
 * "major", "minor" or "patch"; `group` is a groupId, which may be a glob such
 * as `"org.typelevel"` or `"*"`. An entry setting both requires both to match,
 * and the entries of a rule's `filter` list are alternatives.
 */
case class ScalaStewardGroupFilter(group: Option[String] = None, version: Option[String] = None)

/**
 * One named `pullRequests.grouping` rule: updates matching any filter in
 * `filter` are bundled into a single pull request named `name`, instead of one
 * PR per dependency.
 */
case class ScalaStewardGroupingRule(name: String, filter: Seq[ScalaStewardGroupFilter])

/**
 * Full `.scala-steward.conf` document. Every field defaults to empty or `None`,
 * and `ScalaStewardConfig.render` returns `None` for a configuration that
 * leaves all of them at those defaults.
 */
case class ScalaStewardConfig(
  pins: Seq[ScalaStewardDependency] = Seq.empty,
  allow: Seq[ScalaStewardDependency] = Seq.empty,
  ignore: Seq[ScalaStewardDependency] = Seq.empty,
  updatesLimit: Option[Int] = None,
  updatePullRequests: Option[String] = None,
  commitMessage: Option[String] = None,
  pullRequestsFrequency: Option[String] = None,
  grouping: Seq[ScalaStewardGroupingRule] = Seq.empty,
  assignees: Seq[String] = Seq.empty,
  reviewers: Seq[String] = Seq.empty
)

/** Rendering of [[ScalaStewardConfig]] as a `.scala-steward.conf` document. */
object ScalaStewardConfig {

  private def hoconStr(s: String): String =
    "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\""

  private def renderDependency(d: ScalaStewardDependency): String = {
    val fields = Seq(
      Some("groupId" -> hoconStr(d.groupId)),
      d.artifactId.map(a => "artifactId" -> hoconStr(a)),
      d.version.map(v => "version" -> hoconStr(v))
    ).flatten
    "{ " + fields.map { case (k, v) => s"$k = $v" }.mkString(", ") + " }"
  }

  private def renderDependencyList(entries: Seq[ScalaStewardDependency]): String =
    "[\n" + entries.map(d => "  " + renderDependency(d)).mkString(",\n") + "\n]"

  private def renderGroupFilter(f: ScalaStewardGroupFilter): String = {
    val fields = Seq(
      f.group.map(g => "\"group\"" -> hoconStr(g)),
      f.version.map(v => "\"version\"" -> hoconStr(v))
    ).flatten
    "{ " + fields.map { case (k, v) => s"$k = $v" }.mkString(", ") + " }"
  }

  private def renderGroupingRule(r: ScalaStewardGroupingRule): String =
    s"{ name = ${hoconStr(r.name)}, filter = [${r.filter.map(renderGroupFilter).mkString(", ")}] }"

  private def renderStringList(values: Seq[String]): String =
    "[" + values.map(hoconStr).mkString(", ") + "]"

  /**
   * Renders the HOCON document, omitting every field left at its default/empty
   * value. Returns `None` (rather than `Some("")`) when the whole config is
   * default.
   */
  def render(config: ScalaStewardConfig): Option[String] = {
    val lines = Seq(
      if (config.pins.nonEmpty) Some(s"updates.pin = ${renderDependencyList(config.pins)}") else None,
      if (config.allow.nonEmpty) Some(s"updates.allow = ${renderDependencyList(config.allow)}") else None,
      if (config.ignore.nonEmpty) Some(s"updates.ignore = ${renderDependencyList(config.ignore)}") else None,
      config.updatesLimit.map(n => s"updates.limit = $n"),
      config.updatePullRequests.map(v => s"updatePullRequests = ${hoconStr(v)}"),
      config.commitMessage.map(v => s"commits.message = ${hoconStr(v)}"),
      config.pullRequestsFrequency.map(v => s"pullRequests.frequency = ${hoconStr(v)}"),
      if (config.grouping.nonEmpty)
        Some(
          "pullRequests.grouping = [\n" +
            config.grouping.map(r => "  " + renderGroupingRule(r)).mkString(",\n") +
            "\n]"
        )
      else None,
      if (config.assignees.nonEmpty) Some(s"assignees = ${renderStringList(config.assignees)}") else None,
      if (config.reviewers.nonEmpty) Some(s"reviewers = ${renderStringList(config.reviewers)}") else None
    ).flatten

    if (lines.isEmpty) None else Some(lines.mkString("\n") + "\n")
  }
}
