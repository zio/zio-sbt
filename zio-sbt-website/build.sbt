addSbtPlugin("org.scalameta"                     % "sbt-mdoc"         % "2.9.1")
addSbtPlugin("com.github.sbt"                    % "sbt-unidoc"       % "0.6.1")
addSbtPlugin("com.thoughtworks.sbt-api-mappings" % "sbt-api-mappings" % "3.0.3")

// No Scala 3 / sbt 2.x build of sbt-api-mappings exists yet, and it is not referenced anywhere in
// this module's own source - only declared for downstream sbt-1.x consumers' convenience - so it is
// dropped on the Scala-3 axis rather than blocking zio-sbt-website's sbt-2.x cross-build on it.
// Filtered out after the fact (rather than made conditional at the `addSbtPlugin` call site above)
// so the proven-correct cross-version resolution `addSbtPlugin` performs internally is untouched.
libraryDependencies := {
  if (scalaBinaryVersion.value == "3")
    libraryDependencies.value.filterNot(_.name == "sbt-api-mappings")
  else
    libraryDependencies.value
}

libraryDependencies += "dev.zio" %% "zio"           % "2.1.26"
libraryDependencies += "dev.zio" %% "zio-json"      % "0.9.2"
libraryDependencies += "dev.zio" %% "zio-json-yaml" % "0.9.2"
