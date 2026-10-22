// `ciCheckGithubWorkflow` must cover .github/dependabot.yml, which sits outside .github/workflows.
import scala.sys.process.Process

ThisBuild / name := "Test Project"

def run(dir: File, args: String*): Unit = {
  val code = Process("git" +: args, dir).!
  if (code != 0) sys.error(s"git ${args.mkString(" ")} failed with $code")
}

lazy val root = (project in file("."))
  .settings(
    version := "0.1",
    TaskKey[Unit]("gitInit") := {
      val dir = baseDirectory.value
      run(dir, "init", "-q", "-b", "main")
      run(dir, "config", "user.email", "test@example.com")
      run(dir, "config", "user.name", "Test")
      run(dir, "add", "-A")
      run(dir, "commit", "-q", "-m", "initial")
    },
    TaskKey[Unit]("commitHandEdit") := {
      val dir  = baseDirectory.value
      val file = dir / ".github" / "dependabot.yml"
      IO.write(file, IO.read(file) + "\n# hand-edited\n")
      run(dir, "add", "-A")
      run(dir, "commit", "-q", "-m", "hand-edit the generated dependabot config")
    }
  )
