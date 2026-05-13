/*
 * Copyright 2024-2026 John A. De Goes and the ZIO Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package zio.sbt

import scala.reflect.macros.whitebox

object ExprEvalMacro {

  def showImpl(c: whitebox.Context)(exprs: c.Expr[Any]*): c.Expr[Unit] = {
    import c.universe._

    val pos      = c.enclosingPosition
    val filePath = pos.source.file.path
    val line     = pos.line

    def extractSourceText(tree: Tree): String = {
      // Prefer using tree's own source if available and has valid range position
      if (tree.pos != NoPosition && tree.pos.isRange) {
        val source = tree.pos.source.content
        val start  = tree.pos.start
        val end    = tree.pos.end
        // Guard against out-of-bounds access
        if (start >= 0 && end <= source.length && start <= end) {
          return new String(source.slice(start, end)).trim
        }
      }
      // Fallback: use showCode if tree position is invalid
      showCode(tree).trim
    }

    def extractStmts(tree: Tree): List[Tree] = tree match {
      case Block(stmts, last) =>
        // Only unpack if all statements are expression-like (no definitions/imports/etc)
        val onlyTerms = stmts.forall(t => !isNonTermStatement(t))
        if (onlyTerms)
          stmts.flatMap(extractStmts) ::: extractStmts(last)
        else
          List(tree)
      case other => List(other)
    }

    // Check if tree is a non-expression statement (definition, import, etc) that shouldn't be unpacked
    def isNonTermStatement(tree: Tree): Boolean = tree match {
      case _: ValDef    => true
      case _: DefDef    => true
      case _: Import    => true
      case _: TypeDef   => true
      case _: ClassDef  => true
      case _: ModuleDef => true
      case _            => false
    }

    // Handle varargs: if single block argument, try to unpack statements
    val trees: List[Tree] = if (exprs.length == 1) {
      val arg = exprs(0).tree
      arg match {
        case Block(_, _) => extractStmts(arg)
        case _           => List(arg)
      }
    } else {
      exprs.map(_.tree).toList
    }

    val printStatements: List[Tree] = trees.map { tree =>
      val src      = extractSourceText(tree)
      val exprTree = tree
      q"""
        println($src)
        val result = $exprTree
        String.valueOf(result).linesIterator.foreach(l => println(s"// $$l"))
      """
    }

    val allPrints: Tree = if (printStatements.isEmpty) {
      q"()"
    } else {
      printStatements.reduce((a: Tree, b: Tree) => q"$a; $b")
    }

    val filePath_lit = Literal(Constant(filePath))
    val line_lit     = Literal(Constant(line))

    c.Expr[Unit](q"""
      zio.sbt.SourceReader.commentsAbove($filePath_lit, $line_lit).foreach(println)
      $allPrints
      println()
    """)
  }
}
