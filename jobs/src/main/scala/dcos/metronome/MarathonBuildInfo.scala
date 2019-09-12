package dcos.metronome

import java.util.jar.{ Attributes, Manifest }

import dcos.metronome.utils.SemVer

import scala.collection.JavaConverters
import scala.util.control.NonFatal

case object MarathonBuildInfo {
  private val marathonJar = """\/mesosphere\/marathon\/marathon_2.12\/[0-9.]+""".r
  val DefaultBuildVersion = SemVer(1, 7, 0, Some("SNAPSHOT"))

  /**
    * sbt-native-package provides all of the files as individual JARs. By default, `getResourceAsStream` returns the
    * first matching file for the first JAR in the class path. Instead, we need to enumerate through all of the
    * manifests, and find the one that applies to the Marathon application jar.
    */
  private lazy val marathonManifestPath: List[java.net.URL] = {
    val resources = JavaConverters.enumerationAsScalaIterator(getClass().getClassLoader().getResources("META-INF/MANIFEST.MF"))
    resources
      .filter(manifest => marathonJar.findFirstMatchIn(manifest.getPath).nonEmpty)
      .toList
  }

  lazy val manifest: Option[Manifest] = marathonManifestPath match {
    case Nil => None
    case List(file) =>

      val closeable = file.openStream
      try {
        val mf = new Manifest()
        mf.read(closeable)
        Some(mf)
      } finally {
        try {
          closeable.close()
        } catch {
          case ex: Exception =>
          // suppress exceptions
        }
      }
    case otherwise =>
      throw new RuntimeException(s"Multiple marathon JAR manifests returned! ${otherwise}")
  }

  lazy val attributes: Option[Attributes] = manifest.map(_.getMainAttributes())

  def getAttribute(name: String): Option[String] = attributes.flatMap { attrs =>
    try {
      Option(attrs.getValue(name))
    } catch {
      case NonFatal(_) => None
    }
  }

  lazy val name: String = getAttribute("Implementation-Title").getOrElse("unknown")

  // IntelliJ has its own manifest.mf that will inject a version that doesn't necessarily match
  // our actual version. This can cause Migrations to fail since the version number doesn't correctly match up.
  lazy val version: SemVer = getAttribute("Implementation-Version").filterNot(_ == "0.1-SNAPSHOT").map(SemVer(_)).getOrElse(DefaultBuildVersion)

  lazy val scalaVersion: String = getAttribute("Scala-Version").getOrElse("2.x.x")

  lazy val buildref: String = getAttribute("Git-Commit").getOrElse("unknown")

  override val toString: String = {
    "name: %s, version: %s, scalaVersion: %s, buildref: %s" format (
      name, version, scalaVersion, buildref)
  }
}

