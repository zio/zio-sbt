package zio.sbt

object V {
  def apply(packageName: String): String =
    Map(
      "peter-evans/create-pull-request" -> "v7",
      "zio/generate-github-app-token"   -> "v1.0.0",
      "pierotofy/set-swap-space"        -> "49819abfb41bd9b44fb781159c033dba90353a7c", // 1.0,
      "actions/checkout"                -> "v4",
      "coursier/cache-action"           -> "v6",
      "actions/setup-java"              -> "v4",
      "actions/setup-node"              -> "v4",
      "sbt/setup-sbt"                   -> "v1"
    ).map { case (k, v) => (k, s"$k@$v") }.apply(packageName)
}
