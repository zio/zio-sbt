// Build Server Plugins
addSbtPlugin("ch.epfl.scala" % "sbt-bloop" % "2.0.5")

// Linting Plugins
addSbtPlugin("org.scalameta"    % "sbt-scalafmt"              % "2.5.2")
addSbtPlugin("ch.epfl.scala"    % "sbt-scalafix"              % "0.13.0")
addSbtPlugin("com.github.cb372" % "sbt-explicit-dependencies" % "0.3.1")
addSbtPlugin("com.typesafe"     % "sbt-mima-plugin"           % "1.1.4")

// Versioning and Release Plugins
addSbtPlugin("com.eed3si9n"   % "sbt-buildinfo"  % "0.13.1")
addSbtPlugin("com.github.sbt" % "sbt-ci-release" % "1.9.0")

// Docs Plugins
addSbtPlugin("org.scalameta"     % "sbt-mdoc"   % "2.6.1")
addSbtPlugin("com.github.sbt"    % "sbt-unidoc" % "0.5.0")
addSbtPlugin("de.heikoseeberger" % "sbt-header" % "5.10.0")

// Cross-Compiler Plugins
addSbtPlugin("org.scala-js"       % "sbt-scalajs"                   % "1.17.0")
addSbtPlugin("org.portable-scala" % "sbt-scalajs-crossproject"      % "1.3.2")
addSbtPlugin("org.scala-native"   % "sbt-scala-native"              % "0.5.5")
addSbtPlugin("org.portable-scala" % "sbt-scala-native-crossproject" % "1.3.2")
addSbtPlugin("org.portable-scala" % "sbt-platform-deps"             % "1.0.2")
addSbtPlugin("com.eed3si9n"       % "sbt-projectmatrix"             % "0.10.0")

libraryDependencies += "org.snakeyaml" % "snakeyaml-engine" % "2.8"
libraryDependencies += "dev.zio"      %% "zio"              % "2.1.13"
libraryDependencies += "dev.zio"      %% "zio-json"         % "0.7.3"
libraryDependencies += "dev.zio"      %% "zio-json-yaml"    % "0.7.3"
