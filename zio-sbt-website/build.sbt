addSbtPlugin("org.scalameta"                     % "sbt-mdoc"         % "2.5.2")
addSbtPlugin("com.github.sbt"                    % "sbt-unidoc"       % "0.5.0")
addSbtPlugin("com.thoughtworks.sbt-api-mappings" % "sbt-api-mappings" % "3.0.2")

libraryDependencies += "dev.zio"  %% "zio"        % "2.1.1"
libraryDependencies += "io.circe" %% "circe-yaml" % "0.15.1"
