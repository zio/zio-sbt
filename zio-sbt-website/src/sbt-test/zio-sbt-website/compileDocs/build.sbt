lazy val root = (project in file("."))
  .settings(
    version        := "0.1",
    projectName    := "ZIO SBT",
    mainModuleName := "test-project",
    projectStage   := ProjectStage.ProductionReady,
    docsVersion := version.value,
    publishToNpm := {}
  )
  .enablePlugins(WebsitePlugin)
