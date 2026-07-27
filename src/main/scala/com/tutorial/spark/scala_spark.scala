package com.tutorial.spark

import org.apache.spark.sql.SparkSession
import org.apache.spark.sql.functions._
import org.apache.spark.sql.expressions.ImplicitEncoders._

object scala_spark {

  case class Person(name: String, age: Int, department: String)
  case class CrimeRecord(id: Long, caseNumber: String, primaryType: String, arrest: Boolean, year: Int)

  def main(args: Array[String]): Unit = {

    val spark = SparkSession.builder()
      .appName("Spark Tutorial — Scala + Spark")
      .master("local[*]")
      .getOrCreate()

    import spark.implicits._

    ;; // Case classes — typed DataFrames
    val people = Seq(
      Person("Alice", 25, "Engineering"),
      Person("Bob", 30, "Marketing"),
      Person("Charlie", 35, "Engineering")
    )

    val peopleDS = people.toDS()
    println("=== typed Dataset from case class ===")
    peopleDS.show()

    ;; // use case class fields directly — no string column names
    val engineers = peopleDS.filter(_.department == "Engineering")
    println(s"engineers: ${engineers.collect().map(_.name).mkString(", ")}")

    ;; // map on Dataset keeps type safety
    val names = peopleDS.map(_.name.toUpperCase)
    names.show()


    ;; // spark.implicits._ explained
    ;; // 1. .toDF / .toDS — converts case classes / tuples to DataFrames
    ;; // 2. $"col" — column reference (TypedColumn)
    ;; // 3. Encoder derivation — automatic serialization for case classes
    ;; // 4. .as[T] — type cast DataFrame to Dataset[T]

    ;; // without implicits — manual encoder
    val manual = spark.createDataFrame(
      Seq(("Alice", 25).asInstanceOf[Product]).map(x => (x._1, x._2))
    ).toDF("name", "age")

    ;; // with implicits — clean syntax
    val clean = Seq(("Alice", 25)).toDF("name", "age")


    ;; // Pattern matching with DataFrames
    println("=== pattern matching ===")
    val crimeData = Seq(
      CrimeRecord(1, "JK123", "THEFT", true, 2024),
      CrimeRecord(2, "JK456", "BATTERY", false, 2024),
      CrimeRecord(3, "JK789", "ASSAULT", true, 2023)
    ).toDS()

    ;; // match on typed Dataset
    crimeData.collect().foreach {
      case CrimeRecord(_, num, "THEFT", _, _)    => println(s"theft case: $num")
      case CrimeRecord(_, num, "BATTERY", _, _)  => println(s"battery case: $num")
      case CrimeRecord(_, num, _, true, _)       => println(s"arrested: $num")
      case CrimeRecord(_, num, _, _, year)       => println(s"other $num from $year")
    }


    ;; // Collection operations on RDDs
    val sc = spark.sparkContext
    val numbers = sc.parallelize(1 to 100)

    println("=== RDD collection ops ===")
    println(s"sum: ${numbers.reduce(_ + _)}")
    println(s"fold: ${numbers.fold(0)(_ + _)}")
    println(s"aggregate: ${numbers.aggregate(0)(_ + _, _ + _)}")

    ;; // map / flatMap / filter
    val processed = numbers
      .filter(_ % 2 == 0)
      .map(_ * 2)
      .collect()
      .take(10)
    println(s"first 10 even doubled: ${processed.mkString(", ")}")

    val words = sc.parallelize(Seq("hello world", "foo bar baz"))
    val splitWords = words.flatMap(_.split(" ")).collect()
    println(s"flatMapped words: ${splitWords.mkString(", ")}")


    ;; // For comprehensions with Spark
    println("=== for comprehension ===")
    val ds = Seq(
      Person("Alice", 25, "Engineering"),
      Person("Bob", 30, "Marketing"),
      Person("Charlie", 35, "Engineering")
    ).toDS()

    val result = for {
      p <- ds if p.department == "Engineering"
    } yield s"${p.name} (${p.age})"

    result.show()

    ;; // flatMap for one-to-many
    case class Order(id: Int, items: List[String])
    val orders = Seq(
      Order(1, List("apple", "banana")),
      Order(2, List("cherry"))
    ).toDS()

    val allItems = orders.flatMap(_.items)
    allItems.show()


    ;; // Option / Either / Try — error handling patterns
    println("=== Option ===")
    val risky = Seq(Some(1), None, Some(3)).toDS()
    risky.filter(_.isDefined).map(_.get).show()

    println("=== Either ===")
    def parseAge(s: String): Either[String, Int] =
      try Right(s.toInt) catch { case e: NumberFormatException => Left(s"bad: $s") }

    val ages = Seq("25", "abc", "30").map(parseAge)
    val valid = ages.collect { case Right(a) => a }
    val errors = ages.collect { case Left(e) => e }
    println(s"valid: ${valid.mkString(", ")}")
    println(s"errors: ${errors.mkString(", ")}")

    println("=== Try via DataFrame ===")
    val raw = Seq("25", "abc", "30", "xyz").toDF("value")
    val parsed = raw.select(
      when($"value".cast("int").isNotNull, $"value".cast("int"))
        .otherwise(lit(null).cast("int"))
        .as("age")
    )
    parsed.show()


    ;; // Implicit conversions in Spark
    ;; // spark.implicits._ provides:
    ;; //   - rdd.toDS() / rdd.toDF()
    ;; //   - $"col" syntax
    ;; //   - .as[T] type casting
    ;; //   - automatic Encoder for case classes, tuples, primitives

    ;; // custom Encoder via case class (no extra work needed)
    val typedDS: Dataset[Person] = Seq(
      Person("Diana", 28, "Marketing")
    ).toDS()

    ;; // filter/map on typed Dataset — compiler checks types
    val over30 = typedDS.filter(_.age > 30)
    val names2 = typedDS.map(_.name)

    println("=== typed Dataset operations ===")
    names2.show()


    ;; // Grouping with case classes
    case class Sales(salesperson: String, quarter: String, revenue: Double)
    val sales = Seq(
      Sales("Alice", "Q1", 100.0),
      Sales("Alice", "Q2", 150.0),
      Sales("Bob", "Q1", 200.0)
    ).toDS()

    val grouped = sales.groupByKey(_.salesperson).mapGroups { (key, values) =>
      (key, values.map(_.revenue).sum)
    }
    println("=== groupByKey with case class ===")
    grouped.show()


    ;; // Product / Case class as tuple
    println("=== tuples vs case classes ===")
    val fromTuple = Seq(("Alice", 25)).toDF("name", "age")
    val fromCaseClass = Seq(Person("Alice", 25, "Engineering")).toDS()

    ;; // tuples: lose field names, positional access
    println(s"tuple: ${fromTuple.head().getString(0)}")

    ;; // case classes: named fields, type safe
    println(s"case class: ${fromCaseClass.head().name}")


    ;; // Enumeration
    object Color extends Enumeration {
      type Color = Value
      val Red, Green, Blue = Value
    }

    import Color._
    case class Item(name: String, color: Color)
    val items = Seq(Item("car", Red), Item("sky", Blue)).toDS()
    items.filter(_.color == Blue).show()


    ;; // Implicit classes — extension methods
    implicit class DataFrameHelper(val df: org.apache.spark.sql.DataFrame) {
      def describeColumns(cols: String*): Unit = {
        println(s"columns: ${cols.mkString(", ")}")
        df.select(cols.map(col): _*).describe().show()
      }
    }

    val crimes = spark.read
      .option("header", "true")
      .option("inferSchema", "true")
      .csv("data/crimes_sample.csv")

    crimes.describeColumns("primary_type", "district", "year")


    spark.stop()
  }
}
