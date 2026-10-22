import zio.sbt.githubactions.{ScalaStewardConfig, ScalaStewardDependency, ScalaStewardGroupFilter, ScalaStewardGroupingRule}

ThisBuild / name := "Test Project"

ThisBuild / ciScalaStewardSchedule       := "0 3 * * 1"
ThisBuild / ciScalaStewardTimeoutMinutes := 60
ThisBuild / ciScalaStewardPermissions    := Map("contents" -> "write", "pull-requests" -> "write", "issues" -> "write")
ThisBuild / ciScalaStewardWorkflowEnv    := Map("JDK_JAVA_OPTIONS" -> "-Xmx8G")
ThisBuild / ciScalaStewardConfig := ScalaStewardConfig(
  pins = Seq(ScalaStewardDependency("org.scala-sbt", Some("sbt"), Some("1."))),
  allow = Seq(ScalaStewardDependency("org.typelevel")),
  ignore = Seq(ScalaStewardDependency("org.scala-native", Some("sbt-scala-native"), Some("0.5.7"))),
  updatesLimit = Some(5),
  updatePullRequests = Some("always"),
  commitMessage = Some("""Update ${artifactName}, say "hello""""),
  pullRequestsFrequency = Some("@asap"),
  grouping = Seq(
    ScalaStewardGroupingRule("patches", Seq(ScalaStewardGroupFilter(version = Some("patch")))),
    ScalaStewardGroupingRule("typelevel", Seq(ScalaStewardGroupFilter(group = Some("org.typelevel"))))
  ),
  assignees = Seq("octocat"),
  reviewers = Seq("octocat")
)

lazy val root = (project in file("."))
  .settings(
    version                        := "0.1",
    TaskKey[Unit]("checkWorkflows") := Golden.check(
      baseDirectory.value,
      "scalaStewardSettings",
      "scala-steward.yml"
    ),
    TaskKey[Unit]("checkConfig")    := Golden.checkFile(
      baseDirectory.value,
      "scalaStewardSettings",
      ".scala-steward.conf"
    )
  )
