package zio.sbt

object V {
  def apply(packageName: String): String =
    Map(
      "peter-evans/create-pull-request" -> "v5.0.0",
      "zio/generate-github-app-token"   -> "v1.0.0",
      "pierotofy/set-swap-space"        -> "master",
      "actions/checkout"                -> "v3.6.0",
      "actions/setup-java"              -> "v3.12.0",
      "coursier/cache-action"           -> "v6",
      "actions/setup-node"              -> "v3"
    ).map { case (k, v) => (k, s"$k@$v") }.apply(packageName)
}
