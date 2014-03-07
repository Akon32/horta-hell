name := "horta-hell"

version := "0.2-SNAPSHOT"

mainClass in (Compile, run) := Some("ru.org.codingteam.horta.Application")

scalaVersion := "2.10.3"

scalacOptions ++= Seq("-unchecked", "-deprecation", "-feature")

libraryDependencies += "com.typesafe.akka" % "akka-actor_2.10" % "2.2.3"

com.github.retronym.SbtOneJar.oneJarSettings
