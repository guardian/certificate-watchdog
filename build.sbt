name := "certificate-watchdog"

organization := "com.gu"

description:= "This project checks the certificates referenced in prism are up to date, otherwise warns the respective team owners."

version := "1.0"

scalaVersion := "2.12.2"

scalacOptions ++= Seq(
  "-deprecation",
  "-encoding", "UTF-8",
  "-target:jvm-1.8",
  "-Ywarn-dead-code"
)

libraryDependencies ++= Seq(
  "org.slf4j" % "slf4j-simple" % "1.7.25",
  "com.typesafe.play" %% "play-json" % "2.6.2",
  "com.amazonaws" % "aws-java-sdk-ses" % "1.11.165",
  "org.scalatest" %% "scalatest" % "3.0.1" % "test"
)

enablePlugins(RiffRaffArtifact)

assemblyJarName := s"${name.value}.jar"
riffRaffPackageType := assembly.value
riffRaffUploadArtifactBucket := Option("riffraff-artifact")
riffRaffUploadManifestBucket := Option("riffraff-builds")
riffRaffArtifactResources += (file("cfn.yaml"), s"${name.value}-cfn/cfn.yaml")