/*
 *  This file is part of android-tree-sitter.
 *
 *  android-tree-sitter library is free software; you can redistribute it and/or
 *  modify it under the terms of the GNU Lesser General Public
 *  License as published by the Free Software Foundation; either
 *  version 2.1 of the License, or (at your option) any later version.
 *
 *  android-tree-sitter library is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 *  Lesser General Public License for more details.
 *
 *   You should have received a copy of the GNU General Public License
 *  along with android-tree-sitter.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.itsaky.androidide.treesitter

import com.android.build.api.variant.AndroidComponentsExtension
import com.android.build.api.variant.Variant
import com.android.build.api.dsl.LibraryExtension
import com.android.build.gradle.tasks.ExternalNativeBuildTask
import com.itsaky.androidide.treesitter.jni.GenerateNativeHeadersTask
import org.gradle.api.Plugin
import org.gradle.api.Project
import java.util.Locale

/**
 * Marker plugin.
 *
 * @author Akash Yadav
 */
class TreeSitterPlugin : Plugin<Project> {

  override fun apply(target: Project) {
    target.run {
      val nativeHeadersDir =
        project.layout.buildDirectory.dir("generated/native_headers")

      val libraryExtension = extensions.getByType(LibraryExtension::class.java)

      libraryExtension.defaultConfig.externalNativeBuild.cmake.arguments(
        "-DAUTOGEN_HEADERS=${nativeHeadersDir.get().asFile.invariantSeparatorsPath}")

      extensions.getByType(AndroidComponentsExtension::class.java).apply {
        onVariants { variant ->
          configureVariant(variant, libraryExtension)
        }
      }
    }
  }

  private fun Project.configureVariant(
    variant: Variant,
    libraryExtension: LibraryExtension
  ) {
    val variantName = variant.name.replaceFirstChar { name ->
      if (name.isLowerCase()) name.titlecase(Locale.ROOT) else name.toString()
    }
    configureGenNativeHeadersTask(variantName, libraryExtension, variant)
  }

  @Suppress("UnstableApiUsage")
  private fun Project.configureGenNativeHeadersTask(variantName: String,
                                                    libraryExtension: LibraryExtension,
                                                    variant: Variant
  ) {

    val generateNativeHeadersTask =
      tasks.register("generateNativeHeaders$variantName",
        GenerateNativeHeadersTask::class.java) {

        val javaSrcDirs = libraryExtension.sourceSets.getByName("main").java.directories.map { project.file(it) }
        srcFiles = project.files(javaSrcDirs).asFileTree
        classPath = variant.compileClasspath
        srcDirs.set(javaSrcDirs)
        outputDirectory.set(
          project.layout.buildDirectory.dir("generated/native_headers"))
      }

    tasks.withType(ExternalNativeBuildTask::class.java).configureEach {
      dependsOn(generateNativeHeadersTask)
    }
  }
}
