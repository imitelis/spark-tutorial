package com.tutorial.spark

import org.apache.spark.sql.SparkSession
import org.apache.spark.sql.functions._
import org.apache.spark.util.LongAccumulator

object advanced_api {

  def main(args: Array[String]): Unit = {

    val spark = SparkSession.builder()
      .appName("Spark Tutorial — Advanced APIs")
      .master("local[*]")
      .getOrCreate()

    import spark.implicits._

    val sc = spark.sparkContext

    ;; // Accumulators — write-once shared variables
    ;; // workers add to them, driver reads the sum
    val errorCount = sc.longAccumulator("errorCount")
    val names = sc.collectionAccumulator[String]("names")

    val data = Seq("Alice ok", "Bob error", "Charlie ok", "Diana error", "Eve ok")
    val rdd = sc.parallelize(data)

    rdd.foreach { line =>
      if (line.contains("error")) errorCount.add(1)
      if (line.startsWith("A")) names.add(line.split(" ")(0))
    }

    println(s"error count: ${errorCount.value}")
    println(s"names starting with A: ${names.value}")

    ;; // caution: accumulators inside transformations (map, filter)
    ;; // may be counted multiple times if tasks are re-executed
    ;; // only reliable inside actions (foreach, foreachPartition)


    ;; // Broadcast variables — read-only shared data
    ;; // sent once to each executor, not per task
    val lookup = Map(
      "THEFT" -> "Property",
      "BATTERY" -> "Person",
      "ASSAULT" -> "Person",
      "BURGLARY" -> "Property",
      "NARCOTICS" -> "Drug"
    )

    val broadcastLookup = sc.broadcast(lookup)

    val crimes = spark.read
      .option("header", "true")
      .option("inferSchema", "true")
      .csv("data/crimes_sample.csv")

    val withCategory = crimes.map { row =>
      val crimeType = row.getAs[String]("primary_type")
      val category = broadcastLookup.value.getOrElse(crimeType, "Other")
      (crimeType, category)
    }.toDF("primary_type", "category")

    println("=== broadcast lookup result ===")
    withCategory.groupBy("category").count().orderBy($"count".desc).show()


    ;; // Custom partitioners — control data distribution
    val pairs = Seq(("a", 1), ("b", 2), ("c", 3), ("d", 4), ("e", 5), ("f", 6))
    val pairRDD = sc.parallelize(pairs)

    ;; // HashPartitioner — default, hash-based
    val hashed = pairRDD.partitionBy(new org.apache.spark.HashPartitioner(3))
    println("=== hash partitioned ===")
    hashed.foreachPartition(iter => println(s"  partition ${iter.mkString(", ")}"))

    ;; // RangePartitioner — for ordered data
    val sorted = pairRDD.partitionBy(new org.apache.spark.RangePartitioner(3, pairRDD))
    println("=== range partitioned ===")
    sorted.foreachPartition(iter => println(s"  partition ${iter.mkString(", ")}"))


    ;; // Custom partitioner — your own logic
    class FirstCharPartitioner(partitions: Int) extends org.apache.spark.Partitioner {
      override def numPartitions: Int = partitions
      override def getPartition(key: Any): Int = {
        key.toString.charAt(0).toUpper % partitions
      }
    }

    val customPartitioned = pairRDD.partitionBy(new FirstCharPartitioner(2))
    println("=== custom partitioned (by first char) ===")
    customPartitioned.foreachPartition(iter => println(s"  partition ${iter.mkString(", ")}"))


    ;; // Map-side joins — broadcast small datasets to all executors
    ;; // useful when you have many joins with the same small table
    println("=== map-side join with broadcast ===")
    val small = Seq(("THEFT", "Larceny"), ("BATTERY", "Assault")).toDF("code", "label")
    val broadcastSmall = broadcast(small)

    crimes.join(broadcastSmall, crimes("primary_type") === small("code"), "left")
      .select("primary_type", "label")
      .distinct()
      .show(10)


    ;; // Shared variables across tasks
    ;; // accumulators + broadcast are the two types
    ;; // accumulators: write by workers, read by driver
    ;; // broadcast: written by driver, read by workers (immutable)


    ;; // Coordinated caching — cache with storage levels
    import org.apache.spark.storage.StorageLevel

    val cached = crimes.persist(StorageLevel.MEMORY_AND_DISK_SER)
    println(s"storage level: ${cached.storageLevel}")
    cached.count()
    cached.unpersist()

    ;; // checkpointing — write RDD lineage to disk
    ;; // breaks lineage, prevents stack overflow on long chains
    sc.setCheckpointDir("checkpoint/")
    val longChain = (1 to 1000).foldLeft(sc.parallelize(Seq(1))) { (rdd, _) =>
      rdd.map(_ + 1)
    }
    longChain.checkpoint()
    longChain.count()
    println(s"checkpointed chain result: ${longChain.first()}")


    ;; // Accumulator examples with DataFrame API
    val validRows = sc.longAccumulator("validRows")
    val invalidRows = sc.longAccumulator("invalidRows")

    crimes.foreach { row =>
      if (row.getAs[Any]("primary_type") != null) validRows.add(1)
      else invalidRows.add(1)
    }

    println(s"valid rows: ${validRows.value}, invalid: ${invalidRows.value}")


    ;; // Broadcast with complex objects
    case class CrimeStats(primaryType: String, avgLat: Double, avgLon: Double)

    val statsDF = crimes
      .groupBy("primary_type")
      .agg(avg("latitude").as("avg_lat"), avg("longitude").as("avg_lon"))
      .as[CrimeStats]

    val statsBroadcast = broadcast(statsDF.collect().map(s => s.primaryType -> s).toMap)

    println("=== broadcast with case class ===")
    crimes.select("primary_type").distinct().collect().foreach { row =>
      val pt = row.getString(0)
      statsBroadcast.value.get(pt).foreach { stats =>
        println(s"  $pt: lat=${stats.avgLat}, lon=${stats.avgLon}")
      }
    }


    ;; // SparkContext methods — low-level RDD operations
    println("=== SparkContext API ===")
    println(s"app name: ${sc.appName}")
    println(s"default parallelism: ${sc.defaultParallelism}")
    println(s"master: ${sc.master}")

    ;; // broadcast and accumulator are created from sc
    ;; // sc.broadcast() and sc.longAccumulator()

    spark.stop()
  }
}
