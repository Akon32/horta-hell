
sourceGenerators in Compile <+= Def.task {
  val dirs = file("plugins").listFiles.filter(_.isDirectory).map(_.getName)
  def pluginName(dirName: String) = s"`${dirName}Plugin`"
  val pluginNames = dirs map pluginName
  val pluginEntries = dirs map {
    d => s"""lazy val ${pluginName(d)} = project in file("plugins/$d") dependsOn `horta-core`"""
  }
  val buildText =
    s"""
      |import sbt._
      |
      |object HortaBuild extends Build{
      |
      |  lazy val `horta-hell` = project in file(".") dependsOn(`horta-core`, plugins)
      |
      |  lazy val `horta-core` = project in file("horta-core")
      |
      |  lazy val plugins = project in file("plugins") dependsOn(${pluginNames mkString ","})
      |
      |  ${pluginEntries mkString "\n  "}
      |
      |}
    """.stripMargin
  val out = (sourceManaged in Compile).value / "HortaBuild.scala"
  IO.write(out, buildText)
  Seq(out)
}
