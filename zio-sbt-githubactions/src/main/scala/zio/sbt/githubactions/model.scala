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
    JsonEncoder[Json].contramap {
      case All         => Json.Str("*")
      case Named(name) => Json.Str(name)
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
    override def toKeyValuePair: (String, Json) = {
      val inputsMap = inputs.map { i =>
        (
          i.key,
          Json.Obj(
            Chunk(
              ("description", Json.Str(i.description)),
              ("required", Json.Bool(i.required)),
              ("default", Json.Str(i.defaultValue))
            )
          )
        )
      }.toMap
      ("workflow_dispatch", inputsMap.toJsonAST.getOrElse(Json.Null))
    }
  }

  case class Release(
    releaseTypes: Seq[String] = Seq.empty
  ) extends Trigger {
    override def toKeyValuePair: (String, Json) =
      ("release", Json.Obj(Chunk("types" -> releaseTypes.toJsonAST.getOrElse(Json.Null))))
  }

  case class PullRequest(
    branches: Seq[Branch] = Seq.empty,
    ignoredBranches: Seq[Branch] = Seq.empty
  ) extends Trigger {
    override def toKeyValuePair: (String, Json) = {
      val fields = Seq(
        ("branches", branches.toJsonAST.getOrElse(Json.Null)),
        ("branches-ignore", ignoredBranches.toJsonAST.getOrElse(Json.Null))
      ).filter { case (_, data) =>
        data match {
          case Json.Arr(elements) => elements.nonEmpty
          case _                  => false
        }
      }
      ("pull_request", Json.Obj(Chunk.fromIterable(fields)))
    }
  }

  case class Push(
    branches: Seq[Branch] = Seq.empty,
    ignoredBranches: Seq[Branch] = Seq.empty
  ) extends Trigger {
    override def toKeyValuePair: (String, Json) = {
      val fields = Seq(
        ("branches", branches.toJsonAST.getOrElse(Json.Null)),
        ("branches-ignore", ignoredBranches.toJsonAST.getOrElse(Json.Null))
      ).filter { case (_, data) =>
        data match {
          case Json.Arr(elements) => elements.nonEmpty
          case _                  => false
        }
      }
      ("push", Json.Obj(Chunk.fromIterable(fields)))
    }
  }

  case class Create(
    branches: Seq[Branch] = Seq.empty,
    ignoredBranches: Seq[Branch] = Seq.empty
  ) extends Trigger {
    override def toKeyValuePair: (String, Json) = {
      val fields = Seq(
        ("branches", branches.toJsonAST.getOrElse(Json.Null)),
        ("branches-ignore", ignoredBranches.toJsonAST.getOrElse(Json.Null))
      ).filter { case (_, data) =>
        data match {
          case Json.Arr(elements) => elements.nonEmpty
          case _                  => false
        }
      }
      ("create", Json.Obj(Chunk.fromIterable(fields)))
    }
  }
}

case class Strategy(matrix: Map[String, List[String]], maxParallel: Option[Int] = None, failFast: Boolean = true)

object Strategy {
  implicit val encoder: JsonEncoder[Strategy] =
    JsonEncoder[Json].contramap { s =>
      Json.Obj(
        Chunk(
          ("fail-fast", Json.Bool(s.failFast)),
          ("max-parallel", s.maxParallel.toJsonAST.getOrElse(Json.Null)),
          ("matrix", s.matrix.toJsonAST.getOrElse(Json.Null))
        )
      )
    }
}

