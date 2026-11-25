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

/**
 * A runner operating system, rendered as the `os` values of a job matrix and as
 * `runs-on`.
 */
sealed trait OS {

  /** The runner label rendered in the workflow, for example `ubuntu-latest`. */
  val asString: String
}

/** The predefined operating systems. */
object OS {

  /** The `ubuntu-latest` runner label. */
  case object UbuntuLatest extends OS { val asString = "ubuntu-latest" }
}

/**
 * A bot account that opens dependency update pull requests, identified by its
 * GitHub login.
 */
sealed trait DependencyBot {

  /** The GitHub login of the bot account, including the `[bot]` suffix. */
  def login: String
}

/** The predefined and parameterised dependency bots. */
object DependencyBot {

  /** The Dependabot account, with login `dependabot[bot]`. */
  case object Dependabot extends DependencyBot { val login = "dependabot[bot]" }

  /** The Renovate account, with login `renovate[bot]`. */
  case object Renovate extends DependencyBot { val login = "renovate[bot]" }

  /**
   * A Scala Steward account running as the GitHub App `githubAppName`, with
   * login `githubAppName[bot]`.
   */
  final case class ScalaSteward(githubAppName: String) extends DependencyBot {

    /** The login, built as the app name followed by `[bot]`. */
    val login: String = s"$githubAppName[bot]"
  }

  /** A bot with the given `login`, used as is. */
  final case class Custom(login: String) extends DependencyBot
}

/**
 * How the Netlify website preview of a pull request is deployed.
 *
 *   - `Automatic`: deploy after every successful CI run on a pull request.
 *   - `OnDemand`: post a comment with a checkbox after every successful CI run,
 *     and deploy only when someone with write access ticks it.
 */
sealed trait DeployPreviewMode

/** The modes of deploying a website preview. */
object DeployPreviewMode {

  /** Deploys the preview after every successful CI run on a pull request. */
  case object Automatic extends DeployPreviewMode

  /**
   * Posts a comment with a checkbox after every successful CI run, and deploys
   * only when someone with write access ticks it.
   */
  case object OnDemand extends DeployPreviewMode
}

/**
 * A branch filter entry of a trigger, rendered as a string in a `branches` or
 * `branches-ignore` list.
 */
sealed trait Branch

/** The branch filters and their JSON encoder. */
object Branch {

  /** Matches every branch, rendered as `*`. */
  case object All extends Branch

  /** Matches the branch or pattern `name`, rendered as is. */
  case class Named(name: String) extends Branch

  /** Encodes `All` as `*` and `Named(name)` as `name`. */
  implicit val encoder: JsonEncoder[Branch] =
    JsonEncoder.string.contramap {
      case All         => "*"
      case Named(name) => name
    }
}

/**
 * A workflow event that starts a run, rendered as one entry under the
 * workflow's `on` key.
 */
sealed trait Trigger {

  /**
   * Returns the event name and its configuration, the key and value placed
   * under `on`.
   */
  def toKeyValuePair: (String, Json)
}

/**
 * An input offered by a `workflow_dispatch` trigger, rendered under `inputs`
 * with the keys `description`, `required` and `default`.
 *
 * @param key
 *   the input name, used as the key under `inputs`
 * @param defaultValue
 *   the value rendered as `default`
 */
case class Input(key: String, description: String, required: Boolean, defaultValue: String)

/** The workflow triggers. */
object Trigger {

  /**
   * The `workflow_dispatch` event, which lets a run be started by hand. Renders
   * an empty object when `inputs` is empty, and otherwise an `inputs` object
   * that keeps the declaration order.
   */
  case class WorkflowDispatch private (
    inputs: Chunk[Input]
  ) extends Trigger {
    override def toKeyValuePair: (String, Json) = {
      // GitHub reads the inputs from an `inputs` key under `workflow_dispatch`; inputs placed
      // directly under it are not offered on the "Run workflow" form. Json.Obj keeps them in the
      // order they were declared, which is the order the form lists them in.
      val inputsObj = Json.Obj(inputs.map { i =>
        (
          i.key,
          Json.Obj(
            ("description", Json.Str(i.description)),
            ("required", Json.Bool(i.required)),
            ("default", Json.Str(i.defaultValue))
          )
        )
      })
      ("workflow_dispatch", if (inputs.isEmpty) Json.Obj() else Json.Obj("inputs" -> inputsObj))
    }
  }

  /** Constructor of [[Trigger.WorkflowDispatch]]. */
  object WorkflowDispatch {

