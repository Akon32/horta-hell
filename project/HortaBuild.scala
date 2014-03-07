import sbt._

object HortaBuild extends Build {

  lazy val `horta-hell` = project in file(".") dependsOn(`horta-core`, plugins)

  lazy val `horta-core` = project in file("horta-core")

  lazy val plugins = project in file("plugins") dependsOn versionPlugin

  lazy val versionPlugin = project in file("plugins/version") dependsOn `horta-core`
}
