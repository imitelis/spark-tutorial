package com.tutorial.spark

import org.apache.spark.sql.{DataFrame, SparkSession}
import munit.FunSuite

trait SparkSuite extends FunSuite {

  var spark: SparkSession = _

  override def beforeAll(): Unit = {
    super.beforeAll()
    spark = SparkSession.builder()
      .appName("test")
      .master("local[*]")
      .config("spark.ui.enabled", "false")
      .config("spark.sql.shuffle.partitions", "2")
      .getOrCreate()
    spark.sparkContext.setLogLevel("ERROR")
  }

  override def afterAll(): Unit = {
    if (spark != null) {
      spark.stop()
      spark = null
    }
    super.afterAll()
  }

  implicit class DataFrameOps(df: DataFrame) {
    def columnNames: Array[String] = df.columns
    def rowCount: Long = df.count()
    def headValues(col: String): Array[Any] =
      df.select(col).collect().map(_.get(0))
  }
}
