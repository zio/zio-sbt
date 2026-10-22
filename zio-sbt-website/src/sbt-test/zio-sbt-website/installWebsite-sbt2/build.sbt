import zio.json._
import zio.json.ast.Json

lazy val root = (project in file("."))
  .settings(
    version        := "0.1",
    projectName    := "ZIO SBT",
    mainModuleName := "test-project",
    projectStage   := ProjectStage.ProductionReady,

    // create-zio-website 0.1.0 scaffolds Docusaurus 3.10.2, which depends on webpack ^5.95.0 and
    // must NOT carry the 5.75.0 pin -- that constraint is unsatisfiable and fails `npm install`
    // with ERESOLVE. The pin is conditional on the scaffolded major version, so this asserts it
    // is absent here, and would flip back to requiring it on a Docusaurus 2 scaffold.
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
