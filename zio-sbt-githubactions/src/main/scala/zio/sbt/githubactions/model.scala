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

import scala.annotation.nowarn

import zio.Chunk
import zio.json._
import zio.json.ast.Json
import zio.sbt.githubactions.Step.StepSequence

sealed trait OS {
  val asString: String
}
object OS {
  case object UbuntuLatest extends OS { val asString = "ubuntu-latest" }
}

sealed trait DependencyBot {
  def login: String
}
object DependencyBot {
  case object Dependabot extends DependencyBot { val login = "dependabot[bot]" }
  case object Renovate   extends DependencyBot { val login = "renovate[bot]"   }

  final case class ScalaSteward(githubAppName: String) extends DependencyBot {
    val login: String = s"$githubAppName[bot]"
  }

  final case class Custom(login: String) extends DependencyBot
}

sealed trait Branch
object Branch {
  case object All                extends Branch
  case class Named(name: String) extends Branch

  implicit val encoder: JsonEncoder[Branch] =
    JsonEncoder.string.contramap {
      case All         => "*"
      case Named(name) => name
    }
}

sealed trait Trigger {
  def toKeyValuePair: (String, Json)
}

case class Input(key: String, description: String, required: Boolean, defaultValue: String)

object Trigger {
  case class WorkflowDispatch private (
    inputs: Chunk[Input]
  ) extends Trigger {
    override def toKeyValuePair: (String, Json) = {
      val inputsMap = inputs.map { i =>
        (
          i.key,
          Json.Obj(
            ("description", Json.Str(i.description)),
            ("required", Json.Bool(i.required)),
            ("default", Json.Str(i.defaultValue))
          )
        )
      }.toMap
      ("workflow_dispatch", inputsMap.toJsonAST.getOrElse(Json.Null))
    }
  }

  object WorkflowDispatch {
    def apply(inputs: Seq[Input] = Seq.empty): WorkflowDispatch =
      WorkflowDispatch(Chunk.fromIterable(inputs))
  }

  case class Release private (
    releaseTypes: Chunk[String]
  ) extends Trigger {
    override def toKeyValuePair: (String, Json) =
      ("release", Json.Obj("types" -> releaseTypes.toJsonAST.getOrElse(Json.Null)))
  }
  object Release {
    def apply(releaseTypes: Seq[String] = Seq.empty): Release =
      Release(Chunk.fromIterable(releaseTypes))
  }

  case class PullRequest private (
    branches: Chunk[Branch],
    ignoredBranches: Chunk[Branch]
  ) extends Trigger {
    override def toKeyValuePair: (String, Json) = {
      val fields = Chunk(
        ("branches", branches.toJsonAST.getOrElse(Json.Null)),
        ("branches-ignore", ignoredBranches.toJsonAST.getOrElse(Json.Null))
      ).filter { case (_, data) =>
        data match {
          case Json.Arr(elements) => elements.nonEmpty
          case _                  => false
        }
      }
      ("pull_request", Json.Obj(fields))
    }
  }

  object PullRequest {
    def apply(
      branches: Seq[Branch] = Seq.empty,
      ignoredBranches: Seq[Branch] = Seq.empty
    ): PullRequest =
      PullRequest(
        Chunk.fromIterable(branches),
        Chunk.fromIterable(ignoredBranches)
      )
  }

  case class PullRequestTarget private (
    types: Chunk[String],
    branches: Chunk[Branch],
    ignoredBranches: Chunk[Branch]
  ) extends Trigger {
    override def toKeyValuePair: (String, Json) = {
      val fields = Chunk(
        ("types", types.toJsonAST.getOrElse(Json.Null)),
        ("branches", branches.toJsonAST.getOrElse(Json.Null)),
        ("branches-ignore", ignoredBranches.toJsonAST.getOrElse(Json.Null))
      ).filter { case (_, data) =>
        data match {
          case Json.Arr(elements) => elements.nonEmpty
          case _                  => false
        }
      }
      ("pull_request_target", Json.Obj(fields))
    }
  }

