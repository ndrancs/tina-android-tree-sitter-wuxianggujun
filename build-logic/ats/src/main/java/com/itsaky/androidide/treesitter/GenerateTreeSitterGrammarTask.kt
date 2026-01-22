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
import java.nio.file.Files

/**
 * @author Akash Yadav
 */
abstract class GenerateTreeSitterGrammarTask : DefaultTask() {

  @TaskAction
  fun generateGrammar() {
    val langName = project.name.substringAfterLast('-')

    val grammarDirFile = project.rootProject.file("grammars/$langName")

    // tree-sitter-cli <= 0.20 expects a "tree-sitter" section in package.json.
    // Some upstream grammars (e.g. newer tree-sitter-rust) ship "tree-sitter.json" instead.
    // To keep submodules untouched, we patch package.json temporarily for generation.
    val restorePackageJson = ensureTreeSitterPackageJson(grammarDirFile, langName)

    val grammarDir = grammarDirFile.absolutePath
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

    try {
      project.logger.log(LIFECYCLE, "Using '$tsCmd' to generate '${project.name}' grammar")
      project.logger.log(LIFECYCLE, "NODE_PATH set to: ${env["NODE_PATH"]}")
      project.executeCommand(grammarDir, env, tsCmd, "generate")
    } finally {
      restorePackageJson?.invoke()
    }
  }

  private fun ensureTreeSitterPackageJson(grammarDir: File, langName: String): (() -> Unit)? {
    val packageJson = File(grammarDir, "package.json")

    if (packageJson.exists()) {
      val original = packageJson.readText()
      if (containsTopLevelJsonKey(original, "tree-sitter")) {
        return null
      }

      project.logger.log(LIFECYCLE, "Patching package.json to add 'tree-sitter' section: ${packageJson.absolutePath}")
      val patched = injectTreeSitterSection(original, langName)
      packageJson.writeText(patched)
      return { packageJson.writeText(original) }
    }

    // No package.json: create a minimal one for legacy CLI
    val minimal = """
      {
        "name": "tree-sitter-$langName",
        "version": "0.0.0",
        "private": true,
        "tree-sitter": [
          {
            "scope": "source.$langName",
            "file-types": ["$langName"],
            "highlights": ["queries/highlights.scm"]
          }
        ]
      }
    """.trimIndent() + "\n"

    packageJson.parentFile?.mkdirs()
    project.logger.log(LIFECYCLE, "Creating temporary package.json with 'tree-sitter' section: ${packageJson.absolutePath}")
    packageJson.writeText(minimal)
    return { Files.deleteIfExists(packageJson.toPath()) }
  }

  private fun containsTopLevelJsonKey(json: String, key: String): Boolean {
    var curlyDepth = 0
    var i = 0

    while (i < json.length) {
      val c = json[i]
      when (c) {
        '{' -> {
          curlyDepth++
          i++
        }

        '}' -> {
          curlyDepth--
          i++
        }

        '"' -> {
          val (stringValue, endIndex) = readJsonString(json, i)
          if (curlyDepth == 1 && stringValue == key) {
            var j = endIndex + 1
            while (j < json.length && json[j].isWhitespace()) j++
            if (j < json.length && json[j] == ':') return true
          }
          i = endIndex + 1
        }

        else -> i++
      }
    }

    return false
  }

  private fun readJsonString(json: String, startQuoteIndex: Int): Pair<String, Int> {
    val sb = StringBuilder()
    var i = startQuoteIndex + 1
    var escaped = false

    while (i < json.length) {
      val c = json[i]
      if (escaped) {
        sb.append(c)
        escaped = false
        i++
        continue
      }

      when (c) {
        '\\' -> {
          escaped = true
          i++
        }

        '"' -> return sb.toString() to i

        else -> {
          sb.append(c)
          i++
        }
      }
    }

    return sb.toString() to (json.length - 1)
  }

  private fun injectTreeSitterSection(originalJson: String, langName: String): String {
    val injection = """
      "tree-sitter": [
        {
          "scope": "source.$langName",
          "file-types": ["$langName"],
          "highlights": ["queries/highlights.scm"]
        }
      ]
    """.trimIndent()

    val end = originalJson.lastIndexOf('}')
    if (end <= 0) {
      return originalJson
    }

    val prefix = originalJson.substring(0, end)
    val suffix = originalJson.substring(end)

    val trimmedPrefix = prefix.trimEnd()
    val needsComma = trimmedPrefix.isNotEmpty() && trimmedPrefix.last() != '{' && trimmedPrefix.last() != ','

    val builder = StringBuilder()
    builder.append(trimmedPrefix)
    if (needsComma) builder.append(',')
    builder.append('\n')
    builder.append("  ")
    builder.append(injection.replace("\n", "\n  "))
    builder.append('\n')
    builder.append(suffix)
    return builder.toString()
  }
}