    /** Creates the trigger with the given `inputs`, none by default. */
    def apply(inputs: Seq[Input] = Seq.empty): WorkflowDispatch =
      WorkflowDispatch(Chunk.fromIterable(inputs))
  }

  /**
   * The `schedule` event, rendered as a list with one `cron` entry per element
   * of `crons`.
   */
  case class Schedule private (crons: Chunk[String]) extends Trigger {
    override def toKeyValuePair: (String, Json) =
      (
        "schedule",
        crons.map(cron => Json.Obj(("cron", Json.Str(cron)))).toJsonAST.getOrElse(Json.Null)
      )
  }

  /** Constructor of [[Trigger.Schedule]]. */
  object Schedule {

    /** Creates the trigger from the given cron expressions. */
    def apply(crons: Seq[String]): Schedule = Schedule(Chunk.fromIterable(crons))
  }

  /**
   * The `release` event, rendered with a `types` list. The `types` key is
   * rendered even when `releaseTypes` is empty.
   */
  case class Release private (
    releaseTypes: Chunk[String]
  ) extends Trigger {
    override def toKeyValuePair: (String, Json) =
      ("release", Json.Obj("types" -> releaseTypes.toJsonAST.getOrElse(Json.Null)))
  }

  /** Constructor of [[Trigger.Release]]. */
  object Release {

    /**
     * Creates the trigger with the given release activity types, none by
     * default.
     */
    def apply(releaseTypes: Seq[String] = Seq.empty): Release =
      Release(Chunk.fromIterable(releaseTypes))
  }

  /**
   * The `pull_request` event. Branches are rendered under `branches` and
   * ignored branches under `branches-ignore`; a key is left out when its list
   * is empty.
   */
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

  /** Constructor of [[Trigger.PullRequest]]. */
  object PullRequest {

    /**
     * Creates the trigger with the given branch filters, both empty by default.
     */
    def apply(
      branches: Seq[Branch] = Seq.empty,
      ignoredBranches: Seq[Branch] = Seq.empty
    ): PullRequest =
      PullRequest(
        Chunk.fromIterable(branches),
        Chunk.fromIterable(ignoredBranches)
      )
  }

  /**
   * The `pull_request_target` event. Renders `types`, `branches` and
   * `branches-ignore`; a key is left out when its list is empty.
   */
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

  /** Constructor of [[Trigger.PullRequestTarget]]. */
  object PullRequestTarget {

    /**
     * Creates the trigger with the given activity types and branch filters, all
     * empty by default.
     */
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

  /**
   * The `push` event. Branches are rendered under `branches` and ignored
   * branches under `branches-ignore`; a key is left out when its list is empty.
   */
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

  /** Constructor of [[Trigger.Push]]. */
  object Push {

    /**
     * Creates the trigger with the given branch filters, both empty by default.
     */
    def apply(
      branches: Seq[Branch] = Seq.empty,
      ignoredBranches: Seq[Branch] = Seq.empty
    ): Push =
      Push(
        Chunk.fromIterable(branches),
        Chunk.fromIterable(ignoredBranches)
      )
  }

  /**
   * The `create` event. Branches are rendered under `branches` and ignored
   * branches under `branches-ignore`; a key is left out when its list is empty.
   */
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

  /** Constructor of [[Trigger.Create]]. */
  object Create {

    /**
     * Creates the trigger with the given branch filters, both empty by default.
     */
    def apply(
      branches: Seq[Branch] = Seq.empty,
      ignoredBranches: Seq[Branch] = Seq.empty
    ): Create =
      Create(
        Chunk.fromIterable(branches),
        Chunk.fromIterable(ignoredBranches)
      )
  }

  /**
   * The `workflow_run` event, rendered with the `workflows` to watch and the
   * activity `types`.
   */
  case class WorkflowRun private (
    workflows: Chunk[String],
    types: Chunk[String]
  ) extends Trigger {
    override def toKeyValuePair: (String, Json) =
      (
        "workflow_run",
        Json.Obj(
          ("workflows", workflows.toJsonAST.getOrElse(Json.Null)),
          ("types", types.toJsonAST.getOrElse(Json.Null))
        )
      )
  }

  /** Constructor of [[Trigger.WorkflowRun]]. */
  object WorkflowRun {

    /**
     * Creates the trigger for the named `workflows`. The `types` default to
     * `completed`.
     */
    def apply(workflows: Seq[String], types: Seq[String] = Seq("completed")): WorkflowRun =
      WorkflowRun(Chunk.fromIterable(workflows), Chunk.fromIterable(types))
  }

