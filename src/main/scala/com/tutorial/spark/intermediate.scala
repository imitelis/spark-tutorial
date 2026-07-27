package com.tutorial.spark

import org.apache.spark.sql.SparkSession
import org.apache.spark.sql.functions._
import org.apache.spark.sql.expressions.Window

object intermediate {

  def main(args: Array[String]): Unit = {

    val spark = SparkSession.builder()
      .appName("Spark Tutorial — Intermediate")
      .master("local[*]")
      .getOrCreate()

    import spark.implicits._

    ;; // Joins — combining DataFrames
    val employees = Seq(
      (1, "Alice", 101),
      (2, "Bob", 102),
      (3, "Charlie", 101),
      (4, "Diana", 103)
    ).toDF("emp_id", "name", "dept_id")

    val departments = Seq(
      (101, "Engineering"),
      (102, "Marketing"),
      (104, "Sales")
    ).toDF("dept_id", "dept_name")

    ;; // inner join — only matching rows
    println("=== inner join ===")
    employees.join(departments, employees("dept_id") === departments("dept_id"), "inner")
      .drop(departments("dept_id"))
      .show()

    ;; // left join — all employees, departments null if no match
    println("=== left join ===")
    employees.join(departments, Seq("dept_id"), "left")
      .show()

    ;; // right join — all departments, employees null if no match
    println("=== right join ===")
    employees.join(departments, Seq("dept_id"), "right")
      .show()

    ;; // full outer join — everything
    println("=== full outer join ===")
    employees.join(departments, Seq("dept_id"), "full")
      .show()

    ;; // left semi — rows from left that have a match (no right columns)
    println("=== left semi ===")
    employees.join(departments, Seq("dept_id"), "left_semi")
      .show()

    ;; // left anti — rows from left that have NO match
    println("=== left anti ===")
    employees.join(departments, Seq("dept_id"), "left_anti")
      .show()


    ;; // Window functions — calculations across row groups
    val sales = Seq(
      ("Alice", "Q1", 100), ("Alice", "Q2", 150), ("Alice", "Q3", 120),
      ("Bob", "Q1", 200), ("Bob", "Q2", 180), ("Bob", "Q3", 220),
      ("Charlie", "Q1", 90), ("Charlie", "Q2", 110), ("Charlie", "Q3", 95)
    ).toDF("salesperson", "quarter", "revenue")

    val bySalesperson = Window.partitionBy("salesperson").orderBy("quarter")
    val globalWindow = Window.orderBy("quarter")

    ;; // row_number, rank, dense_rank
    println("=== ranking within each salesperson ===")
    sales
      .withColumn("row_num", row_number().over(bySalesperson))
      .withColumn("rank", rank().over(bySalesperson))
      .withColumn("dense_rank", dense_rank().over(bySalesperson))
      .show()

    ;; // lag and lead — previous and next values
    println("=== lag/lead quarter-over-quarter ===")
    sales
      .withColumn("prev_revenue", lag("revenue", 1).over(bySalesperson))
      .withColumn("next_revenue", lead("revenue", 1).over(bySalesperson))
      .withColumn("change", $"revenue" - $"prev_revenue")
      .show()

    ;; // running total
    println("=== running total across quarters ===")
    sales
      .withColumn("running_total", sum("revenue").over(bySalesperson))
      .show()

    ;; // percent of total
    println("=== percent of total revenue ===")
    sales
      .withColumn("pct_of_total", round($"revenue" / sum("revenue").over(globalWindow) * 100, 1))
      .show()


    ;; // Null handling
    val dirty = Seq(
      ("Alice", Some(100), Some("A")),
      ("Bob", None, Some("B")),
      ("Charlie", Some(200), None),
      ("Diana", None, None),
      ("Eve", Some(150), Some("A"))
    ).toDF("name", "score", "grade")

    println("=== raw data with nulls ===")
    dirty.show()

    ;; // drop rows with any null
    println("=== na.drop (any null) ===")
    dirty.na.drop("any").show()

    ;; // drop rows where score is null
    println("=== na.drop on specific column ===")
    dirty.na.drop("any", Seq("score")).show()

    ;; // fill nulls with default values
    println("=== na.fill ===")
    dirty.na.fill(0, Seq("score")).na.fill("unknown", Seq("grade")).show()

    ;; // when/otherwise — conditional logic
    println("=== when/otherwise ===")
    dirty
      .withColumn("score_label",
        when($"score".isNull, "missing")
          .when($"score" >= 150, "high")
          .otherwise("normal")
      )
      .show()

    ;; // coalesce — first non-null value
    println("=== coalesce ===")
    dirty.withColumn("score_or_default", coalesce($"score", lit(0))).show()


    ;; // UDFs — user-defined functions
    ;; // typed UDF
    val categorize = udf((score: Int) => score match {
      case s if s >= 200 => "excellent"
      case s if s >= 100 => "good"
      case _             => "needs improvement"
    })

    println("=== typed UDF ===")
    dirty
      .withColumn("category", categorize(coalesce($"score", lit(0))))
      .show()

    ;; // untyped UDF
    val upperUDF = udf((s: String) => s.toUpperCase)
    println("=== untyped UDF ===")
    dirty.withColumn("name_upper", upperUDF($"name")).show()


    ;; // Pivots & advanced aggregation
    println("=== pivot: revenue by quarter per salesperson ===")
    sales.groupBy("salesperson").pivot("quarter").sum("revenue").show()

    ;; // multiple aggregations in one pass
    println("=== multiple aggregations ===")
    sales.groupBy("salesperson").agg(
      sum("revenue").as("total"),
      avg("revenue").as("avg"),
      min("revenue").as("min"),
      max("revenue").as("max"),
      count("quarter").as("quarters")
    ).show()

    ;; // using real data — crimes sample
    println("=== Crimes dataset ===")
    val crimesDF = spark.read
      .option("header", "true")
      .option("inferSchema", "true")
      .csv("data/crimes_sample.csv")

    crimesDF.printSchema()

    ;; // top 10 crime types
    println("=== top 10 crime types ===")
    crimesDF
      .groupBy("primary_type")
      .agg(count("*").as("count"))
      .orderBy($"count".desc)
      .show(10)

    ;; // arrest rate by crime type
    println("=== arrest rate by crime type ===")
    crimesDF
      .groupBy("primary_type")
      .agg(
        count("*").as("total"),
        sum(when($"arrest", 1).otherwise(0)).as("arrests")
      )
      .withColumn("arrest_rate", round($"arrests" / $"total" * 100, 1))
      .orderBy($"arrest_rate".desc)
      .show(10)

    ;; // crimes per district with window rank
    val districtWindow = Window.partitionBy("district").orderBy($"count".desc)
    println("=== top crime per district ===")
    crimesDF
      .groupBy("district", "primary_type")
      .agg(count("*").as("count"))
      .withColumn("rank", rank().over(districtWindow))
      .filter($"rank" === 1)
      .orderBy("district")
      .show()

    spark.stop()
  }
}
