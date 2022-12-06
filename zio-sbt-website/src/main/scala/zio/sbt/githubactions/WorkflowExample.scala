/*
 * Copyright 2022 dev.zio
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

object WorkflowExample extends scala.App {

  import io.circe.syntax.*

  val result: String = io.circe.yaml
    .Printer(dropNullKeys = true)
    .pretty(
      Workflow(
        name = "Website",
        triggers = Seq(Trigger.Release(Seq("published"))),
        jobs = Seq(
          Job(
            id = "publish-docs",
            name = "Publish Docs to The NPM Registry",
            steps = Seq(
              Step.StepSequence(
                Seq(
                  Step.SingleStep(
                    name = "Git Checkout",
                    uses = Some(ActionRef("actions/checkout@v3.1.0")),
                    parameters = Map("fetch-depth" -> "0".asJson)
                  ),
                  Step.SingleStep(
                    name = "Setup Scala",
                    uses = Some(ActionRef("actions/setup-java@v3.6.0")),
                    parameters = Map(
                      "node-version" -> "16.x".asJson,
                      "registry-url" -> "https://registry.npmjs.org".asJson
                    )
                  ),
                  Step.SingleStep(
                    name = "Publish Docs to NPM Registry",
                    run = Some("sbt docs/publishToNpm"),
                    env = Map(
                      "NODE_AUTH_TOKEN" -> "$${{ secrets.NPM_TOKEN }}"
                    )
                  )
                )
              )
            )
          )
        )
      ).asJson
    )

}
