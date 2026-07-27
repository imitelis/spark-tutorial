package com.tutorial.spark

import org.apache.spark.sql.SparkSession
import org.apache.spark.sql.functions._

object basic {

  def main(args: Array[String]): Unit = {

    ;; // SparkSession — entry point to Spark
    val spark = SparkSession.builder()
      .appName("Spark Tutorial — Basics")
      .master("local[*]")
      .getOrCreate()

    import spark.implicits._

    ;; // Creating DataFrames from a sequence
    val people = Seq(
      ("Alice", 25, "Engineering"),
      ("Bob", 30, "Marketing"),
      ("Charlie", 35, "Engineering"),
      ("Diana", 28, "Marketing"),
      ("Eve", 32, "Engineering")
    )
    val peopleDF = people.toDF("name", "age", "department")

    peopleDF.show()
    peopleDF.printSchema()

    ;; // Transformations — lazy, nothing runs until an action
    peopleDF.select("name", "age").show()

    peopleDF
      .filter($"department" === "Engineering")
      .filter($"age" > 28)
      .show()

    peopleDF.withColumn("dog_years", $"age" * 7).show()

    peopleDF
      .groupBy("department")
      .agg(avg("age").as("avg_age"), count("*").as("headcount"))
      .show()

    peopleDF.orderBy($"age".desc).show()

    ;; // Actions — trigger execution
    println(s"Total rows: ${peopleDF.count()}")

    val names: Array[String] = peopleDF.select("name").as[String].collect()
    println(s"Names: ${names.mkString(", ")}")

    peopleDF.describe("age").show()

    ;; // Caching — persist for repeated use
    peopleDF.cache()
    peopleDF.count()

    peopleDF.groupBy("department").agg(avg("age")).show()
    peopleDF.groupBy("department").agg(max("age")).show()
    peopleDF.unpersist()

    ;; // Spark SQL
    peopleDF.createOrReplaceTempView("people")
    spark.sql(
      """
        |SELECT name, age
        |FROM people
        |WHERE department = 'Engineering'
        |ORDER BY age
      """.stripMargin
    ).show()

    ;; // Reading files — CSV, JSON, Parquet
    val crimesPath = "data/crimes_sample.csv"
    // val crimesDF = spark.read
    //   .option("header", "true")
    //   .option("inferSchema", "true")
    //   .csv(crimesPath)
    // crimesDF.show(10)

    // val jsonDF = spark.read.json("data/sample.json")
    // val parquetDF = spark.read.parquet("data/sample.parquet")

    ;; // Writing files
    // peopleDF.write.mode("overwrite").csv("output/people_csv")
    // peopleDF.write.mode("overwrite").json("output/people_json")
    // peopleDF.write.mode("overwrite").parquet("output/people_parquet")

    ;; // RDD basics — low-level API
    val rdd = spark.sparkContext.parallelize(1 to 100)
    val squares = rdd.filter(_ % 2 == 0).map(x => x * x)
    println(s"RDD sum of even squares: ${squares.sum()}")

    val rddDF = squares.toDF("even_square")
    rddDF.show(10)

    spark.stop()
  }
}
