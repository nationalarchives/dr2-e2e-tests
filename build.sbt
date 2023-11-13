import Dependencies._

ThisBuild / scalaVersion := "2.13.12"
ThisBuild / version := "0.1.0-SNAPSHOT"
ThisBuild / organization := "com.example"
ThisBuild / organizationName := "example"

lazy val root = (project in file("."))
  .settings(
    name := "dr2-e2e-tests",
    libraryDependencies ++= Seq(
      docx4j % Test,
      jaxb % Test,
      circeCore % Test,
      circeGeneric % Test,
      s3Client % Test,
      sqsClient % Test,
      preservicaClient % Test,
      log4jCore % Test,
      log4jSlf4j % Test,
      log4jJson % Test,
      fs2ReactiveStreams % Test,
      scalaTest % Test
    )
  )
