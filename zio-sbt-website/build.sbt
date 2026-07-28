addSbtPlugin("org.scalameta"                     % "sbt-mdoc"         % "2.9.1")
addSbtPlugin("com.github.sbt"                    % "sbt-unidoc"       % "0.6.1")
addSbtPlugin("com.thoughtworks.sbt-api-mappings" % "sbt-api-mappings" % "3.0.3")

libraryDependencies += "dev.zio" %% "zio"           % "2.1.26"
libraryDependencies += "dev.zio" %% "zio-json"      % "0.10.0"
libraryDependencies += "dev.zio" %% "zio-json-yaml" % "0.10.0"
