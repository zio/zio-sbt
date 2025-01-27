package zio.sbt

import zio.json.{ExplicitEmptyCollections, JsonCodecConfiguration}

package object githubactions {
  implicit val jsonConfig: JsonCodecConfiguration =
    JsonCodecConfiguration.default.copy(explicitEmptyCollections = ExplicitEmptyCollections(false, false))
}
