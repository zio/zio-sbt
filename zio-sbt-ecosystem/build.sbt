// Linting Plugins
addSbtPlugin("org.scalameta"    % "sbt-scalafmt"              % "2.6.2")
addSbtPlugin("ch.epfl.scala"    % "sbt-scalafix"              % "0.14.7")
addSbtPlugin("com.github.cb372" % "sbt-explicit-dependencies" % "0.3.1")

// Versioning and Release Plugins
addSbtPlugin("com.eed3si9n"   % "sbt-buildinfo"  % "0.13.1")
addSbtPlugin("com.github.sbt" % "sbt-ci-release" % "1.12.1")

// Docs Plugins
addSbtPlugin("org.scalameta"  % "sbt-mdoc"   % "2.9.1")
addSbtPlugin("com.github.sbt" % "sbt-unidoc" % "0.6.1")
addSbtPlugin("com.github.sbt" % "sbt-header" % "5.11.0")

// Cross-Compiler Plugins
addSbtPlugin("org.scala-js"       % "sbt-scalajs"                   % "1.22.0")
addSbtPlugin("org.portable-scala" % "sbt-scalajs-crossproject"      % "1.4.0")
addSbtPlugin("org.scala-native"   % "sbt-scala-native"              % "0.5.12")
addSbtPlugin("org.portable-scala" % "sbt-scala-native-crossproject" % "1.4.0")
addSbtPlugin("org.portable-scala" % "sbt-platform-deps"             % "1.0.2")

// Benchmarking Plugins
addSbtPlugin("pl.project13.scala" % "sbt-jmh"      % "0.4.8")
addSbtPlugin("pl.project13.scala" % "sbt-jcstress" % "0.2.0")

// Binary Compatibility Plugin
addSbtPlugin("com.typesafe" % "sbt-mima-plugin" % "1.1.6")

// None of sbt-explicit-dependencies, sbt-platform-deps, or sbt-jcstress has an sbt-2.x / Scala-3
// release yet. sbt-jcstress is unused in this module's own source (dropped outright); the other
// two ARE used (see zio-sbt/PlatformCompat.scala's scala-2.12/scala-3 split for how the code
// copes), so they are simply omitted here on the Scala-3 axis rather than blocking the whole
// module's sbt-2.x cross-build on them.
libraryDependencies := {
  if (scalaBinaryVersion.value == "3")
    libraryDependencies.value.filterNot(m =>
      Set("sbt-explicit-dependencies", "sbt-platform-deps", "sbt-jcstress").contains(m.name)
    )
  else
    libraryDependencies.value
}

libraryDependencies += "org.snakeyaml" % "snakeyaml-engine" % "3.1.1"
libraryDependencies += "dev.zio"      %% "zio"              % "2.1.26"
libraryDependencies += "dev.zio"      %% "zio-json"         % "0.9.2"
libraryDependencies += "dev.zio"      %% "zio-json-yaml"    % "0.9.2"
