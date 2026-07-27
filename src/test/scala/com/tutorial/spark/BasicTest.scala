package com.tutorial.spark

import org.apache.spark.sql.functions._

class BasicTest extends SparkSuite {

  import spark.implicits._

  lazy val peopleDF = Seq(
    ("Alice", 25, "Engineering"),
    ("Bob", 30, "Marketing"),
    ("Charlie", 35, "Engineering"),
    ("Diana", 28, "Marketing"),
    ("Eve", 32, "Engineering")
  ).toDF("name", "age", "department")

  test("toDF creates correct schema") {
    assertEquals(peopleDF.columnNames, Array("name", "age", "department"))
    assertEquals(peopleDF.rowCount, 5L)
  }

  test("filter returns matching rows") {
    val engineers = peopleDF.filter($"department" === "Engineering")
    assertEquals(engineers.rowCount, 3L)
    assertEquals(engineers.headValues("name"), Array("Alice", "Charlie", "Eve"))
  }

  test("filter with multiple conditions") {
    val result = peopleDF
      .filter($"department" === "Engineering")
      .filter($"age" > 28)

    assertEquals(result.rowCount, 2L)
    assertEquals(result.headValues("name"), Array("Charlie", "Eve"))
  }

  test("select picks correct columns") {
    val result = peopleDF.select("name", "age")
    assertEquals(result.columnNames, Array("name", "age"))
    assertEquals(result.rowCount, 5L)
  }

  test("withColumn adds new column") {
    val result = peopleDF.withColumn("dog_years", $"age" * 7)
    assert(result.columnNames.contains("dog_years"))
    assertEquals(result.select("name", "dog_years").collect().head.get(1), 175)
  }

  test("groupBy and agg compute correctly") {
    val result = peopleDF
      .groupBy("department")
      .agg(avg("age").as("avg_age"))
      .orderBy("department")

    val avgAges = result.headValues("avg_age").map(_.asInstanceOf[Double])
    assertEquals(avgAges(0), 30.666666666666668, 0.001) // Engineering
    assertEquals(avgAges(1), 29.0, 0.001) // Marketing
  }

  test("orderBy sorts correctly") {
    val result = peopleDF.orderBy($"age".desc)
    assertEquals(result.headValues("name").head, "Charlie")
    assertEquals(result.headValues("name").last, "Alice")
  }

  test("count returns correct number") {
    assertEquals(peopleDF.count(), 5L)
  }

  test("empty DataFrame") {
    val empty = spark.emptyDataFrame
    assertEquals(empty.rowCount, 0L)
    assertEquals(empty.columnNames.length, 0)
  }

  test("DataFrame with null values") {
    val withNulls = Seq(
      ("Alice", Some(25)),
      ("Bob", None),
      ("Charlie", Some(35))
    ).toDF("name", "age")

    val result = withNulls.filter($"age".isNotNull)
    assertEquals(result.rowCount, 2L)
  }
}
