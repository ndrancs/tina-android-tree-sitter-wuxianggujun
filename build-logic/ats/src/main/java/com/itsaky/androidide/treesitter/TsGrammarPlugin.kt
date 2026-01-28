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

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.compile.JavaCompile

/**
 * Plugin applied to grammar modules.
 *
 * @author Akash Yadav
 */
class TsGrammarPlugin : Plugin<Project> {

  override fun apply(target: Project) {
    target.run {
      val grammars = target.readGrammars()
      val (grammarName, srcExtra) = grammars.find {
        it.name == project.name.substringAfterLast('-')
      }!!

      val grammarDir = objects.directoryProperty()
      grammarDir.set(rootProject.rootDir.resolve("grammars/$grammarName"))

      val forceGenerate = providers
        .gradleProperty("androidTreeSitter.forceGenerateGrammars")
        .map { it.equals("true", ignoreCase = true) }
        .orElse(false)

      val generateTask = tasks.register("generateTreeSitterGrammar",
        GenerateTreeSitterGrammarTask::class.java) {

        if (BUILD_TS_CLI_FROM_SOURCE) {
          dependsOn(rootProject.tasks.getByName("buildTreeSitter"))
        }

        inputs.file(grammarDir.file("grammar.js"))
        inputs.file(grammarDir.file("package.json"))
        for (extra in srcExtra) {
          inputs.file(grammarDir.file(extra))
        }

        outputs.file(grammarDir.file("src/parser.c"))
        outputs.file(grammarDir.file("src/grammar.json"))
        outputs.file(grammarDir.file("src/node-types.json"))

        // 在 CI / 全新 checkout 场景下，我们通常已经提交了生成产物（parser.c 等）。
        // Gradle 首次构建没有历史快照时会强制执行该任务，导致缺少 `tree-sitter` CLI 的环境直接失败。
        // 因此：默认仅在产物缺失时才生成；需要强制生成可传入：
        //   -PandroidTreeSitter.forceGenerateGrammars=true
        onlyIf {
          if (forceGenerate.get()) {
            return@onlyIf true
          }

          val parserC = grammarDir.file("src/parser.c").get().asFile
          val grammarJson = grammarDir.file("src/grammar.json").get().asFile
          val nodeTypes = grammarDir.file("src/node-types.json").get().asFile
          !(parserC.exists() && grammarJson.exists() && nodeTypes.exists())
        }
      }
      tasks.withType(JavaCompile::class.java) { dependsOn(generateTask) }
    }
  }
}
