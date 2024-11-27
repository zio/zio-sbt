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
import scala.util.{Failure, Success, Try}

import zio.json._
import zio.json.ast.Json

abstract class OS(name: String) {
  val asString: String = name
}
object OS {

  def apply(name: String): OS = Custom(name)

  case class Custom(name: String) extends OS(name)

  case object UbuntuLatest extends OS("ubuntu-latest")
  case object Ubuntu2404   extends OS("ubuntu-24.04")
  case object Ubuntu2204   extends OS("ubuntu-22.04")
}

sealed trait Branch
object Branch {
  case object All                extends Branch
  case class Named(name: String) extends Branch

  implicit lazy val codec: JsonCodec[Branch] = JsonCodec.string.transform(
    {
      case "*"  => All
      case name => Named(name)
    },
    {
      case All         => "*"
      case Named(name) => name
    }
  )
}

@jsonMemberNames(SnakeCase)
case class Triggers(
  workflowDispatch: Trigger.WorkflowDispatch = Trigger.WorkflowDispatch(),
  release: Option[Trigger.Release] = None,
  pullRequest: Option[Trigger.PullRequest] = None,
  push: Option[Trigger.Push] = None,
  create: Option[Trigger.Create] = None
)

object Triggers {

  implicit lazy val codec: JsonCodec[Triggers] = DeriveJsonCodec.gen[Triggers]
}

sealed trait Trigger

object Trigger {
  case class InputValue(description: String, required: Boolean, default: String)
  object InputValue {
    implicit lazy val jsonCodec: JsonCodec[InputValue] = DeriveJsonCodec.gen[InputValue]
  }

  case class WorkflowDispatch(
    inputs: Option[ListMap[String, InputValue]] = None
  ) extends Trigger

  object WorkflowDispatch {
    implicit def listMapCodec[K: JsonFieldDecoder: JsonFieldEncoder, V: JsonCodec]: JsonCodec[ListMap[K, V]] =
      JsonCodec(
        JsonEncoder.keyValueIterable[K, V, ListMap],
        JsonDecoder.keyValueChunk[K, V].map(c => ListMap(c: _*))
      )

    implicit lazy val jsonCodec: JsonCodec[WorkflowDispatch] = DeriveJsonCodec.gen[WorkflowDispatch]
  }

  case class Release(
    types: Seq[ReleaseType] = Seq.empty
  ) extends Trigger

  object Release {
    implicit lazy val jsonCodec: JsonCodec[Release] = DeriveJsonCodec.gen[Release]
  }

  sealed trait ReleaseType
  object ReleaseType {
    case object Created     extends ReleaseType
    case object Published   extends ReleaseType
    case object Prereleased extends ReleaseType

    implicit lazy val codec: JsonCodec[ReleaseType] = JsonCodec.string.transformOrFail(
      {
        case "created"     => Right(Created)
        case "published"   => Right(Published)
        case "prereleased" => Right(Prereleased)
        case other         => Left(s"Invalid release type: $other")
      },
      {
        case Created     => "created"
        case Published   => "published"
        case Prereleased => "prereleased"
      }
    )
  }

  @jsonMemberNames(KebabCase)
  case class PullRequest(
    // types: Option[Seq[PullRequestType]] = None,
    branches: Option[Seq[Branch]] = None,
    branchesIgnore: Option[Seq[Branch]] = None,
    paths: Option[Seq[String]] = None
  ) extends Trigger

  object PullRequest {
    implicit lazy val jsonCodec: JsonCodec[PullRequest] = DeriveJsonCodec.gen[PullRequest]
  }

  case class Push(
    branches: Option[Seq[Branch]] = None,
    branchesIgnore: Option[Seq[Branch]] = None
  ) extends Trigger

  object Push {
    implicit lazy val jsonCodec: JsonCodec[Push] = DeriveJsonCodec.gen[Push]
  }

  case class Create(
    branches: Option[Seq[Branch]] = None,
    branchesIgnore: Option[Seq[Branch]] = None
  ) extends Trigger

  object Create {
    implicit lazy val jsonCodec: JsonCodec[Create] = DeriveJsonCodec.gen[Create]
  }
}

@jsonMemberNames(KebabCase)
case class Strategy(matrix: ListMap[String, List[String]], maxParallel: Option[Int] = None, failFast: Boolean = true)

object Strategy {
  import Workflow.listMapCodec

  implicit lazy val codec: JsonCodec[Strategy] = DeriveJsonCodec.gen[Strategy]
}

case class ActionRef(ref: String)
object ActionRef {
  implicit lazy val codec: JsonCodec[ActionRef] = JsonCodec.string.transform(ActionRef(_), _.ref)
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

  object Expression {
    implicit lazy val codec: JsonCodec[Expression] = JsonCodec.string.transform(Expression(_), _.asString)
  }

  case class Function(expression: String) extends Condition {
    def &&(other: Condition): Condition =
      throw new IllegalArgumentException("Not supported currently")

    def ||(other: Condition): Condition =
      throw new IllegalArgumentException("Not supported currently")

    def asString: String = expression
  }

  object Function {
    implicit lazy val codec: JsonCodec[Function] = JsonCodec.string.transform(Function(_), _.expression)
  }

  implicit lazy val codec: JsonCodec[Condition] = JsonCodec.string.transform(
    {
      case expression if expression.startsWith("${{") => Expression(expression)
      case expression                                 => Function(expression)
    },
    _.asString
  )
}

