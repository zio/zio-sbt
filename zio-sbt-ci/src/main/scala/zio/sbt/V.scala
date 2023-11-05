package zio.sbt

object V {
  def apply(packageName: String): String =
    Map(
      "peter-evans/create-pull-request" -> "v5.0.2",
      "zio/generate-github-app-token"   -> "v1.0.0",
      "pierotofy/set-swap-space"        -> "master",
      "actions/checkout"                -> "v4.1.1",
      "actions/setup-java"              -> "v3.13.0",
      "coursier/cache-action"           -> "v6",
      "actions/setup-node"              -> "v4"
    ).map { case (k, v) => (k, s"$k@$v") }.apply(packageName)
}
