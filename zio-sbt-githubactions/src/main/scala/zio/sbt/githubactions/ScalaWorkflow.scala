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

import scala.collection.immutable.ListMap

import zio.json._

// The original code of the githubactions package was originally copied from the zio-aws-codegen project:
// https://github.com/zio/zio-aws/tree/master/zio-aws-codegen/src/main/scala/zio/aws/codegen/githubactions
object ScalaWorkflow {
  import Step._

  def checkoutCurrentBranch(fetchDepth: Int = 0): Step =
    SingleStep(
      name = "Checkout current branch",
      uses = Some(ActionRef("actions/checkout@v2")),
      `with` = Some(
        ListMap(
          "fetch-depth" -> fetchDepth.toJsonAST.right.get
        )
      )
    )

  def setupScala(javaVersion: Option[JavaVersion] = None): Step =
    SingleStep(
      name = "Setup Java and Scala",
      uses = Some(ActionRef("olafurpg/setup-scala@v11")),
      `with` = Some(
        ListMap(
          "java-version" -> (javaVersion match {
            case None          => "${{ matrix.java }}"
            case Some(version) => version.asString
          }).toJsonAST.right.get
        )
      )
    )

  def setupNode(javaVersion: Option[JavaVersion] = None): Step =
    SingleStep(
      name = "Setup NodeJS",
      uses = Some(ActionRef("actions/setup-node@v3")),
      `with` = Some(
        ListMap(
          "node-version" -> (javaVersion match {
            case None          => "16.x"
            case Some(version) => version.asString
          }).toJsonAST.right.get,
          "registry-url" -> "https://registry.npmjs.org".toJsonAST.right.get
        )
      )
    )

  def setupGPG(): Step =
    SingleStep(
      "Setup GPG",
      uses = Some(ActionRef("olafurpg/setup-gpg@v3"))
    )

  def cacheSBT(
    os: Option[OS] = None,
    scalaVersion: Option[ScalaVersion] = None
  ): Step = {
    val osS    = os.map(_.asString).getOrElse("${{ matrix.os }}")
    val scalaS = scalaVersion.map(_.version).getOrElse("${{ matrix.scala }}")

    SingleStep(
      name = "Cache SBT",
      uses = Some(ActionRef("actions/cache@v2")),
      `with` = Some(
        ListMap(
          "path" -> Seq(
            "~/.ivy2/cache",
            "~/.sbt",
            "~/.coursier/cache/v1",
            "~/.cache/coursier/v1"
          ).mkString("\n").toJsonAST.right.get,
          "key" -> s"$osS-sbt-$scalaS-$${{ hashFiles('**/*.sbt') }}-$${{ hashFiles('**/build.properties') }}".toJsonAST.right.get
        )
      )
    )
  }

  def setupGitUser(): Step =
    SingleStep(
      name = "Setup GIT user",
      uses = Some(ActionRef("fregante/setup-git-user@v1"))
    )

  def runSBT(
    name: String,
    parameters: List[String],
    heapGb: Int = 6,
    stackMb: Int = 16,
    env: ListMap[String, String] = ListMap.empty
  ): Step =
    SingleStep(
      name,
      run = Some(
        s"sbt -J-XX:+UseG1GC -J-Xmx${heapGb}g -J-Xms${heapGb}g -J-Xss${stackMb}m ${parameters.mkString(" ")}"
      ),
      env = Some(env).filter(_.nonEmpty)
    )

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
          uses = Some(ActionRef("actions/upload-artifact@v2")),
          `with` = Some(
            ListMap(
              "name" -> s"target-$id-$osS-$scalaS-$javaS".toJsonAST.right.get,
              "path" -> "targets.tar".toJsonAST.right.get
            )
          )
        )
      )
    )
  }

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
          uses = Some(ActionRef("actions/download-artifact@v2")),
          `with` = Some(
            ListMap(
              "name" -> s"target-$id-$osS-$scalaS-$javaS".toJsonAST.right.get
            )
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

  def loadStoredTargets(
    ids: List[String],
    os: Option[OS] = None,
    scalaVersion: Option[ScalaVersion] = None,
    javaVersion: Option[JavaVersion] = None
  ): Step =
    StepSequence(
      ids.map(loadStoredTarget(_, os, scalaVersion, javaVersion))
    )

  def loadPGPSecret(): Step =
    SingleStep(
      "Load PGP secret",
      run = Some(".github/import-key.sh"),
      env = Some(ListMap("PGP_SECRET" -> "${{ secrets.PGP_SECRET }}"))
    )

  def turnstyle(): Step =
    SingleStep(
      "Turnstyle",
      uses = Some(ActionRef("softprops/turnstyle@v1")),
      env = Some(
        ListMap(
          "GITHUB_TOKEN" -> "${{ secrets.ADMIN_GITHUB_TOKEN }}"
        )
      )
    )

  def collectDockerLogs(): Step =
    SingleStep(
      "Collect Docker logs",
      uses = Some(ActionRef("jwalton/gh-docker-logs@v1"))
    )

  val isMaster: Condition = Condition.Expression(
    "github.ref == 'refs/heads/master'"
  )
  val isNotMaster: Condition = Condition.Expression(
    "github.ref != 'refs/heads/master'"
  )
  def isScalaVersion(version: ScalaVersion): Condition = Condition.Expression(
    s"matrix.scala == '${version.version}'"
  )
  def isNotScalaVersion(version: ScalaVersion): Condition =
    Condition.Expression(
      s"matrix.scala != '${version.version}'"
    )
  val isFailure: Condition = Condition.Function("failure()")

  case class ScalaVersion(version: String)

  sealed trait JavaVersion {
    def distribution: String
    def version: String
    def asString: String = s"$distribution:$version"
  }

  object JavaVersion {

    def apply(distribution: String, version: String): JavaVersion = CustomJDK(distribution, version)

    case class CustomJDK(distribution: String, version: String) extends JavaVersion

    case class CorrettoJDK(version: String) extends JavaVersion {
      override def distribution: String = "corretto"
    }

    object CorrettoJDK {
      val `11`: JavaVersion = CorrettoJDK("11")
      val `17`: JavaVersion = CorrettoJDK("17")
      val `21`: JavaVersion = CorrettoJDK("21")
    }
  }

  implicit class JobOps(job: Job) {
    def matrix(
      scalaVersions: Seq[ScalaVersion],
      operatingSystems: Seq[OS] = Seq(OS.UbuntuLatest),
      javaVersions: Seq[JavaVersion] = Seq(JavaVersion.CorrettoJDK.`11`)
    ): Job =
      job.copy(
        strategy = Some(
          Strategy(
            matrix = ListMap(
              "os"    -> operatingSystems.map(_.asString).toList,
              "scala" -> scalaVersions.map(_.version).toList,
              "java"  -> javaVersions.map(_.asString).toList
            )
          )
        ),
        runsOn = "${{ matrix.os }}"
      )
  }
}
