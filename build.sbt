name := "main"

version := "latest"

scalaVersion := "2.13.5"

libraryDependencies ++= Seq(
  "com.typesafe.slick" %% "slick" % "3.4.0-SNAPSHOT",
  "com.typesafe.scala-logging" %% "scala-logging" % "3.9.4",
  "ch.qos.logback" % "logback-classic" % "1.2.3",
  "org.postgresql" % "postgresql" % "42.2.2",
  "com.typesafe.slick" %% "slick-hikaricp" % "3.4.0-SNAPSHOT"
)

fork := true
javaOptions += s"""-javaagent:${System.getProperty("user.home")}/.ivy2/local/com.github.rssh/trackedfuture_2.13/0.5.0-SNAPSHOT/jars/trackedfuture_2.13-assembly.jar"""

