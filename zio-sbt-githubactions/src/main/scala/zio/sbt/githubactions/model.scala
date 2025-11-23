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

case class ServicePort(inner: Int, outer: Int)
object ServicePort {
  implicit val encoder: JsonEncoder[ServicePort] =
    JsonEncoder.string.contramap(sp => s"${sp.inner}:${sp.outer}")
}

case class Service(
  name: String,
  image: ImageRef,
  env: Map[String, String] = Map.empty,
  ports: Chunk[ServicePort] = Chunk.empty
)
object Service {
  implicit val encoder: JsonEncoder[Service] =
    JsonEncoder[Json].contramap { s =>
      Json.Obj(
        ("image", s.image.toJsonAST.getOrElse(Json.Null)),
        ("env", s.env.toJsonAST.getOrElse(Json.Null)),
        ("ports", s.ports.toJsonAST.getOrElse(Json.Null))
      )
    }
}

case class Job private (
  id: String,
  name: String,
  runsOn: String,
  timeoutMinutes: Int,
  continueOnError: Boolean,
  strategy: Option[Strategy],
  steps: Chunk[Step],
  need: Chunk[String],
  services: Chunk[Service],
  condition: Option[Condition]
) {
  def withStrategy(strategy: Strategy): Job =
    copy(strategy = Some(strategy))

  def withSteps(steps: Step*): Job =
    copy(steps = Chunk.fromIterable(steps))

  def withServices(services: Service*): Job =
    copy(services = Chunk.fromIterable(services))
}

object Job {
  def apply(
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
    condition = condition
  )

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
  jobs: Chunk[Job]
) {
  def on(triggers: Trigger*): Workflow =
    copy(triggers = Chunk.fromIterable(triggers))

  def withJobs(jobs: Job*): Workflow =
    copy(jobs = Chunk.fromIterable(jobs))

  def addJob(job: Job): Workflow =
    copy(jobs = jobs :+ job)

  def addJobs(newJobs: Chunk[Job]): Workflow =
    copy(jobs = jobs ++ newJobs)
}

object Workflow {
  def apply(
    name: String,
    env: Map[String, String] = Map.empty,
    triggers: Seq[Trigger] = Seq.empty,
    jobs: Seq[Job] = Seq.empty
  ): Workflow = Workflow(
    name = name,
    env = env,
    triggers = Chunk.fromIterable(triggers),
    jobs = Chunk.fromIterable(jobs)
  )

  implicit val encoder: JsonEncoder[Workflow] =
    JsonEncoder[Json].contramap { wf =>
      val onJson = if (wf.triggers.isEmpty) {
        Json.Null
      } else {
        Json.Obj(wf.triggers.map(_.toKeyValuePair))
      }

      val concurrencyJson = Json.Obj(
        (
          "group",
          Json.Str(
            "${{ github.workflow }}-${{ github.ref == format('refs/heads/{0}', github.event.repository.default_branch) && github.run_id || github.ref }}"
          )
        ),
        ("cancel-in-progress", Json.Bool(true))
      )

      val jobsJson = Json.Obj(wf.jobs.map(job => (job.id, job.toJsonAST.getOrElse(Json.Null))): _*)

      Json.Obj(
        ("name", Json.Str(wf.name)),
        ("env", wf.env.toJsonAST.getOrElse(Json.Null)),
        ("on", onJson),
        ("concurrency", concurrencyJson),
        ("jobs", jobsJson)
      )
    }
}