case class ActionRef(ref: String)
object ActionRef {
  implicit val encoder: JsonEncoder[ActionRef] =
    JsonEncoder[Json].contramap { action =>
      Json.Str(action.ref)
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

  implicit val encoder: JsonEncoder[Condition] =
    JsonEncoder[Json].contramap { c =>
      Json.Str(c.asString)
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

  implicit val encoder: JsonEncoder[SingleStep] =
    JsonEncoder[Json].contramap { s =>
      Json.Obj(
        Chunk(
          ("name", Json.Str(s.name)),
          ("id", s.id.toJsonAST.getOrElse(Json.Null)),
          ("uses", s.uses.toJsonAST.getOrElse(Json.Null)),
          ("if", s.condition.toJsonAST.getOrElse(Json.Null)),
          ("with", if (s.parameters.nonEmpty) s.parameters.toJsonAST.getOrElse(Json.Null) else Json.Null),
          ("run", s.run.toJsonAST.getOrElse(Json.Null)),
          ("env", if (s.env.nonEmpty) s.env.toJsonAST.getOrElse(Json.Null) else Json.Null)
        )
      )
    }
}

case class ImageRef(ref: String)
object ImageRef {
  implicit val encoder: JsonEncoder[ImageRef] =
    JsonEncoder[Json].contramap { image =>
      Json.Str(image.ref)
    }
}

case class ServicePort(inner: Int, outer: Int)
object ServicePort {
  implicit val encoder: JsonEncoder[ServicePort] =
    JsonEncoder[Json].contramap { sp =>
      Json.Str(s"${sp.inner}:${sp.outer}")
    }
}

case class Service(
  name: String,
  image: ImageRef,
  env: Map[String, String] = Map.empty,
  ports: Seq[ServicePort] = Seq.empty
)
object Service {
  implicit val encoder: JsonEncoder[Service] =
    JsonEncoder[Json].contramap { s =>
      Json.Obj(
        Chunk(
          ("image", s.image.toJsonAST.getOrElse(Json.Null)),
          ("env", s.env.toJsonAST.getOrElse(Json.Null)),
          ("ports", s.ports.toJsonAST.getOrElse(Json.Null))
        )
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
  implicit val encoder: JsonEncoder[Job] =
    JsonEncoder[Json].contramap { job =>
      val servicesJson = if (job.services.nonEmpty) {
        Json.Obj(Chunk.fromIterable(job.services.map(svc => (svc.name, svc.toJsonAST.getOrElse(Json.Null)))))
      } else {
        Json.Null
      }

      Json.Obj(
        Chunk(
          ("name", Json.Str(job.name)),
          ("runs-on", Json.Str(job.runsOn)),
          ("continue-on-error", Json.Bool(job.continueOnError)),
          ("strategy", job.strategy.toJsonAST.getOrElse(Json.Null)),
          ("needs", if (job.need.nonEmpty) job.need.toJsonAST.getOrElse(Json.Null) else Json.Null),
          ("services", servicesJson),
          ("if", job.condition.toJsonAST.getOrElse(Json.Null)),
          ("steps", StepSequence(job.steps).flatten.toJsonAST.getOrElse(Json.Null))
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
  implicit val encoder: JsonEncoder[Workflow] =
    JsonEncoder[Json].contramap { wf =>
      val onJson = if (wf.triggers.isEmpty) {
        Json.Null
      } else {
        Json.Obj(Chunk.fromIterable(wf.triggers.map(_.toKeyValuePair)))
      }

      val concurrencyJson = Json.Obj(
        Chunk(
          (
            "group",
            Json.Str(
              "${{ github.workflow }}-${{ github.ref == format('refs/heads/{0}', github.event.repository.default_branch) && github.run_id || github.ref }}"
            )
          ),
          ("cancel-in-progress", Json.Bool(true))
        )
      )

      val jobsJson = Json.Obj(Chunk.fromIterable(wf.jobs.map(job => (job.id, job.toJsonAST.getOrElse(Json.Null)))))

      Json.Obj(
        Chunk(
          ("name", Json.Str(wf.name)),
          ("env", wf.env.toJsonAST.getOrElse(Json.Null)),
          ("on", onJson),
          ("concurrency", concurrencyJson),
          ("jobs", jobsJson)
        )
      )
    }
}