sealed trait Step {
  def when(condition: Condition): Step
  def flatten: Seq[Step.SingleStep]
}
object Step {
  case class SingleStep(
    name: String,
    id: Option[String] = None,
    uses: Option[ActionRef] = None,
    `if`: Option[Condition] = None,
    `with`: Option[ListMap[String, Json]] = None,
    run: Option[String] = None,
    env: Option[ListMap[String, String]] = None
  ) extends Step {
    override def when(condition: Condition): Step =
      copy(`if` = Some(condition))

    override def flatten: Seq[Step.SingleStep] = Seq(this)
  }

  object SingleStep {
    import Workflow.listMapCodec

    implicit lazy val codec: JsonCodec[SingleStep] = DeriveJsonCodec.gen[SingleStep]
  }

  case class StepSequence(steps: Seq[Step]) extends Step {
    override def when(condition: Condition): Step =
      copy(steps = steps.map(_.when(condition)))

    override def flatten: Seq[SingleStep] =
      steps.flatMap(_.flatten)
  }

  implicit lazy val codec: JsonCodec[Step] = DeriveJsonCodec.gen[Step]
}

case class ImageRef(ref: String)
object ImageRef {
  implicit lazy val codec: JsonCodec[ImageRef] = JsonCodec.string.transform(ImageRef(_), _.ref)
}

case class ServicePort(inner: Int, outer: Int)
object ServicePort {
  implicit lazy val codec: JsonCodec[ServicePort] = JsonCodec.string.transformOrFail(
    v =>
      Try(v.split(":", 2).map(_.toInt).toList) match {
        case Success(inner :: outer :: Nil) => Right(ServicePort(inner.toInt, outer.toInt))
        case Success(_)                     => Left("Invalid service port format: " + v)
        case Failure(_)                     => Left("Invalid service port format: " + v)
      },
    sp => s"${sp.inner}:${sp.outer}"
  )
}

case class Service(
  name: String,
  image: ImageRef,
  env: Option[Map[String, String]] = None,
  ports: Option[Seq[ServicePort]] = None
)
object Service {
  implicit lazy val codec: JsonCodec[Service] = DeriveJsonCodec.gen[Service]
}

@jsonMemberNames(KebabCase)
case class Job(
  name: String,
  runsOn: String = "ubuntu-latest",
  timeoutMinutes: Option[Int] = None,
  continueOnError: Boolean = false,
  strategy: Option[Strategy] = None,
  needs: Option[Seq[String]] = None,
  services: Option[Seq[Service]] = None,
  `if`: Option[Condition] = None,
  steps: Seq[Step.SingleStep] = Seq.empty
) {

  def id: String = name.toLowerCase().replace(" ", "-")

  def withStrategy(strategy: Strategy): Job =
    copy(strategy = Some(strategy))

  def withSteps(steps: Step*): Job = steps match {
    case steps: Step.StepSequence =>
      copy(steps = steps.flatten)
    case step: Step.SingleStep =>
      copy(steps = step :: Nil)
  }

  def withServices(services: Service*): Job =
    copy(services = Some(services))

  def withRunsOn(runsOn: String): Job =
    copy(runsOn = runsOn)

  def withName(name: String): Job =
    copy(name = name)

  def withTimeoutMinutes(timeoutMinutes: Option[Int]): Job =
    copy(timeoutMinutes = timeoutMinutes)

  def withContinueOnError(continueOnError: Boolean): Job =
    copy(continueOnError = continueOnError)

  def withStrategy(strategy: Option[Strategy]): Job =
    copy(strategy = strategy)

  def withNeeds(needs: Option[Seq[String]]): Job =
    copy(needs = needs)
}

object Job {
  implicit lazy val codec: JsonCodec[Job] = DeriveJsonCodec.gen[Job]
}

@jsonMemberNames(KebabCase)
case class Concurrency(
  group: String,
  cancelInProgress: Boolean = true
)

object Concurrency {
  implicit lazy val codec: JsonCodec[Concurrency] = DeriveJsonCodec.gen[Concurrency]
}

case class Workflow(
  name: String,
  env: Option[ListMap[String, String]] = None,
  on: Option[Triggers] = None,
  concurrency: Concurrency = Concurrency(
    "${{ github.workflow }}-${{ github.ref == format('refs/heads/{0}', github.event.repository.default_branch) && github.run_id || github.ref }}"
  ),
  jobs: ListMap[String, Job] = ListMap.empty
) {
  def withOn(on: Triggers): Workflow =
    copy(on = Some(on))

  def withJobs(jobs: (String, Job)*): Workflow =
    copy(jobs = ListMap(jobs: _*))

  def addJob(job: (String, Job)): Workflow =
    copy(jobs = jobs + job)

  def addJobs(newJobs: (String, Job)*): Workflow =
    copy(jobs = jobs ++ newJobs)
}

object Workflow {

  implicit def listMapCodec[K: JsonFieldDecoder: JsonFieldEncoder, V: JsonCodec]: JsonCodec[ListMap[K, V]] =
    JsonCodec(
      JsonEncoder.keyValueIterable[K, V, ListMap],
      JsonDecoder.keyValueChunk[K, V].map(c => ListMap(c: _*))
    )
  implicit lazy val codec: JsonCodec[Workflow] = DeriveJsonCodec.gen[Workflow]
}
