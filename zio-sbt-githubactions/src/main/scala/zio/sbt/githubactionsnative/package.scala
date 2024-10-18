package zio.sbt

package object githubactionsnative {
  type Job = (String, JobValue)

  object Job {
    def apply(
      id: String,
      name: String,
      runsOn: String = "ubuntu-latest",
      timeoutMinutes: Option[Int] = None,
      continueOnError: Boolean = false,
      strategy: Option[Strategy] = None,
      needs: Option[Seq[String]] = None,
      services: Option[Seq[Service]] = None,
      `if`: Option[Condition] = None,
      steps: Seq[Step.SingleStep] = Seq.empty
    ): Job = id -> JobValue(
      name,
      runsOn,
      timeoutMinutes,
      continueOnError,
      strategy,
      needs,
      services,
      `if`,
      steps
    )
  }

  implicit class JobOps(job: Job) {

    def withRunsOn(runsOn: String): Job =
      job._1 -> job._2.copy(runsOn = runsOn)

    def withName(name: String): Job =
      job._1 -> job._2.copy(name = name)

    def withTimeoutMinutes(timeoutMinutes: Option[Int]): Job =
      job._1 -> job._2.copy(timeoutMinutes = timeoutMinutes)

    def withContinueOnError(continueOnError: Boolean): Job =
      job._1 -> job._2.copy(continueOnError = continueOnError)

    def withStrategy(strategy: Option[Strategy]): Job =
      job._1 -> job._2.copy(strategy = strategy)

    def withNeeds(needs: Option[Seq[String]]): Job =
      job._1 -> job._2.copy(needs = needs)

    def withStrategy(strategy: Strategy): Job =
      job._1 -> job._2.withStrategy(strategy)

    def withServices(services: Service*): Job =
      job._1 -> job._2.withServices(services: _*)

    def withSteps(steps: Seq[Step.SingleStep]): Job =
      job._1 -> job._2.withSteps(steps: _*)
  }
}
