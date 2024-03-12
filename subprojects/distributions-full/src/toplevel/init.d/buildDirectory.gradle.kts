// @help: https://docs.gradle.org/current/kotlin-dsl

import java.nio.file.Paths

val savedRootDir = file(System.getProperty("user.dir"))

allprojects {
  val oldBuildDir = layout.buildDirectory.asFile.get().relativeTo(savedRootDir)
  val newBuildDir = Paths.get(savedRootDir.path, "./out/", oldBuildDir.path)
  layout.buildDirectory = newBuildDir.toFile()
}
