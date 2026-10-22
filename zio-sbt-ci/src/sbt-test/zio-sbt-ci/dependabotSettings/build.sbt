import zio.sbt.githubactions.{DependabotConfig, DependabotGroup, DependabotUpdate}

ThisBuild / name := "Test Project"

ThisBuild / ciDependabotConfig := DependabotConfig(
  Seq(
    DependabotUpdate("github-actions"),
    DependabotUpdate(
      "npm",
      directory = "/website",
      interval = "daily",
      openPullRequestsLimit = Some(5),
      groups = Seq(
        DependabotGroup("docusaurus", patterns = Seq("@docusaurus/*", "react")),
        DependabotGroup("minor-and-patch", updateTypes = Seq("minor", "patch"))
      )
    )
  )
)

lazy val root = (project in file("."))
  .settings(
    version                       := "0.1",
    TaskKey[Unit]("checkDependabot") := Golden.checkFile(
      baseDirectory.value,
      "dependabotSettings",
      ".github/dependabot.yml"
    )
  )
