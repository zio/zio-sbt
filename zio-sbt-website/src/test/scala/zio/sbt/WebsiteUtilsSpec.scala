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

package zio.sbt

import zio.test._

object WebsiteUtilsSpec extends ZIOSpecDefault {

  def spec: Spec[Any, Nothing] =
    suite("WebsiteUtils.npmPublishTag")(
      test("prerelease with a stable latest is sidelined under its own tag") {
        assertTrue(WebsiteUtils.npmPublishTag("1.0.0-RC49", Some("1.2.3")).contains("rc"))
      },
      test("prerelease with a prerelease latest is published as latest") {
        assertTrue(WebsiteUtils.npmPublishTag("1.0.0-RC49", Some("1.0.0-RC44")).contains("latest"))
      },
      test("prerelease with no latest yet is published as latest") {
        assertTrue(WebsiteUtils.npmPublishTag("1.0.0-RC1", None).contains("latest"))
      },
      test("snapshot never takes latest") {
        assertTrue(
          WebsiteUtils.npmPublishTag("1.0.0-SNAPSHOT", Some("1.0.0-RC44")).contains("snapshot"),
          WebsiteUtils.npmPublishTag("1.0.0--SNAPSHOT", None).contains("snapshot")
        )
      },
      test("stable version needs no tag") {
        assertTrue(
          WebsiteUtils.npmPublishTag("1.0.0", Some("0.9.0")).isEmpty,
          WebsiteUtils.npmPublishTag("1.0.0", Some("1.0.0-RC44")).isEmpty,
          WebsiteUtils.npmPublishTag("1.0.0", None).isEmpty
        )
      }
    )
}
