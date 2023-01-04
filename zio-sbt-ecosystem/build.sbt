addSbtPlugin("com.github.sbt"                    % "sbt-pgp"                   % "2.1.2")
addSbtPlugin("ch.epfl.scala"                     % "sbt-bloop"                 % "1.4.9")
addSbtPlugin("ch.epfl.scala"                     % "sbt-scalafix"              % "0.10.4")
addSbtPlugin("com.eed3si9n"                      % "sbt-buildinfo"             % "0.10.0")
addSbtPlugin("com.eed3si9n"                      % "sbt-unidoc"                % "0.4.3")
addSbtPlugin("com.github.cb372"                  % "sbt-explicit-dependencies" % "0.2.16")
addSbtPlugin("com.thoughtworks.sbt-api-mappings" % "sbt-api-mappings"          % "3.0.0")
addSbtPlugin("com.typesafe"                      % "sbt-mima-plugin"           % "1.1.1")
addSbtPlugin("de.heikoseeberger"                 % "sbt-header"                % "5.9.0")
addSbtPlugin("org.scalameta"                     % "sbt-mdoc"                  % "2.3.6")
addSbtPlugin("org.scalameta"                     % "sbt-scalafmt"              % "2.4.3")
// addSbtPlugin("pl.project13.scala" % "sbt-jcstress" % "0.2.0")
// addSbtPlugin("pl.project13.scala" % "sbt-jmh"      % "0.4.3")
// addSbtPlugin("org.portable-scala"                % "sbt-scala-native-crossproject" % "1.0.0")
// addSbtPlugin("org.portable-scala"                % "sbt-scalajs-crossproject"      % "1.0.0")
// addSbtPlugin("org.scala-js"                      % "sbt-scalajs"                   % "1.8.0")
// addSbtPlugin("org.scala-native"                  % "sbt-scala-native"              % "0.4.0")

// addSbtPlugin("com.geirsson"                      % "sbt-ci-release"            % "1.5.7")
libraryDependencies ++= Seq("org.snakeyaml" % "snakeyaml-engine" % "2.3")
