/*
 * Copyright 2022-2026 dev.zio
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package zio.sbt

import java.io.{BufferedInputStream, ByteArrayOutputStream, File}
import java.nio.file.Files
import java.security.MessageDigest

import scala.sys.process._

import sbt.Keys._
import sbt._

object GhQueryPlugin extends AutoPlugin {

  override def requires = plugins.JvmPlugin
  override def trigger  = allRequirements

  val autoImport = GhQueryKeys

  object GhQueryKeys {
    val ghRepo: SettingKey[String] = settingKey[String]("GitHub repository (owner/repo)")
    val ghDir: SettingKey[File]    = settingKey[File]("Base directory for plugin data")
  }

  import GhQueryKeys._

  /** Read all bytes from an InputStream using a buffered approach. */
  private def readAllBytes(stream: java.io.InputStream): Array[Byte] = {
    val bis = new BufferedInputStream(stream)
    val bos = new ByteArrayOutputStream()
    try {
      val buf = new Array[Byte](8192)
      var n   = bis.read(buf)
      while (n != -1) {
        bos.write(buf, 0, n)
        n = bis.read(buf)
      }
      bos.toByteArray
    } finally {
      bis.close()
    }
  }

  private val scriptNames: List[String] = List(
    "fetch-github-data.sh",
    "update-github-data.sh",
    "build_search_db.py",
    "update_search_db.py",
    "search_db.py",
    "status_db.py"
  )

  /**
   * Compute a hash of all bundled script contents for cache invalidation (issue
   * #4).
   */
  private lazy val scriptsContentHash: String = {
    val digest = MessageDigest.getInstance("SHA-256")
    scriptNames.foreach { name =>
      val stream = getClass.getResourceAsStream(s"/$name")
      if (stream != null) {
        try {
          digest.update(readAllBytes(stream))
        } finally {
          stream.close()
        }
      }
    }
    digest.digest().map("%02x".format(_)).mkString.take(12)
  }

  /**
   * Extract bundled scripts to a versioned temp directory (issue #3, #4). Uses
   * java.nio.file.Files instead of scala.tools.nsc.io.File. Includes a content
   * hash in the directory name for cache invalidation.
   */
  private lazy val scriptDir: File = {
    val dir = new File(sys.props("java.io.tmpdir"), s"zio-sbt-gh-query-scripts-$scriptsContentHash")
    if (!dir.exists()) {
      dir.mkdirs()
      scriptNames.foreach { name =>
        val stream = getClass.getResourceAsStream(s"/$name")
        if (stream != null) {
          try {
            val targetFile = new File(dir, name)
            Files.write(targetFile.toPath, readAllBytes(stream))
            targetFile.setExecutable(true)
          } finally {
            stream.close()
          }
        }
      }
    }
    dir
  }

  private def scriptPath(scriptName: String): String =
    new File(scriptDir, scriptName).getAbsolutePath

  /**
   * Resolve ghDir against the project base directory (issue #6). Prevents NPE
   * from calling getParentFile on a relative File.
   */
  private def resolveProjectDir(state: State): (File, String, File) = {
    val extracted  = Project.extract(state)
    val baseDir    = extracted.get(baseDirectory)
    val ghDirValue = extracted.get(ghDir)
    val repo       = extracted.get(ghRepo)
    val resolved   = if (ghDirValue.isAbsolute) ghDirValue else new File(baseDir, ghDirValue.getPath)
    resolved.mkdirs()
    (resolved, repo, baseDir)
  }

  /**
   * Unified sync command: fetches data from GitHub and builds/updates the
   * database.
   *   - --force: full fetch + full DB rebuild (always)
   *   - No DB exists, no data: full fetch + full DB rebuild
   *   - No DB exists, data present: skip fetch, just build the DB
   *   - DB exists: incremental fetch + incremental DB update
   */
  private def ghSyncCommand: Command = Command.args("gh-sync", "<--force>") { (state, args) =>
    val force                           = args.contains("--force")
    val (resolvedDir, repo, projectDir) = resolveProjectDir(state)
    val dataDir                         = new File(resolvedDir, "github-data")
    val dbPath                          = new File(resolvedDir, "gh.db")
    val hasData                         = new File(dataDir, "issues.json").exists()

    if (force || !dbPath.exists()) {
      // Fetch from GitHub unless data files already exist (and not --force)
      val fetchOk = if (force || !hasData) {
        if (force) println(s"Force sync: fetching all issues and PRs for $repo...")
        else println(s"First sync: fetching all issues and PRs for $repo...")

        val fetchExit = runBash(
          s"bash ${scriptPath("fetch-github-data.sh")}",
          projectDir,
          Map("GH_QUERY_REPO" -> repo, "GH_QUERY_DATA_DIR" -> dataDir.getAbsolutePath)
        )
        if (fetchExit != 0) {
          println(s"[error] gh-sync fetch exited with code $fetchExit")
          false
        } else true
      } else {
        println(s"Data files found in ${dataDir.getPath}, skipping fetch...")
        true
      }

      if (fetchOk) {
        println("Building search database...")
        val buildExit = runBash(
          s"python3 ${scriptPath("build_search_db.py")}",
          projectDir,
          Map(
            "GH_QUERY_REPO"     -> repo,
            "GH_QUERY_DATA_DIR" -> dataDir.getAbsolutePath,
            "GH_QUERY_DB_PATH"  -> dbPath.getAbsolutePath
          )
        )
        if (buildExit != 0) println(s"[warn] gh-sync db build exited with code $buildExit")
      }
    } else {
      println(s"Incremental sync: fetching updated issues and PRs for $repo...")
      val updateExit = runBash(
        s"bash ${scriptPath("update-github-data.sh")}",
        projectDir,
        Map(
          "GH_QUERY_REPO"     -> repo,
          "GH_QUERY_DATA_DIR" -> dataDir.getAbsolutePath,
          "GH_QUERY_DB_PATH"  -> dbPath.getAbsolutePath,
          "GH_QUERY_SCRIPTS"  -> scriptDir.getAbsolutePath
        )
      )
      if (updateExit != 0) println(s"[warn] gh-sync update exited with code $updateExit")
    }
    state
  }

  private def ghStatusCommand: Command = Command.command("gh-status") { state =>
    val (resolvedDir, _, projectDir) = resolveProjectDir(state)
    val dbPath                       = new File(resolvedDir, "gh.db")
    showStatus(dbPath, projectDir)
    state
  }

  private def ghQueryCommand: Command = Command.args("gh-query", "<--verbose> <query>") { (state, args) =>
    val (resolvedDir, _, projectDir) = resolveProjectDir(state)
    val dbPath                       = new File(resolvedDir, "gh.db")

    val includeBody = args.contains("--verbose")
    val queryParts  = args.filterNot(_ == "--verbose")
    val query       = queryParts.mkString(" ").trim

    if (query.isEmpty) {
      println("Usage: gh-query <query>")
      println("       gh-query --verbose <query>  (to include full body)")
    } else {
      println(s"Searching for: $query")
      runSearch(dbPath, query, includeBody, projectDir)
    }
    state
  }

  /**
   * Extract "owner/repo" from scmInfo's browseUrl. Expects a URL like
   * https://github.com/owner/repo.
   */
  private def repoFromScmInfo: Def.Initialize[String] = Def.setting {
    scmInfo.value match {
      case Some(info) =>
        val path     = info.browseUrl.getPath.stripPrefix("/").stripSuffix("/")
        val segments = path.split("/")
        if (segments.length >= 2) s"${segments(0)}/${segments(1)}"
        else
          sys.error(
            s"Cannot derive ghRepo from scmInfo browseUrl '${info.browseUrl}': " +
              "expected path with at least owner/repo segments. Set ghRepo manually."
          )
      case None =>
        sys.error(
          "ghRepo is not set and scmInfo is not defined. " +
            "Please set ghRepo := \"owner/repo\" or configure scmInfo."
        )
    }
  }

  override lazy val projectSettings: Seq[Setting[_]] = Seq(
    ghRepo := repoFromScmInfo.value,
    ghDir  := file(".zio-sbt"),
    commands ++= Seq(
      ghSyncCommand,
      ghStatusCommand,
      ghQueryCommand
    )
  )

  /**
   * Run a bash command with environment variables and a working directory.
   * Returns the exit code (issue #1 for error handling).
   */
  private def runBash(command: String, cwd: File, env: Map[String, String]): Int =
    Process(command, cwd, env.toSeq: _*).!

  /**
   * Run a search query via the bundled search_db.py script. Passes db path,
   * query, and verbose flag as command-line arguments to prevent SQL injection
   * (issue #1).
   */
  private def runSearch(db: File, query: String, includeBody: Boolean, cwd: File): Unit = {
    val verboseStr = if (includeBody) "True" else "False"
    val _: Int     = Process(
      Seq("python3", scriptPath("search_db.py"), db.getAbsolutePath, query, verboseStr),
      cwd
    ).!
  }

  /**
   * Show database status via the bundled status_db.py script.
   */
  private def showStatus(db: File, cwd: File): Unit = {
    val _: Int = Process(Seq("python3", scriptPath("status_db.py"), db.getAbsolutePath), cwd).!
  }
}
