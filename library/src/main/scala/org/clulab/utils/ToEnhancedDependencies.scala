package org.clulab.utils

import org.clulab.processors.Sentence
import org.clulab.scala.WrappedArrayBuffer._
import org.clulab.scala.WrappedListBuffer._
import org.clulab.struct.{DirectedGraph, DirectedGraphIndex, Edge}

import scala.collection.mutable.{ArrayBuffer, ListBuffer}

/**
  * Converts Stanford basic dependencies to collapsed ones
  * This follows the rules from http://universaldependencies.org/u/overview/enhanced-syntax.html
  *   (this applies to both Stanford basic deps and universal basic ones)
  * We support:
  * - Collapsing of prepositions to the prep_* (or nmod_*) label
  * - Controlled/raised subjects
  * - Propagating subjects and objects in conjoined verbs
  * - Propagating conjoined subjects and objects to same verb
  * - Pushing subjects/objects inside relative clauses
  * User: mihais
  * Date: 8/1/17
  */
object ToEnhancedDependencies {
  type EdgeSpec = (Int, Int, String)

  def generateStanfordEnhancedDependencies(words: Array[String], tags: Seq[String], dg:DirectedGraph[String]): DirectedGraph[String] = {
    val dgi = dg.toDirectedGraphIndex()
    collapsePrepositionsStanford(words, dgi)
    raiseSubjects(dgi)
    pushSubjectsObjectsInsideRelativeClauses(tags, dgi, universal = false)
    propagateSubjectsAndObjectsInConjVerbs(tags, dgi, universal = false)
    propagateConjSubjectsAndObjects(tags, dgi)
    dgi.toDirectedGraph(Some(words.length))
  }

  def generateUniversalEnhancedDependencies(words: Seq[String], lemmas: Seq[String], tags: Seq[String], dg: DirectedGraph[String]): DirectedGraph[String] = {
    val dgi = dg.toDirectedGraphIndex()
    collapseMWEs(lemmas, tags, dgi)
    val collapsedNmods = collapsePrepositionsUniversal(words, lemmas, tags, dgi)
    replicateCollapsedNmods(collapsedNmods, dgi)
    raiseSubjects(dgi)
    pushSubjectsObjectsInsideRelativeClauses(tags, dgi, universal = true)
    propagateSubjectsAndObjectsInConjVerbs(tags, dgi, universal = true) // requires tags
    propagateConjSubjectsAndObjects(tags, dgi)
    mergeNsubjXcomp(dgi)
    replicateCopulativeSubjects(dgi)
    expandConj(words, dgi) // this must be last because several of the above methods expect "conj" labels
    dgi.toDirectedGraph(Some(words.length))
  }

  /**
   * Replicates nmod_* accross conj dependencies
   * economic decline has led to violence and displacement => nmod_to from "led" to both "violence" and "displacement"
   */
  def replicateCollapsedNmods(collapsedNmods: Seq[EdgeSpec],
                              dgi: DirectedGraphIndex[String]): Unit = {
    for(nmod <- collapsedNmods) {
      val conjs = dgi.findByHeadAndName(nmod._2, "conj")
      for(conj <- conjs) {
        dgi.addEdge(nmod._1, conj.destination, nmod._3)
      }
    }
  }

  /**
   * Replicates copulative subjects across conjunctions
   * It is difficult and expensive => nsubj from 2 to 0 and from 4 to 0
   */
  def replicateCopulativeSubjects(dgi: DirectedGraphIndex[String]): Unit = {
    val nsubjs = dgi.findByName("nsubj")
    for(nsubj <- nsubjs) {
      val cops = dgi.findByHeadAndName(nsubj.source, "cop")
      if(cops.nonEmpty) { // found a copulative nsubj
        val conjs = dgi.findByHeadAndName(nsubj.source, "conj")
        for(conj <- conjs) {
          dgi.addEdge(conj.destination, nsubj.destination, "nsubj")
        }
      }
    }
  }

  /**
   * nsubj + xcomp => nsubj:xsubj
   * Disagreements over land rights for crop cultivation and livestock grazing continue to be a major source of conflict. => nsubj:xsubj from "Disagreements" to "source"
   */
  def mergeNsubjXcomp(dgi: DirectedGraphIndex[String]): Unit = {
    val nsubjs = dgi.findByName("nsubj")
    for(nsubj <- nsubjs) {
      for(xcomp <- dgi.findByHeadAndName(nsubj.source, "xcomp")){
        dgi.addEdge(nsubj.destination, xcomp.destination, "nsubj:xsubj")
      }
    }
  }

  private def remove(edges: Seq[Edge[String]], dgi: DirectedGraphIndex[String]): Unit = {
    edges.foreach(e => dgi.removeEdge(e.source, e.destination, e.relation))
  }

