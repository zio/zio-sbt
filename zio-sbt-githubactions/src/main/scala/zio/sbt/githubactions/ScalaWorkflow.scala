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

package zio.sbt.githubactions

import zio.json.ast.Json
import zio.sbt.V
import zio.sbt.githubactions.ScalaWorkflow.JavaVersion.JDK17

// The original code of the githubactions package was originally copied from the zio-aws-codegen project:
// https://github.com/zio/zio-aws/tree/master/zio-aws-codegen/src/main/scala/zio/aws/codegen/githubactions
/**
 * Builders for workflow steps, conditions and matrix settings used by Scala
 * projects.
 */
object ScalaWorkflow {
  import Step._

  /**
   * Returns a step named "Checkout current branch" that runs `actions/checkout`
   * with `fetch-depth` set to `fetchDepth`, which defaults to 0.
   */
  def checkoutCurrentBranch(fetchDepth: Int = 0): Step =
    SingleStep(
      name = "Checkout current branch",
      uses = Some(ActionRef(V("actions/checkout"))),
      parameters = Map(
        "fetch-depth" -> Json.Num(fetchDepth)
      )
    )

  /**
   * Returns a step named "Setup Java and Scala" that runs
   * `olafurpg/setup-scala`. Its `java-version` is `\${{ matrix.java }}` when
   * `javaVersion` is `None`, and the `asString` of the given version otherwise.
   */
  def setupScala(javaVersion: Option[JavaVersion] = None): Step =
    SingleStep(
      name = "Setup Java and Scala",
      uses = Some(ActionRef(V("olafurpg/setup-scala"))),
      parameters = Map(
        "java-version" -> Json.Str(javaVersion match {
          case None          => "${{ matrix.java }}"
          case Some(version) => version.asString
        })
      )
    )

  /**
   * Returns a step named "Setup NodeJS" that runs `actions/setup-node` with
   * `registry-url` set to `https://registry.npmjs.org`. Its `node-version` is
   * `16.x` when `javaVersion` is `None`, and the `asString` of the given value
   * otherwise.
   */
  def setupNode(javaVersion: Option[JavaVersion] = None): Step =
    SingleStep(
      name = "Setup NodeJS",
      uses = Some(ActionRef(V("actions/setup-node"))),
      parameters = Map(
        "node-version" -> Json.Str(javaVersion match {
          case None          => "16.x"
          case Some(version) => version.asString
        }),
        "registry-url" -> Json.Str("https://registry.npmjs.org")
      )
    )

  /** Returns a step named "Setup GPG" that runs `olafurpg/setup-gpg`. */
  def setupGPG(): Step =
    SingleStep(
      "Setup GPG",
      uses = Some(ActionRef(V("olafurpg/setup-gpg")))
    )

  /**
   * Returns a step named "Cache SBT" that runs `actions/cache` on the
   * directories `~/.ivy2/cache`, `~/.sbt`, `~/.coursier/cache/v1` and
   * `~/.cache/coursier/v1`. The cache key is the operating system, `sbt`, the
   * Scala version and the hashes of the `.sbt` files and of the
   * `build.properties` files, joined with dashes. The operating system and
   * Scala version fall back to `\${{ matrix.os }}` and `\${{ matrix.scala }}`
   * when `None`.
   */
  def cacheSBT(
    os: Option[OS] = None,
    scalaVersion: Option[ScalaVersion] = None
  ): Step = {
    val osS    = os.map(_.asString).getOrElse("${{ matrix.os }}")
    val scalaS = scalaVersion.map(_.version).getOrElse("${{ matrix.scala }}")

    SingleStep(
      name = "Cache SBT",
      uses = Some(ActionRef(V("actions/cache"))),
      parameters = Map(
        "path" -> Json.Str(
          Seq(
            "~/.ivy2/cache",
            "~/.sbt",
            "~/.coursier/cache/v1",
            "~/.cache/coursier/v1"
          ).mkString("\n")
        ),
        "key" -> Json.Str(s"$osS-sbt-$scalaS-$${{ hashFiles('**/*.sbt') }}-$${{ hashFiles('**/build.properties') }}")
      )
    )
  }

