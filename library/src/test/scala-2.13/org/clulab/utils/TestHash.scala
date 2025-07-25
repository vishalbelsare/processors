package org.clulab.utils

import org.clulab.odin.{CrossSentenceMention, EventMention, RelationMention, TextBoundMention, _}
import org.clulab.odin.serialization.json.MentionOps
import org.clulab.processors.clu.BalaurProcessor
import org.clulab.sequences.LexiconNER
import org.clulab.struct.{DirectedGraph, Edge}

class TestHash extends Test {
  val customLexiconNer = {
    val kbsAndCaseInsensitiveMatchings: Seq[(String, Boolean)] = Seq(
      ("org/clulab/odinstarter/FOOD.tsv", true)
    )
    val kbs = kbsAndCaseInsensitiveMatchings.map(_._1)
    val caseInsensitiveMatchings = kbsAndCaseInsensitiveMatchings.map(_._2)

    LexiconNER(kbs, caseInsensitiveMatchings, None)
  }
  val processor = new BalaurProcessor(lexiconNerOpt = Some(customLexiconNer))
  val extractorEngine = {
    val rules = FileUtils.getTextFromResource("/org/clulab/odinstarter/main.yml")

    ExtractorEngine(rules)
  }
  val document = processor.annotate("John eats cake.")
  val mentions = extractorEngine.extractFrom(document).sortBy(_.arguments.size)
  val sortedMentions = mentions.sortBy { mention => (mention.startOffset, mention.endOffset) }
  val eventMention = sortedMentions.find(_.isInstanceOf[EventMention]).get.asInstanceOf[EventMention]
  val otherMentions = sortedMentions.filterNot(_.eq(eventMention))
  val relationMention = eventMention.toRelationMention
  val crossSentenceMention = newCrossSentenceMention(eventMention, otherMentions.head, otherMentions.last)
  val allMentions = sortedMentions :+ relationMention :+ crossSentenceMention

  behavior of "Hash"

  it should "compute the expected equivalence hash for a Document" in {
    val expectedHash = -1029127286
//    val expectedHash = 1145238653
    val actualHash = document.equivalenceHash

    actualHash should be (expectedHash)
  }

  def getEquivalenceHash(mention: Mention): Int = MentionOps(mention).equivalenceHash

  def newCrossSentenceMention(mention: EventMention, anchor: Mention, neighbor: Mention): CrossSentenceMention = {
    new CrossSentenceMention(
      mention.labels,
      anchor,
      neighbor,
      mention.arguments,
      mention.document,
      mention.keep,
      mention.foundBy,
      mention.attachments
    )
  }

  it should "compute the expected equivalence hashes for Mentions" in {
    val expectedHashes = Array(-674187334, 1183699787, 391766831, -495035159, -2089326276)
//    val expectedHashes = Array(1317064233, 418554464, 269168883, 1021871359, 1657321605)
    val actualHashes = allMentions.map(getEquivalenceHash)

    actualHashes should be (expectedHashes)
  }

  it should "compute the expected hashCode for Mentions" in {
    val expectedHashes = Array(1493402696, -1515246319, 205797074, -1416141606, -1294266266)
    val actualHashes = allMentions.map(_.hashCode)

    actualHashes should be(expectedHashes)
  }

  it should "compute the expected hashCode for a String" in {
    val expectedHash = 1077910243
    val actualHash = "supercalifragilisticexpialidocious".hashCode

    actualHash should be(expectedHash)
  }

  it should "compute the expected equivalence hash for a String" in {
    val expectedHash = 887441175
    val actualHash = Hash("supercalifragilisticexpialidocious")

    actualHash should be(expectedHash)
  }

  it should "compute the expected equivalence hash for a DirectedGraph" in {
    val expectedHash = 821315811
    val edge = Edge(0, 1, "relation")
    val directedGraph = DirectedGraph(List(edge))
    val actualHash = directedGraph.equivalenceHash

    actualHash should be (expectedHash)
  }
}
