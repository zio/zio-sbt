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

import zio.sbt.{githubactionsnative => ghnative}

import zio.json.ast.Json
import scala.collection.immutable.ListMap

sealed trait OS {
  val asString: String
}
object OS {
  case object UbuntuLatest extends OS { val asString = "ubuntu-latest" }

  implicit class OSOps(val os: OS) extends AnyVal {
    def toNative: ghnative.OS = os match {
      case UbuntuLatest => ghnative.OS.UbuntuLatest
    }
  }
}

sealed trait Branch
object Branch {
  case object All                extends Branch
  case class Named(name: String) extends Branch

  implicit class BranchOps(val branch: Branch) extends AnyVal {
    def toNative: ghnative.Branch = branch match {
      case All         => ghnative.Branch.All
      case Named(name) => ghnative.Branch.Named(name)
    }
  }
}

sealed trait Trigger

case class Input(key: String, description: String, required: Boolean, defaultValue: String)

object Input {
  implicit class InputOps(val input: Input) extends AnyVal {
    def toNative: (String, ghnative.Trigger.InputValue) =
      input.key -> ghnative.Trigger.InputValue(input.description, input.required, input.defaultValue)
  }
}

object Trigger {
  case class WorkflowDispatch(
    inputs: Seq[Input] = Seq.empty
  ) extends Trigger

  case class Release(
    releaseTypes: Seq[String] = Seq.empty
  ) extends Trigger

  case class PullRequest(
    branches: Seq[Branch] = Seq.empty,
    ignoredBranches: Seq[Branch] = Seq.empty
  ) extends Trigger

  case class Push(
    branches: Seq[Branch] = Seq.empty,
    ignoredBranches: Seq[Branch] = Seq.empty
  ) extends Trigger

  case class Create(
    branches: Seq[Branch] = Seq.empty,
    ignoredBranches: Seq[Branch] = Seq.empty
  ) extends Trigger

  implicit class TriggerOps(val trigger: Trigger) extends AnyVal {
    def toNative: ghnative.Trigger = trigger match {
      case WorkflowDispatch(inputs) =>
        ghnative.Trigger.WorkflowDispatch(Some(ListMap(inputs.map(_.toNative): _*)).filter(_.nonEmpty))
      case Release(releaseTypes) =>
        ghnative.Trigger.Release(releaseTypes.map {
          case "created"     => ghnative.Trigger.ReleaseType.Created
          case "published"   => ghnative.Trigger.ReleaseType.Published
          case "prereleased" => ghnative.Trigger.ReleaseType.Prereleased
        })
      case PullRequest(branches, ignoredBranches) =>
        ghnative.Trigger.PullRequest(
          Some(branches.map(_.toNative)).filter(_.nonEmpty),
          Some(ignoredBranches.map(_.toNative)).filter(_.nonEmpty)
        )
      case Push(branches, ignoredBranches) =>
        ghnative.Trigger.Push(
          Some(branches.map(_.toNative)).filter(_.nonEmpty),
          Some(ignoredBranches.map(_.toNative)).filter(_.nonEmpty)
        )
      case Create(branches, ignoredBranches) =>
        ghnative.Trigger.Create(
          Some(branches.map(_.toNative)).filter(_.nonEmpty),
          Some(ignoredBranches.map(_.toNative)).filter(_.nonEmpty)
        )
    }
  }
}

case class Strategy(matrix: Map[String, List[String]], maxParallel: Option[Int] = None, failFast: Boolean = true)

object Strategy {
  implicit class StrategyOps(val strategy: Strategy) extends AnyVal {
    def toNative: ghnative.Strategy =
      ghnative.Strategy(
        matrix = strategy.matrix.map { case (key, values) => key -> values },
        maxParallel = strategy.maxParallel,
        failFast = strategy.failFast
      )
  }
}

case class ActionRef(ref: String)

object ActionRef {
  implicit class ActionRefOps(val actionRef: ActionRef) extends AnyVal {
    def toNative: ghnative.ActionRef = ghnative.ActionRef(actionRef.ref)
  }
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