  /**
   * Returns a step named "Setup GIT user" that runs `fregante/setup-git-user`.
   */
  def setupGitUser(): Step =
    SingleStep(
      name = "Setup GIT user",
      uses = Some(ActionRef(V("fregante/setup-git-user")))
    )

  /**
   * Returns a step that runs sbt with the G1 garbage collector, an initial and
   * maximum heap of `heapGb` gigabytes, a stack of `stackMb` megabytes, and
   * `parameters` joined by spaces. `env` is the environment of the step.
   */
  def runSBT(
    name: String,
    parameters: List[String],
    heapGb: Int = 6,
    stackMb: Int = 16,
    env: Map[String, String] = Map.empty
  ): Step =
    SingleStep(
      name,
      run = Some(
        s"sbt -J-XX:+UseG1GC -J-Xmx${heapGb}g -J-Xms${heapGb}g -J-Xss${stackMb}m ${parameters.mkString(" ")}"
      ),
      env = env
    )

  /**
   * Returns two steps. The first, "Compress `id` targets", runs
   * `tar cvf targets.tar` on the `target` folder of each of `directories`. The
   * second uploads `targets.tar` as an artifact named
   * `target-<id>-<os>-<scala>-<java>`. The operating system, Scala version and
   * Java version fall back to the `matrix.os`, `matrix.scala` and `matrix.java`
   * expressions when `None`.
   */
  def storeTargets(
    id: String,
    directories: List[String],
    os: Option[OS] = None,
    scalaVersion: Option[ScalaVersion] = None,
    javaVersion: Option[JavaVersion] = None
  ): Step = {
    val osS    = os.map(_.asString).getOrElse("${{ matrix.os }}")
    val scalaS = scalaVersion.map(_.version).getOrElse("${{ matrix.scala }}")
    val javaS  = javaVersion.map(_.asString).getOrElse("${{ matrix.java }}")

    StepSequence(
      Seq(
        SingleStep(
          s"Compress $id targets",
          run = Some(
            s"tar cvf targets.tar ${directories.map(dir => s"$dir/target".dropWhile(_ == '/')).mkString(" ")}"
          )
        ),
        SingleStep(
          s"Upload $id targets",
          uses = Some(ActionRef(V("actions/upload-artifact"))),
          parameters = Map(
            "name" -> Json.Str(s"target-$id-$osS-$scalaS-$javaS"),
            "path" -> Json.Str("targets.tar")
          )
        )
      )
    )
  }

  /**
   * Returns two steps that restore what `storeTargets` stored for `id`: the
   * first downloads the artifact `target-<id>-<os>-<scala>-<java>`, the second
   * runs `tar xvf targets.tar` and removes `targets.tar`. The operating system,
   * Scala version and Java version fall back to the `matrix.os`, `matrix.scala`
   * and `matrix.java` expressions when `None`.
   */
  def loadStoredTarget(
    id: String,
    os: Option[OS] = None,
    scalaVersion: Option[ScalaVersion] = None,
    javaVersion: Option[JavaVersion] = None
  ): Step = {
    val osS    = os.map(_.asString).getOrElse("${{ matrix.os }}")
    val scalaS = scalaVersion.map(_.version).getOrElse("${{ matrix.scala }}")
    val javaS  = javaVersion.map(_.asString).getOrElse("${{ matrix.java }}")

    StepSequence(
      Seq(
        SingleStep(
          s"Download stored $id targets",
          uses = Some(ActionRef(V("actions/download-artifact"))),
          parameters = Map(
            "name" -> Json.Str(s"target-$id-$osS-$scalaS-$javaS")
          )
        ),
        SingleStep(
          s"Inflate $id targets",
          run = Some(
            "tar xvf targets.tar\nrm targets.tar"
          )
        )
      )
    )
  }

  /** Returns the steps of `loadStoredTarget` for each of `ids`, in order. */
  def loadStoredTargets(
    ids: List[String],
    os: Option[OS] = None,
    scalaVersion: Option[ScalaVersion] = None,
    javaVersion: Option[JavaVersion] = None
  ): Step =
    StepSequence(
      ids.map(loadStoredTarget(_, os, scalaVersion, javaVersion))
    )

