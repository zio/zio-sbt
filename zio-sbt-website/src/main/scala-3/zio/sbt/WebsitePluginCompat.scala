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

import sbt.Keys._
import sbt._

/**
 * sbt-mdoc's `mdoc` task is `Compile / runMain`. In sbt 2.x, `runMain` defaults
 * to `clientSide := true`: when a command comes from the thin client (which the
 * sbt 2.x launcher uses by default) it does not run the main class, it hands
 * the run over to the client and returns at once. `compileDocs` then completes
 * before mdoc has written anything, and every task that reads `website/docs`
 * after it (`generateReadme`, `buildWebsite`, `publishToNpm`, ...) finds
 * nothing there. Running it in the server makes `compileDocs` return only after
 * mdoc has finished.
 */
private[sbt] object WebsitePluginCompat {
  def runMdocInServerSettings: Seq[Setting[_]] = Seq(
    Compile / runMain / clientSide := false
  )
}
