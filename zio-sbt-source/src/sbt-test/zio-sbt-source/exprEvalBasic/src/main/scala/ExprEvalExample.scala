import zio.sbt.ExprEval

object ExprEvalExample {
  def main(args: Array[String]): Unit = {
    // Test basic expression evaluation
    ExprEval.show(1 + 2)

    // Test string concatenation
    ExprEval.show("Hello" + " " + "World")

    // Test multiple expressions
    ExprEval.show(
      42,
      "test",
      3.14
    )

    // Test block form with only expressions
    ExprEval.show {
      1 + 2
      3 * 4
      "result"
    }
  }
}
