package zio.sbt

import sbt.ProjectExtra.projectToLocalProject
import sbt.{Def, Keys, Project, SettingKey}

object CiTargetMap {

  /**
   * Maps each project's id to its `crossScalaVersions`.
   *
   * This used to be a Scala 2 whitebox macro; sbt 2.0 builds plugins with Scala
   * 3, which dropped `scala.reflect.macros`, so it is now a plain function. A
   * macro was never actually required: a project's id is available statically
   * via `Project#id`, and the per-project `crossScalaVersions` value is read
   * reactively by combining `Def.Initialize` values.
   */
  def makeTargetScalaMap(projects: Project*): Def.Initialize[Map[String, Seq[String]]] =
    projects.foldLeft(Def.setting(Map.empty[String, Seq[String]])) { (acc, p) =>
      acc.zipWith(Def.setting(p.id -> (p / Keys.crossScalaVersions).value))(_ + _)
    }

  /**
   * Maps each project's id to its `javaPlatform` setting.
   *
   * `javaPlatform` is defined in `ZioSbtEcosystemPlugin.autoImport`, which this
   * module does not depend on, so it is referenced by name (sbt resolves
   * setting keys by label + type). The original macro referenced a non-existent
   * `sbt.Keys.javaPlatform`, so it never compiled at any call site.
   */
  def makeTargetJavaMap(projects: Project*): Def.Initialize[Map[String, String]] = {
    val javaPlatform = SettingKey[String]("javaPlatform")
    projects.foldLeft(Def.setting(Map.empty[String, String])) { (acc, p) =>
      acc.zipWith(Def.setting(p.id -> (p / javaPlatform).value))(_ + _)
    }
  }
}
