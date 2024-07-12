// Build Server Plugins
addSbtPlugin("ch.epfl.scala" % "sbt-bloop" % "1.6.0")

// Linting Plugins
addSbtPlugin("org.scalameta"    % "sbt-scalafmt"              % "2.5.2")
addSbtPlugin("ch.epfl.scala"    % "sbt-scalafix"              % "0.12.1")
addSbtPlugin("com.github.cb372" % "sbt-explicit-dependencies" % "0.3.1")

// Versioning and Release Plugins
addSbtPlugin("com.eed3si9n"   % "sbt-buildinfo"  % "0.12.0")
addSbtPlugin("com.github.sbt" % "sbt-ci-release" % "1.5.12")

// Docs Plugins
addSbtPlugin("org.scalameta"     % "sbt-mdoc"   % "2.5.4")
addSbtPlugin("com.github.sbt"    % "sbt-unidoc" % "0.5.0")
addSbtPlugin("de.heikoseeberger" % "sbt-header" % "5.10.0")

// Cross-Compiler Plugins
addSbtPlugin("org.scala-js"       % "sbt-scalajs"                   % "1.16.0")
addSbtPlugin("org.portable-scala" % "sbt-scalajs-crossproject"      % "1.3.2")
addSbtPlugin("org.scala-native"   % "sbt-scala-native"              % "0.5.4")
addSbtPlugin("org.portable-scala" % "sbt-scala-native-crossproject" % "1.3.2")
addSbtPlugin("org.portable-scala" % "sbt-platform-deps"             % "1.0.2")

// Benchmarking Plugins
addSbtPlugin("pl.project13.scala" % "sbt-jmh" % "0.4.7")

libraryDependencies += "org.snakeyaml" % "snakeyaml-engine" % "2.7"
libraryDependencies += "dev.zio"      %% "zio"              % "2.1.6"
libraryDependencies += "io.circe"     %% "circe-yaml"       % "0.15.2"
