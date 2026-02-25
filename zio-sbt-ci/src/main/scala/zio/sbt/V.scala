package zio.sbt

object V {
  def apply(packageName: String): String =
    Map(
      "peter-evans/create-pull-request" -> "v8",
      "zio/generate-github-app-token"   -> "v1.0.0",
      "pierotofy/set-swap-space"        -> "master",
      "actions/checkout"                -> "v6",
      "coursier/cache-action"           -> "v8",
      "actions/setup-java"              -> "v5",
      "actions/setup-node"              -> "v6",
      "sbt/setup-sbt"                   -> "v1"
    ).map { case (k, v) => (k, s"$k@$v") }.apply(packageName)
}
