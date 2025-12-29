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

import org.gradle.api.DefaultTask
import org.gradle.api.logging.LogLevel.LIFECYCLE
import org.gradle.api.tasks.TaskAction
import java.io.File

/**
 * @author Akash Yadav
 */
abstract class GenerateTreeSitterGrammarTask : DefaultTask() {

  @TaskAction
  fun generateGrammar() {
    val langName = project.name.substringAfterLast('-')

    val grammarDir = project.rootProject.file("grammars/$langName").absolutePath
    val grammarsDir = project.rootProject.file("grammars").absolutePath
    var tsCmd = project.rootProject.file("tree-sitter-lib/cli/build/release/tree-sitter").absolutePath
    if (!BUILD_TS_CLI_FROM_SOURCE) {
      tsCmd = "tree-sitter"
    }

    // Set NODE_PATH to include the grammars directory so that tree-sitter-cpp can find tree-sitter-c
    val env = mutableMapOf<String, String>()
    val existingNodePath = System.getenv("NODE_PATH") ?: ""
    val separator = if (File.separator == "\\") ";" else ":"
    env["NODE_PATH"] = if (existingNodePath.isNotEmpty()) {
      "$grammarsDir$separator$existingNodePath"
    } else {
      grammarsDir
    }

    project.logger.log(LIFECYCLE, "Using '$tsCmd' to generate '${project.name}' grammar")
    project.logger.log(LIFECYCLE, "NODE_PATH set to: ${env["NODE_PATH"]}")
    project.executeCommand(grammarDir, env, tsCmd, "generate")
  }
}