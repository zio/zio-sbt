Compile / unmanagedSourceDirectories += file("zio-sbt-ecosystem/src/main/scala")
// The meta-build always compiles under Scala 2.12 (see project/build.properties), so it needs the
// scala-2.12 half of zio-sbt-ecosystem's PlatformCompat.scala cross-source split too.
Compile / unmanagedSourceDirectories += file("zio-sbt-ecosystem/src/main/scala-2.12")
Compile / unmanagedResourceDirectories += file("zio-sbt-ecosystem/src/main/resources")

Compile / unmanagedSourceDirectories += file("zio-sbt-website/src/main/scala")
// Same for zio-sbt-website's WebsitePluginCompat.scala.
Compile / unmanagedSourceDirectories += file("zio-sbt-website/src/main/scala-2.12")
Compile / unmanagedResourceDirectories += file("zio-sbt-website/src/main/resources")

Compile / unmanagedSourceDirectories += file("zio-sbt-ci/src/main/scala")
Compile / unmanagedResourceDirectories += file("zio-sbt-ci/src/main/resources")

Compile / unmanagedSourceDirectories += file("zio-sbt-githubactions/src/main/scala")
Compile / unmanagedResourceDirectories += file("zio-sbt-githubactions/src/main/resources")

Compile / unmanagedSourceDirectories += file("zio-sbt-scaladoc/src/main/scala")
// Same for zio-sbt-scaladoc's ScaladocCheckCompat.scala. Loading the plugin here makes zio-sbt check
// the Scaladoc examples of its own modules (`sbt +scaladocCheck`).
Compile / unmanagedSourceDirectories += file("zio-sbt-scaladoc/src/main/scala-2.12")
