addSbtPlugin("org.scalameta"                     % "sbt-mdoc"         % "2.3.7")
addSbtPlugin("com.github.sbt"                    % "sbt-unidoc"       % "0.5.0")
addSbtPlugin("com.thoughtworks.sbt-api-mappings" % "sbt-api-mappings" % "3.0.2")

libraryDependencies += "dev.zio"  %% "zio"        % "2.0.18"
libraryDependencies += "io.circe" %% "circe-yaml" % "0.14.2"
