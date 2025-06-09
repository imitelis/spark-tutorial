import org.apache.spark.sql.SparkSession

object Main {
  def main(args: Array[String]): Unit = {
    val spark = SparkSession.builder()
      .appName("Simple Spark App")
      .master("local[*]")  // use all local cores
      .getOrCreate()

    val data = Seq(("Alice", 25), ("Bob", 30), ("Charlie", 35))
    val df = spark.createDataFrame(data).toDF("name", "age")

    df.show()
    df.printSchema()

    spark.stop()
  }
}