  object PullRequestTarget {
    def apply(
      types: Seq[String] = Seq.empty,
      branches: Seq[Branch] = Seq.empty,
      ignoredBranches: Seq[Branch] = Seq.empty
    ): PullRequestTarget =
      PullRequestTarget(
        Chunk.fromIterable(types),
        Chunk.fromIterable(branches),
        Chunk.fromIterable(ignoredBranches)
      )
  }

  case class Push private (
    branches: Chunk[Branch],
    ignoredBranches: Chunk[Branch]
  ) extends Trigger {
    override def toKeyValuePair: (String, Json) = {
      val fields = Chunk(
        ("branches", branches.toJsonAST.getOrElse(Json.Null)),
        ("branches-ignore", ignoredBranches.toJsonAST.getOrElse(Json.Null))
      ).filter { case (_, data) =>
        data match {
          case Json.Arr(elements) => elements.nonEmpty
          case _                  => false
        }
      }
      ("push", Json.Obj(fields))
    }
  }

  object Push {
    def apply(
      branches: Seq[Branch] = Seq.empty,
      ignoredBranches: Seq[Branch] = Seq.empty
    ): Push =
      Push(
        Chunk.fromIterable(branches),
        Chunk.fromIterable(ignoredBranches)
      )
  }

  case class Create private (
    branches: Chunk[Branch],
    ignoredBranches: Chunk[Branch]
  ) extends Trigger {
    override def toKeyValuePair: (String, Json) = {
      val fields = Chunk(
        ("branches", branches.toJsonAST.getOrElse(Json.Null)),
        ("branches-ignore", ignoredBranches.toJsonAST.getOrElse(Json.Null))
      ).filter { case (_, data) =>
        data match {
          case Json.Arr(elements) => elements.nonEmpty
          case _                  => false
        }
      }
      ("create", Json.Obj(fields))
    }
  }

  object Create {
    def apply(
      branches: Seq[Branch] = Seq.empty,
      ignoredBranches: Seq[Branch] = Seq.empty
    ): Create =
      Create(
        Chunk.fromIterable(branches),
        Chunk.fromIterable(ignoredBranches)
      )
  }
}

case class Strategy(matrix: Map[String, List[String]], maxParallel: Option[Int] = None, failFast: Boolean = true)

object Strategy {
  implicit val encoder: JsonEncoder[Strategy] =
    JsonEncoder[Json].contramap { s =>
      Json.Obj(
        ("fail-fast", Json.Bool(s.failFast)),
        ("max-parallel", s.maxParallel.toJsonAST.getOrElse(Json.Null)),
        ("matrix", s.matrix.toJsonAST.getOrElse(Json.Null))
      )
    }
}

case class ActionRef(ref: String)
object ActionRef {
  implicit val encoder: JsonEncoder[ActionRef] =
    JsonEncoder.string.contramap(_.ref)
}

sealed trait Condition {
  def &&(other: Condition): Condition
  def ||(other: Condition): Condition
  def asString: String
}

object Condition {
  case class Expression(expression: String) extends Condition {
    def &&(other: Condition): Condition =
      other match {
        case Expression(otherExpression: String) =>
          Expression(s"($expression) && ($otherExpression)")
        case Function(_: String) =>
          throw new IllegalArgumentException("Not supported currently")
      }

    def ||(other: Condition): Condition =
      other match {
        case Expression(otherExpression: String) =>
          Expression(s"($expression) || ($otherExpression)")
        case Function(_: String) =>
          throw new IllegalArgumentException("Not supported currently")
      }

    def asString: String = s"$${{ $expression }}"
  }

  case class Function(expression: String) extends Condition {
    def &&(other: Condition): Condition =
      throw new IllegalArgumentException("Not supported currently")

    def ||(other: Condition): Condition =
      throw new IllegalArgumentException("Not supported currently")

    def asString: String = expression
  }

  implicit val encoder: JsonEncoder[Condition] =
    JsonEncoder.string.contramap(_.asString)
}