  /**
   * Collapses conj and cc into conj_CCWORD
   * John and Mary ate cake => conj_and from 0 to 2
   * @param sentence
   * @param dgi
   */
  def expandConj(words: Seq[String], dgi: DirectedGraphIndex[String]): Unit = {
    val toRemove = new ListBuffer[Edge[String]]
    val conjs = dgi.findByName("conj")
    for (conj <- conjs) {
      var shouldRemove = false
      for(cc <- dgi.findByName("cc").filter(_.source == conj.source)) {
        val ccWord = words(cc.destination).toLowerCase()
        dgi.addEdge(conj.source, conj.destination, s"conj_$ccWord")
        shouldRemove = true
      }
      if(shouldRemove) {
        toRemove += conj
      }
    }
    remove(toRemove, dgi)
  }

  /**
    * Collapses prep + pobj into prep_x (Stanford dependencies)
    * Mary gave a book to Jane => prep_to from 1 to 5
    * @param sentence The sentence to operate on
    * @param dgi The directed graph of collapsed dependencies at this stage
    */
  def collapsePrepositionsStanford(words: Array[String], dgi:DirectedGraphIndex[String]): Unit = {
    val toRemove = new ListBuffer[Edge[String]]
    val preps = dgi.findByName("prep")
    for(prep <- preps) {
      toRemove += prep
      val word = words(prep.destination)
      for(pobj <- dgi.findByName("pobj").filter(_.source == prep.destination)) {
        dgi.addEdge(prep.source, pobj.destination, s"prep_$word")
        toRemove += pobj
      }
    }
    remove(toRemove, dgi)
  }

  def collapsePrepositionsUniversal(
    words: Seq[String], lemmas: Seq[String], tags: Seq[String],
    dgi:DirectedGraphIndex[String]): Seq[EdgeSpec] = {

    val collapsedNmods = new ArrayBuffer[EdgeSpec]()
    collapsePrepositionsUniversalNmodCase(words, dgi, collapsedNmods)
    collapsePrepositionsUniversalDueTo(lemmas, tags, dgi, collapsedNmods)
    collapsedNmods
  }

  /**
    * Collapses nmod + case into nmod_x (universal dependencies)
    *     Mary gave a book to Jane => nmod_to from 1 to 5
    * @param sentence The sentence to operate on
    * @param dgi The directed graph of collapsed dependencies at this stage
    */
  def collapsePrepositionsUniversalNmodCase(
    words: Seq[String],
    dgi:DirectedGraphIndex[String],
    collapsedNmods: ArrayBuffer[EdgeSpec]): Unit = {

    val toRemove = new ListBuffer[Edge[String]]
    var shouldRemove = false
    val preps = dgi.findByName("nmod")
    for(prep <- preps) {
      toRemove += prep
      for(c <- dgi.findByName("case").filter(_.source == prep.destination)) {
        val word = words(c.destination).toLowerCase()
        // find multi-word prepositions such as "such as"
        val mwe = findMultiWord(word, c.destination, words, dgi)

        // TODO: add nmod:agent (if word == "by") and passive voice here?
        dgi.addEdge(prep.source, prep.destination, s"nmod_$mwe")
        val edgeSpec = (prep.source, prep.destination, s"nmod_$mwe")
        collapsedNmods += edgeSpec
        shouldRemove = true
      }
    }

    if(shouldRemove) remove(toRemove, dgi)
  }

  /**
    * Collapses nmod + case into nmod_x (universal dependencies)
    *     1) There was famine due to drought: amod 2 to 3, nmod 3 to 5 => nmod_due_to from 2 to 5
    *     2) They ate cake due to hunger => xcomp from 1 to 3, nmod 3 to 5 => nmod_due_to from 1 to 5
    * @param sentence The sentence to operate on; tags must be defined
    * @param dgi The directed graph of collapsed dependencies at this stage
    */
  def collapsePrepositionsUniversalDueTo(
    lemmas: Seq[String], tags: Seq[String],
    dgi:DirectedGraphIndex[String], 
    collapsedNmods: ArrayBuffer[EdgeSpec]): Unit = {

    val toRemove = new ListBuffer[Edge[String]]
    var shouldRemove = false
    val preps = dgi.findByName("mwe")
    for(prep <- preps) {
      if(lemmas(prep.source) == "due" && lemmas(prep.destination) == "to") {
        // found a "due to" MWE
        for(leftDep <- dgi.findByModifier(prep.source)) {
          // found the dep from "famine" to "due"
          toRemove += leftDep
          if(leftDep.source >= 0) { // && leftDep.relation.contains("mod")) { // we may other labels here, e.g.: xcomp
            val source = leftDep.source
            
            for(rightDep <- dgi.findByHead(prep.source).filterNot(_.relation == "mwe")) {
              // found the dep from "due" to "drought"
              val destination = rightDep.destination
              val tag = tags(destination)
              val labelPrefix = 
                if(tag.startsWith("JJ")) "amod"
                else if(tag.startsWith("RB")) "advmod"
                else "nmod"
              val label = labelPrefix + "_due_to"
              dgi.addEdge(source, destination, label)
              val edgeSpec = (source, destination, label)
              collapsedNmods += edgeSpec
              shouldRemove = true
            }
          }
        }
      }
    }

    if(shouldRemove) remove(toRemove, dgi)
  }

