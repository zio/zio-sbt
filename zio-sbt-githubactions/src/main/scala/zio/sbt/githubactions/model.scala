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

import io.circe._
import io.circe.syntax._

import zio.sbt.githubactions.Step.StepSequence

sealed trait OS {
  val asString: String
}
object OS {
  case object UbuntuLatest extends OS { val asString = "ubuntu-latest" }
}

sealed trait Branch
object Branch {
  case object All                extends Branch
  case class Named(name: String) extends Branch

  implicit val encoder: Encoder[Branch] = {
    case All         => Json.fromString("*")
    case Named(name) => Json.fromString(name)
  }
}

sealed trait Trigger {
  def toKeyValuePair: (String, Json)
}

case class Input(key: String, description: String, required: Boolean, defaultValue: String)

object Trigger {
  case class WorkflowDispatch(
    inputs: Seq[Input] = Seq.empty
  ) extends Trigger {
    override def toKeyValuePair: (String, Json) =
      "workflow_dispatch" := inputs.map { i =>
        i.key ->
          Json.obj(
            ("description", i.description.asJson),
            ("required", i.required.asJson),
            ("default", i.defaultValue.asJson)
          )
      }.toMap.asJson
  }

  case class Release(
    releaseTypes: Seq[String] = Seq.empty
  ) extends Trigger {
    override def toKeyValuePair: (String, Json) =
      "release" := Json.obj("types" := releaseTypes)
  }

  case class PullRequest(
    branches: Seq[Branch] = Seq.empty,
    ignoredBranches: Seq[Branch] = Seq.empty
  ) extends Trigger {
    override def toKeyValuePair: (String, Json) =
      "pull_request" := Json.obj(
        Seq(
          "branches"        := branches,
          "branches-ignore" := ignoredBranches
        ).filter { case (_, data) => data.asArray.exists(_.nonEmpty) }: _*
      )
  }

  case class Push(
    branches: Seq[Branch] = Seq.empty,
    ignoredBranches: Seq[Branch] = Seq.empty
  ) extends Trigger {
    override def toKeyValuePair: (String, Json) =
      "push" := Json.obj(
        Seq(
          "branches"        := branches,
          "branches-ignore" := ignoredBranches
        ).filter { case (_, data) => data.asArray.exists(_.nonEmpty) }: _*
      )
  }

  case class Create(
    branches: Seq[Branch] = Seq.empty,
    ignoredBranches: Seq[Branch] = Seq.empty
  ) extends Trigger {
    override def toKeyValuePair: (String, Json) =
      "create" := Json.obj(
        Seq(
          "branches"        := branches,
          "branches-ignore" := ignoredBranches
        ).filter { case (_, data) => data.asArray.exists(_.nonEmpty) }: _*
      )
  }
}

case class Strategy(matrix: Map[String, List[String]], maxParallel: Option[Int] = None, failFast: Boolean = true)

object Strategy {
  implicit val encoder: Encoder[Strategy] =
    (s: Strategy) =>
      Json.obj(
        "fail-fast"    := s.failFast,
        "max-parallel" := s.maxParallel,
        "matrix"       := s.matrix
      )
}

case class ActionRef(ref: String)
object ActionRef {
  implicit val encoder: Encoder[ActionRef] =
    (action: ActionRef) => Json.fromString(action.ref)
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

  implicit val encoder: Encoder[Condition] =
    (c: Condition) => Json.fromString(c.asString)
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

  implicit val encoder: Encoder[SingleStep] =
    (s: SingleStep) =>
      Json
        .obj(
          "name" := s.name,
          "id"   := s.id,
          "uses" := s.uses,
          "if"   := s.condition,
          "with" := (if (s.parameters.nonEmpty) s.parameters.asJson
                     else Json.Null),
          "run" := s.run,
          "env" := (if (s.env.nonEmpty) s.env.asJson else Json.Null)
        )
}

case class ImageRef(ref: String)
object ImageRef {
  implicit val encoder: Encoder[ImageRef] =
    (image: ImageRef) => Json.fromString(image.ref)
}

case class ServicePort(inner: Int, outer: Int)
object ServicePort {
  implicit val encoder: Encoder[ServicePort] =
    (sp: ServicePort) => Json.fromString(s"${sp.inner}:${sp.outer}")
}

case class Service(
  name: String,
  image: ImageRef,
  env: Map[String, String] = Map.empty,
  ports: Seq[ServicePort] = Seq.empty
)
object Service {
  implicit val encoder: Encoder[Service] =
    (s: Service) =>
      Json.obj(
        "image" := s.image,
        "env"   := s.env,
        "ports" := s.ports
      )
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
  implicit val encoder: Encoder[Job] =
    (job: Job) =>
      Json
        .obj(
          "name"              := job.name,
          "runs-on"           := job.runsOn,
          "continue-on-error" := job.continueOnError,
          "strategy"          := job.strategy,
          "needs"             := (if (job.need.nonEmpty) job.need.asJson
                      else Json.Null),
          "services" := (if (job.services.nonEmpty) {
                           Json.obj(
                             job.services.map(svc => svc.name := svc): _*
                           )
                         } else {
                           Json.Null
                         }),
          "if"    := job.condition,
          "steps" := StepSequence(job.steps).flatten
        )
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
  implicit val encoder: Encoder[Workflow] =
    (wf: Workflow) =>
      Json
        .obj(
          "name" := wf.name,
          "env"  := wf.env,
          "on"   := (if (wf.triggers.isEmpty)
                     Json.Null
                   else {
                     Json.obj(
                       wf.triggers
                         .map(_.toKeyValuePair): _*
                     )
                   }),
          "concurrency" := Json.obj(
            "group" := Json.fromString(
              "${{ github.workflow }}-${{ github.ref == format('refs/heads/{0}', github.event.repository.default_branch) && github.run_id || github.ref }}"
            ),
            "cancel-in-progress" := true
          ),
          "jobs" := Json.obj(wf.jobs.map(job => job.id := job): _*)
        )
}
