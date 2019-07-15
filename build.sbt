name := """scala-bookworm"""
organization := "dev.zenathark"

version := "1.0-SNAPSHOT"

lazy val root = (project in file(".")).enablePlugins(PlayScala)

scalaVersion := "2.13.0"

libraryDependencies += ws //Check
libraryDependencies += guice //Check
libraryDependencies += "org.scalatestplus.play" %% "scalatestplus-play" % "4.0.3" % Test
libraryDependencies += "com.typesafe.akka" %% "akka-distributed-data" % "2.5.23"
libraryDependencies += "org.abstractj.kalium" % "kalium" % "0.8.0"

scalacOptions ++= Seq(
  "-feature",
  "-deprecation",
  "-Xfatal-warnings"
)

// Adds additional packages into Twirl
//TwirlKeys.templateImports += "dev.zenathark.controllers._"

// Adds additional packages into conf/routes
// play.sbt.routes.RoutesKeys.routesImport += "dev.zenathark.binders._"
