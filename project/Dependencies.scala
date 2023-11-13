import sbt._

object Dependencies {
  lazy val docx4j = "org.docx4j" % "docx4j" % "6.1.2"
  lazy val jaxb = "org.glassfish.jaxb" % "jaxb-runtime" % "2.3.7"
  lazy val circeCore = "io.circe" %% "circe-core" % "0.14.5"
  lazy val circeGeneric = "io.circe" %% "circe-generic" % "0.14.5"
  lazy val s3Client = "uk.gov.nationalarchives" %% "da-s3-client" % "0.1.27"
  lazy val sqsClient = "uk.gov.nationalarchives" %% "da-sqs-client" % "0.1.27"
  lazy val preservicaClient = "uk.gov.nationalarchives" %% "preservica-client-fs2" % "0.0.28"
  lazy val log4jCore = "org.apache.logging.log4j" % "log4j-core" % "2.20.0"
  lazy val log4jSlf4j = "org.apache.logging.log4j" % "log4j-slf4j-impl" % "2.20.0"
  lazy val log4jJson = "org.apache.logging.log4j" % "log4j-layout-template-json" % "2.20.0"
  lazy val fs2ReactiveStreams = "co.fs2" %% "fs2-reactive-streams" % "3.7.0"
  lazy val scalaTest = "org.scalatest" %% "scalatest" % "3.2.15"
}
