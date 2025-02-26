package org.clulab.odin.debugger.graph

import org.clulab.odin.ExtractorEngine
import org.clulab.odin.debugger.odin.DebuggingExtractorEngine
import org.clulab.odin.debugger.{DebugTest, Inspector}
import org.clulab.odin.impl.{GraphExtractor, OdinConfig}
import org.clulab.processors.clu.CluProcessor
import org.clulab.sequences.LexiconNER
import org.clulab.utils.FileUtils

import java.io.File

class DebugTriggerPatternGraphExtractor extends DebugTest {
  OdinConfig.keepRule = true

  val baseResourceDirName = "src/test/resources"
  val baseResourceName = "org/clulab/odin/debugger/GraphExtractor/triggerPattern"
  val resourceDirName = if (!new File(baseResourceDirName).exists()) s"./debugger/$baseResourceDirName" else baseResourceDirName
  val resourceDir: File = new File(resourceDirName)

  val customLexiconNer = LexiconNER(Seq(s"$baseResourceName/FOOD.tsv"), Seq(true), Some(resourceDir))
  val processor = new CluProcessor(optionalNER = Some(customLexiconNer))
  val document = processor.annotate("John eats cake.", keepText = true)
  val sentence = document.sentences.head
  val ruleName = "people-eat-food"

  val badRules = FileUtils.getTextFromFile(new File(resourceDir, s"$baseResourceName/badMain.yml"))
  val badExtractorEngine = ExtractorEngine(badRules, ruleDir = Some(resourceDir))
  val badDebuggingExtractorEngine = DebuggingExtractorEngine(badExtractorEngine, active = true, verbose = false)
  val badMentions = badDebuggingExtractorEngine.extractFrom(document)
  val badDebuggingExtractor = badDebuggingExtractorEngine.getExtractorByName(ruleName).asInstanceOf[GraphExtractor]


  val goodRules = FileUtils.getTextFromFile(new File(resourceDir, s"$baseResourceName/goodMain.yml"))
  val goodExtractorEngine = ExtractorEngine(goodRules, ruleDir = Some(resourceDir))
  val goodDebuggingExtractorEngine = DebuggingExtractorEngine(goodExtractorEngine, active = true, verbose = false)
  val goodMentions = goodDebuggingExtractorEngine.extractFrom(document)
  val goodDebuggingExtractor = goodDebuggingExtractorEngine.getExtractorByName(ruleName).asInstanceOf[GraphExtractor]

  behavior of "debugger"

  it should "find problems with a GraphExtractor" in {
    Inspector(badDebuggingExtractorEngine)
        .inspectSentence(sentence)
        .inspectGraphExtractor(badDebuggingExtractor)
        .inspectDynamicAsHtml("../debug-dynamic-triggerPatternGraphExtractor-bad.html")
    Inspector(goodDebuggingExtractorEngine)
        .inspectSentence(sentence)
        .inspectGraphExtractor(goodDebuggingExtractor)
        .inspectDynamicAsHtml("../debug-dynamic-triggerPatternGraphExtractor-good.html")

    badMentions.length should be (2)
    goodMentions.length should be (3)
  }
}
