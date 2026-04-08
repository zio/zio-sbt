addSbtPlugin("org.scalameta"                     % "sbt-mdoc"         % "2.8.2")
addSbtPlugin("com.github.sbt"                    % "sbt-unidoc"       % "0.6.1")
addSbtPlugin("com.thoughtworks.sbt-api-mappings" % "sbt-api-mappings" % "3.0.2")

libraryDependencies += "dev.zio" %% "zio"           % "2.1.25"
libraryDependencies += "dev.zio" %% "zio-json"      % "0.9.1"
libraryDependencies += "dev.zio" %% "zio-json-yaml" % "0.9.1"