sealed trait Step {
  def when(condition: Condition): Step
  def flatten: Chunk[Step.SingleStep]
}
object Step {
  case class SingleStep(
    name: String,
    id: Option[String] = None,
    uses: Option[ActionRef] = None,
    condition: Option[Condition] = None,
    parameters: Map[String, Json] = Map.empty,
    run: Option[String] = None,
    env: Map[String, String] = Map.empty
  ) extends Step {
    override def when(condition: Condition): Step =
      copy(condition = Some(condition))

    override def flatten: Chunk[Step.SingleStep] = Chunk.single(this)
  }

  case class StepSequence private (steps: Chunk[Step]) extends Step {
    override def when(condition: Condition): Step =
      copy(steps = steps.map(_.when(condition)))

    override def flatten: Chunk[SingleStep] =
      steps.flatMap(_.flatten)
  }

  object StepSequence {
    def apply(steps: Seq[Step]): StepSequence =
      StepSequence(Chunk.fromIterable(steps))
  }

  implicit val encoder: JsonEncoder[SingleStep] =
    JsonEncoder[Json].contramap { s =>
      Json.Obj(
        ("name", Json.Str(s.name)),
        ("id", s.id.toJsonAST.getOrElse(Json.Null)),
        ("uses", s.uses.toJsonAST.getOrElse(Json.Null)),
        ("if", s.condition.toJsonAST.getOrElse(Json.Null)),
        ("with", if (s.parameters.nonEmpty) s.parameters.toJsonAST.getOrElse(Json.Null) else Json.Null),
        ("run", s.run.toJsonAST.getOrElse(Json.Null)),
        ("env", if (s.env.nonEmpty) s.env.toJsonAST.getOrElse(Json.Null) else Json.Null)
      )
    }
}

case class ImageRef(ref: String)
object ImageRef {
  implicit val encoder: JsonEncoder[ImageRef] =
    JsonEncoder.string.contramap(_.ref)
}

/**
 * A port mapping, rendered as `inner:outer`.
 *
 * GitHub reads that as `<host>:<container>`, so `inner` is the port exposed on
 * the runner and `outer` the port inside the service container. A step reaching
 * the service connects to `inner` on localhost.
 */
case class ServicePort(inner: Int, outer: Int)
object ServicePort {
  implicit val encoder: JsonEncoder[ServicePort] =
    JsonEncoder.string.contramap(sp => s"${sp.inner}:${sp.outer}")
}

/**
 * A service container attached to a job.
 *
 * `options` carries the raw `docker create` arguments, which is where health
 * checks live. Without one, a job races the container: the service is started
 * but steps may run before it is accepting connections. For example:
 *
 * {{{
 * options = Some("--health-cmd pg_isready --health-interval 10s --health-timeout 5s --health-retries 5")
 * }}}
 */
case class Service(
  name: String,
  image: ImageRef,
  env: Map[String, String] = Map.empty,
  ports: Chunk[ServicePort] = Chunk.empty,
  options: Option[String] = None
)
object Service {
  implicit val encoder: JsonEncoder[Service] =
    JsonEncoder[Json].contramap { s =>
      Json.Obj(
        ("image", s.image.toJsonAST.getOrElse(Json.Null)),
        // Empty collections would otherwise render as `env: {}` and `ports: []`, which `dropNulls`
        // cannot remove.
        ("env", if (s.env.nonEmpty) s.env.toJsonAST.getOrElse(Json.Null) else Json.Null),
        ("ports", if (s.ports.nonEmpty) s.ports.toJsonAST.getOrElse(Json.Null) else Json.Null),
        ("options", s.options.toJsonAST.getOrElse(Json.Null))
      )
    }
}

/**
 * Whether an in-flight run is cancelled when a new one joins the same
 * concurrency group.
 *
 * GitHub accepts either a boolean or an expression here, which is what lets a
 * workflow cancel superseded pull request runs while letting releases run to
 * completion.
 */
sealed trait CancelInProgress {
  def toJsonValue: Json
}

object CancelInProgress {
  case object Always extends CancelInProgress { def toJsonValue: Json = Json.Bool(true)  }
  case object Never  extends CancelInProgress { def toJsonValue: Json = Json.Bool(false) }

