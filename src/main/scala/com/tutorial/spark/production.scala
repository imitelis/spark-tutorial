package com.tutorial.spark

import com.typesafe.config.ConfigFactory
import org.apache.spark.sql.SparkSession
import org.apache.spark.sql.functions._
import org.slf4j.LoggerFactory

object production {

  private val log = LoggerFactory.getLogger(getClass)

  case class AppConfig(
    sparkMaster: String,
    appName: String,
    inputPath: String,
    outputPath: String
  )

  def loadConfig(): AppConfig = {
    val config = ConfigFactory.load()
    AppConfig(
      sparkMaster = config.getString("spark.master"),
      appName = config.getString("spark.app.name"),
      inputPath = config.getString("input.crimes.path"),
      outputPath = config.getString("output.parquet.path")
    )
  }

  def createSpark(appConfig: AppConfig): SparkSession = {
    log.info(s"creating SparkSession: ${appConfig.appName} on ${appConfig.sparkMaster}")
    SparkSession.builder()
      .appName(appConfig.appName)
      .master(appConfig.sparkMaster)
      .getOrCreate()
  }

  def main(args: Array[String]): Unit = {

    ;; // Load config from application.conf
    val config = loadConfig()
    log.info(s"loaded config: $config")

    ;; // Create SparkSession with config
    implicit val spark: SparkSession = createSpark(config)
    import spark.implicits._

    log.info("SparkSession created, starting processing")

    ;; // Read data from configured path
    log.info(s"reading data from: ${config.inputPath}")
    val crimesDF = spark.read
      .option("header", "true")
      .option("inferSchema", "true")
      .csv(config.inputPath)

    log.info(s"loaded ${crimesDF.count()} rows")
    crimesDF.printSchema()

    ;; // Process with logging
    log.info("computing crime statistics")
    val stats = crimesDF
      .groupBy("primary_type")
      .agg(
        count("*").as("count"),
        sum(when($"arrest", 1).otherwise(0)).as("arrests")
      )
      .withColumn("arrest_rate", round($"arrests" / $"count" * 100, 1))
      .orderBy($"count".desc)

    log.info("showing results")
    stats.show(10)

    ;; // Write output to configured path
    log.info(s"writing output to: ${config.outputPath}")
    stats.write
      .mode("overwrite")
      .parquet(config.outputPath)

    log.info("processing complete")
    spark.stop()
  }

  ;; // Utility functions for production code
  def retry[T](attempts: Int, delay: Long = 1000)(block: => T): T = {
    var lastException: Exception = null
    for (i <- 1 to attempts) {
      try {
        return block
      } catch {
        case e: Exception =>
          lastException = e
          log.warn(s"attempt $i failed: ${e.getMessage}")
          if (i < attempts) Thread.sleep(delay)
      }
    }
    throw lastException
  }

  def withSpark(config: AppConfig)(f: SparkSession => Unit): Unit = {
    val spark = createSpark(config)
    try {
      f(spark)
    } finally {
      log.info("stopping SparkSession")
      spark.stop()
    }
  }

  ;; // Usage:
  ;; // withSpark(loadConfig()) { spark =>
  ;; //   import spark.implicits._
  ;; //   // your processing here
  ;; // }
}
