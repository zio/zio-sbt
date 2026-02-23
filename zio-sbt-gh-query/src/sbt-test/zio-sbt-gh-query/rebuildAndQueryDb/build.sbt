import GhQueryPlugin.autoImport._

lazy val root = (project in file("."))
  .settings(
    version := "0.1",
    ghRepo  := "test-org/test-repo",
    ghDir   := file(".zio-sbt"),
    TaskKey[Unit]("checkDbExists") := {
      val dbFile = file(".zio-sbt") / "gh.db"
      assert(dbFile.exists(), s"Database file should exist at ${dbFile.getAbsolutePath}")
      assert(dbFile.length() > 0, "Database file should not be empty")
      println(s"Database exists: ${dbFile.getAbsolutePath} (${dbFile.length()} bytes)")
      println("Database existence check passed!")
    },
    TaskKey[Unit]("checkDbContents") := {
      Class.forName("org.sqlite.JDBC")
      import java.sql.DriverManager
      val dbFile = file(".zio-sbt") / "gh.db"
      val conn = DriverManager.getConnection(s"jdbc:sqlite:${dbFile.getAbsolutePath}")
      try {
        val stmt = conn.createStatement()

        // Check issue count
        val issueRs = stmt.executeQuery("SELECT COUNT(*) FROM issues WHERE type = 'issue'")
        issueRs.next()
        val issueCount = issueRs.getInt(1)
        assert(issueCount == 3, s"Expected 3 issues, got $issueCount")

        // Check PR count
        val prRs = stmt.executeQuery("SELECT COUNT(*) FROM issues WHERE type = 'pr'")
        prRs.next()
        val prCount = prRs.getInt(1)
        assert(prCount == 2, s"Expected 2 PRs, got $prCount")

        // Check comment counts
        val commentRs = stmt.executeQuery("SELECT COUNT(*) FROM comments WHERE is_pr_comment = 0")
        commentRs.next()
        val commentCount = commentRs.getInt(1)
        assert(commentCount == 3, s"Expected 3 issue comments, got $commentCount")

        val prCommentRs = stmt.executeQuery("SELECT COUNT(*) FROM comments WHERE is_pr_comment = 1")
        prCommentRs.next()
        val prCommentCount = prCommentRs.getInt(1)
        assert(prCommentCount == 1, s"Expected 1 PR comment, got $prCommentCount")

        // Check that URLs use the configured repo, not a hardcoded one
        val urlRs = stmt.executeQuery("SELECT url FROM issues WHERE number = 1 AND type = 'issue'")
        urlRs.next()
        val url = urlRs.getString(1)
        assert(url.contains("test-org/test-repo"), s"URL should contain test-org/test-repo, got $url")

        val prUrlRs = stmt.executeQuery("SELECT url FROM issues WHERE number = 3 AND type = 'pr'")
        prUrlRs.next()
        val prUrl = prUrlRs.getString(1)
        assert(prUrl.contains("test-org/test-repo"), s"PR URL should contain test-org/test-repo, got $prUrl")

        // Check FTS search works
        val searchRs = stmt.executeQuery(
          "SELECT i.number FROM search_index s JOIN issues i ON i.id = s.rowid WHERE search_index MATCH 'codec' LIMIT 5"
        )
        assert(searchRs.next(), "FTS search for 'codec' should return at least one result")
        val matchedNumber = searchRs.getInt(1)
        assert(matchedNumber == 1, s"FTS search for 'codec' should match issue #1, got #$matchedNumber")

        // Check comment_text column is populated for issues with comments
        val ctRs = stmt.executeQuery("SELECT comment_text FROM issues WHERE number = 5 AND type = 'issue'")
        assert(ctRs.next(), "Issue #5 should exist")
        val commentText = ctRs.getString(1)
        assert(commentText != null && commentText.nonEmpty, "comment_text should be populated for issue #5")
        assert(commentText.contains("reproduce"), s"comment_text for issue #5 should contain 'reproduce', got: $commentText")
        assert(commentText.contains("root cause"), s"comment_text for issue #5 should contain 'root cause', got: $commentText")

        // Check comment_text is NULL for issues with no comments
        val ctNullRs = stmt.executeQuery("SELECT comment_text FROM issues WHERE number = 2 AND type = 'issue'")
        assert(ctNullRs.next(), "Issue #2 should exist")
        val emptyCommentText = ctNullRs.getString(1)
        assert(emptyCommentText == null, s"comment_text should be NULL for issue #2 (no comments), got: $emptyCommentText")

        // Check FTS search finds issues via comment text (comment-only search)
        val commentSearchRs = stmt.executeQuery(
          "SELECT i.number FROM search_index s JOIN issues i ON i.id = s.rowid WHERE search_index MATCH 'reproduce' LIMIT 5"
        )
        assert(commentSearchRs.next(), "FTS search for 'reproduce' (comment-only term) should return at least one result")
        val commentMatchedNumber = commentSearchRs.getInt(1)
        assert(commentMatchedNumber == 5, s"FTS search for 'reproduce' should match issue #5, got #$commentMatchedNumber")

        // Check FTS search for 'root cause' also matches via comment text
        val rootCauseRs = stmt.executeQuery(
          "SELECT i.number FROM search_index s JOIN issues i ON i.id = s.rowid WHERE search_index MATCH '\"root cause\"' LIMIT 5"
        )
        assert(rootCauseRs.next(), "FTS search for 'root cause' (comment-only term) should return at least one result")
        val rootCauseNumber = rootCauseRs.getInt(1)
        assert(rootCauseNumber == 5, s"FTS search for 'root cause' should match issue #5, got #$rootCauseNumber")

        println(s"Issues: $issueCount, PRs: $prCount, Comments: $commentCount, PR Comments: $prCommentCount")
        println("Database contents check passed!")
      } finally {
        conn.close()
      }
    }
  )
  .enablePlugins(GhQueryPlugin)