  /** The `issue_comment` event, rendered with the activity `types`. */
  case class IssueComment private (types: Chunk[String]) extends Trigger {
    override def toKeyValuePair: (String, Json) =
      ("issue_comment", Json.Obj(("types", types.toJsonAST.getOrElse(Json.Null))))
  }

  /** Constructor of [[Trigger.IssueComment]]. */
  object IssueComment {

    /**
     * Creates the trigger with the given activity types, which default to
     * `created`, `edited` and `deleted`.
     */
    def apply(types: Seq[String] = Seq("created", "edited", "deleted")): IssueComment =
      IssueComment(Chunk.fromIterable(types))
  }
}

/**
 * A job strategy, rendered with the keys `fail-fast`, `max-parallel` and
 * `matrix`.
 *
 * @param matrix
 *   the values of each matrix axis, keyed by axis name
 * @param maxParallel
 *   the value of `max-parallel`, left unset when `None`
 * @param failFast
 *   the value of `fail-fast`, `true` by default
 */
case class Strategy(matrix: Map[String, List[String]], maxParallel: Option[Int] = None, failFast: Boolean = true)

/** The JSON encoder of [[Strategy]]. */
object Strategy {

  /**
   * Encodes a strategy as an object with the keys `fail-fast`, `max-parallel`
   * and `matrix`.
   */
  implicit val encoder: JsonEncoder[Strategy] =
    JsonEncoder[Json].contramap { s =>
      Json.Obj(
        ("fail-fast", Json.Bool(s.failFast)),
        ("max-parallel", s.maxParallel.toJsonAST.getOrElse(Json.Null)),
        ("matrix", s.matrix.toJsonAST.getOrElse(Json.Null))
      )
    }
}

/**
 * A reference to an action, such as `actions/checkout@v7.0.1`, rendered as a
 * string under `uses`.
 */
case class ActionRef(ref: String)

/** The JSON encoder of [[ActionRef]]. */
object ActionRef {

  /** Encodes the reference as a plain string. */
  implicit val encoder: JsonEncoder[ActionRef] =
    JsonEncoder.string.contramap(_.ref)
}

/** A condition rendered under `if`, on a step or on a job. */
sealed trait Condition {

  /** Returns the conjunction of this condition and `other`. */
  def &&(other: Condition): Condition

  /** Returns the disjunction of this condition and `other`. */
  def ||(other: Condition): Condition

  /** Returns the text rendered under `if`. */
  def asString: String
}

/** The kinds of condition and their JSON encoder. */
object Condition {

  /** A GitHub expression, rendered wrapped as `\${{ expression }}`. */
  case class Expression(expression: String) extends Condition {

    /**
     * Returns an expression that joins the two with `&&`, each side in
     * parentheses. Throws a `java.lang.IllegalArgumentException` with the
     * message "Not supported currently" when `other` is a `Function`.
     */
    def &&(other: Condition): Condition =
      other match {
        case Expression(otherExpression: String) =>
          Expression(s"($expression) && ($otherExpression)")
        case Function(_: String) =>
          throw new IllegalArgumentException("Not supported currently")
      }

    /**
     * Returns an expression that joins the two with `||`, each side in
     * parentheses. Throws a `java.lang.IllegalArgumentException` with the
     * message "Not supported currently" when `other` is a `Function`.
     */
    def ||(other: Condition): Condition =
      other match {
        case Expression(otherExpression: String) =>
          Expression(s"($expression) || ($otherExpression)")
        case Function(_: String) =>
          throw new IllegalArgumentException("Not supported currently")
      }

    /** Returns the expression wrapped as `\${{ expression }}`. */
    def asString: String = s"$${{ $expression }}"
  }

  /**
   * A status check function such as `failure()`, rendered as is without the
   * `\${{ }}` wrapper.
   */
  case class Function(expression: String) extends Condition {

    /**
     * Throws a `java.lang.IllegalArgumentException` with the message "Not
     * supported currently".
     */
    def &&(other: Condition): Condition =
      throw new IllegalArgumentException("Not supported currently")

    /**
     * Throws a `java.lang.IllegalArgumentException` with the message "Not
     * supported currently".
     */
    def ||(other: Condition): Condition =
      throw new IllegalArgumentException("Not supported currently")

    /** Returns the function text unchanged. */
    def asString: String = expression
  }

