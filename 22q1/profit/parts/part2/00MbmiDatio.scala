
// package com.bbva.datioamproduct.mbmidatio.common
// CommonRevenuecost.scala
// ----------------------------------------------------------------------------
import java.text.SimpleDateFormat
import java.util.{Calendar, Date}
import org.apache.hadoop.fs.{FileSystem, Path}
import org.apache.spark.sql.functions.{col, _}
import org.apache.spark.sql.{DataFrame, SparkSession}
import org.joda.time.DateTime

class CommonRevenuecost(spark: SparkSession) {
  def getCustomerAccountBEyG(pathTableCustomerBasic: String, pathTableCustomerCatalog: String,
                             pathTableContractCustomer: String, odate: String): DataFrame = {
    val dateContractCustomer = getMaxDateOfTable(pathTableContractCustomer, "load_date", odate)
    val contract_customer_universe = spark.read.parquet(pathTableContractCustomer).select(col("customer_id"),
      concat(trim(col("product_type")),
      trim(col("account_id"))).alias("account"), col("account_register_branch_id")).
      where(col("load_date") === dateContractCustomer && col("account_intervention_type") === "T"
        && col("account_interventon_id") === "01")
    val dateCustomerBasic = getMaxDateOfTable(pathTableCustomerBasic, "load_date", odate)
    val customerBasic_universe = spark.read.parquet(pathTableCustomerBasic).select(col("customer_id"), concat(trim(col("rfc_id")),
      trim(col("homoclave_name"))).alias("rfc_id_basic")).where(col("load_date") === dateCustomerBasic).distinct
    val dateCustomerCatalog = getMaxDateOfTable(pathTableCustomerCatalog, "cutoff_date", odate)
    val bmiCustomer = spark.read.parquet(pathTableCustomerCatalog).where(col("cutoff_date") === dateCustomerCatalog)
      .select(col("customer_id")).distinct
    val customersBmi_rfcId = bmiCustomer.join(customerBasic_universe, Seq("customer_id"), "left")
    val customer_rfcId_compl = customersBmi_rfcId.join(customerBasic_universe, Seq("rfc_id_basic"), "inner")
      .select(customerBasic_universe("customer_id"), customerBasic_universe("rfc_id_basic")).distinct
    val customer_compl = customer_rfcId_compl.join(customersBmi_rfcId, Seq("customer_id", "rfc_id_basic"), "left_anti")
    val ctes_bmi_compl = customersBmi_rfcId.union(customer_compl)
    val contract_customer_beyg = ctes_bmi_compl.join(contract_customer_universe, Seq("customer_id"), "inner")
    contract_customer_beyg
  }

  def getMaxDateOfTable(pathTable: String, columnName: String, odate: String): String = {
    val c_in = spark.read.parquet(pathTable).select(columnName).where(col(columnName) <= odate).agg(max(columnName).as(columnName))
    val maxDate = c_in.rdd.map(r => r(0)).collect.toList
    maxDate(0).toString
  }

  def calculateLastDayOfMonth(year: Int, month: Int, day: Int): Date = {
    val odateDay = new DateTime().withYear(year).withMonthOfYear(month).withDayOfMonth(day).toDate
    val calendar = Calendar.getInstance
    calendar.setTime(odateDay)
    calendar.set(calendar.get(1), calendar.get(2), calendar.getActualMaximum(Calendar.DAY_OF_MONTH))
    calendar.getTime
  }

  def getDateAsString(d: Date, format: String): String = {
    val dateFormat = new SimpleDateFormat(format)
    dateFormat.format(d)
  }

  def partitionExist(spark: SparkSession, path: String, partition: String): Boolean = {
    val hadoopfs: FileSystem = FileSystem.get(spark.sparkContext.hadoopConfiguration)
    val p = new Path(path)
    val listStatus = hadoopfs.listStatus(p)
    val exist = listStatus.exists { element =>
      element.getPath.getName.contains(partition)
    }
    exist
  }
}


// package com.bbva.datioamproduct.util.validate

import cats.data.Validated.{Invalid, Valid}
import com.datio.kirby.api.exceptions.KirbyException
import com.datio.kirby.errors.SCHEMA_VALIDATION_ERROR
import com.datio.kirby.schema.validation.{SchemaReader, SchemaValidator}
import com.typesafe.config.Config
import org.apache.spark.sql.DataFrame
import org.apache.spark.sql.types.StructType

class Validate(config: Config) extends SchemaReader with SchemaValidator {
  def validate(dfToValidate: DataFrame, configLevel: String): DataFrame = {
    val schemaConf = config.getConfig(configLevel)
    val schema = readSchema(schemaConf,includeMetadata = true).getOrElse(new StructType())
    validateDF(dfToValidate, schema) match {
      case Invalid(error) =>
        throw new KirbyException(SCHEMA_VALIDATION_ERROR,
          error.map(_.toString).toList.mkString(", "))
      case Valid(dataFrame) =>
        logger.info("The validation has been finished sucessfully.")
        dataFrame
    }
  }
}


// package com.bbva.datioamproduct.util.io.output
// Writer
// import com.datio.kirby.CheckFlow
// import com.datio.kirby.config.OutputFactory
// import com.typesafe.config.Config
// import org.apache.spark.sql.DataFrame

// class Writer(config: Config) extends OutputFactory with CheckFlow{
//   def apply(df: DataFrame, outputLevelConf: String): Unit = {
//     val outputConf = config.getConfig(outputLevelConf)
//     writeDataFrame(df, readOutput(outputConf))
//   }
// }