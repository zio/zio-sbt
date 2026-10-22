object Testing {

  def eq[A](name: String, expected: A, actual: A): Unit =
    if (expected != actual) sys.error(s"$name: expected $expected, but got $actual")

}
