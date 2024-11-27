package zio.sbt

import sbt.Keys._
import sbt._

object ZioSbtShared extends AutoPlugin {

  override def trigger = allRequirements

  override def requires: Plugins = super.requires

  object autoImport {

    val welcomeBannerEnabled: SettingKey[Boolean] =
      settingKey[Boolean]("Indicates whether or not to enable the welcome banner.")

    val banners: SettingKey[Seq[String]] = settingKey[Seq[String]](
      "A list of banners that will be displayed as part of the welcome message."
    )

    val welcomeTaskAndSettingsEnabled: SettingKey[Boolean] =
      settingKey[Boolean]("Indicates whether or not to enable the welcome task and settings.")

    val usefulTasksAndSettings: SettingKey[Seq[(String, String)]] = settingKey[Seq[(String, String)]](
      "A map of useful tasks and settings that will be displayed as part of the welcome banner."
    )

    val welcomeMessage: Setting[String] = ZioSbtShared.welcomeMessageSetting
  }

  import autoImport.*

  private val allAggregates =
    ScopeFilter(inAggregates(ThisProject))

  val allBanners: Def.Initialize[Seq[String]] = Def.settingDyn {
    Def.setting(
      banners
        .all(allAggregates)
        .value
        .foldLeft(Seq.empty[String])(_ ++ _)
    )
  }

  val allUsefulTasksAndSettings: Def.Initialize[Seq[(String, String)]] =
    Def.settingDyn {
      Def.setting(
        usefulTasksAndSettings
          .all(allAggregates)
          .value
          .foldLeft(Seq.empty[(String, String)])(_ ++ _)
      )
    }

  def welcomeMessageSetting: Setting[String] =
    onLoadMessage := {
      if (welcomeBannerEnabled.value) {
        import scala.Console

        val allBanners                = ZioSbtShared.allBanners.value
        val allUsefulTasksAndSettings = ZioSbtShared.allUsefulTasksAndSettings.value
        val maxLen                    = (allUsefulTasksAndSettings.map(_._1.length) ++ Seq(1)).max

        def normalizedPadding(s: String) = " " * (maxLen - s.length)

        def item(text: String): String = s"${Console.GREEN}> ${Console.CYAN}$text${Console.RESET}"

        val renderedBanners = allBanners.mkString("\n")
        val renderedTasksAndSettings =
          if (welcomeTaskAndSettingsEnabled.value) {
            s"""|Useful sbt tasks:
                |${allUsefulTasksAndSettings.map { case (task, description) =>
                 s"${item(task)} ${normalizedPadding(task)}${description}"
               }
                 .mkString("\n")}
             """.stripMargin
          } else ""

        s"""|
            |$renderedBanners
            |$renderedTasksAndSettings
            |""".stripMargin
      } else ""
    }

  override def projectSettings: Seq[Setting[_]] = Seq(
    banners                := Seq.empty,
    usefulTasksAndSettings := Seq.empty
  )

  override def globalSettings: Seq[Def.Setting[_]] = super.globalSettings ++
    Seq(
      welcomeBannerEnabled          := true,
      welcomeTaskAndSettingsEnabled := true,
      banners                       := Seq.empty,
      usefulTasksAndSettings        := Seq.empty,
      Global / excludeLintKeys += usefulTasksAndSettings
    )

}
