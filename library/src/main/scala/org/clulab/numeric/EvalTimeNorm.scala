package org.clulab.numeric

import org.clulab.numeric.mentions.Norm
import org.clulab.processors.Processor
import org.clulab.processors.clu.BalaurProcessor

import java.nio.charset.StandardCharsets
import scala.io.Source
import scala.util.Using

object EvalTimeNorm {

  def runEval(
    proc: Processor,
    timeNormEvalDir: String,
    testFile: String,
    ner: NumericEntityRecognizer
  ): Double = {
    val goldStream = getClass.getResourceAsStream(s"$timeNormEvalDir/$testFile")
    val goldLines = Source.fromInputStream(goldStream).getLines()
    // Build a Map with the gold time expressions.
    // The key is the document name. The value is a Seq with the time expressions in the document
    val goldTimex = (for ((goldLine, goldIdx) <- goldLines.toSeq.zipWithIndex) yield {
      goldLine.split(",").map(_.trim) match {
        case Array(docId, startSpan, endSpan, startIntervalStr) =>
          (docId, (startSpan, endSpan, startIntervalStr))
      }
    }).groupBy(t => t._1).map { case (k, v) => k -> v.map(_._2) }
    // For each docId in goldTimex keys get parse the document and get the number of
    // gold time expressions, predicted time expressions and the intersection
    val valuesPerDocument = for (docId <- goldTimex.keys.toSeq.sorted) yield {
      val gold = goldTimex(docId).toSet
      val resource = s"$timeNormEvalDir/$docId/$docId"
      val docStream = getClass.getResourceAsStream(resource)
      val docText = Using.resource(Source.fromInputStream(docStream)(StandardCharsets.UTF_8)) { source =>
        // This ensures that line endings are LF.  FileUtils.getTextFromResource() will not.
        source.getLines().mkString("\n")
      }
      val doc = proc.annotate(docText)
      val mentions = ner.extractFrom(doc)
      // The following line does not change the document.
      // NumericUtils.mkLabelsAndNorms(doc, mentions)
      val prediction = mentions.collect{
        case m: Norm if m.neLabel.equals("DATE") || m.neLabel.equals("DATE-RANGE") =>
          (m.startOffset.toString, m.endOffset.toString, m.neNorm)
      }.toSet
      val intersection = prediction.intersect(gold)
      (prediction.size, gold.size, intersection.size)
    }
    // Calculate the overall performance
    val totalValues = valuesPerDocument.reduce((x, y) => (x._1 + y._1, x._2 + y._2, x._3 + y._3))
    val precision = totalValues._3.toFloat / totalValues._1
    val recall = totalValues._3.toFloat / totalValues._2
    val fscore = 2 * precision * recall / (precision + recall)
    printf("Precision: %.3f\n", precision)
    printf("Recall: %.3f\n", recall)
    printf("F1 score: %.3f\n", fscore)
    fscore
  }

  def run(proc: BalaurProcessor, timeNormEvalDir: String, testFile: String): Double = {
    val ner = proc.numericEntityRecognizerOpt.get

    runEval(proc, timeNormEvalDir, testFile, ner)
  }
}
