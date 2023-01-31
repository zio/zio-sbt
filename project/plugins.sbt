// Build Server Plugins
addSbtPlugin("ch.epfl.scala" % "sbt-bloop" % "1.5.6")

// Linting Plugins
addSbtPlugin("org.scalameta"    % "sbt-scalafmt"              % "2.5.0")
addSbtPlugin("ch.epfl.scala"    % "sbt-scalafix"              % "0.10.4")
addSbtPlugin("com.github.cb372" % "sbt-explicit-dependencies" % "0.2.16")

// Versioning and Release Plugins
addSbtPlugin("com.eed3si9n"   % "sbt-buildinfo"  % "0.11.0")
addSbtPlugin("com.jsuereth"   % "sbt-pgp"        % "2.1.1")
addSbtPlugin("com.dwijnand"   % "sbt-dynver"     % "4.1.1")
addSbtPlugin("org.xerial.sbt" % "sbt-sonatype"   % "3.9.15")
addSbtPlugin("com.geirsson"   % "sbt-ci-release" % "1.5.7")

// Docs Plugins
addSbtPlugin("org.scalameta"     % "sbt-mdoc"   % "2.3.7")
addSbtPlugin("com.github.sbt"    % "sbt-unidoc" % "0.5.0")
addSbtPlugin("de.heikoseeberger" % "sbt-header" % "5.9.0")

// Cross-Compiler Plugins
addSbtPlugin("org.scala-js"       % "sbt-scalajs"                   % "1.13.0")
addSbtPlugin("org.portable-scala" % "sbt-scalajs-crossproject"      % "1.2.0")
addSbtPlugin("org.scala-native"   % "sbt-scala-native"              % "0.4.10")
addSbtPlugin("org.portable-scala" % "sbt-scala-native-crossproject" % "1.2.0")

// Benchmarking Plugins
addSbtPlugin("pl.project13.scala" % "sbt-jmh" % "0.4.3")

libraryDependencies += "org.snakeyaml" % "snakeyaml-engine" % "2.5"
libraryDependencies += "dev.zio"      %% "zio"              % "2.0.6"
libraryDependencies += "io.circe"     %% "circe-yaml"       % "0.14.2"
