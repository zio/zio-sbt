// Linting Plugins
addSbtPlugin("org.scalameta"    % "sbt-scalafmt"              % "2.5.6")
addSbtPlugin("ch.epfl.scala"    % "sbt-scalafix"              % "0.14.5")
addSbtPlugin("com.github.cb372" % "sbt-explicit-dependencies" % "0.3.1")

// Versioning and Release Plugins
addSbtPlugin("com.eed3si9n"   % "sbt-buildinfo"  % "0.13.1")
addSbtPlugin("com.github.sbt" % "sbt-ci-release" % "1.11.2")

// Docs Plugins
addSbtPlugin("org.scalameta"  % "sbt-mdoc"   % "2.8.2")
addSbtPlugin("com.github.sbt" % "sbt-unidoc" % "0.6.1")
addSbtPlugin("com.github.sbt" % "sbt-header" % "5.11.0")

// Cross-Compiler Plugins
addSbtPlugin("org.scala-js"       % "sbt-scalajs"                   % "1.20.2")
addSbtPlugin("org.portable-scala" % "sbt-scalajs-crossproject"      % "1.3.2")
addSbtPlugin("org.scala-native"   % "sbt-scala-native"              % "0.5.9")
addSbtPlugin("org.portable-scala" % "sbt-scala-native-crossproject" % "1.3.2")
addSbtPlugin("org.portable-scala" % "sbt-platform-deps"             % "1.0.2")

// Benchmarking Plugins
addSbtPlugin("pl.project13.scala" % "sbt-jmh"      % "0.4.8")
addSbtPlugin("pl.project13.scala" % "sbt-jcstress" % "0.2.0")

// Binary Compatibility Plugin
addSbtPlugin("com.typesafe" % "sbt-mima-plugin" % "1.1.4")

libraryDependencies += "org.snakeyaml" % "snakeyaml-engine" % "3.0.1"
libraryDependencies += "dev.zio"      %% "zio"              % "2.1.24"
libraryDependencies += "dev.zio"      %% "zio-json"         % "0.8.0"
libraryDependencies += "dev.zio"      %% "zio-json-yaml"    % "0.8.0"
