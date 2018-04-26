import com.typesafe.sbt.packager.linux.LinuxSymlink
import com.typesafe.sbt.packager.SettingsHelper.makeDeploymentSettings

lazy val root = (project in file("."))
  .enablePlugins(JavaServerAppPackaging)
  .enablePlugins(SystemVPlugin)
  .enablePlugins(DebianPlugin)
  .enablePlugins(JDebPackaging)
  .enablePlugins(DockerPlugin)

val sduTeam = settingKey[String]("Sdu team: betty|extra|cwc|local")
sduTeam := sys.props.getOrElse("sduTeam", default = "local")
organization := "nl.sdu." + sduTeam.value
scalaVersion := "2.11.7"
crossScalaVersions := Seq("2.10.5", "2.11.7")

// Debian
name := "diffy"
maintainer := "Sdu CWC Extra Team <sdu-cwc-extra@sdu.nl>"
packageSummary := "Diffy Regression Test Tool"
packageDescription := "Diffy Regression Test Tool"
makeDeploymentSettings(Debian, packageBin in Debian, "deb")

homepage := Some(url("https://github.com/sdu-cwc-extra/diffy"))
licenses := Seq("Apache 2.0" -> url("http://www.apache.org/licenses/LICENSE-2.0"))

git.remoteRepo := "git@github.com:sdu-cwc-extra/diffy.git"

mainClass in Compile := Some("com.twitter.diffy.Main")

mappings in Universal <+= (packageBin in Compile, sourceDirectory ) map { (_, src) =>
  val conf = src / "main" / "resources" / "log4j.xml"
  conf -> "conf/log4j.xml"
}

// The same as linuxPackageMappings
linuxPackageSymlinks := {
  linuxPackageSymlinks.value :+ LinuxSymlink("/usr/share/diffy/differences.log", "/var/log/diffy/differences.log")
}

lazy val compilerOptions = Seq(
  "-deprecation",
  "-encoding", "UTF-8",
  "-feature",
  "-language:existentials",
  "-language:higherKinds",
  "-language:postfixOps",
  "-unchecked",
  "-Ywarn-dead-code",
  "-Ywarn-numeric-widen",
  "-Xfuture"
)

lazy val testDependencies = Seq(
  "junit" % "junit" % "4.8.1",
  "org.mockito" % "mockito-all" % "1.8.5",
  "org.scalacheck" %% "scalacheck" % "1.12.4",
  "org.scalatest" %% "scalatest" % "2.2.5"
)

lazy val finatraVersion = "2.0.0.M2"

lazy val finatraDependencies = Seq(
  "com.twitter.finatra" %% "finatra-http" % finatraVersion,
  "com.twitter.finatra" %% "finatra-http" % finatraVersion % "test" classifier "tests",
  "com.twitter.inject" %% "inject-app" % finatraVersion % "test",
  "com.twitter.inject" %% "inject-app" % finatraVersion % "test" classifier "tests",
  "com.twitter.inject" %% "inject-core" % finatraVersion % "test",
  "com.twitter.inject" %% "inject-core" % finatraVersion % "test" classifier "tests",
  "com.twitter.inject" %% "inject-modules" % finatraVersion % "test",
  "com.twitter.inject" %% "inject-modules" % finatraVersion % "test" classifier "tests",
  "com.twitter.inject" %% "inject-server" % finatraVersion % "test",
  "com.twitter.inject" %% "inject-server" % finatraVersion % "test" classifier "tests"
)

resolvers += "Twitter's Repository" at "https://maven.twttr.com/"
scalacOptions ++= compilerOptions ++ (
  CrossVersion.partialVersion(scalaVersion.value) match {
    case Some((2, 11)) => Seq("-Ywarn-unused-import")
    case _ => Nil
  }
)
scalacOptions in (Compile, console) := compilerOptions
libraryDependencies ++= Seq(
  "com.twitter" %% "finagle-http" % "6.28.0",
  "com.twitter" %% "finagle-thriftmux" % "6.28.0",
  "com.twitter" %% "scrooge-generator" % "4.0.0",
  "javax.mail" % "mail" % "1.4.7",
  "org.jsoup" % "jsoup" % "1.7.2",
  "org.slf4j" % "slf4j-log4j12" % "1.7.7",
  "org.scala-lang" % "scala-compiler" % scalaVersion.value
) ++ finatraDependencies ++ testDependencies.map(_ % "test")
assemblyMergeStrategy in assembly := {
  case "BUILD" => MergeStrategy.discard
  case PathList("scala", "tools", _*) => MergeStrategy.last
  case other => MergeStrategy.defaultMergeStrategy(other)
}

excludeFilter in unmanagedResources := HiddenFileFilter || "BUILD"
unmanagedResourceDirectories in Compile += baseDirectory.value / "src" / "main" / "webapp"

publishTo := {
  val artifactory = "https://artifactory.k8s.awssdu.nl/artifactory/"
  if (version.value.trim.endsWith("SNAPSHOT"))
    Some("snapshots" at artifactory + "cwc-snapshots")
  else
    Some("releases" at artifactory + "cwc-releases")
}

publish in Debian <<= (publish in Debian).triggeredBy(publish in Compile)

// Docker
dockerBaseImage := "openjdk:8"
dockerExposedPorts := Seq(8888)
dockerEntrypoint := Seq("bin/diffy")
dockerRepository := Some("117533191630.dkr.ecr.eu-west-1.amazonaws.com")
dockerUsername := Some("cwc")
