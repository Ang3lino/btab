
// DataFPConstants.scala
// ----------------------------------------------------------------------------
object DataFPConstants {
    val TMP_0_FILE = "kirby.tmp.pathProfitTmp0"
    val TMP_1_FILE = "kirby.tmp.pathProfitTmp1"
    val PARTITION_DF = 200
}


// GetDataFPSource.scala
// ----------------------------------------------------------------------------
// package com.bbva.datioamproduct.mbmi_financial_profit_part2.data

// import com.bbva.datioamproduct.mbmidatio.common.CommonRevenuecost
import com.typesafe.scalalogging.LazyLogging
import org.apache.spark.sql.functions.{col, max, trim, when}
import org.apache.spark.sql.{DataFrame, SparkSession}

class GetDataFPSource(spark: SparkSession) extends LazyLogging{

  private lazy val common = new CommonRevenuecost(spark)
  private val entity_id: String = "0074"
  private val type_currency: String = "M"
  private val quote_type: String = "S"
  private val currency_id: String = "MXP"
  private val type_currency_d: String = "D"

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

  def getTCData(path: String, odate: String): DataFrame = {

    val GetDataFrameTipoCambio = spark.read.format("parquet").load(path).select(col("load_date"),
      col("exchange_currency_min_amount"), col("currency_id")).
      where(col("entity_id") === entity_id && col("exchange_currency_type") === type_currency &&
        col("currency_quote_type") === quote_type && col("load_date") === odate &&
        col("outgoing_currency_id") === currency_id)

    GetDataFrameTipoCambio
  }

