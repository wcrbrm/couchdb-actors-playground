name := "couchdb-actors"
version := "1.0"

lazy val akkaVersion = "2.5.18"
lazy val akkaStreamVersion = "2.5.18"

enablePlugins(JavaAppPackaging)
// enablePlugins(JavaServerAppPackaging)



libraryDependencies ++= Seq(
  "org.scalaj" %% "scalaj-http" % "2.4.1",
  "com.lihaoyi" %% "ujson" % "0.6.6",

  "com.typesafe.akka" %% "akka-actor" % akkaVersion,
  "com.typesafe.akka" %% "akka-stream" % akkaStreamVersion,
  "io.kamon" %% "kamon-core" % "1.1.2",
  "io.kamon" %% "kamon-graphite" % "1.2.1",
  "io.kamon" %% "kamon-logback" % "1.0.0"
)

