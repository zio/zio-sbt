lazy val root = (project in file("."))
  .settings(
    version := "0.1",
    projectName := "ZIO SBT",
    mainModuleName := "test-project",
    projectStage   := ProjectStage.ProductionReady,
  ).enablePlugins(WebsitePlugin)