  implicit class ConditionOps(val condition: Condition) extends AnyVal {
    def toNative: ghnative.Condition = condition match {
      case Expression(expression) => ghnative.Condition.Expression(expression)
      case Function(expression)   => ghnative.Condition.Function(expression)
    }
  }
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
    condition: Option[Condition] = None,
    parameters: Map[String, Json] = Map.empty,
    run: Option[String] = None,
    env: Map[String, String] = Map.empty
  ) extends Step {
    override def when(condition: Condition): Step =
      copy(condition = Some(condition))

    override def flatten: Seq[Step.SingleStep] = Seq(this)
  }

  case class StepSequence(steps: Seq[Step]) extends Step {
    override def when(condition: Condition): Step =
      copy(steps = steps.map(_.when(condition)))

    override def flatten: Seq[SingleStep] =
      steps.flatMap(_.flatten)
  }

  implicit class StepOps(val step: Step) extends AnyVal {
    def toNative: ghnative.Step = step match {
      case SingleStep(name, id, uses, condition, parameters, run, env) =>
        ghnative.Step.SingleStep(
          name = name,
          id = id,
          uses = uses.map(_.toNative),
          `if` = condition.map(_.toNative),
          `with` = Some(parameters).filter(_.nonEmpty),
          run = run,
          env = Some(env).filter(_.nonEmpty)
        )
      case StepSequence(steps) =>
        ghnative.Step.StepSequence(steps.map(_.toNative))
    }
  }
}

case class ImageRef(ref: String)

object ImageRef {
  implicit class ImageRefOps(val imageRef: ImageRef) extends AnyVal {
    def toNative: ghnative.ImageRef = ghnative.ImageRef(imageRef.ref)
  }
}

case class ServicePort(inner: Int, outer: Int)

object ServicePort {
  implicit class ServicePortOps(val servicePort: ServicePort) extends AnyVal {
    def toNative: ghnative.ServicePort = ghnative.ServicePort(servicePort.inner, servicePort.outer)
  }
}

case class Service(
  name: String,
  image: ImageRef,
  env: Map[String, String] = Map.empty,
  ports: Seq[ServicePort] = Seq.empty
)

object Service {
  implicit class ServiceOps(val service: Service) extends AnyVal {
    def toNative: ghnative.Service = ghnative.Service(
      name = service.name,
      image = service.image.toNative,
      env = Some(service.env).filter(_.nonEmpty),
      ports = Some(service.ports.map(_.toNative)).filter(_.nonEmpty)
    )
  }
}

case class Job(
  id: String,
  name: String,
  runsOn: String = "ubuntu-latest",
  timeoutMinutes: Int = 30,
  continueOnError: Boolean = false,
  strategy: Option[Strategy] = None,
  steps: Seq[Step] = Seq.empty,
  need: Seq[String] = Seq.empty,
  services: Seq[Service] = Seq.empty,
  condition: Option[Condition] = None
) {
  def withStrategy(strategy: Strategy): Job =
    copy(strategy = Some(strategy))

  def withSteps(steps: Step*): Job =
    copy(steps = steps)

  def withServices(services: Service*): Job =
    copy(services = services)
}

object Job {
  implicit class JobOps(val job: Job) extends AnyVal {
    def toNative: ghnative.Job = (
      job.id,
      ghnative.JobValue(
        name = job.name,
        runsOn = job.runsOn,
        timeoutMinutes = Some(job.timeoutMinutes),
        continueOnError = job.continueOnError,
        strategy = job.strategy.map(_.toNative),
        steps = job.steps.map(_.toNative).flatMap(_.flatten),
        needs = Some(job.need),
        services = Some(job.services.map(_.toNative)).filter(_.nonEmpty),
        `if` = job.condition.map(_.toNative)
      )
    )
  }
}

case class Workflow(
  name: String,
  env: Map[String, String] = Map.empty,
  triggers: Seq[Trigger] = Seq.empty,
  jobs: Seq[Job] = Seq.empty
) {
  def on(triggers: Trigger*): Workflow =
    copy(triggers = triggers)

  def withJobs(jobs: Job*): Workflow =
    copy(jobs = jobs)

  def addJob(job: Job): Workflow =
    copy(jobs = jobs :+ job)

  def addJobs(newJobs: Seq[Job]): Workflow =
    copy(jobs = jobs ++ newJobs)
}

object Workflow {
  implicit class WorkflowOps(val workflow: Workflow) extends AnyVal {
    def toNative: ghnative.Workflow = ghnative.Workflow(
      name = workflow.name,
      env = Some(ListMap.empty ++ workflow.env).filter(_.nonEmpty),
      on = {
        val triggers = workflow.triggers.map(_.toNative)
        Some(
          ghnative.Triggers(
            workflowDispatch = triggers.collectFirst { case t: ghnative.Trigger.WorkflowDispatch => t }
              .getOrElse(ghnative.Trigger.WorkflowDispatch()),
            release = triggers.collectFirst { case t: ghnative.Trigger.Release => t },
            pullRequest = triggers.collectFirst { case t: ghnative.Trigger.PullRequest => t },
            push = triggers.collectFirst { case t: ghnative.Trigger.Push => t },
            create = triggers.collectFirst { case t: ghnative.Trigger.Create => t }
          )
        ).filter(_ => triggers.nonEmpty)
      },
      jobs = ListMap(workflow.jobs.map(_.toNative): _*)
    )
  }
}
