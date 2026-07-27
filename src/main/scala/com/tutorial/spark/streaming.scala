package com.tutorial.spark

import org.apache.spark.sql.SparkSession
import org.apache.spark.sql.functions._
import org.apache.spark.sql.streaming.Trigger

object streaming {

  def main(args: Array[String]): Unit = {

    val spark = SparkSession.builder()
      .appName("Spark Tutorial — Structured Streaming")
      .master("local[*]")
      .getOrCreate()

    import spark.implicits._

    ;; // Structured Streaming — process data as it arrives
    ;; // Spark treats a stream as an unbounded table

    ;; // 1. File stream — read new files as they appear
    ;; // mkdir -p data/streaming and drop JSON files into it
    println("=== file stream (JSON) ===")
    val fileStream = spark.readStream
      .format("json")
      .schema(
        spark.read.json("data/crimes_sample.csv")
          .schema  // reuse schema from batch data
      )
      .option("maxFilesPerTrigger", 1)
      .load("data/streaming/*.json")

    ;; // treat stream like a regular DataFrame
    val streamAgg = fileStream
      .filter($"primary_type".isNotNull)
      .groupBy($"primary_type")
      .agg(count("*").as("count"))

    ;; // write stream to console
    val fileQuery = streamAgg.writeStream
      .outputMode("complete")
      .format("console")
      .option("truncate", "false")
      .trigger(Trigger.ProcessingTime("5 seconds"))
      .start()

    ;; // let it run for a few seconds then stop
    ;; // fileQuery.awaitTermination(10000)
    ;; // fileQuery.stop()

    ;; // 2. Socket stream — read text from a TCP socket
    ;; // in another terminal: nc -lk 9999
    println("=== socket stream ===")
    val socketStream = spark.readStream
      .format("socket")
      .option("host", "localhost")
      .option("port", 9999)
      .load()

    val wordCounts = socketStream
      .as[String]
      .flatMap(_.split(" "))
      .filter(x => x != null && x.nonEmpty)
      .groupBy($"value")
      .agg(count("*").as("count"))

    ;; // output modes:
    ;; // append — only new rows (no aggregation allowed)
    ;; // complete — entire result table each trigger
    ;; // update — only rows that changed since last trigger
    val socketQuery = wordCounts.writeStream
      .outputMode("complete")
      .format("console")
      .option("truncate", "false")
      .start()

    ;; // socketQuery.awaitTermination(10000)
    ;; // socketQuery.stop()

    ;; // 3. Rate stream — generate synthetic data for testing
    println("=== rate stream (synthetic) ===")
    val rateStream = spark.readStream
      .format("rate")
      .option("rowsPerSecond", 5)
      .option("numPartitions", 1)
      .load()
      .select(
        $"timestamp".cast("string").as("time"),
        $"value"
      )

    val rateQuery = rateStream.writeStream
      .outputMode("append")
      .format("console")
      .option("truncate", "false")
      .start()

    ;; // rateQuery.awaitTermination(10000)
    ;; // rateQuery.stop()

    ;; // 4. Windowed aggregations — group by time windows
    println("=== windowed aggregation ===")
    val events = spark.readStream
      .format("rate")
      .option("rowsPerSecond", 10)
      .load()
      .withColumn("event_time", $"timestamp")
      .withColumn("window", window($"event_time", "10 seconds"))
      .groupBy($"window", $"value" % 3 as "category")
      .agg(count("*").as("count"))

    val windowQuery = events.writeStream
      .outputMode("update")
      .format("console")
      .option("truncate", "false")
      .start()

    ;; // windowQuery.awaitTermination(15000)
    ;; // windowQuery.stop()

    ;; // 5. Watermarks — handle late data
    ;; // tell Spark to discard events older than watermark
    println("=== watermark ===")
    val watermarked = spark.readStream
      .format("rate")
      .option("rowsPerSecond", 5)
      .load()
      .withColumn("event_time", $"timestamp")
      .withWatermark("event_time", "30 seconds")
      .groupBy(
        window($"event_time", "10 seconds"),
        $"value" % 3 as "category"
      )
      .agg(count("*").as("count"))

    val wmQuery = watermarked.writeStream
      .outputMode("update")
      .format("console")
      .option("truncate", "false")
      .start()

    ;; // wmQuery.awaitTermination(15000)
    ;; // wmQuery.stop()

    ;; // 6. Streaming + static join
    ;; // join a stream with a batch DataFrame
    println("=== stream-static join ===")
    val stream = spark.readStream
      .format("rate")
      .option("rowsPerSecond", 5)
      .load()
      .withColumn("category", $"value" % 3)

    val lookup = Seq((0, "low"), (1, "medium"), (2, "high")).toDF("category", "level")

    val joined = stream.join(lookup, Seq("category"), "left")
      .select($"timestamp", $"category", $"level")

    val joinQuery = joined.writeStream
      .outputMode("append")
      .format("console")
      .option("truncate", "false")
      .start()

    ;; // joinQuery.awaitTermination(10000)
    ;; // joinQuery.stop()

    ;; // 7. Stream with foreach — custom per-row processing
    println("=== foreach (custom processing) ===")
    val forEachStream = spark.readStream
      .format("rate")
      .option("rowsPerSecond", 3)
      .load()

    ;; // define a ForeachWriter (trait)
    ;; // class MyWriter extends ForeachWriter[Row] {
    ;; //   override def open(partitionId: Long, epochId: Long) = true
    ;; //   override def process(row: Row) = println(s"got: $row")
    ;; //   override def close(errorOrNull: Throwable) = ()
    ;; // }
    ;; // forEachStream.writeStream.foreach(new MyWriter()).start()

    ;; // 8. Stream to Kafka / Delta Lake (common sinks)
    ;; // .format("kafka")
    ;; //   .option("kafka.bootstrap.servers", "localhost:9092")
    ;; //   .option("topic", "output-topic")
    ;; //
    ;; // .format("delta")
    ;; //   .option("checkpointLocation", "checkpoints/")
    ;; //   .start("output/delta/")

    ;; // Management
    println("=== active streams ===")
    spark.streams.active.foreach { s =>
      println(s"  id=${s.id}, name=${s.name}, status=${s.status}")
    }

    ;; // stop all
    spark.streams.active.foreach(_.stop())
    spark.stop()
  }
}
