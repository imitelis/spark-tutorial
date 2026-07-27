package com.tutorial.spark

import org.apache.spark.sql.functions._
import org.apache.spark.sql.expressions.Window

class IntermediateTest extends SparkSuite {

  import spark.implicits._

  lazy val employees = Seq(
    (1, "Alice", 101),
    (2, "Bob", 102),
    (3, "Charlie", 101),
    (4, "Diana", 103)
  ).toDF("emp_id", "name", "dept_id")

  lazy val departments = Seq(
    (101, "Engineering"),
    (102, "Marketing"),
    (104, "Sales")
  ).toDF("dept_id", "dept_name")

  lazy val sales = Seq(
    ("Alice", "Q1", 100), ("Alice", "Q2", 150), ("Alice", "Q3", 120),
    ("Bob", "Q1", 200), ("Bob", "Q2", 180), ("Bob", "Q3", 220)
  ).toDF("salesperson", "quarter", "revenue")

  test("inner join matches correctly") {
    val result = employees.join(departments, Seq("dept_id"), "inner")
    assertEquals(result.rowCount, 3L) // Diana has no dept 103
    assert(result.columnNames.contains("dept_name"))
  }

  test("left join includes all left rows") {
    val result = employees.join(departments, Seq("dept_id"), "left")
    assertEquals(result.rowCount, 4L)
    val nulls = result.filter($"dept_name".isNull).rowCount
    assertEquals(nulls, 1L) // Diana
  }

  test("right join includes all right rows") {
    val result = employees.join(departments, Seq("dept_id"), "right")
    assertEquals(result.rowCount, 4L) // Sales has no employees
  }

  test("full outer join includes everything") {
    val result = employees.join(departments, Seq("dept_id"), "full")
    assertEquals(result.rowCount, 5L)
  }

  test("left semi returns only left columns") {
    val result = employees.join(departments, Seq("dept_id"), "left_semi")
    assertEquals(result.rowCount, 3L)
    assertEquals(result.columnNames.length, 3) // no dept_name
  }

  test("left anti returns unmatched left rows") {
    val result = employees.join(departments, Seq("dept_id"), "left_anti")
    assertEquals(result.rowCount, 1L)
    assertEquals(result.headValues("name").head, "Diana")
  }

  test("window rank partitions correctly") {
    val bySalesperson = Window.partitionBy("salesperson").orderBy("quarter")
    val result = sales
      .withColumn("rank", rank().over(bySalesperson))

    val aliceRanks = result.filter($"salesperson" === "Alice").headValues("rank")
    assertEquals(aliceRanks, Array(1L, 2L, 3L))
  }

  test("lag/lead compute correctly") {
    val bySalesperson = Window.partitionBy("salesperson").orderBy("quarter")
    val result = sales
      .withColumn("prev", lag("revenue", 1).over(bySalesperson))
      .filter($"salesperson" === "Alice")

    val prevValues = result.headValues("prev")
    assertEquals(prevValues(0), null) // Q1 has no prev
    assertEquals(prevValues(1), 100)  // Q2 prev = Q1 = 100
    assertEquals(prevValues(2), 150)  // Q3 prev = Q2 = 150
  }

  test("running total computes correctly") {
    val bySalesperson = Window.partitionBy("salesperson").orderBy("quarter")
    val result = sales
      .withColumn("running", sum("revenue").over(bySalesperson))
      .filter($"salesperson" === "Alice")

    val running = result.headValues("running")
    assertEquals(running, Array(100L, 250L, 370L))
  }

  test("na.drop removes rows with nulls") {
    val dirty = Seq(
      ("Alice", Some(100)),
      ("Bob", None),
      ("Charlie", Some(200))
    ).toDF("name", "score")

    assertEquals(dirty.na.drop().rowCount, 2L)
  }

  test("na.fill replaces nulls") {
    val dirty = Seq(
      ("Alice", Some(100)),
      ("Bob", None)
    ).toDF("name", "score")

    val result = dirty.na.fill(0, Seq("score"))
    val scores = result.headValues("score")
    assertEquals(scores, Array(100, 0))
  }

  test("when/otherwise creates categories") {
    val df = Seq(Some(50), None, Some(150), Some(250)).toDF("score")
    val result = df.withColumn("label",
      when($"score".isNull, "missing")
        .when($"score" >= 200, "high")
        .when($"score" >= 100, "medium")
        .otherwise("low")
    )

    assertEquals(result.headValues("label").toSeq,
      Seq("low", "missing", "medium", "high"))
  }

  test("pivot creates columns from values") {
    val result = sales.groupBy("salesperson").pivot("quarter").sum("revenue")
    assert(result.columnNames.contains("Q1"))
    assert(result.columnNames.contains("Q2"))
    assert(result.columnNames.contains("Q3"))
  }

  test("multiple aggregations") {
    val result = sales.groupBy("salesperson").agg(
      sum("revenue").as("total"),
      avg("revenue").as("avg")
    )

    val totals = result.headValues("total")
    assertEquals(totals(0), 370L) // Alice
    assertEquals(totals(1), 600L) // Bob
  }
}
