package zio.sbt

object V {
  def apply(packageName: String): String =
    Map(
      "peter-evans/create-pull-request" -> "v6.0.3",
      "zio/generate-github-app-token"   -> "v1.0.0",
      "pierotofy/set-swap-space"        -> "master",
      "actions/checkout"                -> "v4.1.2",
      "coursier/cache-action"           -> "v6.4.6",
      "actions/setup-java"              -> "v4.2.1",
      "actions/setup-node"              -> "v4"
    ).map { case (k, v) => (k, s"$k@$v") }.apply(packageName)
}
