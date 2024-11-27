// Linting Plugins
addSbtPlugin("org.scalameta"    % "sbt-scalafmt"              % "2.5.2")
addSbtPlugin("ch.epfl.scala"    % "sbt-scalafix"              % "0.13.0")
addSbtPlugin("com.github.cb372" % "sbt-explicit-dependencies" % "0.3.1")

// Versioning and Release Plugins
addSbtPlugin("com.eed3si9n"   % "sbt-buildinfo"  % "0.13.1")
addSbtPlugin("com.github.sbt" % "sbt-ci-release" % "1.9.0")

// Docs Plugins
addSbtPlugin("org.scalameta"     % "sbt-mdoc"   % "2.6.1")
addSbtPlugin("com.github.sbt"    % "sbt-unidoc" % "0.5.0")
addSbtPlugin("de.heikoseeberger" % "sbt-header" % "5.10.0")

// Binary Compatibility Plugin
addSbtPlugin("com.typesafe" % "sbt-mima-plugin" % "1.1.4")

libraryDependencies += "dev.zio" %% "zio" % "2.1.13"
