
// package com.bbva.datioamproduct.mbmi_financial_profit_part1.process

import org.apache.spark.sql.functions.{col, concat, _}
import org.apache.spark.sql.types.{DateType, DecimalType, StringType}
import org.apache.spark.sql.{DataFrame, SparkSession}

class FinancialProfitProcess(spark: SparkSession) {
  def calIniMes(odate: String): String = {
    val inimes=  odate.substring(0,8).concat("01")
    inimes
  }

  def calIniMesT(date: String): String = {
    val inimest=  date.substring(0,8).concat("01")
    inimest
  }

  def crossB40withAccount(b40wFilter:DataFrame,account:DataFrame): DataFrame= {
    val crossB40wDfAndAccount = b40wFilter.join(account,
      concat(b40wFilter.col("counterpart_id"), b40wFilter.col("folio_contract_id")) === account.col("account_id"), "inner").select(
      b40wFilter.col("load_date"), b40wFilter.col("counterpart_id"), b40wFilter.col("folio_contract_id"),
      b40wFilter.col("transaction_date"), b40wFilter.col("transaction_desc"), b40wFilter.col("keyB40W"),
      b40wFilter.col("currency_id"), b40wFilter.col("transaction_amount"), account.col("commercial_product_type"),
      account.col("commercial_subproduct_type"), account.col("gl_branch_id")
    )
    crossB40wDfAndAccount
  }

  def filterCatalog(catalogo: DataFrame): DataFrame = {
    val filteredCatalog = catalogo.where(col("data_code_id").isNotNull)
    filteredCatalog
  }

  def crossNoFinancialWithCatalogo(crossB40wDfAndAccount: DataFrame,catalogo: DataFrame): DataFrame={
    val noFinancialBEyG = crossB40wDfAndAccount.join(catalogo,
      crossB40wDfAndAccount.col("keyB40W")===
        catalogo.col("primary_key_type"), "inner" ).
      select(crossB40wDfAndAccount.col("load_date"),
        crossB40wDfAndAccount.col("counterpart_id"),
        crossB40wDfAndAccount.col("folio_contract_id"),
        crossB40wDfAndAccount.col("transaction_date"),
        crossB40wDfAndAccount.col("transaction_desc"),
        crossB40wDfAndAccount.col("currency_id"),
        crossB40wDfAndAccount.col("transaction_amount"),
        catalogo.col("commercial_product_type").alias("product_cat"),
        catalogo.col("commercial_subproduct_type").alias("subproduct_cat"),
        catalogo.col("data_code_id"),catalogo.col("sign_type"),
        catalogo.col("sender_application_id"),
        crossB40wDfAndAccount.col("commercial_product_type"),
        crossB40wDfAndAccount.col("commercial_subproduct_type"),
        crossB40wDfAndAccount.col("keyB40W"))
    noFinancialBEyG
  }

  def selectNoFinancial(noFinancialBEyG:DataFrame):DataFrame={
    val selectNoFinancial = noFinancialBEyG.select(
      concat(noFinancialBEyG.col("counterpart_id"),
        noFinancialBEyG.col("folio_contract_id")).alias("accout"),
      noFinancialBEyG.col("keyB40W"),noFinancialBEyG.col("load_date"),
      noFinancialBEyG.col("transaction_date"),
      noFinancialBEyG.col("transaction_desc"),
      noFinancialBEyG.col("currency_id"),noFinancialBEyG.col("product_cat"),
      noFinancialBEyG.col("subproduct_cat"),noFinancialBEyG.col("data_code_id"),
      noFinancialBEyG.col("sender_application_id"),
      noFinancialBEyG.col("commercial_product_type"),
      noFinancialBEyG.col("commercial_subproduct_type"),
      noFinancialBEyG.col("sign_type"),noFinancialBEyG.col("keyB40W"),
      col("transaction_amount"))
    selectNoFinancial
  }

  def groupByFinancialProfit(noFinancialR:DataFrame):DataFrame={
    val transaction_amount = when(col("sign_type")==="+" ,col("transaction_amount")*(-1)).otherwise(col("transaction_amount"))
    val noFinancialR3 = noFinancialR.select(noFinancialR.col("accout"),
      noFinancialR.col("keyB40W"),noFinancialR.col("load_date"),
      noFinancialR.col("transaction_date"),noFinancialR.col("transaction_desc"),
      noFinancialR.col("currency_id"),noFinancialR.col("product_cat"),
      noFinancialR.col("subproduct_cat"),noFinancialR.col("data_code_id"),
      noFinancialR.col("sender_application_id"),
      noFinancialR.col("commercial_product_type"),
      noFinancialR.col("commercial_subproduct_type"),
      noFinancialR.col("sign_type"),noFinancialR.col("keyB40W"),
      transaction_amount.alias("transaction_amount"))

    val groupNoFinancialRenta = noFinancialR3.groupBy(noFinancialR3.col("accout"),
      noFinancialR3.col("keyB40W"),noFinancialR3.col("load_date"),
      noFinancialR3.col("transaction_date"),noFinancialR3.col("transaction_desc"),
      noFinancialR3.col("currency_id"),noFinancialR3.col("product_cat"),
      noFinancialR3.col("subproduct_cat"),noFinancialR3.col("data_code_id"),
      noFinancialR3.col("sender_application_id"),
      noFinancialR3.col("commercial_product_type"),
      noFinancialR3.col("commercial_subproduct_type"),
      noFinancialR3.col("sign_type"),
      noFinancialR3.col("keyB40W")).
      agg(sum(col("transaction_amount")).alias("transaction_amount"))

    groupNoFinancialRenta
  }

