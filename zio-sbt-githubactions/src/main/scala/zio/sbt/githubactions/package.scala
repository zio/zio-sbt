package zio.sbt

import zio.json.ExplicitEmptyCollections
import zio.json.JsonCodecConfiguration

package object githubactions {
  implicit val jsonConfig: JsonCodecConfiguration =
    JsonCodecConfiguration.default.copy(explicitEmptyCollections = ExplicitEmptyCollections(false, false))
}