  final case class When(condition: Condition) extends CancelInProgress {
    def toJsonValue: Json = Json.Str(condition.asString)
  }
}

case class Concurrency(group: String, cancelInProgress: CancelInProgress = CancelInProgress.Always)

object Concurrency {
  implicit val encoder: JsonEncoder[Concurrency] =
    JsonEncoder[Json].contramap { c =>
      Json.Obj(
        ("group", Json.Str(c.group)),
        ("cancel-in-progress", c.cancelInProgress.toJsonValue)
      )
    }
}

// The synthetic `copy`/`apply`/`unapply` all mention the deprecated `timeoutMinutes`, which would
// otherwise warn here on every compile - and `enableStrictCompile` turns warnings into errors.
@nowarn("cat=deprecation")
case class Job private (
  id: String,
  name: String,
  runsOn: String,
  @deprecated("Use jobTimeout instead", "0.6.4")
  timeoutMinutes: Int,
  continueOnError: Boolean,
  strategy: Option[Strategy],
  steps: Chunk[Step],
  need: Chunk[String],
  services: Chunk[Service],
  condition: Option[Condition],
  jobTimeout: Option[Int],
  concurrency: Option[Concurrency],
  permissions: Map[String, String]
) {
  def withPermissions(permissions: (String, String)*): Job =
    copy(permissions = permissions.toMap)

  def withStrategy(strategy: Strategy): Job =
    copy(strategy = Some(strategy))

  def withSteps(steps: Step*): Job =
    copy(steps = Chunk.fromIterable(steps))

  def withServices(services: Service*): Job =
    copy(services = Chunk.fromIterable(services))

  def withTimeout(minutes: Int): Job =
    copy(jobTimeout = Some(minutes))
}

object Job {

  /**
   * The historical default of the deprecated `timeoutMinutes` field.
   *
   * `timeoutMinutes` was accepted but never rendered, so no repository has ever
   * had a `timeout-minutes` line in its generated workflow. Emitting the
   * default would silently impose a 30 minute cap on every existing job,
   * killing any that legitimately runs longer, so only a value that differs
   * from this default is treated as a request for a timeout.
   */
  private final val UnsetTimeoutMinutes = 30

  def apply(
    id: String,
    name: String,
    runsOn: String = "ubuntu-latest",
    timeoutMinutes: Int = UnsetTimeoutMinutes,
    continueOnError: Boolean = false,
    strategy: Option[Strategy] = None,
    steps: Seq[Step] = Seq.empty,
    need: Seq[String] = Seq.empty,
    services: Seq[Service] = Seq.empty,
    condition: Option[Condition] = None,
    jobTimeout: Option[Int] = None,
    concurrency: Option[Concurrency] = None,
    permissions: Map[String, String] = Map.empty
  ): Job = Job(
    id = id,
    name = name,
    runsOn = runsOn,
    timeoutMinutes = timeoutMinutes,
    continueOnError = continueOnError,
    strategy = strategy,
    steps = Chunk.fromIterable(steps),
    need = Chunk.fromIterable(need),
    services = Chunk.fromIterable(services),
    condition = condition,
    jobTimeout = jobTimeout,
    concurrency = concurrency,
    permissions = permissions
  )

  /**
   * The timeout to render, if any.
   *
   * `jobTimeout` wins. Failing that, a `timeoutMinutes` that differs from its
   * default is honoured, so builds that set it before it was ever rendered -
   * zio-prelude sets 60 - get what they asked for without having to change.
   */
  @nowarn("cat=deprecation")
  private def timeoutOf(job: Job): Option[Int] =
    job.jobTimeout.orElse(Some(job.timeoutMinutes).filter(_ != UnsetTimeoutMinutes))

