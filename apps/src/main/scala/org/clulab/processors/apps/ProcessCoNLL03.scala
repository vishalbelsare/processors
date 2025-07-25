package org.clulab.processors.apps

import org.clulab.processors.clu.BalaurProcessor
import org.clulab.sequences.{ColumnReader, Row}

import java.io.PrintWriter
import scala.util.Using

/**
 * Little utility that regenerates the POS tags and chunk labels for the CoNLL-03 dataset
 * The expected format is a 4-column file with POS tags on the second position, and chunk labels on the third
 * The words and gold NE labels are in the first and fourth columns, respectively. These are not changed
 */
object ProcessCoNLL03 extends App {
  val proc = new BalaurProcessor()
  val rows = ColumnReader.readColumns(args(0))
  println(s"Found ${rows.length} sentences.")

  Using.resource(new PrintWriter(args(0) + ".reparsed")) { printWriter =>
    for (row <- rows) {
      val words = row.map(e => e.get(0))
      if (row.length == 1 && words(0) == "-DOCSTART-") {
        saveSent(printWriter, row)
      }
      else {
        val doc = proc.mkDocumentFromTokens(Seq(words))
        proc.annotate(doc)
        saveSent(printWriter, row, doc.sentences(0).tags, doc.sentences(0).chunks)
      }
    }
  }

  def saveSent(pw: PrintWriter, sent: Array[Row], tags: Option[Seq[String]] = None, chunks: Option[Seq[String]] = None): Unit = {
    if (tags.isDefined) {
      assert(sent.length == tags.get.length)
      //println("Using generated POS tags")
    }
    if (chunks.isDefined) {
      assert(sent.length == chunks.get.length)
      //println("Using generated chunks")
    }

    for (i <- sent.indices) {
      val word = sent(i).get(0)
      val label = sent(i).get(3)
      val tag = if (tags.isDefined) tags.get(i) else sent(i).get(1)
      val chunk = if (chunks.isDefined) chunks.get(i) else sent(i).get(2)

      pw.println(s"$word $tag $chunk $label")
    }
    pw.println()
  }
}
