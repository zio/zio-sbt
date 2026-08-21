import zio.json._
import zio.json.ast.Json

lazy val root = (project in file("."))
  .settings(
    version        := "0.1",
    projectName    := "ZIO SBT",
    mainModuleName := "test-project",
    projectStage   := ProjectStage.ProductionReady,

    // create-zio-website still scaffolds Docusaurus 2.1.0, which needs webpack pinned to 5.75.0.
    // The pin is conditional on the scaffolded major version, so assert it is actually applied
    // here; if the scaffold ever moves to Docusaurus 3, this expectation flips to `absent`.
    TaskKey[Unit]("checkWebpackPin") := {
      val pkgJson = IO.read(baseDirectory.value / "website" / "package.json")
      val obj     = pkgJson.fromJson[Json.Obj].fold(err => sys.error(s"bad package.json: $err"), identity)

      def field(o: Json.Obj, k: String): Option[Json] =
        o.fields.collectFirst { case (n, v) if n == k => v }

      val docusaurus = field(obj, "dependencies")
        .collect { case o: Json.Obj => o }
        .flatMap(field(_, "@docusaurus/core"))
        .collect { case Json.Str(v) => v }
        .getOrElse(sys.error("scaffold has no @docusaurus/core dependency"))

      val pinned = field(obj, "overrides")
        .collect { case o: Json.Obj => o }
        .flatMap(field(_, "webpack"))
        .collect { case Json.Str(v) => v }

      if (docusaurus.startsWith("2.")) {
        assert(
          pinned.contains("5.75.0"),
          s"Docusaurus $docusaurus scaffold must pin webpack to 5.75.0, found: $pinned"
        )
      } else {
        assert(
          pinned.isEmpty,
          s"Docusaurus $docusaurus must not carry the webpack 5.75.0 pin, found: $pinned"
        )
      }
    }
  )
  .enablePlugins(WebsitePlugin)
