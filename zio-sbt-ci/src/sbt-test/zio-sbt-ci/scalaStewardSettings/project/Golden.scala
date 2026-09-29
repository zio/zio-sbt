import sbt._

/** Golden-file comparison for the workflows emitted by `ciGenerateGithubWorkflow`.
  *
  * The generated YAML is the plugin's real output contract: every downstream repository commits it
  * and guards it with `sbt ciCheckGithubWorkflow`, so an unintended change turns their CI red. These
  * fixtures pin that output so such a change cannot pass unnoticed.
  *
  * The fixtures are self-bootstrapping. When a golden file is missing it is recorded from the
  * generated output and the task fails, asking for a re-run. Updating the expectation after a
  * deliberate change is therefore a matter of deleting the golden file and running the test again,
  * rather than editing expected YAML by hand.
  */
object Golden {

  /** Compares each `.github/workflows/<name>` against `expected/<name>` (flat, no `.github/workflows`
    * prefix under `expected/`) - the original, still-used shape for every pre-existing fixture.
    */
  def check(base: File, fixture: String, names: String*): Unit = {
    val recorded = names.flatMap(name => checkOne(base, fixture, ".github/workflows/" + name, name))
    reportRecorded(fixture, recorded)
  }

  /** Compares an arbitrary file, at `relativePath` under `base`, against `expected/<relativePath>`
    * (golden files nested to mirror the generated path, e.g. a config file outside
    * `.github/workflows/` golden-checked at `expected/.github/release-drafter.yml`). Generalizes
    * `check` beyond its hard-coded `.github/workflows/` assumption.
    */
  def checkFile(base: File, fixture: String, relativePath: String): Unit = {
    val recorded = checkOne(base, fixture, relativePath, relativePath)
    reportRecorded(fixture, recorded.toSeq)
  }

  /** Compares one generated file (at `generatedRelPath` under `base`) against its golden copy (at
    * `expected/<goldenRelPath>`), recording it (rather than failing closed) when the golden copy is
    * missing.
    */
  private def checkOne(
    base: File,
    fixture: String,
    generatedRelPath: String,
    goldenRelPath: String
  ): Option[String] = {
    val generated = base / generatedRelPath
    val golden    = base / "expected" / goldenRelPath

    // Scripted runs in a temporary copy of the fixture and deletes it afterwards, so a recorded
    // golden written next to the test would be lost. Drop it somewhere stable instead.
    val record = file(sys.props("java.io.tmpdir")) / "zio-sbt-golden" / fixture / goldenRelPath

    if (!generated.exists)
      sys.error(s"'$generatedRelPath' was not generated; expected it at $generated")

    if (golden.exists) {
      val actual   = IO.read(generated)
      val expected = IO.read(golden)
      if (actual != expected) sys.error(report(goldenRelPath, expected, actual))
      None
    } else {
      IO.copyFile(generated, record)
      Some(record.getAbsolutePath)
    }
  }

  private def reportRecorded(fixture: String, recorded: Seq[String]): Unit =
    if (recorded.nonEmpty)
      sys.error(
        s"""|Recorded ${recorded.length} golden file(s) for fixture '$fixture'. Copy them into
            |zio-sbt-ci/src/sbt-test/zio-sbt-ci/$fixture/expected/ and re-run to verify:
            |
            |${recorded.mkString("\n")}
            |""".stripMargin
      )

  /** Points at the first differing line, with a little context on either side. */
  private def report(name: String, expected: String, actual: String): String = {
    val e = expected.split("\n", -1).toVector
    val a = actual.split("\n", -1).toVector

    val at = (0 until math.max(e.length, a.length))
      .find(i => e.lift(i) != a.lift(i))
      .getOrElse(0)

    def context(label: String, lines: Vector[String]): String =
      (math.max(0, at - 3) until math.min(lines.length, at + 4))
        .map(i => f"$label%-8s ${i + 1}%4d ${if (i == at) ">" else " "} ${lines(i)}")
        .mkString("\n")

    s"""|'$name' does not match its golden copy (first difference on line ${at + 1}).
        |
        |${context("expected", e)}
        |
        |${context("actual", a)}
        |
        |If this change is intentional, delete expected/$name and re-run to re-record it.""".stripMargin
  }
}
