/*
 * Copyright 2022-2023 dev.zio
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

import sbt._
import Keys._
import scala.sys.process._
import java.io.File

object GhQueryPlugin extends AutoPlugin {

  override def requires = plugins.JvmPlugin
  override def trigger = allRequirements

  val autoImport = GhQueryKeys

  object GhQueryKeys {
    val ghRepo = settingKey[String]("GitHub repository (owner/repo)")
    val ghDir = settingKey[File]("Base directory for plugin data")
  }

  import GhQueryKeys._

  private def ghFetchCommand: Command = Command.command("gh-fetch") { state =>
    val extracted = Project.extract(state)
    val baseDir = extracted.get(ghDir)
    val projectDir = baseDir.getParentFile
    val repo = extracted.get(ghRepo)
    println(s"Fetching all issues and PRs for $repo...")
    runBash(s"bash ${scriptPath("fetch-github-data.sh")}", projectDir)
    state
  }

  private def ghUpdateCommand: Command = Command.command("gh-update") { state =>
    val extracted = Project.extract(state)
    val baseDir = extracted.get(ghDir)
    val projectDir = baseDir.getParentFile
    println("Updating GitHub data (incremental)...")
    runBash(s"bash ${scriptPath("update-github-data.sh")}", projectDir)
    state
  }

  private def ghUpdateDbCommand: Command = Command.command("gh-update-db") { state =>
    val extracted = Project.extract(state)
    val baseDir = extracted.get(ghDir)
    val projectDir = baseDir.getParentFile
    println("Updating search database...")
    runBash(s"python3 ${scriptPath("update_search_db.py")}", projectDir)
    state
  }

  private def ghRebuildDbCommand: Command = Command.command("gh-rebuild-db") { state =>
    val extracted = Project.extract(state)
    val baseDir = extracted.get(ghDir)
    val projectDir = baseDir.getParentFile
    println("Rebuilding search database from scratch...")
    runBash(s"python3 ${scriptPath("build_search_db.py")}", projectDir)
    state
  }

  private def ghStatusCommand: Command = Command.command("gh-status") { state =>
    val extracted = Project.extract(state)
    val baseDir = extracted.get(ghDir)
    val dbPath = baseDir / "gh.db"
    showStatus(dbPath)
    state
  }

  private def ghQueryCommand: Command = Command.single("gh-query") { (state, args) =>
    val extracted = Project.extract(state)
    val baseDir = extracted.get(ghDir)
    val dbPath = baseDir / "gh.db"
    
    val trimmed = args.trim
    val (query, includeBody) = if (trimmed.startsWith("--verbose ")) {
      (trimmed.stripPrefix("--verbose ").trim, "True")
    } else if (trimmed == "--verbose") {
      ("", "True")
    } else if (trimmed.isEmpty) {
      ("", "False")
    } else {
      (trimmed, "False")
    }
    
    if (query.isEmpty) {
      println("Usage: gh-query \"query\"")
      println("       gh-query --verbose \"query\"  (to include full body)")
    } else {
      println(s"Searching for: $query")
      runSearch(dbPath, query, includeBody)
    }
    state
  }

  override lazy val projectSettings: Seq[Setting[_]] = Seq(
    ghRepo := "zio/zio-blocks",
    ghDir := file(".zio-sbt"),

    commands ++= Seq(
      ghFetchCommand,
      ghUpdateCommand,
      ghUpdateDbCommand,
      ghRebuildDbCommand,
      ghStatusCommand,
      ghQueryCommand
    )
  )

  private val scriptDir: File = {
    val dir = new File(sys.props("java.io.tmpdir"), "zio-sbt-gh-query-scripts")
    if (!dir.exists()) {
      dir.mkdirs()
      List(
        "fetch-github-data.sh",
        "update-github-data.sh",
        "build_search_db.py",
        "update_search_db.py"
      ).foreach { name =>
        val stream = getClass.getResourceAsStream(s"/$name")
        if (stream != null) {
          val file = new File(dir, name)
          scala.io.Source.fromInputStream(stream).getLines.foreach { line =>
            scala.tools.nsc.io.File(file).appendAll(line + "\n")
          }
          file.setExecutable(true)
          stream.close()
        }
      }
    }
    dir
  }

  private def scriptPath(scriptName: String): String = {
    new File(scriptDir, scriptName).getAbsolutePath
  }

  private def runBash(command: String, cwd: File): Int = {
    Process(command, cwd).!
  }

  private def runSearch(db: File, query: String, includeBody: String): Unit = {
    val tempFile = File.createTempFile("search_", ".py")
    val code =
      s"""import sqlite3
         |conn = sqlite3.connect('$db')
         |cursor = conn.cursor()
         |try:
         |    cursor.execute('''
         |        SELECT i.type, i.number, i.title, i.state, i.author, i.url, i.body
         |        FROM search_index s
         |        JOIN issues i ON i.id = s.rowid
         |        WHERE search_index MATCH ?
         |        ORDER BY rank
         |        LIMIT 20
         |    ''', ('$query',))
         |    results = cursor.fetchall()
         |    include_body = $includeBody
         |    if not results:
         |        print('No results found')
         |    else:
         |        for row in results:
         |            print(f"{row[0]:5} #{row[1]}: {row[2][:60]}")
         |            print(f"       Author: {row[4]} | State: {row[3]}")
         |            print(f"       {row[5]}")
         |            if include_body and row[6]:
         |                print(f"       Body: {row[6]}")
         |            print()
         |except Exception as e:
         |    print(f'Search error: {e}')
         |finally:
         |    conn.close()
         |""".stripMargin
    IO.write(tempFile, code)
    Process(s"python3 ${tempFile.getAbsolutePath}").!
    tempFile.delete()
  }

  private def showStatus(db: File): Unit = {
    val tempFile = File.createTempFile("status_", ".py")
    val code =
      s"""import sqlite3
         |import sys
         |from pathlib import Path
         |if not Path('$db').exists():
         |    print('Database not found. Run gh-rebuild-db first.')
         |    sys.exit(1)
         |conn = sqlite3.connect('$db')
         |cursor = conn.cursor()
         |cursor.execute("SELECT COUNT(*) FROM issues WHERE type = 'issue'")
         |issues = cursor.fetchone()[0]
         |cursor.execute("SELECT COUNT(*) FROM issues WHERE type = 'pr'")
         |prs = cursor.fetchone()[0]
         |cursor.execute("SELECT COUNT(*) FROM comments WHERE is_pr_comment = 0")
         |issue_comments = cursor.fetchone()[0]
         |cursor.execute("SELECT COUNT(*) FROM comments WHERE is_pr_comment = 1")
         |pr_comments = cursor.fetchone()[0]
         |cursor.execute("SELECT fetched_at FROM issues ORDER BY fetched_at DESC LIMIT 1")
         |fetched = cursor.fetchone()[0]
         |print('=' * 50)
         |print('GitHub Query Database Status')
         |print('=' * 50)
         |print(f'Issues:        {issues}')
         |print(f'PRs:           {prs}')
         |print(f'Issue Comments:{issue_comments}')
         |print(f'PR Comments:   {pr_comments}')
         |print(f'Last fetched:  {fetched}')
         |print('=' * 50)
         |conn.close()
         |""".stripMargin
    IO.write(tempFile, code)
    Process(s"python3 ${tempFile.getAbsolutePath}").!
    tempFile.delete()
  }
}
