package zio.sbt

import zio.test._

object SampleSpec extends ZIOSpecDefault {

  def spec =
    test("UnitType default value") {
      assertTrue(true)
    }
}
