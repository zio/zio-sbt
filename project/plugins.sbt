// Linting Plugins
addSbtPlugin("org.scalameta"    % "sbt-scalafmt"              % "2.5.6")
addSbtPlugin("ch.epfl.scala"    % "sbt-scalafix"              % "0.14.4")
addSbtPlugin("com.github.cb372" % "sbt-explicit-dependencies" % "0.3.1")

// Versioning and Release Plugins
addSbtPlugin("com.eed3si9n"   % "sbt-buildinfo"  % "0.13.1")
addSbtPlugin("com.github.sbt" % "sbt-ci-release" % "1.11.2")

// Docs Plugins
addSbtPlugin("org.scalameta"  % "sbt-mdoc"   % "2.8.0")
addSbtPlugin("com.github.sbt" % "sbt-unidoc" % "0.6.0")
addSbtPlugin("com.github.sbt" % "sbt-header" % "5.11.0")

// Cross-Compiler Plugins
addSbtPlugin("org.scala-js"       % "sbt-scalajs"                   % "1.20.1")
addSbtPlugin("org.portable-scala" % "sbt-scalajs-crossproject"      % "1.3.2")
addSbtPlugin("org.scala-native"   % "sbt-scala-native"              % "0.5.9")
addSbtPlugin("org.portable-scala" % "sbt-scala-native-crossproject" % "1.3.2")
addSbtPlugin("org.portable-scala" % "sbt-platform-deps"             % "1.0.2")

// Benchmarking Plugins
addSbtPlugin("pl.project13.scala" % "sbt-jmh" % "0.4.8")

libraryDependencies += "org.snakeyaml" % "snakeyaml-engine" % "3.0.1"
libraryDependencies += "dev.zio"      %% "zio"              % "2.1.22"
libraryDependencies += "dev.zio"      %% "zio-json"         % "0.7.3"
libraryDependencies += "dev.zio"      %% "zio-json-yaml"    % "0.7.3"
