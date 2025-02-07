addSbtPlugin("org.scalameta"                     % "sbt-mdoc"         % "2.6.3")
addSbtPlugin("com.github.sbt"                    % "sbt-unidoc"       % "0.5.0")
addSbtPlugin("com.thoughtworks.sbt-api-mappings" % "sbt-api-mappings" % "3.0.2")

libraryDependencies += "dev.zio" %% "zio"           % "2.1.14"
libraryDependencies += "dev.zio" %% "zio-json"      % "0.7.8"
libraryDependencies += "dev.zio" %% "zio-json-yaml" % "0.7.8"
