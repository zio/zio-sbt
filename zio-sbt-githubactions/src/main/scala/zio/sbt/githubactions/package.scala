package zio.sbt

import zio.json.JsonCodecConfiguration

package object githubactions {
  implicit val jsonConfig: JsonCodecConfiguration =
    JsonCodecConfiguration.default.copy(explicitEmptyCollections = false)
}
