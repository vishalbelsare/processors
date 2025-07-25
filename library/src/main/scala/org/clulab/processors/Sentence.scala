package org.clulab.processors

import org.clulab.struct.{DirectedGraph, GraphMap, RelationTriple, Tree}
import org.clulab.utils.Hash

import scala.collection.mutable

/** Stores the annotations for a single sentence */
class Sentence(
  /** Raw tokens in this sentence; these MUST match the original text */
  val raw: Seq[String],
  /** Start character offsets for the raw tokens; start at 0 */
  val startOffsets: Seq[Int],
  /** End character offsets for the raw tokens; start at 0 */
  val endOffsets: Seq[Int],

  /**
    * Words produced from raw tokens, closer to what the downstream components expect
    * These MAY differ from raw tokens,
    *   e.g., Unicode characters in raw are replaced with ASCII strings, and parens are replaced with -LRB-, -RRB-, etc.
    * However, the number of raw tokens MUST always equal the number of words, so if the exact text must be recovered,
    *   please use the raw tokens with the same positions
    */
  val words: Seq[String],

  /** POS tags for words */
  val tags: Option[Seq[String]] = None,
  /** Lemmas */
  val lemmas: Option[Seq[String]] = None,
  /** NE labels */
  val entities: Option[Seq[String]] = None,
  /** Normalized values of named/numeric entities, such as dates */
  val norms: Option[Seq[String]] = None,
  /** Shallow parsing labels */
  val chunks: Option[Seq[String]] = None,
  /** Constituent tree of this sentence; includes head words */
  val syntacticTree: Option[Tree] = None,
  /** DAG of syntactic and semantic dependencies; word offsets start at 0 */
  val graphs: GraphMap.Type = GraphMap.empty,
  /** Relation triples from OpenIE */
  val relations:Option[Seq[RelationTriple]] = None
) extends Serializable {

  def size:Int = raw.length

  def indices: Range = 0 until size

  def ambivalenceHash: Int = cachedAmbivalenceHash

  protected lazy val cachedAmbivalenceHash = calculateAmbivalenceHash

  protected def calculateAmbivalenceHash: Int = Hash(
    Hash(Sentence.getClass.getName),
    Hash.ordered(raw),
    Hash.ordered(startOffsets),
    Hash.ordered(endOffsets)
  )

  /**
    * Used to compare Sentences.
    * @return a hash (Int) based on the contents of a sentence
    */
  def equivalenceHash: Int = {
    val stringCode = "org.clulab.processors.Sentence"

    def getAnnotationsHash(labelsOpt: Option[Seq[_]]): Int = labelsOpt
        .map { labels =>
          val hs = labels.map(_.hashCode)
          val result = Hash.withLast(labels.length)(
            Hash(s"$stringCode.annotations"),
            Hash.ordered(hs)
          )
          
          result
        }
        .getOrElse(None.hashCode)

    Hash(
      Hash(stringCode),
      getAnnotationsHash(Some(raw)),
      getAnnotationsHash(Some(words)),
      getAnnotationsHash(Some(startOffsets)),
      getAnnotationsHash(Some(endOffsets)),
      getAnnotationsHash(tags),
      getAnnotationsHash(lemmas),
      getAnnotationsHash(entities),
      getAnnotationsHash(norms),
      getAnnotationsHash(chunks),
      if (dependencies.nonEmpty) dependencies.get.equivalenceHash else None.hashCode
    )
  }

  /**
    * Default dependencies: first Universal enhanced, then Universal basic, then None
    *
    * @return A directed graph of dependencies if any exist, otherwise None
    */
  def dependencies: Option[DirectedGraph[String]] = graphs match {
    case collapsed if collapsed.contains(GraphMap.UNIVERSAL_ENHANCED) => collapsed.get(GraphMap.UNIVERSAL_ENHANCED)
    case basic if basic.contains(GraphMap.UNIVERSAL_BASIC) => basic.get(GraphMap.UNIVERSAL_BASIC)
    case _ => None
  }

  /** Fetches the universal basic dependencies */
  def universalBasicDependencies: Option[DirectedGraph[String]] = graphs.get(GraphMap.UNIVERSAL_BASIC)

  /** Fetches the universal enhanced dependencies */
  def universalEnhancedDependencies: Option[DirectedGraph[String]] = graphs.get(GraphMap.UNIVERSAL_ENHANCED)

  /** Fetches the Stanford basic dependencies */
  def stanfordBasicDependencies: Option[DirectedGraph[String]] = graphs.get(GraphMap.STANFORD_BASIC)

  /** Fetches the Stanford collapsed dependencies */
  def stanfordCollapsedDependencies: Option[DirectedGraph[String]] = graphs.get(GraphMap.STANFORD_COLLAPSED)

  def semanticRoles: Option[DirectedGraph[String]] = graphs.get(GraphMap.SEMANTIC_ROLES)

  def enhancedSemanticRoles: Option[DirectedGraph[String]] = graphs.get(GraphMap.ENHANCED_SEMANTIC_ROLES)

  def hybridDependencies: Option[DirectedGraph[String]] = graphs.get(GraphMap.HYBRID_DEPENDENCIES)

  /**
    * Recreates the text of the sentence, preserving the original number of white spaces between tokens
    *
    * @return the text of the sentence
    */
  def getSentenceText: String =  getSentenceFragmentText(0, words.length)

  def getSentenceFragmentText(start: Int, end: Int):String = {
    // optimize the single token case
    if (end - start == 1) raw(start)
    else {
      val text = new mutable.StringBuilder()
      for(i <- start until end) {
        if(i > start) {
          // add as many white spaces as recorded between tokens
          val numberOfSpaces = math.max(1, startOffsets(i) - endOffsets(i - 1))
          for (j <- 0 until numberOfSpaces) {
            text.append(" ")
          }
        }
        text.append(raw(i))
      }
      text.toString()
    }
  }

  /** Reverses the current sentence */
  def reverse(): Sentence = {
    val reversedSentence = Sentence(
      raw.reverse,
      startOffsets.reverse,
      endOffsets.reverse,
      words.reverse,
      tags.map(_.reverse),
      lemmas.map(_.reverse),
      entities.map(_.reverse),
      norms.map(_.reverse),
      chunks.map(_.reverse),
      // TODO: revert syntacticTree and graphs!
      syntacticTree,
      graphs,
      relations
    )

    reversedSentence
  }

  def copy(
    raw: Seq[String] = raw,
    startOffsets: Seq[Int] = startOffsets,
    endOffsets: Seq[Int] = endOffsets,
    words: Seq[String] = words,

    tags: Option[Seq[String]] = tags,
    lemmas: Option[Seq[String]] = lemmas,
    entities: Option[Seq[String]] = entities,
    norms: Option[Seq[String]] = norms,
    chunks: Option[Seq[String]] = chunks,
    syntacticTree: Option[Tree] = syntacticTree,
    graphs: GraphMap.Type = graphs,
    relations: Option[Seq[RelationTriple]] = relations
  ): Sentence =
    new Sentence(
      raw, startOffsets, endOffsets, words,
      tags, lemmas, entities, norms, chunks, syntacticTree, graphs, relations
    )

  def offset(offset: Int): Sentence = {
    if (offset == 0) this
    else {
      val newStartOffsets = startOffsets.map(_ + offset)
      val newEndOffsets = endOffsets.map(_ + offset)

      copy(startOffsets = newStartOffsets, endOffsets = newEndOffsets)
    }
  }
}

object Sentence {

  def apply(
    raw: Seq[String],
    startOffsets: Seq[Int],
    endOffsets: Seq[Int]): Sentence =
    new Sentence(raw, startOffsets, endOffsets, raw) // words are identical to raw tokens (a common situation)

  def apply(
    raw: Seq[String],
    startOffsets: Seq[Int],
    endOffsets: Seq[Int],
    words: Seq[String]): Sentence =
    new Sentence(raw, startOffsets, endOffsets, words)

  def apply(
    raw: Seq[String],
    startOffsets: Seq[Int],
    endOffsets: Seq[Int],
    words: Seq[String],
    tags: Option[Seq[String]],
    lemmas: Option[Seq[String]],
    entities: Option[Seq[String]] = None,
    norms: Option[Seq[String]] = None,
    chunks: Option[Seq[String]] = None,
    tree: Option[Tree] = None,
    deps: GraphMap.Type = GraphMap.empty,
    relations: Option[Seq[RelationTriple]] = None
  ): Sentence = {
    new Sentence(
      raw, startOffsets, endOffsets, words,
      tags, lemmas, entities, norms, chunks, tree, deps, relations
    )
  }
}
