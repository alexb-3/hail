package org.broadinstitute.hail.io

import org.apache.hadoop.io.LongWritable
import org.apache.hadoop.mapred._
import org.broadinstitute.hail.variant._
import scala.collection.mutable.ArrayBuffer


abstract class IndexedBinaryInputFormat[K] extends FileInputFormat[LongWritable, VariantRecord[K]] {

  def getRecordReader(split: InputSplit, job: JobConf, reporter: Reporter): RecordReader[LongWritable,
    VariantRecord[K]]
}