  /** Encodes a condition as the string returned by `asString`. */
  implicit val encoder: JsonEncoder[Condition] =
    JsonEncoder.string.contramap(_.asString)
}

/**
 * One step of a job, or a sequence of steps that is flattened into the job's
 * step list.
 */
sealed trait Step {

  /**
   * Returns a copy with `condition` as the `if` condition of each step it
   * contains, replacing any condition already set.
   */
  def when(condition: Condition): Step

  /** Returns the single steps this step consists of, in order. */
  def flatten: Chunk[Step.SingleStep]
}

/** The kinds of step and their JSON encoder. */
object Step {

  /**
   * A single workflow step.
   *
   * @param uses
   *   the action to run, rendered as `uses`
   * @param condition
   *   rendered as `if`
   * @param parameters
   *   the inputs of the action, rendered as `with` and left out when empty
   * @param run
   *   the shell command to run
   * @param env
   *   the environment of the step, left out when empty
   * @param continueOnError
   *   rendered as `continue-on-error: true` when `true`, and left out otherwise
   */
  case class SingleStep(
    name: String,
    id: Option[String] = None,
    uses: Option[ActionRef] = None,
    condition: Option[Condition] = None,
    parameters: Map[String, Json] = Map.empty,
    run: Option[String] = None,
    env: Map[String, String] = Map.empty,
    // Kept boolean (rather than an `Option`) to match GitHub's own field, but only ever rendered
    // when `true` - a step that never sets it must not gain a `continue-on-error: false` line, or
    // every existing generated workflow (and its checked-in golden fixtures) picks up a spurious
    // diff on every step, for every consumer of this DSL.
    continueOnError: Boolean = false
  ) extends Step {
    override def when(condition: Condition): Step =
      copy(condition = Some(condition))

    override def flatten: Chunk[Step.SingleStep] = Chunk.single(this)
  }

  /**
   * A group of steps. `when` applies a condition to each of them and `flatten`
   * returns all their single steps in order.
   */
  case class StepSequence private (steps: Chunk[Step]) extends Step {
    override def when(condition: Condition): Step =
      copy(steps = steps.map(_.when(condition)))

    override def flatten: Chunk[SingleStep] =
      steps.flatMap(_.flatten)
  }

  /** Constructor of [[Step.StepSequence]]. */
  object StepSequence {

    /** Creates a sequence from the given steps. */
    def apply(steps: Seq[Step]): StepSequence =
      StepSequence(Chunk.fromIterable(steps))
  }

  /**
   * Encodes a step with the keys `name`, `id`, `uses`, `if`, `with`, `run`,
   * `env` and `continue-on-error`. Keys without a value are encoded as null.
   */
  implicit val encoder: JsonEncoder[SingleStep] =
    JsonEncoder[Json].contramap { s =>
      Json.Obj(
        ("name", Json.Str(s.name)),
        ("id", s.id.toJsonAST.getOrElse(Json.Null)),
        ("uses", s.uses.toJsonAST.getOrElse(Json.Null)),
        ("if", s.condition.toJsonAST.getOrElse(Json.Null)),
        ("with", if (s.parameters.nonEmpty) s.parameters.toJsonAST.getOrElse(Json.Null) else Json.Null),
        ("run", s.run.toJsonAST.getOrElse(Json.Null)),
        ("env", if (s.env.nonEmpty) s.env.toJsonAST.getOrElse(Json.Null) else Json.Null),
        ("continue-on-error", if (s.continueOnError) Json.Bool(true) else Json.Null)
      )
    }
}

/** A container image reference, such as `postgres:17`, rendered as a string. */
case class ImageRef(ref: String)

/** The JSON encoder of [[ImageRef]]. */
object ImageRef {

  /** Encodes the reference as a plain string. */
  implicit val encoder: JsonEncoder[ImageRef] =
    JsonEncoder.string.contramap(_.ref)
}

/**
 * A port mapping, rendered as `inner:outer`.
 *
 * GitHub reads that as `<host>:<container>`, so `inner` is the port exposed on
 * the runner and `outer` the port inside the service container. A step that
 * runs on the runner reaches the service at `inner` on localhost.
 */
case class ServicePort(inner: Int, outer: Int)

/** The JSON encoder of [[ServicePort]]. */
object ServicePort {

  /** Encodes a port mapping as the string `inner:outer`. */
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
 * val postgres = Service(
 *   name = "postgres",
 *   image = ImageRef("postgres:17"),
 *   options = Some("--health-cmd pg_isready --health-interval 10s --health-timeout 5s --health-retries 5")
 * )
 * }}}
 */