  def outputNoFinancial(groupNoFinancialRenta:DataFrame,odate:String):DataFrame={
    val outputNoFinancial = groupNoFinancialRenta.select(col("accout").alias("account_id"),
      col("data_code_id").alias("analytic_concept_id"),
      col("sender_application_id"),col("product_cat").alias("commercial_product_type"),
      col("subproduct_cat").alias("commercial_subproduct_type"),
      col("transaction_amount").alias("analytic_concept_amount"),lit(odate).cast(DateType).alias("cutoff_date"))
    outputNoFinancial
  }

  def crossSicocoAccounts(sicocoDF : DataFrame, accountAll : DataFrame) : DataFrame = {
    val accountDF = accountAll.select(col("account_id"))
    val crossSicocoAccount = sicocoDF.join(accountDF,
      sicocoDF.col("commission_account_id") === accountDF.col("account_id"),"inner")
    crossSicocoAccount
  }

  def crossSicoco5Accounts(sicoco5DF : DataFrame, accountAll : DataFrame) : DataFrame = {
    val complementAcc = lit(format_string("%010d", col("commission_account_id").cast("int")))
    val accountDF = accountAll.select(col("account_id"))
    val crossSicoco5Account = sicoco5DF.join(accountDF,when(length(col("commission_account_id"))=== 10,
      concat(col("commission_account_id"))).otherwise(complementAcc) === accountDF.col("account_id"),"inner")
    crossSicoco5Account
  }

  def crossSicAccCatag(crossSicocoAccount: DataFrame, catagAll : DataFrame) : DataFrame = {
    val catagDF = catagAll.select(col("sender_application_id"),
      col("commercial_product_type"),col("commercial_subproduct_type"),
      col("primary_key_type"),col("data_code_id")).where(col("bg_operation_id") === "SIC")
    val crossSicAccCatag = crossSicocoAccount.join(catagDF,
      crossSicocoAccount.col("pk_sicoco") === catagDF.col("primary_key_type"),"inner")
    crossSicAccCatag
  }
  def crossSic5AccCatag(crossSicoco5Account: DataFrame, catagAll : DataFrame) : DataFrame = {
    val keySicoco5 = lit("SICCOBR5").cast(StringType)
    val zero = lit("0").cast(StringType)
    val catagDF = catagAll.select(col("sender_application_id"),
      col("commercial_product_type"),col("commercial_subproduct_type"),
      col("primary_key_type"),col("data_code_id")).where(col("bg_operation_id") === "SIC")
    val getKeySicoco5 = crossSicoco5Account.select(crossSicoco5Account.col("*"),concat(keySicoco5,when(length(col("card_service_type"))=== 2,
      concat(col("card_service_type"))).
      otherwise(concat(zero,col("card_service_type")))).alias("pk_sicoco5"))
    val crossSic5AccCatag = getKeySicoco5.join(catagDF,
      getKeySicoco5.col("pk_sicoco5") === catagDF.col("primary_key_type"),"inner")
    crossSic5AccCatag
  }

  def groupByFinalSicoco (crossSicAccCatag : DataFrame,odate: String): DataFrame = {
    val cutoff_date = lit(odate).cast(DateType).alias("cutoff_date")
    val groupByFinalSicoco = crossSicAccCatag.groupBy(
      col("commission_account_id"),col("data_code_id"),col("sender_application_id"),
      col("commercial_product_type"),col("commercial_subproduct_type")).agg(sum(
      col("total_commission_tax_amount")).alias("analytic_concept_amount")).select(
      col("commission_account_id").alias("account_id"),
      col("data_code_id").alias("analytic_concept_id"), col("sender_application_id"),
      col("commercial_product_type"), col("commercial_subproduct_type"),
      col("analytic_concept_amount").cast(DecimalType(17,2)),cutoff_date)
    groupByFinalSicoco
  }

  def groupByFinalSicoco5 (crossSic5AccCatag : DataFrame,odate: String): DataFrame = {
    val cutoff_date = lit(odate).cast(DateType).alias("cutoff_date")
    val groupByFinalSicoco = crossSic5AccCatag.groupBy(
      col("commission_account_id"), col("data_code_id"), col("sender_application_id"),
      col("commercial_product_type"), col("commercial_subproduct_type")).agg(sum(
      col("total_commission_tax_amount")).alias("analytic_concept_amount")).select(
      col("commission_account_id").alias("account_id"),
      col("data_code_id").alias("analytic_concept_id"), col("sender_application_id"),
      col("commercial_product_type"), col("commercial_subproduct_type"),
      col("analytic_concept_amount").cast(DecimalType(17, 2)), cutoff_date)
    groupByFinalSicoco
  }

  def unionLayoutRenta(dfTmp: DataFrame, dfOutputNoFinancial: DataFrame): DataFrame = {
    val union = dfTmp.union(dfOutputNoFinancial)
    union
  }

  def selectFinalRentability(dataFrameFinal2:DataFrame): DataFrame ={
    val outputRentability = dataFrameFinal2.select(col("account_id"),
      col("analytic_concept_id"),
      col("sender_application_id"),col("commercial_product_type"),
      col("commercial_subproduct_type"),
      col("analytic_concept_amount").cast(DecimalType(17,2)).alias("analytic_concept_amount"),col("cutoff_date"))
    outputRentability
  }

}
