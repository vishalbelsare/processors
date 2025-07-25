package org.clulab.sequences

import org.clulab.learning._
import org.clulab.processors.{Document, Sentence}
import org.clulab.scala.WrappedArray._
import org.clulab.scala.WrappedArrayBuffer._
import org.clulab.sequences.SequenceTaggerLogger._
import org.clulab.struct.Counter
import org.clulab.utils.SeqUtils

import java.io._
import scala.collection.mutable.ArrayBuffer
import scala.reflect.ClassTag
import scala.util.Using

/**
  * Sequence tagger using a maximum entropy Markov model (MEMM)
  * User: mihais
  * Date: 8/26/17
  */
abstract class MEMMSequenceTagger[L: ClassTag, F: ClassTag](var order:Int = 1, var leftToRight:Boolean = true) extends SequenceTagger[L, F] {
  var model:Option[Classifier[L, F]] = None

  private def mkDataset: Dataset[L, F] = new RVFDataset[L, F]()
  private def mkDatum(label:L, features:Counter[F]): Datum[L, F] = new RVFDatum[L, F](label, features)
  private def mkClassifier: Classifier[L, F] = new L1LogisticRegressionClassifier[L, F]()

  override def train(docs:Iterator[Document]): Unit = {
    val dataset = mkDataset

    logger.debug(s"Generating features using order $order...")
    var sentCount = 0
    for(doc <- docs; origSentence <- doc.sentences) {
      // labels and features for one sentence
      val sentence = if(leftToRight) origSentence else origSentence.reverse()
      val labels =
        if(leftToRight) labelExtractor(origSentence)
        else SeqUtils.revert(labelExtractor(origSentence)).toArray

      val features = new Array[Counter[F]](sentence.size)
      for(i <- features.indices) features(i) = new Counter[F]()

      (0 until sentence.size).foreach(i => featureExtractor(features(i), sentence, i))

      //
      // add history features:
      //   concatenate the labels of the previous <order> tokens to the features
      // then store each example in the training dataset
      //
      for(i <- features.indices) {
        addHistoryFeatures(features(i), order, labels, i)
        val d = mkDatum(labels(i), features(i))
        dataset += d
      }

      sentCount += 1
      if(sentCount % 100 == 0) {
        logger.debug(s"Processed $sentCount sentences...")
      }
    }
    logger.debug("Finished processing all sentences.")

    val classifier = mkClassifier
    logger.debug("Started training the classifier...")
    classifier.train(dataset)
    model = Some(classifier)
    logger.debug("Finished training.")
  }

  override def classesOf(origSentence: Sentence):Seq[L] = {
    val sentence = if(leftToRight) origSentence else origSentence.reverse()

    val history = new ArrayBuffer[L]()
    for(i <- 0 until sentence.size) {
      val feats = new Counter[F]
      featureExtractor(feats, sentence, i)
      addHistoryFeatures(feats, order, history, i)
      val d = mkDatum(null.asInstanceOf[L], feats)
      val label = model.get.classOf(d)
      history += label
    }

    if(leftToRight) history else SeqUtils.revert(history)
  }

  override def save(file: File): Unit = {
    Using.resource(new PrintWriter(file)) { w =>
      w.println(order)
      model.get.saveTo(w)
    }
  }

  override def load(reader: BufferedReader): Unit = {
    order = reader.readLine().toInt
    val c = LiblinearClassifier.loadFrom[L, F] (reader)
    model = Some(c)
  }
}
