// Include the local zio-sbt-source plugin
sys.props.get("plugin.version") match {
  case Some(x) => addSbtPlugin("dev.zio" %% "zio-sbt-source" % x)
  case _       => sys.error("""the system property 'plugin.version' is not defined.""")
}
