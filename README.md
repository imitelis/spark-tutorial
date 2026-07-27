# Spark Tutorial

A hands-on Spark tutorial with Scala. Covers basics through production patterns using the [Chicago Crimes Dataset](https://catalog.data.gov/dataset/crimes-2001-to-present).

## Prerequisites

- Java 11+ ([Adoptium](https://adoptium.net/))
- [sbt](https://www.scala-sbt.org/download.html)

```bash
# macOS
brew install sbt

# Ubuntu/Debian
echo "deb https://repo.scala-sbt.org/scalasbt/debian all main" | sudo tee /etc/apt/sources.list.d/sbt.list
curl -sL "https://keyserver.ubuntu.com/pks/lookup?op=get&search=0x2EE0EA64E40A89B84B2DF73499E82A75642AC823" | sudo apt-key add
sudo apt update && sudo apt install sbt
```

## Quick Start

```bash
git clone https://github.com/imitelis/spark-tutorial.git
cd spark-tutorial
sbt run          # runs the last tutorial (basic.scala by default)
sbt "runMain com.tutorial.spark.basic"     # run a specific file
sbt test         # run all tests
```

## Project Structure

```
src/
├── main/scala/com/tutorial/spark/
│   ├── basic.scala          # SparkSession, DataFrames, transformations, actions, caching, SQL, RDDs
│   ├── intermediate.scala   # Joins, window functions, null handling, UDFs, pivots
│   ├── performance.scala    # Explain plans, partitioning, broadcast joins, AQE, skew
│   ├── streaming.scala      # Structured streaming, windows, watermarks, foreach
│   ├── advanced_api.scala   # Accumulators, broadcast variables, custom partitioners
│   ├── scala_spark.scala    # Case classes, typed Datasets, implicits, pattern matching
│   └── production.scala     # Typesafe Config, logging, retry patterns
├── main/resources/
│   ├── application.conf     # App configuration
│   └── logback.xml          # Logging configuration
└── test/scala/com/tutorial/spark/
    ├── SparkSuite.scala         # Base test trait with SparkSession
    ├── BasicTest.scala          # DataFrame operations tests
    ├── IntermediateTest.scala   # Joins, windows, aggregations tests
    └── EdgeCaseTest.scala       # Nulls, special chars, type coercion tests
```

## Tutorials

### 1. Basics (`basic.scala`)
SparkSession creation, DataFrames from sequences, transformations (select, filter, withColumn, groupBy, orderBy), actions (count, collect, describe), caching, Spark SQL, reading/writing CSV/JSON/Parquet, and RDD fundamentals.

### 2. Intermediate (`intermediate.scala`)
Six join types (inner, left, right, full outer, left semi, left anti), window functions (row_number, rank, dense_rank, lag, lead, running totals), null handling (na.drop, na.fill, when/otherwise, coalesce), typed and untyped UDFs, pivots, and real analysis on the crimes dataset.

### 3. Performance (`performance.scala`)
Reading explain plans, repartition vs coalesce, caching strategies, broadcast joins, AQE (Adaptive Query Execution), data skew detection, column pruning, predicate pushdown, and shuffle partition tuning.

### 4. Streaming (`streaming.scala`)
File streams, socket streams, rate streams for testing, windowed aggregations, watermarks for late data, stream-static joins, foreach for custom processing, and Kafka/Delta Lake sink examples.

### 5. Advanced APIs (`advanced_api.scala`)
Accumulators, broadcast variables, HashPartitioner, RangePartitioner, custom partitioners, checkpointing, storage levels, and SparkContext methods.

### 6. Scala + Spark (`scala_spark.scala`)
Case classes with typed Datasets, `spark.implicits._` explained, pattern matching on DataFrames, RDD collection operations (map, flatMap, reduce, fold, aggregate), for comprehensions, Option/Either/Try for error handling, implicit classes.

### 7. Production (`production.scala`)
Typesafe Config for environment-based settings, SLF4J/Logback logging, retry with backoff, `withSpark` resource management pattern, Docker setup.

### 8. Testing (`src/test/`)
MUnit-based tests with a shared SparkSuite trait, covering DataFrame operations, joins, window functions, null handling, edge cases (empty DataFrames, Unicode, type coercion, malformed data).

## Dataset

5,000 rows from the [Chicago Crimes 2001-Present](https://catalog.data.gov/dataset/crimes-2001-to-present) dataset, stored in `data/crimes_sample.csv`. Columns include: `primary_type`, `district`, `arrest`, `year`, `latitude`, `longitude`, and more.

## Running with Docker

```bash
docker build -t spark-tutorial .
docker run spark-tutorial com.tutorial.spark.basic
```