  def getTCDataDaily(path: String, odate: String,inimes:String): DataFrame = {

    val getTCDataDaily = spark.read.format("parquet").load(path).select(col("load_date"),
      col("exchange_currency_min_amount"), col("currency_id")).
      where(col("entity_id") === entity_id && col("exchange_currency_type") === type_currency_d &&
        col("currency_quote_type") === quote_type && col("load_date")>= inimes && col("load_date")<= odate &&
        col("outgoing_currency_id") === currency_id)

    getTCDataDaily
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

  def getDataSit(path: String, odate: String,inimes:String,date: String) : DataFrame = {

    val getDataSit = spark.read.format("parquet").load(path).
      where(col("operation_status_type") === "CO" && col("load_date") >= inimes && col("load_date") <= date
        && col("sit_transaction_date") >= inimes && col("sit_transaction_date") <= odate).select(
      col("agreement_id"),
      col("sit_transaction_date"),
      col("operation_currency_id"),
      col("total_transaction_amount"),
      col("sender_application_id"),
      col("operation_status_type"),
      col("dispersal_account_id"))

    getDataSit
  }

  def getDataCie(path: String, odate: String, inimes: String,date: String) : DataFrame = {

    val getDataCie = spark.read.format("parquet").load(path).where(
      col("srce_appl_operation_status_type") === "CO" && col("load_date") >= inimes && col("load_date") <= date &&
        col("transaction_date") >= inimes && col("transaction_date") <= odate).select(
      col("agreement_id"),col("transaction_date"),col("value_date"),
      col("currency_id"),col("operation_amount"),col("credit_internal_account_id"),
      col("srce_appl_operation_status_type"),col("transaction_channel_type"))
    getDataCie
  }

  def getDataCheck(path: String, odate: String, inimes: String,date: String) : DataFrame = {

    val getDataCheck = spark.read.format("parquet").load(path).where(
      col("load_date")>=inimes && col("load_date")<= date
        && col("transfer_debit_date")>=inimes && col("transfer_debit_date")<=odate
        && trim(col("check_status_id"))==="PR"
        && trim(col("cie_transaction_type")).isin("B","C","D","E","3","4","5","6")).select(
      col("transfer_debit_date"),
      when(col("contract_currency_id") > 19 && col("contract_currency_id") < 29, "USD")
        .when(col("contract_currency_id") > 69 && col("contract_currency_id") < 79, "USD")
        .otherwise("MXP").alias("currency_id"),
      col("credit_internal_account_id"),
      col("cie_transaction_type"),
      col("check_status_id"),
      when(col("return_reason_type") === "00", col("cash_payment_amount"))
        .otherwise(col("cash_payment_amount")*(-1)).alias("cash_tot_amount"),
      when(col("return_reason_type") === "00", col("dpst_file_checks_tot_amount"))
        .otherwise(col("dpst_file_checks_tot_amount")*(-1)).alias("checks_tot_amount"))
    getDataCheck
  }

  def getDataDep(path: String, odate: String, inimes: String,date: String) : DataFrame = {

    val getDataDep = spark.read.format("parquet").load(path).where(
      col("load_date")>=inimes && col("load_date")<= date
        && col("transfer_debit_date")>=inimes && col("transfer_debit_date")<=odate
        && trim(col("check_status_id"))==="PR"
        && trim(col("cie_transaction_type")).isin("B","C","D","E","3","4","5","6")).select(
      col("transfer_debit_date"),
      when(col("contract_currency_id") > 19 && col("contract_currency_id") < 29, "USD")
        .when(col("contract_currency_id") > 69 && col("contract_currency_id") < 79, "USD")
        .otherwise("MXP").alias("currency_id"),
      col("credit_internal_account_id"),
      when(col("cie_transaction_type") === "1", "3")
        .otherwise("4").alias("cie_transaction_type"),
      col("check_status_id"),
      when(col("return_reason_type") === "00", col("check_amount"))
        .otherwise(col("check_amount")*(-1)).alias("check_tot_amount"))
    getDataDep
  }

  def getDataDie(path: String, inimes: String, odate: String): DataFrame = {
    val getDataDie = spark.read.format("parquet").load(path)
      .where(col("fee_amount") =!= 0 &&
        col("transaction_status_type") === "P001" &&
        col("value_date").between(inimes,odate))
      .select(col("movement_desc"),
        col("currency_id"),
        col("fee_amount"),
        col("transaction_status_type"),
        col("value_date"),
        col("agreement_dispersal_acct_id"),
        col("channel_id"))
    getDataDie
  }

  def getDataDomEmi(path: String, odate: String, inimes: String):DataFrame = {
    val dataDomEmi = spark.read.format("parquet").load(path)
      .select(col("issuing_id"), col("source_account_id"), col("source_channel_id")
        , col("load_date"))
      .where(col("load_date").between(inimes, odate))

    val dataDomMax = dataDomEmi.select(col("issuing_id"), col("load_date"))
      .groupBy("issuing_id").agg(max("load_date").as("load_date"))

    val dataUniqueEmi = dataDomEmi.join(
      dataDomMax,
      dataDomMax("issuing_id") === dataDomEmi("issuing_id") && dataDomMax("load_date") === dataDomEmi("load_date"),
      "inner"
    ).select(dataDomEmi.col("*"))

    dataUniqueEmi
  }

  def getDataDomMov(path: String, odate: String, inimes: String): DataFrame = {
    val dataDomMov = spark.read.format("parquet").load(path)
      .filter(col("auto_payments_clct_proc_date").between(inimes,odate)
        && col("collecting_mark_type") === "RO"
        && col("debit_amount") =!= 0)
      .select(
        col("issuing_id"),
        col("debit_amount"),
        col("issued_receipt_number"),
        col("collecting_mark_type"),
        col("auto_payments_clct_proc_date"))
    dataDomMov
  }

  def getDataH2H(path: String, odate: String, inimes: String): DataFrame = {
    val dfH2H = spark.read.format("parquet").load(path)
      .filter(col("transaction_date").between(inimes,odate)
        && col("transaction_amount") =!= 0 )
      .select(
        trim(col("primary_subject_id")).as("primary_subject_id"),
        trim(col("operation_currency_type")).as("operation_currency_type"),
        col("transaction_amount"),
        trim(col("subservice_type")).as("subservice_type"),
        col("transaction_date")
      )
    dfH2H
  }

  def getDataNom(path: String, inimes: String, odate: String): DataFrame = {
    val dfNom = spark.read.format("parquet").load(path).filter(
      col("payroll_customer_deposit_date").between(inimes,odate) &&
        col("payroll_amount") =!= 0).select(
      col("commerce_account_id"),
      col("payroll_amount"),
      col("payroll_channel_id"))
    dfNom
  }

  def getDataPart1(path: String) : DataFrame ={
    val path1Df = spark.read.parquet(path)
    path1Df
  }
}

