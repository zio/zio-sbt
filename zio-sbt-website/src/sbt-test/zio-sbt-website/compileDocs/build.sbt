lazy val root = (project in file("."))
  .settings(
    version := "0.1",
  ).enablePlugins(WebsitePlugin)
