package zio.sbt

import scala.language.experimental.macros
import scala.reflect.macros.whitebox

import sbt.Project

object CiTargetMap {
  def makeTargetScalaMap(projects: Project*): sbt.Def.Initialize[Map[String, Seq[String]]] =
    macro macroMakeTargetScalaMapImpl

  def makeTargetJavaMap(projects: Project*): sbt.Def.Initialize[Map[String, String]] =
    macro macroMakeJavaVersionMapImpl

  def macroMakeTargetScalaMapImpl(
    c: whitebox.Context
  )(projects: c.Expr[Project]*): c.Expr[sbt.Def.Initialize[Map[String, Seq[String]]]] = {
    import c.universe.*

    // Define the keys and values to use in the final Map
    val keys   = projects.map(p => q"(${p.tree} / sbt.Keys.thisProject).value.id")
    val values = projects.map(p => q"(${p.tree} / sbt.Keys.crossScalaVersions).value")

    // Combine the keys and values into a Map
    val map = q"_root_.scala.collection.immutable.Map(..${keys.zip(values).map { case (k, v) => q"$k -> $v" }})"

    // Return the final setting
    c.Expr(q"sbt.Def.setting($map)")
  }

  def macroMakeJavaVersionMapImpl(
    c: whitebox.Context
  )(projects: c.Expr[Project]*): c.Expr[sbt.Def.Initialize[Map[String, String]]] = {
    import c.universe.*

    // Define the keys and values to use in the final Map
    val keys   = projects.map(p => q"(${p.tree} / sbt.Keys.thisProject).value.id")
    val values = projects.map(p => q"(${p.tree} / sbt.Keys.javaPlatform).value")

    // Combine the keys and values into a Map
    val map = q"_root_.scala.collection.immutable.Map(..${keys.zip(values).map { case (k, v) => q"$k -> $v" }})"

    // Return the final setting
    c.Expr(q"sbt.Def.setting($map)")
  }
}