case class Service(
  name: String,
  image: ImageRef,
  env: Map[String, String] = Map.empty,
  ports: Chunk[ServicePort] = Chunk.empty,
  options: Option[String] = None
)

/** The JSON encoder of [[Service]]. */
object Service {

  /**
   * Encodes a service with the keys `image`, `env`, `ports` and `options`. The
   * `env` and `ports` keys are encoded as null when empty.
   */
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

  /** Returns the value rendered under `cancel-in-progress`. */
  def toJsonValue: Json
}

/** The kinds of cancel-in-progress setting. */
object CancelInProgress {

  /** Always cancels the earlier run, rendered as `true`. */
  case object Always extends CancelInProgress { def toJsonValue: Json = Json.Bool(true) }

  /** Never cancels the earlier run, rendered as `false`. */
  case object Never extends CancelInProgress { def toJsonValue: Json = Json.Bool(false) }

  /**
   * Cancels the earlier run according to `condition`, rendered as its
   * `asString` text.
   */
  final case class When(condition: Condition) extends CancelInProgress {

    /** Returns the `asString` text of the condition as a JSON string. */
    def toJsonValue: Json = Json.Str(condition.asString)
  }
}

/**
 * A concurrency setting, rendered with the keys `group` and
 * `cancel-in-progress`.
 *
 * @param group
 *   the concurrency group name, which may contain expressions
 * @param cancelInProgress
 *   whether an in-flight run of the group is cancelled,
 *   `CancelInProgress.Always` by default
 */
case class Concurrency(group: String, cancelInProgress: CancelInProgress = CancelInProgress.Always)

/** The JSON encoder of [[Concurrency]]. */
object Concurrency {

  /**
   * Encodes a concurrency setting with the keys `group` and
   * `cancel-in-progress`.
   */
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
/**
 * A workflow job. Create one with `Job.apply`, which takes sequences and fills
 * in defaults.
 *
 * @param id
 *   the key of the job under `jobs`
 * @param runsOn
 *   the runner label rendered as `runs-on`
 * @param timeoutMinutes
 *   deprecated; a value other than 30 is rendered as `timeout-minutes` when
 *   `jobTimeout` is not set
 * @param need
 *   the ids of the jobs this job waits for, rendered as `needs`
 * @param condition
 *   rendered as `if`
 * @param jobTimeout
 *   the value of `timeout-minutes`, which takes precedence over
 *   `timeoutMinutes`
 * @param permissions
 *   the job-level `permissions`, left out when empty
 */
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

  /** Returns a copy with the given strategy. */
  def withStrategy(strategy: Strategy): Job =
    copy(strategy = Some(strategy))

  /** Returns a copy with the given `runs-on` label. */
  def withRunsOn(runsOn: String): Job =
    copy(runsOn = runsOn)

  /**
   * Returns a copy whose steps are the given ones, replacing the existing
   * steps.
   */
  def withSteps(steps: Step*): Job =
    copy(steps = Chunk.fromIterable(steps))

  /**
   * Returns a copy whose services are the given ones, replacing the existing
   * services.
   */
  def withServices(services: Service*): Job =
    copy(services = Chunk.fromIterable(services))

  /**
   * Returns a copy with `timeout-minutes` set to the given number of minutes.
   */
  def withTimeout(minutes: Int): Job =
    copy(jobTimeout = Some(minutes))

  /**
   * Returns a copy whose job-level permissions are the given pairs, replacing
   * the existing permissions.
   */
  def withPermissions(permissions: (String, String)*): Job =
    copy(permissions = permissions.toMap)
}

/** Constructors and the encoder of [[Job]]. */
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

  /**
   * Creates a job. `runsOn` defaults to `ubuntu-latest`, and the other
   * arguments default to empty or unset values.
   */
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

  /**
   * Encodes the body of a job, without its `id`. Renders `name`, `runs-on`,
   * `timeout-minutes`, `concurrency`, `continue-on-error`, `permissions`,
   * `strategy`, `needs`, `services`, `if` and `steps`, where the steps are
   * flattened. Keys without a value are encoded as null.
   */
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
        ("timeout-minutes", timeoutOf(job).toJsonAST.getOrElse(Json.Null)),
        ("concurrency", job.concurrency.toJsonAST.getOrElse(Json.Null)),
        ("continue-on-error", Json.Bool(job.continueOnError)),
        (
          "permissions",
          if (job.permissions.nonEmpty) job.permissions.toJsonAST.getOrElse(Json.Null) else Json.Null
        ),
        ("strategy", job.strategy.toJsonAST.getOrElse(Json.Null)),
        ("needs", if (job.need.nonEmpty) job.need.toJsonAST.getOrElse(Json.Null) else Json.Null),
        ("services", servicesJson),
        ("if", job.condition.toJsonAST.getOrElse(Json.Null)),
        ("steps", StepSequence(job.steps).flatten.toJsonAST.getOrElse(Json.Null))
      )
    }
}