  /**
    * Creates mwe dependencies between clear MWEs such as "due to"
    *
    * @param sentence lemmas and POS tags must be present
    * @param dgi
    */
  def collapseMWEs(
    lemmas: Seq[String],
    tags: Seq[String],
    dgi:DirectedGraphIndex[String]): Unit = {

    val size = lemmas.length
    val toRemove = new ListBuffer[Edge[String]]
    var shouldRemove = true
    
    for(i <- 0 until size - 1) {
      if(lemmas(i) == "due" && lemmas(i + 1) == "to" && tags(i) == "IN") {
        val toHeads = dgi.findByModifier(i + 1)
        var found = false
        for(toHead <- toHeads if ! found) {
          if(toHead.source == i && toHead.relation == "mwe") {
            found = true
          }
        }
        if(! found) {
          toRemove ++= toHeads
          dgi.addEdge(i, i + 1, "mwe")
        }
      }
    }

    if(shouldRemove) remove(toRemove, dgi)
  }

  def findMultiWord(first: String, firstPos: Int, words: Seq[String], dgi:DirectedGraphIndex[String]): String = {
    val buffer = new StringBuilder
    buffer.append(first)

    var head = firstPos
    var done = false
    while(! done) {
      val mods = dgi.findByHeadAndName(head, "mwe")
      if(mods.isEmpty) {
        done = true
      } else {
        val word = words(mods.head.destination).toLowerCase()
        buffer.append("_")
        buffer.append(word)
        head = mods.head.destination
      }
    }

    buffer.toString()
  }

  /**
    * Pushes subjects inside xcomp clauses (works for both SD and UD)
    * Mary wants to buy a book => nsubj from 3 to 0
    * @param dgi The directed graph of collapsed dependencies at this stage
    */
  def raiseSubjects(dgi:DirectedGraphIndex[String]): Unit = {
    val subjects = dgi.findByName("nsubj")
    for(se <- subjects) {
      for(xcomp <- dgi.findByName("xcomp").filter(_.source == se.source)) {
        dgi.addEdge(xcomp.destination, se.destination, "nsubj")
      }
    }
  }

  /**
    * Propagates subjects/objects between conjoined verbs (works for both SD and UD)
    * The store buys and sells cameras => nsubj from 2 to 1 and from 4 to 1; dobj from 2 to 5 and from 4 to 5
    * @param sentence The sentence to operate on
    * @param dgi The directed graph of collapsed dependencies at this stage
    */
  def propagateSubjectsAndObjectsInConjVerbs(tags: Seq[String], dgi:DirectedGraphIndex[String], universal:Boolean): Unit = {
    val conjs = dgi.findByName("conj").sortBy(_.source)
    for(conj <- conjs) {
      val left = math.min(conj.source, conj.destination)
      val right = math.max(conj.source, conj.destination)
      if(tags(left).startsWith("VB") && tags(right).startsWith("VB")) { // two verbs

        // add the subject of the left verb to the right, if the right doesn't have a subject already
        for(label <- List("nsubj", "nsubjpass", "agent")) {
          val leftSubjs = dgi.findByHeadAndName(left, label)
          val rightSubjs = dgi.findByHeadAndName(right, label)
          if (leftSubjs.nonEmpty && rightSubjs.isEmpty) {
            for (s <- leftSubjs) {
              dgi.addEdge(right, s.destination, label)
            }
          }
        }

        // add the subject of the right verb to the left, if the left doesn't have a subject already
        for(label <- List("nsubj", "nsubjpass", "agent")) {
          val rightSubjs = dgi.findByHeadAndName(right, label)
          val leftSubjs = dgi.findByHeadAndName(left, label)
          if (rightSubjs.nonEmpty && leftSubjs.isEmpty) {
            for (s <- rightSubjs) {
              dgi.addEdge(left, s.destination, label)
            }
          }
        }

        // add the dobj of the right verb to the left, if the left doesn't have a dobj already
        for(label <- List("dobj")) {
          val leftObjs = dgi.findByHeadAndName(left, label)
          val rightObjs = dgi.findByHeadAndName(right, label)
          if (leftObjs.isEmpty && rightObjs.nonEmpty) {
            for (o <- rightObjs) {
              dgi.addEdge(left, o.destination, label)
            }
          }
        }

        // add the dobj of the left verb to the right, if the right doesn't have a dobj already
        for(label <- List("dobj")) {
          val leftObjs = dgi.findByHeadAndName(left, label)
          val rightObjs = dgi.findByHeadAndName(right, label)
          if (rightObjs.isEmpty && leftObjs.nonEmpty) {
            for (o <- leftObjs) {
              dgi.addEdge(right, o.destination, label)
            }
          }
        }

        // add the prep_x/nmod_x of the right verb to the left, if the left doesn't have a similar prep_x/nmod_x already
        val rightPreps =
          if(universal) dgi.findByHeadAndPattern(right, "^nmod_*".r)
          else dgi.findByHeadAndPattern(right, "^prep_*".r)
        for(rightPrep <- rightPreps) {
          val leftPreps = dgi.findByHeadAndName(left, rightPrep.relation)
          if(leftPreps.isEmpty) {
            dgi.addEdge(left, rightPrep.destination, rightPrep.relation)
          }
        }

        // add the prep_x/nmod_x of the left verb to the right, if the right doesn't have a similar prep_x/nmod_x already
        val leftPreps =
          if(universal) dgi.findByHeadAndPattern(left, "^nmod_*".r)
          else dgi.findByHeadAndPattern(left, "^prep_*".r)
        for(leftPrep <- leftPreps) {
          val rightPreps = dgi.findByHeadAndName(right, leftPrep.relation)
          if(rightPreps.isEmpty) {
            dgi.addEdge(right, leftPrep.destination, leftPrep.relation)
          }
        }
      }
    }
  }

