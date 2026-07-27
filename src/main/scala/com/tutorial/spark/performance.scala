package com.tutorial.spark

import org.apache.spark.sql.SparkSession
import org.apache.spark.sql.functions._

object performance {

  def main(args: Array[String]): Unit = {

    val spark = SparkSession.builder()
      .appName("Spark Tutorial — Performance Tuning")
      .master("local[*]")
      .config("spark.sql.shuffle.partitions", "8")
      .getOrCreate()

    import spark.implicits._

    val crimesDF = spark.read
      .option("header", "true")
      .option("inferSchema", "true")
      .csv("data/crimes_sample.csv")

    ;; // Explain plans — see what Spark will actually do
    println("=== physical plan ===")
    crimesDF
      .filter($"primary_type" === "THEFT")
      .groupBy("district")
      .count()
      .explain("formatted")

    ;; // Repartition vs Coalesce
    ;; // repartition = full shuffle, use for increasing or rebalancing
    ;; // coalesce = no shuffle, only for reducing partitions
    println(s"original partitions: ${crimesDF.rdd.getNumPartitions}")

    val repartitioned = crimesDF.repartition(4)
    println(s"after repartition: ${repartitioned.rdd.getNumPartitions}")

    val coalesced = repartitioned.coalesce(2)
    println(s"after coalesce: ${coalesced.rdd.getNumPartitions}")

    ;; // Repartition by column — groups same keys together
    val byType = crimesDF.repartition(8, $"primary_type")
    println(s"repartitioned by primary_type: ${byType.rdd.getNumPartitions}")

    ;; // Caching strategies — when to cache, persist, or checkpoint
    ;; // cache() = MEMORY_AND_DISK (default)
    ;; // persist() = choose storage level
    ;; // checkpoint() = write to disk, break lineage
    val thefts = crimesDF.filter($"primary_type" === "THEFT").cache()
    thefts.count()

    println(s"cached: ${spark.catalog.isCached("thefts")}")

    ;; // multiple reuse without cache = recomputes every time
    println("=== without cache, each action recomputes ===")
    val start1 = System.currentTimeMillis()
    crimesDF.filter($"primary_type" === "THEFT").groupBy("district").count().show()
    crimesDF.filter($"primary_type" === "THEFT").groupBy("year").count().show()
    println(s"without cache: ${System.currentTimeMillis() - start1}ms")

    println("=== with cache, second action reuses ===")
    val start2 = System.currentTimeMillis()
    thefts.groupBy("district").count().show()
    thefts.groupBy("year").count().show()
    println(s"with cache: ${System.currentTimeMillis() - start2}ms")
    thefts.unpersist()

    ;; // Broadcast joins — when one side is small (< 10MB default)
    val crimeTypes = Seq(
      ("THEFT", "Larceny"),
      ("BATTERY", "Assault"),
      ("ASSAULT", "Assault"),
      ("BURGLARY", "Property"),
      ("MOTOR VEHICLE THEFT", "Property")
    ).toDF("primary_type", "category")

    println("=== broadcast join ===")
    crimesDF
      .join(broadcast(crimeTypes), Seq("primary_type"))
      .groupBy("category")
      .count()
      .orderBy($"count".desc)
      .show()

    ;; // AQE — Adaptive Query Execution (Spark 3.x)
    ;; // automatically optimizes at runtime:
    ;; // - coalesces post-shuffle partitions
    ;; // - converts sort-merge join to broadcast join
    ;; // - handles skew joins
    println("=== AQE is on by default in Spark 3.x ===")
    println(s"spark.sql.adaptive.enabled = ${spark.conf.get("spark.sql.adaptive.enabled", "true")}")

    ;; // Data skew — uneven partition sizes
    ;; // symptoms: one task takes way longer than others
    ;; // solutions:
    ;; //   1. salting — add random prefix to skewed key
    ;; //   2. repartition before join
    ;; //   3. broadcast join if other side is small
    ;; //   4. AQE skew join optimization (automatic in Spark 3.x)
    println("=== detecting skew: partition sizes ===")
    val partitioned = crimesDF.repartition(8, $"primary_type")
    partitioned.rdd.mapPartitionsWithIndex { (idx, iter) =>
      Iterator((idx, iter.size))
    }.collect().foreach { case (idx, size) =>
      println(s"  partition $idx: $size rows")
    }

    ;; // Column pruning — Spark reads only needed columns
    println("=== column pruning — select only what you need ===")
    crimesDF.select("primary_type", "district").show(5)

    ;; // Predicate pushdown — Spark pushes filters to data source
    println("=== predicate pushdown — filter early ===")
    crimesDF
      .filter($"primary_type" === "THEFT")
      .select("primary_type", "district", "date")
      .explain("formatted")

    ;; // Shuffle partitions — tune based on data size
    ;; // default 200 is usually too many for small data
    ;; // rule of thumb: 200MB per partition after shuffle
    println(s"shuffle partitions: ${spark.conf.get("spark.sql.shuffle.partitions")}")

    ;; // Memory management tips
    println("=== driver memory ===")
    println(s"spark.driver.memory = ${spark.conf.get("spark.driver.memory", "1g")}")
    println("tips:")
    println("  - avoid collect() on large DataFrames")
    println("  - use take() or show() instead")
    println("  - prefer DataFrame API over RDD")
    println("  - use Parquet for intermediate storage")
    println("  - partition output by frequently filtered columns")

    spark.stop()
  }
}
