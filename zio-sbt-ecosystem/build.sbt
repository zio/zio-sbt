// Build Server Plugins
addSbtPlugin("ch.epfl.scala" % "sbt-bloop" % "2.0.10")

// Linting Plugins
addSbtPlugin("org.scalameta"    % "sbt-scalafmt"              % "2.5.4")
addSbtPlugin("ch.epfl.scala"    % "sbt-scalafix"              % "0.14.3")
addSbtPlugin("com.github.cb372" % "sbt-explicit-dependencies" % "0.3.1")

// Versioning and Release Plugins
addSbtPlugin("com.eed3si9n"   % "sbt-buildinfo"  % "0.13.1")
addSbtPlugin("com.github.sbt" % "sbt-ci-release" % "1.11.0")

// Docs Plugins
addSbtPlugin("org.scalameta"     % "sbt-mdoc"   % "2.7.1")
addSbtPlugin("com.github.sbt"    % "sbt-unidoc" % "0.5.0")
addSbtPlugin("de.heikoseeberger" % "sbt-header" % "5.10.0")

// Cross-Compiler Plugins
addSbtPlugin("org.scala-js"       % "sbt-scalajs"                   % "1.19.0")
addSbtPlugin("org.portable-scala" % "sbt-scalajs-crossproject"      % "1.3.2")
addSbtPlugin("org.scala-native"   % "sbt-scala-native"              % "0.5.7")
addSbtPlugin("org.portable-scala" % "sbt-scala-native-crossproject" % "1.3.2")
addSbtPlugin("org.portable-scala" % "sbt-platform-deps"             % "1.0.2")

// Benchmarking Plugins
addSbtPlugin("pl.project13.scala" % "sbt-jmh"      % "0.4.7")
addSbtPlugin("pl.project13.scala" % "sbt-jcstress" % "0.2.0")

// Binary Compatibility Plugin
addSbtPlugin("com.typesafe" % "sbt-mima-plugin" % "1.1.4")

libraryDependencies += "org.snakeyaml" % "snakeyaml-engine" % "2.9"
libraryDependencies += "dev.zio"      %% "zio"              % "2.1.18"
libraryDependencies += "io.circe"     %% "circe-yaml"       % "0.15.2"
