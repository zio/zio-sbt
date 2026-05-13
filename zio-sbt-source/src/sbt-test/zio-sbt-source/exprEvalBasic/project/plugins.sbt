// zio-sbt-source is added as a library dependency in build.sbt
// The plugin.version property is required for scripted tests
sys.props.get("plugin.version") match {
  case Some(_) => // version is provided via scriptedLaunchOpts
  case None => sys.error("""the system property 'plugin.version' is not defined.""")
}
