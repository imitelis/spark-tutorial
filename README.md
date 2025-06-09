# Spark Tutorial

Just some Spark tutorial with Scala

## Dataset

For this project we will be consuming data from [here](https://catalog.data.gov/dataset/crimes-2001-to-present/resource/31b027d7-b633-4e82-ad2e-cfa5caaf5837).

Concept	Description
RDD	Low-level distributed collection (rarely used directly now)
DataFrame	Table-like abstraction; most common API
SparkSession	Entry point to Spark functionality
Transformations	.filter(), .select(), .map(), etc.
Actions	.show(), .collect(), .count(), etc.
Caching	df.cache() for repeated use
Reading files	.read.csv, .read.json, .read.parquet