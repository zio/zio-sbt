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

import zio.json._
import zio.json.ast.Json

/**
 * One release-drafter category: PRs carrying any of `labels` are bucketed under
 * `title`.
 */
case class ReleaseDrafterCategory(title: String, labels: Seq[String])

/** The JSON encoder of [[ReleaseDrafterCategory]]. */
object ReleaseDrafterCategory {

  /** Encodes a category as an object with the keys `title` and `labels`. */
  implicit val encoder: JsonEncoder[ReleaseDrafterCategory] =
    JsonEncoder[Json].contramap { c =>
      Json.Obj(
        ("title", Json.Str(c.title)),
        ("labels", c.labels.toJsonAST.getOrElse(Json.Null))
      )
    }
}

/**
 * Maps PR labels present since the last release to a semver bump, used to
 * compute
 * `\$RESOLVED_VERSION`/`\$NEXT_MAJOR_VERSION`/`\$NEXT_MINOR_VERSION`/`\$NEXT_PATCH_VERSION`.
 * `default` is the bump used when no label matches, `"patch"` unless set.
 */
case class ReleaseDrafterVersionResolver(
  major: Seq[String] = Seq("major"),
  minor: Seq[String] = Seq("minor"),
  patch: Seq[String] = Seq("patch"),
  default: String = "patch"
)

/** The JSON encoder of [[ReleaseDrafterVersionResolver]]. */
object ReleaseDrafterVersionResolver {

  /**
   * Encodes a resolver as the keys `major`, `minor` and `patch`, each with its
   * `labels`, plus `default`.
   */
  implicit val encoder: JsonEncoder[ReleaseDrafterVersionResolver] =
    JsonEncoder[Json].contramap { v =>
      Json.Obj(
        ("major", Json.Obj("labels" -> v.major.toJsonAST.getOrElse(Json.Null))),
        ("minor", Json.Obj("labels" -> v.minor.toJsonAST.getOrElse(Json.Null))),
        ("patch", Json.Obj("labels" -> v.patch.toJsonAST.getOrElse(Json.Null))),
        ("default", Json.Str(v.default))
      )
    }
}

/**
 * One autolabeler rule: release-drafter applies `label` to a pull request that
 * matches `files` (glob patterns) or `branch`, `title` or `body` (regular
 * expressions). Empty sequences are left out of the rendered YAML rather than
 * emitted as `[]`.
 */
case class ReleaseDrafterAutolabelerRule(
  label: String,
  files: Seq[String] = Seq.empty,
  branch: Seq[String] = Seq.empty,
  title: Seq[String] = Seq.empty,
  body: Seq[String] = Seq.empty
)

/** The JSON encoder of [[ReleaseDrafterAutolabelerRule]]. */
object ReleaseDrafterAutolabelerRule {

  /**
   * Encodes a rule as an object with `label`, and with `files`, `branch`,
   * `title` and `body` when they are not empty.
   */
  implicit val encoder: JsonEncoder[ReleaseDrafterAutolabelerRule] =
    JsonEncoder[Json].contramap { r =>
      Json.Obj(
        ("label", Json.Str(r.label)),
        ("files", if (r.files.nonEmpty) r.files.toJsonAST.getOrElse(Json.Null) else Json.Null),
        ("branch", if (r.branch.nonEmpty) r.branch.toJsonAST.getOrElse(Json.Null) else Json.Null),
        ("title", if (r.title.nonEmpty) r.title.toJsonAST.getOrElse(Json.Null) else Json.Null),
        ("body", if (r.body.nonEmpty) r.body.toJsonAST.getOrElse(Json.Null) else Json.Null)
      )
    }
}

/**
 * Full `.github/release-drafter.yml` document. The keys are rendered in the
 * order `name-template`, `tag-template`, `categories`, `exclude-labels`,
 * `change-template`, `change-title-escapes`, `autolabeler`, `version-resolver`,
 * `template`, `sort-by`, `sort-direction`.
 *
 * `exclude-labels` and `autolabeler` are left out when their sequences are
 * empty, and `change-title-escapes`, `version-resolver`, `sort-by` and
 * `sort-direction` when they are `None`. The name and tag templates default to
 * `v` followed by release-drafter's next patch version variable.
 */
case class ReleaseDrafterConfig(
  nameTemplate: String = "v$NEXT_PATCH_VERSION",
  tagTemplate: String = "v$NEXT_PATCH_VERSION",
  categories: Seq[ReleaseDrafterCategory] = Seq.empty,
  excludeLabels: Seq[String] = Seq.empty,
  changeTemplate: String = "- $TITLE @$AUTHOR (#$NUMBER)",
  changeTitleEscapes: Option[String] = None,
  autolabeler: Seq[ReleaseDrafterAutolabelerRule] = Seq.empty,
  versionResolver: Option[ReleaseDrafterVersionResolver] = None,
  template: String = "## Changes\n$CHANGES\n",
  sortBy: Option[String] = None,
  sortDirection: Option[String] = None
)

/** The JSON encoder of [[ReleaseDrafterConfig]]. */
object ReleaseDrafterConfig {

  /**
   * Encodes a configuration as the YAML keys of `.github/release-drafter.yml`,
   * in the order listed on [[ReleaseDrafterConfig]].
   */
  implicit val encoder: JsonEncoder[ReleaseDrafterConfig] =
    JsonEncoder[Json].contramap { c =>
      Json.Obj(
        ("name-template", Json.Str(c.nameTemplate)),
        ("tag-template", Json.Str(c.tagTemplate)),
        ("categories", c.categories.toJsonAST.getOrElse(Json.Null)),
        (
          "exclude-labels",
          if (c.excludeLabels.nonEmpty) c.excludeLabels.toJsonAST.getOrElse(Json.Null) else Json.Null
        ),
        ("change-template", Json.Str(c.changeTemplate)),
        ("change-title-escapes", c.changeTitleEscapes.toJsonAST.getOrElse(Json.Null)),
        (
          "autolabeler",
          if (c.autolabeler.nonEmpty) c.autolabeler.toJsonAST.getOrElse(Json.Null) else Json.Null
        ),
        ("version-resolver", c.versionResolver.toJsonAST.getOrElse(Json.Null)),
        ("template", Json.Str(c.template)),
        ("sort-by", c.sortBy.toJsonAST.getOrElse(Json.Null)),
        ("sort-direction", c.sortDirection.toJsonAST.getOrElse(Json.Null))
      )
    }
}
