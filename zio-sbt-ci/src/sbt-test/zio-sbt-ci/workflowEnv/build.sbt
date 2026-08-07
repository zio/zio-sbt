// Replacing the workflow-level environment outright.
//
// By default the plugin exports JDK_JAVA_OPTIONS, but that is not always safe: it makes
// `java -version` print a NOTE containing commas, which corrupts the cache keys computed by
// setup-sbt and coursier/cache-action. Builds hitting that need to pass JVM flags through SBT_OPTS
// instead, which means dropping JDK_JAVA_OPTIONS rather than adding to it.
//
// Assigning `ciWorkflowEnv` replaces the derived map, so `ciJvmOptions` below is deliberately
// ignored in the generated YAML - that is the behaviour being pinned.

ThisBuild / name := "Test Project"

inThisBuild(
  List(
    ciJvmOptions  := Seq("-Xmx4G"),
    ciWorkflowEnv := Map(
      "SBT_OPTS" -> "-XX:+PrintCommandLineFlags -Djava.locale.providers=CLDR,JRE"
    )
  )
)

lazy val root = (project in file("."))
  .settings(
    version                         := "0.1",
    TaskKey[Unit]("checkWorkflows") := Golden.check(baseDirectory.value, "workflowEnv", "ci.yml")
  )
