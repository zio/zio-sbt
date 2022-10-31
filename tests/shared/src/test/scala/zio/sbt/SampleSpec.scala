package zio.sbt

import zio.test._

object SampleSpec extends ZIOSpecDefault {

  def spec: Spec[Environment with TestEnvironment, Any] =
    test("assert true") {
      assertTrue(true)
    }
}
