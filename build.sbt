name := "main"

version := "latest"

scalaVersion := "2.13.5"

libraryDependencies ++= Seq(
  "com.typesafe.slick" %% "slick" % "3.3.3",
  "com.typesafe.scala-logging" %% "scala-logging" % "3.9.4",
  "ch.qos.logback" % "logback-classic" % "1.2.3",
  "org.postgresql" % "postgresql" % "42.2.2",
  "com.typesafe.slick" %% "slick-hikaricp" % "3.3.3",
  "io.opentelemetry" % "opentelemetry-api" % "1.11.0"
)

fork := true
javaOptions += s"""-javaagent:${System.getProperty("user.home")}/projects/carbon/opentelemetry-java-instrumentation/javaagent/build/libs/opentelemetry-javaagent-1.11.0-SNAPSHOT.jar"""
javaOptions += s"""-Dotel.javaagent.configuration-file=${System.getProperty("user.home")}/projects/carbon/scala-slick-deadlock/src/main/resources/opentelemetry-zipkin.conf"""
