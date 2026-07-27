package com.tutorial.spark

import org.apache.spark.sql.functions._
import org.apache.spark.sql.types._

class EdgeCaseTest extends SparkSuite {

  import spark.implicits._

  test("empty DataFrame operations") {
    val empty = Seq.empty[(String, Int)].toDF("name", "age")
    assertEquals(empty.count(), 0L)
    assertEquals(empty.filter($"age" > 10).count(), 0L)
    assertEquals(empty.groupBy("name").count().count(), 0L)
  }

  test("single row DataFrame") {
    val single = Seq(("Alice", 25)).toDF("name", "age")
    assertEquals(single.count(), 1L)
    assertEquals(single.headValues("name").head, "Alice")
  }

  test("special characters in strings") {
    val df = Seq(
      ("O'Brien", 25),
      ("Alice \"The Great\"", 30),
      ("Bob\nNewline", 35),
      ("Charlie\tTab", 40)
    ).toDF("name", "age")

    assertEquals(df.rowCount, 4L)
    assert(df.headValues("name").contains("O'Brien"))
    assert(df.headValues("name").contains("Alice \"The Great\""))
  }

  test("Unicode and emoji in strings") {
    val df = Seq(
      ("café", 1),
      ("日本語", 2),
      ("🚀 rocket", 3)
    ).toDF("text", "id")

    assertEquals(df.rowCount, 3L)
    assertEquals(df.headValues("text").head, "café")
  }

  test("very large numbers") {
    val df = Seq(
      ("max", Long.MaxValue),
      ("min", Long.MinValue),
      ("zero", 0L)
    ).toDF("label", "value")

    assertEquals(df.headValues("value").head, Long.MaxValue)
    assertEquals(df.headValues("value")(1), Long.MinValue)
  }

  test("null in join key") {
    val left = Seq((Some(1), "a"), (None, "b")).toDF("id", "name")
    val right = Seq((1, "x")).toDF("id", "label")

    val result = left.join(right, Seq("id"), "left")
    assertEquals(result.rowCount, 2L)
    val nulls = result.filter($"label".isNull).rowCount
    assertEquals(nulls, 1L)
  }

  test("duplicate column names after join") {
    val a = Seq((1, "x")).toDF("id", "val")
    val b = Seq((1, "y")).toDF("id", "val")

    val result = a.join(b, Seq("id"))
    assertEquals(result.columnNames.toSet, Set("id", "val"))
  }

  test("Data type coercion") {
    val df = Seq(
      ("1", "2.5", "true"),
      ("2", "3.5", "false")
    ).toDF("str_int", "str_double", "str_bool")

    val result = df.select(
      $"str_int".cast(IntegerType).as("int"),
      $"str_double".cast(DoubleType).as("double"),
      $"str_bool".cast(BooleanType).as("bool")
    )

    assertEquals(result.headValues("int").head, 1)
    assertEquals(result.headValues("double").head, 2.5)
    assertEquals(result.headValues("bool").head, true)
  }

  test("malformed cast returns null") {
    val df = Seq("abc", "123", "xyz").toDF("text")
    val result = df.select($"text".cast(IntegerType).as("num"))

    assertEquals(result.headValues("num").head, null)
    assertEquals(result.headValues("num")(1), 123)
    assertEquals(result.headValues("num")(2), null)
  }

  test("groupBy with null keys") {
    val df = Seq(
      ("A", Some(1)),
      (null, Some(2)),
      ("A", Some(3)),
      (null, Some(4))
    ).toDF("key", "value")

    val result = df.groupBy("key").agg(sum("value").as("total"))
    assertEquals(result.rowCount, 2L)
    val nullTotal = result.filter($"key".isNull).headValues("total").head
    assertEquals(nullTotal, 6L)
  }

  test("nested column access on struct") {
    val df = Seq(
      ("Alice", ("Engineering", 5)),
      ("Bob", ("Marketing", 3))
    ).toDF("name", "dept")

    val result = df.select(
      $"name",
      $"dept._1".as("dept_name"),
      $"dept._2".as("years")
    )

    assertEquals(result.columnNames.toSet, Set("name", "dept_name", "years"))
    assertEquals(result.headValues("dept_name").head, "Engineering")
  }

  test("array and map columns") {
    val df = Seq(
      ("Alice", Array("scala", "python"), Map("x" -> 1, "y" -> 2)),
      ("Bob", Array("java"), Map("z" -> 3))
    ).toDF("name", "languages", "scores")

    assertEquals(df.headValues("languages").head.asInstanceOf[Seq[_]].length, 2)
    val scores = df.headValues("scores").head.asInstanceOf[Map[String, Int]]
    assertEquals(scores("x"), 1)
  }

  test("cross join produces cartesian product") {
    val a = Seq(1, 2, 3).toDF("x")
    val b = Seq("a", "b").toDF("y")
    val result = a.crossJoin(b)
    assertEquals(result.rowCount, 6L)
  }

  test("window function on empty partition") {
    import org.apache.spark.sql.expressions.Window
    val df = Seq.empty[(String, Int)].toDF("name", "value")
    val w = Window.orderBy("value")
    val result = df.withColumn("rank", rank().over(w))
    assertEquals(result.rowCount, 0L)
  }

  test("UDF with null input") {
    val upperUDF = udf((s: String) => Option(s).map(_.toUpperCase).getOrElse("NULL"))
    val df = Seq(Some("hello"), None, Some("world")).toDF("text")
    val result = df.withColumn("upper", upperUDF($"text"))

    assertEquals(result.headValues("upper").toSeq, Seq("HELLO", "NULL", "WORLD"))
  }

  test("timezone handling") {
    val df = Seq("2024-01-15 10:30:00").toDF("ts")
    val result = df.select(
      to_timestamp($"ts", "yyyy-MM-dd HH:mm:ss").as("timestamp"),
      to_date($"ts", "yyyy-MM-dd HH:mm:ss").as("date")
    )

    assertEquals(result.headValues("date").head.toString, "2024-01-15")
  }
}