  /**
   * Returns a step named "Load PGP secret" that runs `.github/import-key.sh`
   * with `PGP_SECRET` set from the `PGP_SECRET` secret.
   */
  def loadPGPSecret(): Step =
    SingleStep(
      "Load PGP secret",
      run = Some(".github/import-key.sh"),
      env = Map("PGP_SECRET" -> "${{ secrets.PGP_SECRET }}")
    )

  /**
   * Returns a step named "Turnstyle" that runs `softprops/turnstyle` with
   * `GITHUB_TOKEN` set from the `ADMIN_GITHUB_TOKEN` secret.
   */
  def turnstyle(): Step =
    SingleStep(
      "Turnstyle",
      uses = Some(ActionRef(V("softprops/turnstyle"))),
      env = Map(
        "GITHUB_TOKEN" -> "${{ secrets.ADMIN_GITHUB_TOKEN }}"
      )
    )

  /**
   * Returns a step named "Collect Docker logs" that runs
   * `jwalton/gh-docker-logs`.
   */
  def collectDockerLogs(): Step =
    SingleStep(
      "Collect Docker logs",
      uses = Some(ActionRef(V("jwalton/gh-docker-logs")))
    )

  /** A condition that holds when `github.ref` is `refs/heads/master`. */
  val isMaster: Condition = Condition.Expression(
    "github.ref == 'refs/heads/master'"
  )

  /** A condition that holds when `github.ref` is not `refs/heads/master`. */
  val isNotMaster: Condition = Condition.Expression(
    "github.ref != 'refs/heads/master'"
  )

  /**
   * Returns a condition that holds when `matrix.scala` equals the given Scala
   * version.
   */
  def isScalaVersion(version: ScalaVersion): Condition = Condition.Expression(
    s"matrix.scala == '${version.version}'"
  )

  /**
   * Returns a condition that holds when `matrix.scala` differs from the given
   * Scala version.
   */
  def isNotScalaVersion(version: ScalaVersion): Condition =
    Condition.Expression(
      s"matrix.scala != '${version.version}'"
    )

  /** A condition that calls the `failure()` status function. */
  val isFailure: Condition = Condition.Function("failure()")

  /** A Scala version string, used as a value of the `scala` matrix axis. */
  case class ScalaVersion(version: String)

  /**
   * A Java distribution and version, used as a value of the `java` matrix axis.
   */
  trait JavaVersion {

    /** The text rendered in the workflow, for example `corretto:17`. */
    val asString: String
  }

  /** Predefined Java versions. */
  object JavaVersion {

    /**
     * An Amazon Corretto JDK of the given version, rendered as
     * `corretto:<javaVersion>`.
     */
    case class CorrettoJDK(javaVersion: String) extends JavaVersion {
      override val asString: String = s"corretto:$javaVersion"
    }

    /** Amazon Corretto 17. */
    val JDK17: JavaVersion = CorrettoJDK("17")

    /** Amazon Corretto 21. */
    val JDK21: JavaVersion = CorrettoJDK("21")

    /** Amazon Corretto 25. */
    val JDK25: JavaVersion = CorrettoJDK("25")
  }

  /** Adds `matrix` to a [[Job]]. */
  implicit class JobOps(job: Job) {

    /**
     * Returns the job with a strategy whose matrix has the axes `os`, `scala`
     * and `java`, and with `runs-on` set to `\${{ matrix.os }}`. The operating
     * systems default to `OS.UbuntuLatest` and the Java versions to `JDK17`.
     */
    def matrix(
      scalaVersions: Seq[ScalaVersion],
      operatingSystems: Seq[OS] = Seq(OS.UbuntuLatest),
      javaVersions: Seq[JavaVersion] = Seq(JDK17)
    ): Job =
      job
        .withStrategy(
          Strategy(
            matrix = Map(
              "os"    -> operatingSystems.map(_.asString).toList,
              "scala" -> scalaVersions.map(_.version).toList,
              "java"  -> javaVersions.map(_.asString).toList
            )
          )
        )
        .withRunsOn("${{ matrix.os }}")
  }

}
