
import sys
from pyspark.sql.functions import *

from pyspark.context import SparkContext
from awsglue.context import GlueContext
from awsglue.dynamicframe import DynamicFrame
from awsglue.transforms import *
from awsglue.job import Job
from pyspark.sql import functions as F
from pyspark.sql.functions import udf
from datetime import datetime

# Create a Glue context
glueContext = GlueContext(SparkContext.getOrCreate())

# Create a Glue Job
job = Job(glueContext)
job.init("infrav-demo")

# Create a dynamic frame from the catalog
infrav_DyF = glueContext.create_dynamic_frame.from_catalog(database = "infrav", table_name = "vcenter")
infrav_DyF.printSchema()

# Apply schema
applymapping1 = ApplyMapping.apply(frame = infrav_DyF, mappings = [("fields.usage_average", "double", "usage_average", "double"), ("name", "string", "name", "string"), ("tags.clustername", "string", "clustername", "string"), ("tags.cpu", "string", "cpu", "string"), ("tags.dcname", "string", "dcname", "string"), ("tags.esxhostname", "string", "esxhostname", "string"),("tags.host", "string", "host", "string"), ("tags.moid", "string", "moid", "string"), ("tags.source", "string", "source", "string"), ("tags.vcenter", "string", "vcenter", "string"), ("timestamp", "int", "timestamp", "int"), ("year", "string", "year", "string"), ("month", "string", "month", "string"), ("day", "string", "day", "string"), ("hour", "string", "hour", "string")])
#Print new Schema
applymapping1.printSchema()

# Convert DyF to DF
df=applymapping1.toDF()

# Create a function that returns the desired string from a timestamp
def format_timestamp(ts):
    return datetime.fromtimestamp(ts).strftime('%Y-%m-%d %H:00:00')

# Create the UDF
format_timestamp_udf = udf(lambda x: format_timestamp(x))

# Finally, apply the function to each element of the 'timestamp' column
df  = df.withColumn('timestamp', format_timestamp_udf(df['timestamp']))


#Create a dataframe grouping by
df=df.groupBy(["name","timestamp"]).agg(format_number(F.min(df.usage_average),2).alias("min"),format_number(F.max(df.usage_average),2).alias("max"),format_number(F.avg(df.usage_average),2).alias("avg"))


df=df.withColumn("year",year(col("timestamp"))).withColumn("month",month(col("timestamp"))).withColumn("day",dayofmonth(col("timestamp"))).withColumn("hour",hour(col("timestamp")))

# Save Dataframe in S3
#applymapping1.toDF().repartition(1).write.mode('append').parquet("s3://infrav.bigdata.demo/processed", partitionBy=['year', 'month', 'day', 'hour'])

tmp_dyf = DynamicFrame.fromDF(df, glueContext, "nested")

glueContext.write_dynamic_frame.from_options(
    frame = tmp_dyf,
    connection_type = "s3",
    connection_options = {"path": "s3://infrav.bigdata.demo/agg_hour", "partitionKeys": ['year', 'month', 'day', 'hour']},
    format = "json")