  implicit val encoder: JsonEncoder[Job] =
    JsonEncoder[Json].contramap { job =>
      val servicesJson = if (job.services.nonEmpty) {
        Json.Obj(job.services.map(svc => (svc.name, svc.toJsonAST.getOrElse(Json.Null))))
      } else {
        Json.Null
      }

      Json.Obj(
        ("name", Json.Str(job.name)),
        ("runs-on", Json.Str(job.runsOn)),
        // Job-level permissions replace the workflow-level ones for this job, so only
        // render them when a job actually asks for a different set.
        ("permissions", if (job.permissions.nonEmpty) job.permissions.toJsonAST.getOrElse(Json.Null) else Json.Null),
        ("timeout-minutes", timeoutOf(job).toJsonAST.getOrElse(Json.Null)),
        ("concurrency", job.concurrency.toJsonAST.getOrElse(Json.Null)),
        ("continue-on-error", Json.Bool(job.continueOnError)),
        ("strategy", job.strategy.toJsonAST.getOrElse(Json.Null)),
        ("needs", if (job.need.nonEmpty) job.need.toJsonAST.getOrElse(Json.Null) else Json.Null),
        ("services", servicesJson),
        ("if", job.condition.toJsonAST.getOrElse(Json.Null)),
        ("steps", StepSequence(job.steps).flatten.toJsonAST.getOrElse(Json.Null))
      )
    }
}

case class Workflow private (
  name: String,
  env: Map[String, String],
  triggers: Chunk[Trigger],
  jobs: Chunk[Job],
  concurrency: Option[Concurrency]
)(
  val permissions: Map[String, String]
) {
  def on(triggers: Trigger*): Workflow =
    copy(triggers = Chunk.fromIterable(triggers))(permissions)

  def withJobs(jobs: Job*): Workflow =
    copy(jobs = Chunk.fromIterable(jobs))(permissions)

  def addJob(job: Job): Workflow =
    copy(jobs = jobs :+ job)(permissions)

  def addJobs(newJobs: Chunk[Job]): Workflow =
    copy(jobs = jobs ++ newJobs)(permissions)
}

object Workflow {
  val defaultPermissions: Map[String, String] = Map("id-token" -> "write", "contents" -> "read")

  /**
   * One run per branch, except on the default branch where every run is kept.
   *
   * This was hard-coded into the encoder before it became configurable, so it
   * stays the default to keep generated workflows byte-identical for builds
   * that do not override it.
   */
  val defaultConcurrency: Concurrency = Concurrency(
    group =
      "${{ github.workflow }}-${{ github.ref == format('refs/heads/{0}', github.event.repository.default_branch) && github.run_id || github.ref }}",
    cancelInProgress = CancelInProgress.Always
  )

  def apply(
    name: String,
    env: Map[String, String] = Map.empty,
    triggers: Seq[Trigger] = Seq.empty,
    jobs: Seq[Job] = Seq.empty,
    permissions: Map[String, String] = defaultPermissions,
    concurrency: Option[Concurrency] = Some(defaultConcurrency)
    // `new` rather than the synthetic apply: with `concurrency` added, the two overloads take the
    // same number of named arguments and the call would be ambiguous.
  ): Workflow = new Workflow(
    name = name,
    env = env,
    triggers = Chunk.fromIterable(triggers),
    jobs = Chunk.fromIterable(jobs),
    concurrency = concurrency
  )(permissions)

  implicit val encoder: JsonEncoder[Workflow] =
    JsonEncoder[Json].contramap { wf =>
      val onJson = if (wf.triggers.isEmpty) {
        Json.Null
      } else {
        Json.Obj(wf.triggers.map(_.toKeyValuePair))
      }

      val concurrencyJson = wf.concurrency.toJsonAST.getOrElse(Json.Null)

      val jobsJson = Json.Obj(wf.jobs.map(job => (job.id, job.toJsonAST.getOrElse(Json.Null))): _*)

      Json.Obj(
        ("name", Json.Str(wf.name)),
        ("env", wf.env.toJsonAST.getOrElse(Json.Null)),
        ("on", onJson),
        ("permissions", wf.permissions.toJsonAST.getOrElse(Json.Null)),
        ("concurrency", concurrencyJson),
        ("jobs", jobsJson)
      )
    }
}
