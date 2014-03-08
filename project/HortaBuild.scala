import sbt._

object HortaBuild extends Build {

  lazy val `horta-hell` = project in file(".") dependsOn(`horta-core`, plugins)

  lazy val `horta-core` = project in file("horta-core")

  lazy val plugins = project in file("plugins") dependsOn (
    versionPlugin,
    petPlugin,
    markovPlugin,
    bashPlugin,
    fortunePlugin,
    accessPlugin)

  lazy val versionPlugin = project in file("plugins/version") dependsOn `horta-core`

  lazy val petPlugin = project in file("plugins/pet") dependsOn `horta-core`

  lazy val markovPlugin = project in file("plugins/markov") dependsOn `horta-core`

  lazy val bashPlugin = project in file("plugins/bash") dependsOn `horta-core`

  lazy val fortunePlugin = project in file("plugins/fortune") dependsOn `horta-core`

  lazy val accessPlugin = project in file("plugins/access") dependsOn `horta-core`

}
