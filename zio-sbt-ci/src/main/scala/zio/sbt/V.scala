package zio.sbt

object V {
  def apply(packageName: String): String =
    Map(
      "peter-evans/create-pull-request"        -> "v8.1.1",
      "zio/generate-github-app-token"          -> "v1.0.0",
      "pierotofy/set-swap-space"               -> "v1.0",
      "actions/checkout"                       -> "v7.0.1",
      "coursier/cache-action"                  -> "v8.1.1",
      "actions/setup-java"                     -> "v5.7.0",
      "actions/setup-node"                     -> "v7.0.0",
      "sbt/setup-sbt"                          -> "v1.5.7",
      "actions/upload-artifact"                -> "v4",
      "actions/download-artifact"              -> "v8",
      "nwtgck/actions-netlify"                 -> "v4.0",
      "marocchino/sticky-pull-request-comment" -> "v3"
    ).map { case (k, v) => (k, s"$k@$v") }.apply(packageName)
}
