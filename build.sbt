name := "Event Organizer"

version := "1.0-SNAPSHOT"

lazy val root = (project in file(".")).enablePlugins(PlayScala)

scalaVersion := "2.11.12"

libraryDependencies ++= Seq(
  cache,
  ws,
  specs2 % Test,
  "org.webjars" % "bootstrap" % "3.3.6",
  "org.webjars" % "bootstrap-datepicker" % "1.5.0-1",
  "com.typesafe.play" %% "play-slick" % "2.0.0",
  "com.typesafe.play" %% "play-slick-evolutions" % "2.0.0",
  "mysql" % "mysql-connector-java" % "5.1.47"
)

resolvers += "scalaz-bintray" at "http://dl.bintray.com/scalaz/releases"

// Play provides two styles of routers, one expects its actions to be injected, the
// other, legacy style, accesses its actions statically.
routesGenerator := InjectedRoutesGenerator

sources in (Compile, doc) := Seq.empty

publishArtifact in (Compile, packageDoc) := false
