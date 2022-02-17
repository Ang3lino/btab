
// package com.bbva.datioamproduct.mbmi_financial_profit_part1.data

// import com.bbva.datioamproduct.mbmidatio.common.CommonRevenuecost
import com.typesafe.scalalogging.LazyLogging
import org.apache.spark.sql.functions._
import org.apache.spark.sql.types.StringType
import org.apache.spark.sql.{DataFrame, SparkSession}

class GetDataFPSource(spark: SparkSession) extends LazyLogging{
  private lazy val common = new CommonRevenuecost(spark)

  def getAccountsData(path: String, odate: String): DataFrame = {
    val get_accounts = spark.read.parquet(path).where(col("cutoff_date") <= odate)
    val dataFrame_max = get_accounts.select("account_id", "cutoff_date").groupBy("account_id").
      agg(max("cutoff_date").alias("cutoff_date"))
    val dataFrameAccounts = get_accounts.join(dataFrame_max,
      get_accounts.col("account_id") === dataFrame_max.col("account_id") &&
        get_accounts.col("cutoff_date") === dataFrame_max.col("cutoff_date"), "inner").select(
      get_accounts.col("*")
    )
    val account = dataFrameAccounts.select(col("account_id"),col("commercial_product_type"),col("commercial_subproduct_type"),
      col("gl_branch_id")).where(col("contract_status_type") =!= "T")
    account
  }

  def getDataCatalog(path: String, odate: String): DataFrame = {
    val maxDateCatalog = spark.read.format("parquet").load(path)
      .select("cutoff_date").agg(max("cutoff_date").as("cutoff_date"))
    val date = maxDateCatalog.rdd.map(r => r(0)).collect.toList
    val maxDate = date(0).toString
    val catalog = spark.read.format("parquet").load(path)
      .select(col("cutoff_date"),
        col("sender_application_id"),col("primary_key_type"),col("bg_operation_id"),
        col("transaction_id"),col("operation_id"),col("operation_short_desc"),
        col("operation_large_desc"),col("transaction_name"),col("transaction_operation_name"),
        col("sign_type"),col("data_code_id"),col("commercial_product_type"),
        col("commercial_subproduct_type"),col("product_name")
      ).where(col("cutoff_date") === maxDate)
    catalog
  }

  def getDataB40W(path: String, odate: String,inimes:String): DataFrame = {
    val maxDate = common.getMaxDateOfTable(path,"load_date",odate)
    val b40wDf = spark.read.format("parquet").load(path).
      where(col("load_date") <= maxDate && col("load_date") >= inimes
        && col("transaction_date") >= inimes && col("transaction_date") <= odate)
    val b40wFilter = b40wDf.select(concat(col("bg_operation_id"),
      col("transaction_id")).alias("keyB40W"),col("load_date"),
      col("counterpart_id"),col("folio_contract_id"),
      col("transaction_date"),col("transaction_id"),
      col("transaction_desc"),col("currency_id"),
      col("transaction_amount")).
      where(col("transaction_amount").isNotNull and col("transaction_amount")=!=0)
    b40wFilter
  }

  def getDataSicoco(path: String, odate: String) : DataFrame = {
    val keySicoco = lit("SICCOBRS").cast(StringType)
    val maxDate = common.getMaxDateOfTable(path,"cutoff_date",odate)
    val getDataSicoco = spark.read.parquet(path).select(
      col("commission_account_id").substr(3,10).alias("commission_account_id"),
      col("card_service_type"), col("total_commission_tax_amount"),
      concat(keySicoco,lit(col("card_service_type").substr(8,2))).alias("pk_sicoco")).where(
      col("cutoff_date") === odate && col("total_commission_tax_amount") =!= 0.0 &&
        col("total_commission_tax_amount").isNotNull && col("cutoff_date") <= maxDate)
    getDataSicoco
  }

  def getDataSicoco5(path: String, odate: String) : DataFrame = {
     val getDataSicoco5 = spark.read.parquet(path).select(
        trim(col("commission_account_id")).substr(3,10).alias("commission_account_id"),
        col("card_service_type"), col("total_commission_tax_amount")).
        where(col("cutoff_date") === odate && col("total_commission_tax_amount") =!= 0.0 &&
          col("total_commission_tax_amount").isNotNull)
     getDataSicoco5
  }

  def getDataPart0(path: String) : DataFrame ={
    val path0Df = spark.read.parquet(path)
    path0Df
  }
}
