package zio.sbt

object V {
  def apply(packageName: String): String =
    Map(
      "peter-evans/create-pull-request" -> "v7",
      "zio/generate-github-app-token"   -> "v1.0.0",
      "pierotofy/set-swap-space"        -> "master",
      "actions/checkout"                -> "v5",
      "coursier/cache-action"           -> "v6",
      "actions/setup-java"              -> "v5",
      "actions/setup-node"              -> "v5.0.0",
      "sbt/setup-sbt"                   -> "v1"
    ).map { case (k, v) => (k, s"$k@$v") }.apply(packageName)
}
