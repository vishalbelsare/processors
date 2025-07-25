package org.clulab.processors

import org.clulab.struct.DebugBooleanHashTrie
import org.clulab.utils.Test

/**
 *
 * User: mihais
 * Date: 5/12/15
 */
class TestHashTrie extends Test {
  "the trie" should "label the text correctly with BIO labels" in {
    val trie = new DebugBooleanHashTrie("E")
    trie.add(Array("a", "a", "b"))
    trie.add(Array("a", "b", "b"))
    trie.add(Array("b", "b", "b"))
    trie.add(Array("a", "b", "b", "c"))
    trie.add(Array("a"))

    //println("TRIE:\n" + trie)

    val tokens = Seq("a", "a", "b", "d", "a", "b", "d", "b", "b", "b")
    val labels = trie.find(tokens, "O")
    //println("TOKENS: " + tokens.mkString(" "))
    //println("LABELS: " + labels.mkString(" "))

    sameLabels(Array("B-E", "I-E", "I-E", "O", "B-E", "O", "O", "B-E", "I-E", "I-E"), labels) should be (true)
  }

  "the trie" should "have 2 entries and contain 5 unique strings" in {
    val trie = new DebugBooleanHashTrie("")
    trie.add(Array("a", "a", "b"))
    trie.add(Array("a", "b"))
    trie.add(Array("c", "d", "e"))

    //println("TRIE:\n" + trie)
    trie.entriesSize should be (2)
    trie.uniqueStrings.size should be (5)
  }

  "the trie" should "sort and find the entries correctly" in {
    val trie = new DebugBooleanHashTrie("hello")
    trie.add(Array("this", "is", "a", "test"))
    trie.add(Array("this", "is", "c", "test"))
    trie.add(Array("this", "is", "b", "test"))

    val labels = trie.find(Seq("this", "is", "c", "test"), "o")

    sameLabels(Array("B-hello", "I-hello", "I-hello", "I-hello"), labels)
  }

  "the trie" should "make use of shouldStop" in {
    val trie = new DebugBooleanHashTrie("hello")
    trie.add(Array("this", "is", "a", "test"))
    trie.add(Array("this", "is", "c", "test"))
    trie.add(Array("this", "is", "d", "test"))

    val labels = trie.find(Seq("this", "is", "b", "test"), "o")

    sameLabels(Array("o", "o", "o", "o"), labels)
  }

  private def sameLabels(l1: Array[String], l2: Array[String]): Boolean =
      l1.length == l2.length && l1.indices.forall { i => l1(i) == l2(i) }
}
