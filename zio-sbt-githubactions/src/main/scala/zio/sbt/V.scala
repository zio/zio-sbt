package zio.sbt

object V {
  def apply(packageName: String): String =
    Map(
      "peter-evans/create-pull-request"        -> "v8.1.1",
      "zio/generate-github-app-token"          -> "v1.0.0",
      "pierotofy/set-swap-space"               -> "v1.0",
      "actions/checkout"                       -> "v7.0.1",
      "coursier/cache-action"                  -> "v8.1.1",
      "actions/setup-java"                     -> "v6.0.1",
      "actions/setup-node"                     -> "v7.0.0",
      "sbt/setup-sbt"                          -> "v1.5.7",
      "actions/upload-artifact"                -> "v4",
      "actions/download-artifact"              -> "v8",
      "nwtgck/actions-netlify"                 -> "v4.0",
      "actions/github-script"                  -> "v9",
      "peter-evans/create-or-update-comment"   -> "v5",
      "release-drafter/release-drafter"        -> "v7",
      "scala-steward-org/scala-steward-action" -> "v2.96.0",
      "actions/cache"                          -> "v6.1.0",
      "olafurpg/setup-scala"                   -> "v11",
      "olafurpg/setup-gpg"                     -> "v3",
      "fregante/setup-git-user"                -> "v1",
      "softprops/turnstyle"                    -> "v1",
      "jwalton/gh-docker-logs"                 -> "v1"
    ).map { case (k, v) => (k, s"$k@$v") }.apply(packageName)
}