  /**
    * Propagates conjoined subjects and objects to same verb (works for SD and UD)
    * Paul and Mary are reading a book => nsubj from 4 to 0 and from 4 to 2
    * John is reading a book and a newspaper => dobj from 2 to 4 and from 2 to 7
    * @param sentence The sentence to operate on
    * @param dgi The directed graph of collapsed dependencies at this stage
    */
  def propagateConjSubjectsAndObjects(tags: Seq[String], dgi:DirectedGraphIndex[String]): Unit = {
    val conjs = dgi.findByName("conj").sortBy(_.source)
    for(conj <- conjs) {
      val left = math.min(conj.source, conj.destination)
      val right = math.max(conj.source, conj.destination)

      if((tags(left).startsWith("NN") || tags(left).startsWith("PR")) &&
         (tags(right).startsWith("NN") || tags(right).startsWith("NN"))) {

        for(label <- List("nsubj", "nsubjpass", "agent", "dobj")) {
          val leftDeps = dgi.findByModifierAndName(left, label)
          val rightDeps = dgi.findByModifierAndName(right, label)

          if(leftDeps.nonEmpty && rightDeps.isEmpty) {
            for(s <- leftDeps) {
              dgi.addEdge(s.source, right, label)
            }
          }
          if(rightDeps.nonEmpty && leftDeps.isEmpty) {
            for(s <- rightDeps) {
              dgi.addEdge(s.source, left, label)
            }
          }
        }

      }
    }
  }

  /**
    * Propagates subjects and objects inside relative clauses (works for both SD and UD)
    * The boy who lived => nsubj from 3 to 1
    * the book, which I read, was great. => dobj from 5 to 1
    * @param sentence The sentence to operate on
    * @param dgi The directed graph of collapsed dependencies at this stage
    */
  def pushSubjectsObjectsInsideRelativeClauses(tags: Seq[String], dgi:DirectedGraphIndex[String], universal:Boolean): Unit = {
    val rels =
      if(universal) dgi.findByName("acl:relcl")
      else dgi.findByName("rcmod")

    for(rel <- rels) {
      val head = rel.source
      val relVerb = rel.destination
      if((tags(head).startsWith("NN") || tags(head).startsWith("PR")) &&
         tags(relVerb).startsWith("VB")) {
        var done = false
        for(label <- List("nsubj", "nsubjpass", "dobj", "agent") if ! done) {
          val deps = dgi.findByHeadAndName(relVerb, label)
          for(dep <- deps if ! done) {
            if((tags(dep.destination).startsWith("WP") || tags(dep.destination).startsWith("WD")) &&
                head < dep.destination && dep.destination < relVerb) {
              dgi.addEdge(relVerb, head, label)
              dgi.removeEdge(dep.source, dep.destination, dep.relation)
              done = true
            }
          }
        }
      }
    }
  }
}