/**
 * A GitHub Actions workflow. Create one with `Workflow.apply`.
 *
 * @param env
 *   the workflow-level `env`
 * @param triggers
 *   the events rendered under `on`
 * @param concurrency
 *   the workflow-level `concurrency`, left out when `None`
 */
case class Workflow private (
  name: String,
  env: Map[String, String],
  triggers: Chunk[Trigger],
  jobs: Chunk[Job],
  concurrency: Option[Concurrency]
)(
  val permissions: Map[String, String],
  val renderPermissions: Boolean
) {

  /**
   * Returns a copy whose triggers are the given ones, replacing the existing
   * triggers.
   */
  def on(triggers: Trigger*): Workflow =
    copy(triggers = Chunk.fromIterable(triggers))(permissions, renderPermissions)

  /**
   * Returns a copy whose jobs are the given ones, replacing the existing jobs.
   */
  def withJobs(jobs: Job*): Workflow =
    copy(jobs = Chunk.fromIterable(jobs))(permissions, renderPermissions)

  /** Returns a copy with `job` appended after the existing jobs. */
  def addJob(job: Job): Workflow =
    copy(jobs = jobs :+ job)(permissions, renderPermissions)

  /** Returns a copy with `newJobs` appended after the existing jobs. */
  def addJobs(newJobs: Chunk[Job]): Workflow =
    copy(jobs = jobs ++ newJobs)(permissions, renderPermissions)

  /**
   * Omits the workflow-level `permissions` block altogether.
   *
   * That is not the same as an empty map, which renders `permissions: {}` and
   * grants the token nothing. With no block, the workflow runs with the
   * repository's default token permissions.
   */
  def withoutPermissions: Workflow =
    copy()(permissions, renderPermissions = false)
}

/** Constructors, defaults and the encoder of [[Workflow]]. */
object Workflow {

  /**
   * The default workflow-level permissions: `id-token` set to `write` and
   * `contents` set to `read`.
   */
  val defaultPermissions: Map[String, String] = Map("id-token" -> "write", "contents" -> "read")

  /**
   * One run per branch, except on the default branch where every run is kept.
   *
   * The group is built from the workflow name and, on the default branch, the
   * run id (otherwise the ref), and `cancelInProgress` is
   * `CancelInProgress.Always`. It is the default `concurrency` of
   * `Workflow.apply`.
   */
  val defaultConcurrency: Concurrency = Concurrency(
    group =
      "${{ github.workflow }}-${{ github.ref == format('refs/heads/{0}', github.event.repository.default_branch) && github.run_id || github.ref }}",
    cancelInProgress = CancelInProgress.Always
  )

  /**
   * Creates a workflow. `permissions` defaults to `defaultPermissions` and
   * `concurrency` to `Some(defaultConcurrency)`; the other arguments default to
   * empty.
   */
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
  )(permissions, renderPermissions = true)

  /**
   * Encodes a workflow with the keys `name`, `env`, `on`, `permissions`,
   * `concurrency` and `jobs`. `on` is encoded as null when there are no
   * triggers, and `permissions` when the workflow was built with
   * `withoutPermissions`.
   */
  implicit val encoder: JsonEncoder[Workflow] =
    JsonEncoder[Json].contramap { wf =>
      val onJson = if (wf.triggers.isEmpty) {
        Json.Null
      } else {
        Json.Obj(wf.triggers.map(_.toKeyValuePair))
      }

      val concurrencyJson = wf.concurrency.toJsonAST.getOrElse(Json.Null)

      val jobsJson = Json.Obj(wf.jobs.map(job => (job.id, job.toJsonAST.getOrElse(Json.Null))): _*)

      val permissionsJson =
        if (wf.renderPermissions) wf.permissions.toJsonAST.getOrElse(Json.Null) else Json.Null

      Json.Obj(
        ("name", Json.Str(wf.name)),
        ("env", wf.env.toJsonAST.getOrElse(Json.Null)),
        ("on", onJson),
        ("permissions", permissionsJson),
        ("concurrency", concurrencyJson),
        ("jobs", jobsJson)
      )
    }
}
