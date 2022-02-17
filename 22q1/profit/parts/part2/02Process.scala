
// AdditionalNoFinancialProcess.scala
// ----------------------------------------------------------------------------
// package com.bbva.datioamproduct.mbmi_financial_profit_part2.process

import org.apache.spark.sql.functions._
import org.apache.spark.sql.types.{DateType, DecimalType, StringType}
import org.apache.spark.sql.{DataFrame, SparkSession}

class AdditionalNoFinancialProcess(spark: SparkSession) {
  private val prefH2H = lit("H2H").cast(StringType)
  private val prefDom = lit("DOM").cast(StringType)
  private val prefNom = lit("NOM").cast(StringType)
  private val sufImp = lit("IMP").cast(StringType)
  private val sufOpe = lit("OPE").cast(StringType)

  def getCrossEmiMov(dfDomEmi: DataFrame, dfDomMov: DataFrame): DataFrame = {
    val crossEmiMov = dfDomMov.join(
      dfDomEmi,
      dfDomMov.col("issuing_id") === dfDomEmi.col("issuing_id"),
      "inner").select(
      dfDomMov.col("issuing_id"),
      dfDomMov.col("debit_amount"),
      dfDomMov.col("issued_receipt_number"),
      dfDomMov.col("collecting_mark_type"),
      dfDomMov.col("auto_payments_clct_proc_date"),
      dfDomEmi.col("source_account_id"),
      dfDomEmi.col("source_channel_id"))
    crossEmiMov
  }

  def getCrossDomAccount(dfDom: DataFrame, dfAccount: DataFrame): DataFrame = {
    val crossDomAccount = dfAccount.join(
      dfDom,
      dfAccount("account_id") === dfDom("source_account_id"),
      "inner"
    ).select(dfDom.col("*"))
    crossDomAccount
  }

  def getDomCode(dfDom: DataFrame): DataFrame = {
    val condition = when(col("source_channel_id").isin("O", "M"),
      concat(lit("MAN"), col("source_channel_id")))
      .otherwise(concat(lit("DIG"), col("source_channel_id"))).cast(StringType)
    val pkDomImp = concat(prefDom, condition, sufImp).as("pk_dom_imp")
    val pkDomOpe = concat(prefDom, condition, sufOpe).as("pk_dom_ope")

    val domCode = dfDom.select(
      col("source_account_id"),
      col("issued_receipt_number").as("operaciones"),
      (col("debit_amount") * -1).as("analytic_concept_amount"),
      pkDomImp, pkDomOpe)
    domCode
  }

  def getGroupDom(dfDom: DataFrame): DataFrame = {
    val groupDom = dfDom.groupBy(col("source_account_id"), col("pk_dom_imp"), col("pk_dom_ope")).
      agg(sum("analytic_concept_amount").as("analytic_concept_amount"), sum("operaciones").as("operaciones")).
      select(
        col("source_account_id"),
        col("pk_dom_imp"),
        col("pk_dom_ope"),
        col("analytic_concept_amount"),
        col("operaciones"))
    groupDom
  }

  def getGroupDomOpe(dfDom: DataFrame): DataFrame = {
    val groupDomOpe = dfDom.select(
      col("source_account_id"),
      col("pk_dom_ope").as("primary_key_type"),
      col("operaciones").cast(DecimalType(17, 2)).as("analytic_concept_amount"))
    groupDomOpe
  }

  def getGroupDomImp(dfDom: DataFrame): DataFrame = {
    val groupDomImp = dfDom.select(
      col("source_account_id"),
      col("pk_dom_imp").as("primary_key_type"),
      col("analytic_concept_amount").cast(DecimalType(17, 2)).as("analytic_concept_amount"))
    groupDomImp
  }

  def getDomCatImpOpe(dfDom: DataFrame, dfCatalog: DataFrame): DataFrame = {
    val domCatImpOpe = dfCatalog.join(
      dfDom,
      dfDom.col("primary_key_type") === dfCatalog.col("primary_key_type"),
      "inner")
      .select(
        dfDom.col("source_account_id"),
        dfCatalog.col("data_code_id"),
        dfCatalog.col("commercial_product_type"),
        dfCatalog.col("commercial_subproduct_type"),
        dfDom.col("analytic_concept_amount"),
        dfDom.col("primary_key_type"))
    domCatImpOpe
  }

  def getGroupDomSub(dfDom: DataFrame): DataFrame = {
    val domDigiImp = lit("DOMDIGLIMP").cast(StringType)
    val domDigiOpe = lit("DOMDIGLOPE").cast(StringType)
    val domManuImp = lit("DOMMANUIMP").cast(StringType)
    val domManuOpe = lit("DOMMANUOPE").cast(StringType)
    val pkConditionSub = when(col("data_code_id") === "3610", domDigiImp)
      .when(col("data_code_id") === "5610", domDigiOpe)
      .when(col("data_code_id") === "3620", domManuImp)
      .when(col("data_code_id") === "5620", domManuOpe)

    val groupDomSub = dfDom.groupBy(col("source_account_id"), col("data_code_id")).agg(
      sum(col("analytic_concept_amount")).as("analytic_concept_amount")).select(
      col("source_account_id"),
      col("data_code_id"),
      col("analytic_concept_amount"),
      pkConditionSub.as("primary_key_type"))
    groupDomSub
  }

  def getGroupDomSubCat(dfDom: DataFrame, dfCatalog: DataFrame): DataFrame = {
    val groupDomSubCat = dfCatalog.join(
      dfDom, dfDom.col("primary_key_type") === dfCatalog.col("primary_key_type"), "inner").select(
      dfDom.col("source_account_id"),
      dfCatalog.col("data_code_id"),
      dfCatalog.col("commercial_product_type"),
      dfCatalog.col("commercial_subproduct_type"),
      dfDom.col("analytic_concept_amount"),
      dfDom.col("primary_key_type"))
    groupDomSubCat
  }

  def getDomType(dfDom: DataFrame): DataFrame = {
    val domType = dfDom.select(
      col("source_account_id"),
      col("commercial_product_type"),
      col("analytic_concept_amount"),
      col("primary_key_type"))
    domType
  }

  def getDomTotal(dfDom: DataFrame): DataFrame = {
    val domTotlImp = lit("DOMTDOMIMP").cast(StringType)
    val domTotlOpe = lit("DOMTDOMOPE").cast(StringType)
    val pkConditionTotl = when(col("commercial_product_type").isin("36"), domTotlImp)
      .when(col("commercial_product_type").isin("56"), domTotlOpe)

    val domTotal = dfDom.groupBy(col("source_account_id"), col("commercial_product_type")).agg(
      sum(col("analytic_concept_amount")).as("analytic_concept_amount")).select(
      col("source_account_id"),
      col("commercial_product_type"),
      col("analytic_concept_amount"),
      pkConditionTotl.as("primary_key_type"))
    domTotal
  }

  def getDomTotalCat(dfDom: DataFrame, dfCatalog: DataFrame): DataFrame = {
    val domTotalCat = dfCatalog.join(
      dfDom, dfDom.col("primary_key_type") === dfCatalog.col("primary_key_type"), "inner").select(
      dfDom.col("source_account_id"),
      dfCatalog.col("data_code_id"),
      dfCatalog.col("commercial_product_type"),
      dfCatalog.col("commercial_subproduct_type"),
      dfDom.col("analytic_concept_amount"),
      dfDom.col("primary_key_type"))
    domTotalCat
  }

  def getFinalDomRenta(dfCatalog: DataFrame,dfDom: DataFrame,odate: String): DataFrame = {
    val cutoffDate = lit(odate).cast(DateType).as("cutoff_date")
    val dieFinalDF = dfCatalog.join(
      dfDom,dfDom.col("primary_key_type") === dfCatalog.col("primary_key_type"),"inner").select(
      dfDom.col("source_account_id").as("account_id"),
      dfCatalog.col("data_code_id").as("analytic_concept_id"),
      dfCatalog.col("sender_application_id"),
      dfCatalog.col("commercial_product_type"),
      dfCatalog.col("commercial_subproduct_type"),
      dfDom.col("analytic_concept_amount"),
      cutoffDate)
    dieFinalDF
  }

  def getCrossH2HAccount(dfH2H: DataFrame, dfAccount: DataFrame): DataFrame = {
    val crossH2HAccount = dfAccount.join(dfH2H,
      dfAccount("account_id") === dfH2H("primary_subject_id"),
      "inner"
    ).select(dfH2H.col("*"))
    crossH2HAccount
  }

  def getCrossH2HExch(dfH2H: DataFrame, dfTCDataDaily: DataFrame): DataFrame = {
    val crossH2HExch = dfH2H.join(
      dfTCDataDaily,dfH2H.col("operation_currency_type") === dfTCDataDaily.col("currency_id") &&
        dfH2H.col("transaction_date") === dfTCDataDaily.col("load_date"),"inner").select(
      dfH2H.col("primary_subject_id"),
      dfH2H.col("operation_currency_type"),
      dfH2H.col("transaction_amount"),
      dfH2H.col("subservice_type"),
      dfTCDataDaily.col("exchange_currency_min_amount"))
    crossH2HExch
  }

  def getH2HCode(dfH2H: DataFrame): DataFrame = {
    val condition = col("subservice_type").substr(-4,4).cast(StringType)
    val pkH2HImp = concat(prefH2H,condition,sufImp).as("pk_h2h_imp")
    val pkH2HOpe = concat(prefH2H,condition,sufOpe).as("pk_h2h_ope")

    val h2HCode = dfH2H.select(
      col("primary_subject_id"),
      (col("exchange_currency_min_amount") * col("transaction_amount")).as("analytic_concept_amount"),
      pkH2HImp,
      pkH2HOpe)
    h2HCode
  }

  def getGroupH2H(dfH2H: DataFrame): DataFrame = {
    val groupH2H = dfH2H.groupBy(col("primary_subject_id"), col("pk_h2h_imp"),col("pk_h2h_ope")).
      agg(sum("analytic_concept_amount").as("analytic_concept_amount"),count("*").as("operaciones")).select(
      col("primary_subject_id"),
      col("pk_h2h_imp"),
      col("pk_h2h_ope"),
      col("analytic_concept_amount"),
      col("operaciones"))
    groupH2H
  }

  def getGroupH2HOpe(dfH2H: DataFrame): DataFrame = {
    val dfH2HGroupOpe = dfH2H.select(
      col("primary_subject_id"),
      col("pk_h2h_ope").as("primary_key_type"),
      col("operaciones").cast(DecimalType(17,2)).as("analytic_concept_amount"))
    dfH2HGroupOpe
  }

  def getGroupH2HImp(dfH2H: DataFrame): DataFrame = {
    val dfH2HGroupImp = dfH2H.select(
      col("primary_subject_id"),
      col("pk_h2h_imp").as("primary_key_type"),
      col("analytic_concept_amount").cast(DecimalType(17,2)).as("analytic_concept_amount"))
    dfH2HGroupImp
  }

  def getH2HCatImpOpe(dfH2H: DataFrame, dfCatalog: DataFrame): DataFrame = {
    val h2HCatImpOpe = dfCatalog.join(
      dfH2H,dfH2H.col("primary_key_type") === dfCatalog.col("primary_key_type"),"inner")
      .select(
        dfH2H.col("primary_subject_id"),
        dfCatalog.col("data_code_id"),
        dfCatalog.col("commercial_product_type"),
        dfCatalog.col("commercial_subproduct_type"),
        dfH2H.col("analytic_concept_amount"),
        dfH2H.col("primary_key_type"))
    h2HCatImpOpe
  }

  def getGroupH2HSub(dfH2H: DataFrame): DataFrame = {
    val h2h008Imp = lit("H2HT008IMP").cast(StringType)
    val h2h008lOpe = lit("H2HT008OPE").cast(StringType)
    val h2h009Imp = lit("H2HT009IMP").cast(StringType)
    val h2h009Ope = lit("H2HT009OPE").cast(StringType)
    val pkConditionSub = when(col("data_code_id") === "3710", h2h008Imp)
      .when(col("data_code_id") === "5710", h2h008lOpe)
      .when(col("data_code_id") === "3730", h2h009Imp)
      .when(col("data_code_id") === "5730", h2h009Ope)

    val groupH2HSub = dfH2H.groupBy(col("primary_subject_id"),col("data_code_id")).agg(
      sum(col("analytic_concept_amount")).as("analytic_concept_amount")).select(
      col("primary_subject_id"),
      col("data_code_id"),
      col("analytic_concept_amount"),
      pkConditionSub.as("primary_key_type"))
    groupH2HSub
  }

  def getGroupH2HSubCat(dfH2H: DataFrame, dfCatalog: DataFrame): DataFrame = {
    val groupH2HSubCat = dfCatalog.join(
      dfH2H,dfH2H.col("primary_key_type") === dfCatalog.col("primary_key_type"),"inner").select(
      dfH2H.col("primary_subject_id"),
      dfCatalog.col("data_code_id"),
      dfCatalog.col("commercial_product_type"),
      dfCatalog.col("commercial_subproduct_type"),
      dfH2H.col("analytic_concept_amount"),
      dfH2H.col("primary_key_type"))
    groupH2HSubCat
  }

  def getH2HType(dfH2H: DataFrame): DataFrame = {
    val h2HType = dfH2H.select(
      col("primary_subject_id"),
      col("commercial_product_type"),
      col("analytic_concept_amount"),
      col("primary_key_type"))
    h2HType
  }

  def getH2HTotal(dfH2H: DataFrame): DataFrame = {
    val h2HTotlImp = lit("H2HTH2HIMP").cast(StringType)
    val h2HTotlOpe = lit("H2HTH2HOPE").cast(StringType)
    val pkConditionTotl = when(col("commercial_product_type") === "37", h2HTotlImp)
      .when(col("commercial_product_type") === "57", h2HTotlOpe)

    val h2HTotal = dfH2H.groupBy(col("primary_subject_id"),col("commercial_product_type")).agg(
      sum(col("analytic_concept_amount")).as("analytic_concept_amount")).select(
      col("primary_subject_id"),
      col("commercial_product_type"),
      col("analytic_concept_amount"),
      pkConditionTotl.as("primary_key_type"))
    h2HTotal
  }

  def getH2HTotalCat(dfH2H: DataFrame, dfCatalog: DataFrame): DataFrame = {
    val h2HTotalCat = dfCatalog.join(
      dfH2H,dfH2H.col("primary_key_type") === dfCatalog.col("primary_key_type"),"inner").select(
      dfH2H.col("primary_subject_id"),
      dfCatalog.col("data_code_id"),
      dfCatalog.col("commercial_product_type"),
      dfCatalog.col("commercial_subproduct_type"),
      dfH2H.col("analytic_concept_amount"),
      dfH2H.col("primary_key_type"))
    h2HTotalCat
  }

  def getH2FinalDF(dfCatalog: DataFrame,dfH2H: DataFrame,odate: String): DataFrame = {
    val cutoffDate = lit(odate).cast(DateType).as("cutoff_date")
    val h2FinalDF = dfCatalog.join(
      dfH2H,dfH2H.col("primary_key_type") === dfCatalog.col("primary_key_type"),"inner").select(
      dfH2H.col("primary_subject_id").as("account_id"),
      dfCatalog.col("data_code_id").as("analytic_concept_id"),
      dfCatalog.col("sender_application_id"),
      dfCatalog.col("commercial_product_type"),
      dfCatalog.col("commercial_subproduct_type"),
      dfH2H.col("analytic_concept_amount"),
      cutoffDate)
    h2FinalDF
  }

  def getCrossPayrollAccount(dfNom: DataFrame, dfAccount: DataFrame): DataFrame = {
    val crossNomAccount = dfAccount.join(dfNom,
      dfAccount("account_id") === dfNom("commerce_account_id"),
      "inner").select(dfNom.col("*"))
    crossNomAccount
  }

  def getPayrollCode(dfNom: DataFrame): DataFrame = {
    val condition = concat(lit("NO"),
      when(col("payroll_channel_id").isNull, lit("OT"))
        .when(col("payroll_channel_id") === "BANXICO", lit("BX"))
        .otherwise(col("payroll_channel_id").substr(1, 2))).cast(StringType)
    val pkNomImp = concat(prefNom, condition, sufImp).as("pk_nom_imp")
    val pkNomOpe = concat(prefNom, condition, sufOpe).as("pk_nom_ope")
    val nomCode = dfNom.select(col("commerce_account_id"),
      col("payroll_amount").as("analytic_concept_amount"),
      pkNomImp,
      pkNomOpe)
    nomCode
  }

  def getGroupPayroll(dfNom: DataFrame): DataFrame = {
    val groupNom = dfNom.groupBy(col("commerce_account_id"), col("pk_nom_imp"), col("pk_nom_ope")).
      agg(sum("analytic_concept_amount").as("analytic_concept_amount"), count("*").as("operaciones")).select(
      col("commerce_account_id"),
      col("pk_nom_imp"),
      col("pk_nom_ope"),
      col("analytic_concept_amount"),
      col("operaciones"))
    groupNom
  }

  def getPayrollGroupOperation(dfNom: DataFrame): DataFrame = {
    val nomGroupOpe = dfNom.select(
      col("commerce_account_id"),
      col("pk_nom_ope").as("primary_key_type"),
      col("operaciones").cast(DecimalType(17, 2)).as("analytic_concept_amount"))
    nomGroupOpe
  }
}

// ComplementNoFinancialProcess.scala
// ----------------------------------------------------------------------------

// package com.bbva.datioamproduct.mbmi_financial_profit_part2.process

import org.apache.spark.sql.functions.{col, _}
import org.apache.spark.sql.types.{DateType, _}
import org.apache.spark.sql.{DataFrame, SparkSession}

class ComplementNoFinancialProcess(spark: SparkSession) {
  val prefTIB = lit("TIB").cast(StringType)
  val medfTIB = lit("TIB").cast(StringType)
  val prefDEM = lit("DEM").cast(StringType)
  val medfDEM = lit("DEM").cast(StringType)
  val prefDie = lit("DIE").cast(StringType)
  val sufImp = lit("IMP").cast(StringType)
  val sufOpe = lit("OPE").cast(StringType)

  def crossCheckAccounts (account : DataFrame,getDataCheck : DataFrame): DataFrame ={
    val crossCheckAccounts = account.join(
      getDataCheck,account.col("account_id") === trim(getDataCheck.col("credit_internal_account_id")),"inner").select(
      getDataCheck.col("*"))
    crossCheckAccounts
  }

  def crossDepAccounts (account : DataFrame, getDataDep : DataFrame): DataFrame = {
    val crossDepAccounts = account.join(
      getDataDep,account.col("account_id") === trim(getDataDep.col("credit_internal_account_id")),"inner").select(
      getDataDep.col("*"))
    crossDepAccounts
  }

  def crossChqTpc (crossCheckAccounts : DataFrame, getTCDataDaily : DataFrame): DataFrame ={
    val crossChqTpc = crossCheckAccounts.join(
      getTCDataDaily,crossCheckAccounts.col("currency_id") === getTCDataDaily.col("currency_id") &&
        crossCheckAccounts.col("transfer_debit_date") === getTCDataDaily.col("load_date"),"inner").select(
      crossCheckAccounts.col("*"),
      (crossCheckAccounts.col("cash_tot_amount")* getTCDataDaily.col("exchange_currency_min_amount")).alias("cash_tot_amount_val").cast(DecimalType(17,2)),
      (crossCheckAccounts.col("checks_tot_amount")* getTCDataDaily.col("exchange_currency_min_amount")).alias("checks_tot_amount_val").cast(DecimalType(17,2)))
    crossChqTpc
  }

  def crossDepTpc (crossDepAccounts : DataFrame, getTCDataDaily : DataFrame): DataFrame ={
    val crossDepTpc = crossDepAccounts.join(
      getTCDataDaily,crossDepAccounts.col("currency_id") === getTCDataDaily.col("currency_id") &&
        crossDepAccounts.col("transfer_debit_date") === getTCDataDaily.col("load_date"),"inner").select(
      crossDepAccounts.col("*"),
      (crossDepAccounts.col("check_tot_amount")* getTCDataDaily.col("exchange_currency_min_amount")).alias("check_tot_amount_val").cast(DecimalType(17,2)))
    crossDepTpc
  }

  def getTibChqChq (crossChqTpc : DataFrame): DataFrame ={
    val getTibChqChq = crossChqTpc.where(trim(col("cie_transaction_type")).isin("D","E","3")).select(
        col("transfer_debit_date"),
        col("credit_internal_account_id"),
        concat(prefTIB,medfTIB,lit(col("cie_transaction_type")).cast(StringType),sufImp).alias("pk_imp"),
        concat(prefTIB,medfTIB,lit(col("cie_transaction_type")).cast(StringType),sufOpe).alias("pk_ope"),
        col("checks_tot_amount_val").alias("tot_amount"))
    getTibChqChq
  }

  def getTibEfeChq (crossChqTpc : DataFrame): DataFrame ={
    val getTibEfeChq = crossChqTpc.where(trim(col("cie_transaction_type")).isin("B","C"))
      .select(
        col("transfer_debit_date"),
        col("credit_internal_account_id"),
        concat(prefTIB,medfTIB,lit(col("cie_transaction_type")).cast(StringType),sufImp).alias("pk_imp"),
        concat(prefTIB,medfTIB,lit(col("cie_transaction_type")).cast(StringType),sufOpe).alias("pk_ope"),
        col("cash_tot_amount_val").alias("tot_amount"))
    getTibEfeChq
  }
  def getTibChqEfeDep (crossDepTpc : DataFrame): DataFrame ={
    val getTibChqEfeDep = crossDepTpc.where(trim(col("cie_transaction_type")).isin("D","E","3","B","C"))
      .select(
        col("transfer_debit_date"),
        col("credit_internal_account_id"),
        concat(prefTIB,medfTIB,lit(col("cie_transaction_type")).cast(StringType),sufImp).alias("pk_imp"),
        concat(prefTIB,medfTIB,lit(col("cie_transaction_type")).cast(StringType),sufOpe).alias("pk_ope"),
        col("check_tot_amount_val") .alias("tot_amount"))
    getTibChqEfeDep
  }

  def getDemChqChq (crossChqTpc : DataFrame): DataFrame ={
    val getDemChqChq = crossChqTpc.where(trim(col("cie_transaction_type")).isin("5","6"))
      .select(
        col("transfer_debit_date"),
        col("credit_internal_account_id"),
        concat(prefDEM,medfDEM,lit(col("cie_transaction_type")).cast(StringType),sufImp).alias("pk_imp"),
        concat(prefDEM,medfDEM,lit(col("cie_transaction_type")).cast(StringType),sufOpe).alias("pk_ope"),
        col("checks_tot_amount_val").alias("tot_amount"))
    getDemChqChq
  }

  def getDemEfeChq (crossChqTpc : DataFrame): DataFrame ={
    val getDemEfeChq = crossChqTpc.where(trim(col("cie_transaction_type")).isin("4"))
      .select(
        col("transfer_debit_date"),
        col("credit_internal_account_id"),
        concat(prefDEM,medfDEM,lit(col("cie_transaction_type")).cast(StringType),sufImp).alias("pk_imp"),
        concat(prefDEM,medfDEM,lit(col("cie_transaction_type")).cast(StringType),sufOpe).alias("pk_ope"),
        col("cash_tot_amount_val").alias("tot_amount"))
    getDemEfeChq
  }

  def getDemChqEfeDep (crossDepTpc : DataFrame): DataFrame ={
    val getDemChqEfeDep = crossDepTpc.where(trim(col("cie_transaction_type")).isin("4","5","6"))
      .select(
        col("transfer_debit_date"),
        col("credit_internal_account_id"),
        concat(prefDEM,medfDEM,lit(col("cie_transaction_type")).cast(StringType),sufImp).alias("pk_imp"),
        concat(prefDEM,medfDEM,lit(col("cie_transaction_type")).cast(StringType),sufOpe).alias("pk_ope"),
        col("check_tot_amount_val") .alias("tot_amount"))
    getDemChqEfeDep
  }

  def getGroupCtaTibDem (getTibDem : DataFrame): DataFrame ={
    val getGroupCtaTibDem = getTibDem.groupBy(col("credit_internal_account_id"),col("pk_imp"),col("pk_ope"))
      .agg(sum(col("tot_amount")).alias("tot_amount_imp"),count("*").alias("tot_ope"))
      .select(col("credit_internal_account_id"),col("pk_imp"),col("pk_ope"),col("tot_amount_imp"),col("tot_ope"))
    getGroupCtaTibDem
  }

  def getImpTibDemCat (getGroupCtaTibDem : DataFrame, dataFrameCatalog: DataFrame): DataFrame ={
    val getImpTibDemCat = getGroupCtaTibDem.join(
      dataFrameCatalog,getGroupCtaTibDem.col("pk_imp") === dataFrameCatalog.col("primary_key_type"),"inner").select(
      getGroupCtaTibDem.col("credit_internal_account_id"),
      dataFrameCatalog.col("data_code_id"),
      getGroupCtaTibDem.col("tot_amount_imp").alias("analytic_concept_amount"),
      getGroupCtaTibDem.col("pk_imp").alias("primary_key"))
    getImpTibDemCat
  }

  def getOpeTibDemCat (getGroupCtaTibDem : DataFrame, dataFrameCatalog : DataFrame): DataFrame ={
    val getOpeTibDemCat = getGroupCtaTibDem.join(
      dataFrameCatalog,getGroupCtaTibDem.col("pk_ope") === dataFrameCatalog.col("primary_key_type"),"inner").select(
      getGroupCtaTibDem.col("credit_internal_account_id"),
      dataFrameCatalog.col("data_code_id"),
      getGroupCtaTibDem.col("tot_ope").alias("analytic_concept_amount"),
      getGroupCtaTibDem.col("pk_ope").alias("primary_key"))
    getOpeTibDemCat
  }

  def getSubtRntaTibDem (getCtaRntaTibDem : DataFrame): DataFrame ={
    val cveTibefeImp = lit("TIBTEFEIMP").cast(StringType)
    val cveTibchqImp = lit("TIBTCHQIMP").cast(StringType)
    val cveTibefeOpe = lit("TIBTEFEOPE").cast(StringType)
    val cveTibchqOpe = lit("TIBTCHQOPE").cast(StringType)
    val cveDemefeImp = lit("DEMTEFEIMP").cast(StringType)
    val cveDemchqImp = lit("DEMTCHQIMP").cast(StringType)
    val cveDemefeOpe = lit("DEMTEFEOPE").cast(StringType)
    val cveDemchqOpe = lit("DEMTCHQOPE").cast(StringType)

    val conditionSubt = when(col("data_code_id")==="3310",cveDemefeImp)
      .when(col("data_code_id")==="3330",cveDemchqImp)
      .when(col("data_code_id")==="3410",cveTibefeImp)
      .when(col("data_code_id")==="3430",cveTibchqImp)
      .when(col("data_code_id")==="5310",cveDemefeOpe)
      .when(col("data_code_id")==="5330",cveDemchqOpe)
      .when(col("data_code_id")==="5410",cveTibefeOpe)
      .when(col("data_code_id")==="5430",cveTibchqOpe).alias("primary_key")

    val getSubtRntaTibDem = getCtaRntaTibDem.groupBy(col("credit_internal_account_id"),col("data_code_id"))
      .agg(sum(col("analytic_concept_amount")).alias("analytic_concept_amount"))
      .select(col("credit_internal_account_id"),
        col("data_code_id"),
        col("analytic_concept_amount"),
        conditionSubt)
    getSubtRntaTibDem
  }

  def getTotRntaTibDem (getSubtRntaTibDem : DataFrame): DataFrame ={
    val cveTibTotImp = lit("TIBTOTLIMP").cast(StringType)
    val cveTibTotOpe = lit("TIBTOTLOPE").cast(StringType)
    val cveDemTotImp = lit("DEMTOTLIMP").cast(StringType)
    val cveDemTotOpe = lit("DEMTOTLOPE").cast(StringType)

    val conditionTot =  when(col("data_code_id").isin("3310","3330"),cveDemTotImp)
      .when(col("data_code_id").isin("3410","3430"),cveTibTotImp)
      .when(col("data_code_id").isin("5310","5330"),cveDemTotOpe)
      .when(col("data_code_id").isin("5410","5430"),cveTibTotOpe).alias("primary_key")

    val getTotRntaTibDem = getSubtRntaTibDem.groupBy(col("credit_internal_account_id"),col("data_code_id"))
      .agg(sum(col("analytic_concept_amount")).alias("analytic_concept_amount"))
      .select(col("credit_internal_account_id"),
        col("data_code_id"),
        col("analytic_concept_amount"),
        conditionTot)
    getTotRntaTibDem
  }

  def getTotRntaTibDemGroup (getTotRntaTibDem : DataFrame): DataFrame ={
    val tot_code_id = lit("SIN_CODEID")

    val getTotRntaTibDemGroup = getTotRntaTibDem.groupBy(col("credit_internal_account_id"), col("primary_key"))
      .agg(sum(col("analytic_concept_amount")).alias("analytic_concept_amount"))
      .select(col("credit_internal_account_id"),tot_code_id.alias("data_code_id"),col("analytic_concept_amount"),
        col("primary_key"))
    getTotRntaTibDemGroup
  }

  def dataFrameOutTibDem (getPrevRntaTibDem : DataFrame, dataFrameCatalog : DataFrame, odate: String): DataFrame ={
    val cutoff_date = lit(odate).cast(DateType).alias("cutoff_date")
    val dataFrameOutTibDem = getPrevRntaTibDem.join(
      dataFrameCatalog,getPrevRntaTibDem.col("primary_key") === dataFrameCatalog.col("primary_key_type"),"inner").select(
      getPrevRntaTibDem.col("credit_internal_account_id").alias("account_id"),
      dataFrameCatalog.col("data_code_id").alias("analytic_concept_id"),
      dataFrameCatalog.col("sender_application_id"),
      dataFrameCatalog.col("commercial_product_type"),
      dataFrameCatalog.col("commercial_subproduct_type"),
      getPrevRntaTibDem.col("analytic_concept_amount"),
      cutoff_date)
  dataFrameOutTibDem
  }

  def getCrossDieAccount(dfDie:DataFrame, dfAccount: DataFrame): DataFrame = {
    val crossDieAccount = dfAccount.join(dfDie,
      dfAccount("account_id") === dfDie("agreement_dispersal_acct_id"),
      "inner"
    ).select(dfDie.col("*"))
    crossDieAccount
  }

  def getDieCode(dfDie: DataFrame): DataFrame = {
    val condition = concat(col("movement_desc").substr(1,2),
                    when(col("channel_id").substr(1,2).isNotNull && trim(col("channel_id")) =!= "", col("channel_id").
                      substr(1,2)).otherwise("OT")).cast(StringType)
    val pkDieImp = concat(prefDie,condition,sufImp).as("pk_die_imp")
    val pkDieOpe = concat(prefDie,condition,sufOpe).as("pk_die_ope")

    val dieCode = dfDie.select(
      col("agreement_dispersal_acct_id"),
      col("fee_amount").as("analytic_concept_amount"),
      pkDieImp,
      pkDieOpe)
    dieCode
  }

  def getGroupDie(dfDie: DataFrame): DataFrame = {
    val groupDie = dfDie.groupBy(col("agreement_dispersal_acct_id"), col("pk_die_imp"),col("pk_die_ope")).
      agg(sum("analytic_concept_amount").as("analytic_concept_amount"),count("*").as("operaciones")).select(
      col("agreement_dispersal_acct_id"),
      col("pk_die_imp"),
      col("pk_die_ope"),
      col("analytic_concept_amount"),
      col("operaciones"))
    groupDie
  }

  def getGroupDieOpe(dfDie: DataFrame): DataFrame = {
    val dfDieGroupOpe = dfDie.select(
      col("agreement_dispersal_acct_id"),
      col("pk_die_ope").as("primary_key_type"),
      col("operaciones").cast(DecimalType(17,2)).as("analytic_concept_amount"))
    dfDieGroupOpe
  }

  def getGroupDieImp(dfDie: DataFrame): DataFrame = {
    val dfDieGroupImp = dfDie.select(
      col("agreement_dispersal_acct_id"),
      col("pk_die_imp").as("primary_key_type"),
      col("analytic_concept_amount").cast(DecimalType(17,2)).as("analytic_concept_amount"))
    dfDieGroupImp
  }

  def getDieCatImpOpe(dfDie: DataFrame, dfCatalog: DataFrame):DataFrame = {
    val dieCatImpOpe = dfCatalog.join(
      dfDie,dfDie.col("primary_key_type") === dfCatalog.col("primary_key_type"),"inner")
      .select(
        dfDie.col("agreement_dispersal_acct_id"),
        dfCatalog.col("data_code_id"),
        dfCatalog.col("commercial_product_type"),
        dfCatalog.col("commercial_subproduct_type"),
        dfDie.col("analytic_concept_amount"),
        dfDie.col("primary_key_type"))
    dieCatImpOpe
  }

  def getGroupDieSub(dfDie: DataFrame): DataFrame = {
    val diePaglImp = lit("DIEPAGLIMP").cast(StringType)
    val diePaglOpe = lit("DIEPAGLOPE").cast(StringType)
    val dieCredImp = lit("DIECREDIMP").cast(StringType)
    val dieCredOpe = lit("DIECREDOPE").cast(StringType)
    val pkConditionSub = when(col("data_code_id") === "4210", diePaglImp)
      .when(col("data_code_id") === "6210", diePaglOpe)
      .when(col("data_code_id") === "4230", dieCredImp)
      .when(col("data_code_id") === "6230", dieCredOpe)

    val groupDieSub = dfDie.groupBy(col("agreement_dispersal_acct_id"),col("data_code_id")).agg(
      sum(col("analytic_concept_amount")).as("analytic_concept_amount")).select(
      col("agreement_dispersal_acct_id"),
      col("data_code_id"),
      col("analytic_concept_amount"),
      pkConditionSub.as("primary_key_type"))
    groupDieSub
  }

  def getGruoupDieSubCat(dfDie: DataFrame, dfCatalog: DataFrame): DataFrame = {
    val groupDieSubCat = dfCatalog.join(
      dfDie,dfDie.col("primary_key_type") === dfCatalog.col("primary_key_type"),"inner").select(
      dfDie.col("agreement_dispersal_acct_id"),
      dfCatalog.col("data_code_id"),
      dfCatalog.col("commercial_product_type"),
      dfCatalog.col("commercial_subproduct_type"),
      dfDie.col("analytic_concept_amount"),
      dfDie.col("primary_key_type"))
    groupDieSubCat
  }

  def getDieType(dfDie: DataFrame): DataFrame = {
    val dieType = dfDie.select(
      col("agreement_dispersal_acct_id"),
      col("commercial_product_type"),
      col("analytic_concept_amount"),
      col("primary_key_type"))
    dieType
  }

  def getDieTotal(dfDie: DataFrame): DataFrame = {
    val dieTotlImp = lit("DIETDIEIMP").cast(StringType)
    val dieTotlOpe = lit("DIETDIEOPE").cast(StringType)
    val pkConditionTotl = when(col("commercial_product_type") === "42", dieTotlImp)
      .when(col("commercial_product_type") === "62", dieTotlOpe)

    val dieTotal = dfDie.groupBy(col("agreement_dispersal_acct_id"),col("commercial_product_type")).agg(
      sum(col("analytic_concept_amount")).as("analytic_concept_amount")).select(
      col("agreement_dispersal_acct_id"),
      col("commercial_product_type"),
      col("analytic_concept_amount"),
      pkConditionTotl.as("primary_key_type"))
    dieTotal
  }

  def getDieTotalCat(dfDie: DataFrame, dfCatalog: DataFrame): DataFrame = {
    val dieTotalCat = dfCatalog.join(
      dfDie,dfDie.col("primary_key_type") === dfCatalog.col("primary_key_type"),"inner").select(
      dfDie.col("agreement_dispersal_acct_id"),
      dfCatalog.col("data_code_id"),
      dfCatalog.col("commercial_product_type"),
      dfCatalog.col("commercial_subproduct_type"),
      dfDie.col("analytic_concept_amount"),
      dfDie.col("primary_key_type"))
    dieTotalCat
  }

  def getFinalDieRenta(dfCatalog: DataFrame,dfDie: DataFrame,odate: String): DataFrame = {
    val cutoffDate = lit(odate).cast(DateType).as("cutoff_date")
    val dieFinalDF = dfCatalog.join(
      dfDie,dfDie.col("primary_key_type") === dfCatalog.col("primary_key_type"),"inner").select(
      dfDie.col("agreement_dispersal_acct_id").as("account_id"),
      dfCatalog.col("data_code_id").as("analytic_concept_id"),
      dfCatalog.col("sender_application_id"),
      dfCatalog.col("commercial_product_type"),
      dfCatalog.col("commercial_subproduct_type"),
      dfDie.col("analytic_concept_amount"),
      cutoffDate)
    dieFinalDF
  }
}


// NoFinancialProcess.scala
// ----------------------------------------------------------------------------
// package com.bbva.datioamproduct.mbmi_financial_profit_part2.process

import org.apache.spark.sql.functions.{col, _}
import org.apache.spark.sql.types.{DateType, _}
import org.apache.spark.sql.{DataFrame, SparkSession}

class NoFinancialProcess(spark: SparkSession) {


  def crossAccountsSit(account : DataFrame,getDataSit : DataFrame): DataFrame ={
    val crossAccountsSit = account.join(
      getDataSit,account.col("account_id") === getDataSit.col("dispersal_account_id"),"inner").select(
      getDataSit.col("agreement_id"),
      getDataSit.col("sit_transaction_date"),
      getDataSit.col("operation_currency_id"),
      getDataSit.col("total_transaction_amount"),
      getDataSit.col("sender_application_id"),
      getDataSit.col("operation_status_type"),
      getDataSit.col("dispersal_account_id"))
    crossAccountsSit
  }

  def crossAccSitExchDaily(crossAccountsSit : DataFrame, getTCDataDaily : DataFrame): DataFrame ={
    val crossAccSitExchDaily = crossAccountsSit.join(
      getTCDataDaily,crossAccountsSit.col("operation_currency_id") === getTCDataDaily.col("currency_id") &&
        crossAccountsSit.col("sit_transaction_date") === getTCDataDaily.col("load_date"),"inner").select(
      crossAccountsSit.col("agreement_id"),
      crossAccountsSit.col("sit_transaction_date"),
      crossAccountsSit.col("operation_currency_id"),
      crossAccountsSit.col("total_transaction_amount"),
      crossAccountsSit.col("sender_application_id"),
      crossAccountsSit.col("operation_status_type"),
      crossAccountsSit.col("dispersal_account_id"),
      getTCDataDaily.col("exchange_currency_min_amount"))
    crossAccSitExchDaily
  }

  def sitCode(crossAccSitExchDaily : DataFrame ): DataFrame ={
    val prefSit = lit("SIT").cast(StringType)
    val sufImp = lit("IMP").cast(StringType)
    val sufOpe = lit("OPE").cast(StringType)
    val pk_sit_imp = concat(prefSit,lit(col("sender_application_id")).cast(StringType),sufImp).alias("pk_sit_imp")
    val pk_sit_ope = concat(prefSit,lit(col("sender_application_id")).cast(StringType),sufOpe).alias("pk_sit_ope")

    val sitCode = crossAccSitExchDaily.select(
      col("agreement_id"),
      col("sender_application_id"),
      col("sit_transaction_date"),
      col("operation_currency_id"),
      col("total_transaction_amount"),
      col("exchange_currency_min_amount"),
      (col("total_transaction_amount")*col("exchange_currency_min_amount")).alias("analytic_concept_amount").cast(DecimalType(17,2)),
      pk_sit_imp,
      pk_sit_ope,
      col("dispersal_account_id"))
    sitCode
  }

  def groupSit(sitCode: DataFrame ): DataFrame ={
    val groupSit = sitCode.groupBy(col("dispersal_account_id"),col("pk_sit_imp"),col("pk_sit_ope")).agg(
      sum("analytic_concept_amount").alias("analytic_concept_amount"),count("*").alias("operaciones")).select(
      col("dispersal_account_id"),
      col("pk_sit_imp"),
      col("pk_sit_ope"),
      col("analytic_concept_amount"),
      col("operaciones"))
    groupSit
  }

  def groupSitOpe(groupSit: DataFrame ): DataFrame ={
    val groupSitOpe = groupSit.select(
      col("dispersal_account_id"),
      col("pk_sit_ope").alias("primary_key_type"),
      col("operaciones").cast(DecimalType(17,2)).alias("analytic_concept_amount"))
    groupSitOpe
  }

  def groupSitImp(groupSit: DataFrame ): DataFrame ={
    val groupSitImp = groupSit.select(
      col("dispersal_account_id"),
      col("pk_sit_imp").alias("primary_key_type"),
      col("analytic_concept_amount").cast(DecimalType(17,2)).alias("analytic_concept_amount"))
    groupSitImp
  }

  def getSitCatImpOpe(dfCatalog: DataFrame, unionSitImpOpe: DataFrame ): DataFrame = {
    val getSitCatImpOpe = dfCatalog.join(
      unionSitImpOpe,unionSitImpOpe.col("primary_key_type") === dfCatalog.col("primary_key_type"),"inner").select(
      unionSitImpOpe.col("dispersal_account_id"),
      dfCatalog.col("data_code_id"),
      dfCatalog.col("commercial_product_type"),
      dfCatalog.col("commercial_subproduct_type"),
      unionSitImpOpe.col("analytic_concept_amount"),
      unionSitImpOpe.col("primary_key_type"))
    getSitCatImpOpe
  }

  def dataFrameSitSub(getSitCatImpOpe: DataFrame ): DataFrame ={
    val sitTdigImp = lit("SITTDIGIMP").cast(StringType)
    val sitTdigOpe = lit("SITTDIGOPE").cast(StringType)
    val sitVentImp = lit("SITVTOTIMP").cast(StringType)
    val sitVentOpe = lit("SITVTOTOPE").cast(StringType)
    val pk_condition_sub = when(col("data_code_id") === "4110", sitTdigImp)
                          .when(col("data_code_id") === "6110", sitTdigOpe)
                          .when(col("data_code_id") === "4130", sitVentImp)
                          .when(col("data_code_id") === "6130", sitVentOpe)

    val dataFrameSitSub = getSitCatImpOpe.groupBy(col("dispersal_account_id"),col("data_code_id")).agg(
      sum(col("analytic_concept_amount")).alias("analytic_concept_amount")).select(
      col("dispersal_account_id"),
      col("data_code_id"),
      col("analytic_concept_amount"),
      pk_condition_sub.alias("primary_key_type"))
    dataFrameSitSub
  }

  def dataFrameSubSitCat(dataFrameCatalog: DataFrame, dataFrameSub: DataFrame ): DataFrame = {
    val dataFrameSubSitCat = dataFrameCatalog.join(
      dataFrameSub,dataFrameSub.col("primary_key_type") === dataFrameCatalog.col("primary_key_type"),"inner").select(
      dataFrameSub.col("dispersal_account_id"),
      dataFrameCatalog.col("data_code_id"),
      dataFrameCatalog.col("commercial_product_type"),
      dataFrameCatalog.col("commercial_subproduct_type"),
      dataFrameSub.col("analytic_concept_amount"),
      dataFrameSub.col("primary_key_type"))
    dataFrameSubSitCat
  }

  def dataFrameSitType(dataFrameSubSitCat: DataFrame ): DataFrame = {
    val dataFrameSitType = dataFrameSubSitCat.select(
      col("dispersal_account_id"),
      col("commercial_product_type"),
      col("analytic_concept_amount"),
      col("primary_key_type"))
    dataFrameSitType
  }

  def dataFrameSitTotl(dataFrameSitType: DataFrame ): DataFrame = {
    val sitTotlImp = lit("SITTOTLIMP").cast(StringType)
    val sitTotlOpe = lit("SITTOTLOPE").cast(StringType)

    val pk_condition_totl = when(col("commercial_product_type").isin("41"), sitTotlImp)
      .when(col("commercial_product_type").isin("61"), sitTotlOpe)

    val dataFrameSitTotl = dataFrameSitType.groupBy(col("dispersal_account_id"),col("commercial_product_type")).agg(
      sum(col("analytic_concept_amount")).alias("analytic_concept_amount")).select(
      col("dispersal_account_id"),
      col("commercial_product_type"),
      col("analytic_concept_amount"),
      pk_condition_totl.alias("primary_key_type"))
    dataFrameSitTotl
  }

  def dataFrameCatSitTotl(dataFrameCatalog: DataFrame, dataFrameSitTotl: DataFrame ): DataFrame = {
    val dataFrameCatSitTotl = dataFrameCatalog.join(
      dataFrameSitTotl,dataFrameSitTotl.col("primary_key_type") === dataFrameCatalog.col("primary_key_type"),"inner").select(
      dataFrameSitTotl.col("dispersal_account_id"),
      dataFrameCatalog.col("data_code_id"),
      dataFrameCatalog.col("commercial_product_type"),
      dataFrameCatalog.col("commercial_subproduct_type"),
      dataFrameSitTotl.col("analytic_concept_amount"),
      dataFrameSitTotl.col("primary_key_type"))
    dataFrameCatSitTotl
  }

  def dataFrameOutSitRenta(dataFrameCatalog: DataFrame, dataFrameTotalSitUnion: DataFrame, odate: String ): DataFrame = {
    val cutoff_date = lit(odate).cast(DateType).alias("cutoff_date")
    val dataFrameOutSitRenta = dataFrameCatalog.join(
      dataFrameTotalSitUnion,dataFrameTotalSitUnion.col("primary_key_type") === dataFrameCatalog.col("primary_key_type"),"inner").select(
      dataFrameTotalSitUnion.col("dispersal_account_id").alias("account_id"),
      dataFrameCatalog.col("data_code_id").alias("analytic_concept_id"),
      dataFrameCatalog.col("sender_application_id"),
      dataFrameCatalog.col("commercial_product_type"),
      dataFrameCatalog.col("commercial_subproduct_type"),
      dataFrameTotalSitUnion.col("analytic_concept_amount"),
      cutoff_date)
    dataFrameOutSitRenta
  }


  def crossAccountsCie(account : DataFrame,getDataCie : DataFrame): DataFrame ={
    val crossAccountsCie = account.join(
      getDataCie,account.col("account_id") === getDataCie.col("credit_internal_account_id"),"inner").select(
      getDataCie.col("agreement_id"),
      getDataCie.col("transaction_date"),
      getDataCie.col("value_date"),
      getDataCie.col("currency_id"),
      getDataCie.col("operation_amount").cast(DecimalType(17,2)).alias("operation_amount"),
      getDataCie.col("credit_internal_account_id"),
      getDataCie.col("srce_appl_operation_status_type"),
      getDataCie.col("transaction_channel_type"))
    crossAccountsCie
  }

  def crossAccCieExchDaily(crossAccountsCie : DataFrame, getTCDataDaily : DataFrame): DataFrame ={
    val crossAccCieExchDaily = crossAccountsCie.join(
      getTCDataDaily,trim(crossAccountsCie.col("currency_id")) === getTCDataDaily.col("currency_id") &&
        crossAccountsCie.col("transaction_date") === getTCDataDaily.col("load_date"),"inner").select(
      crossAccountsCie.col("agreement_id"),
      crossAccountsCie.col("transaction_date"),
      crossAccountsCie.col("value_date"),
      crossAccountsCie.col("currency_id"),
      crossAccountsCie.col("operation_amount"),
      crossAccountsCie.col("credit_internal_account_id"),
      getTCDataDaily.col("exchange_currency_min_amount"),
      crossAccountsCie.col("srce_appl_operation_status_type"),
      crossAccountsCie.col("transaction_channel_type"))
    crossAccCieExchDaily
  }

  def cieCode(crossAccCieExchDaily : DataFrame ): DataFrame ={
    val prefCie = lit("CIE").cast(StringType)
    val sufImp = lit("IMP").cast(StringType)
    val sufOpe = lit("OPE").cast(StringType)
    val pk_cie_imp = concat(prefCie,lit(col("transaction_channel_type")).cast(StringType),sufImp).alias("pk_cie_imp")
    val pk_cie_ope = concat(prefCie,lit(col("transaction_channel_type")).cast(StringType),sufOpe).alias("pk_cie_ope")

    val cieCode = crossAccCieExchDaily.select(
      col("agreement_id"),
      col("transaction_channel_type"),
      col("transaction_date"),
      col("currency_id"),
      col("operation_amount"),
      col("exchange_currency_min_amount"),
      (col("operation_amount")*col("exchange_currency_min_amount")).alias("analytic_concept_amount").cast(DecimalType(17,2)),
      pk_cie_imp,
      pk_cie_ope,
      col("credit_internal_account_id"))
    cieCode
  }

  def groupCie(cieCode : DataFrame): DataFrame ={
    val groupCie = cieCode.groupBy(col("credit_internal_account_id"),col("pk_cie_imp"),col("pk_cie_ope")).agg(
      sum("analytic_concept_amount").alias("analytic_concept_amount"),count("*").alias("operaciones")).select(
      col("credit_internal_account_id"),
      col("pk_cie_imp"),
      col("pk_cie_ope"),
      col("analytic_concept_amount"),
      col("operaciones"))
    groupCie
  }

  def groupCieOpe(groupCie: DataFrame): DataFrame ={
    val groupCieOpe = groupCie.select(
      col("credit_internal_account_id"),
      col("pk_cie_ope").alias("primary_key_type"),
      col("operaciones").cast(DecimalType(17,2)).alias("analytic_concept_amount"))
    groupCieOpe
  }

  def groupCieImp(groupCie: DataFrame): DataFrame ={
    val groupCieImp = groupCie.select(
      col("credit_internal_account_id"),
      col("pk_cie_imp").alias("primary_key_type"),
      col("analytic_concept_amount").cast(DecimalType(17,2)).alias("analytic_concept_amount"))
    groupCieImp
  }

  def getCieCatImpOpe(dataFrameCatalog: DataFrame, unionCieImpOpe: DataFrame ): DataFrame = {
    val getCieCatImpOpe = dataFrameCatalog.join(
      unionCieImpOpe,unionCieImpOpe.col("primary_key_type") === dataFrameCatalog.col("primary_key_type"),"inner").select(
      unionCieImpOpe.col("credit_internal_account_id"),
      dataFrameCatalog.col("data_code_id"),
      dataFrameCatalog.col("commercial_product_type"),
      dataFrameCatalog.col("commercial_subproduct_type"),
      unionCieImpOpe.col("analytic_concept_amount"),
      unionCieImpOpe.col("primary_key_type"))
    getCieCatImpOpe
  }

  def dataFrameCieSub(getCieCatImpOpe: DataFrame ): DataFrame ={
    val cieTdigImp = lit("CIETDIGIMP").cast(StringType)
    val cieTdigOpe = lit("CIETDIGOPE").cast(StringType)
    val cieVentImp = lit("CIEVTOTIMP").cast(StringType)
    val cieVentOpe = lit("CIEVTOTOPE").cast(StringType)
    val pk_condition_sub = when(col("data_code_id") === "3210", cieTdigImp).when(col("data_code_id") === "5210", cieTdigOpe)
      .when(col("data_code_id") === "3230", cieVentImp).when(col("data_code_id") === "5230", cieVentOpe)

    val dataFrameCieSub = getCieCatImpOpe.groupBy(col("credit_internal_account_id"),col("data_code_id")).agg(
      sum(col("analytic_concept_amount")).alias("analytic_concept_amount")).select(
      col("credit_internal_account_id"),
      col("data_code_id"),
      col("analytic_concept_amount"),
      pk_condition_sub.alias("primary_key_type"))
    dataFrameCieSub
  }

  def dataFrameSubCieCat(dataFrameCatalog : DataFrame, dataFrameCieSub : DataFrame): DataFrame = {
    val dataFrameSubCieCat = dataFrameCatalog.join(
      dataFrameCieSub,dataFrameCieSub.col("primary_key_type") === dataFrameCatalog.col("primary_key_type"),"inner").select(
      dataFrameCieSub.col("credit_internal_account_id"),
      dataFrameCatalog.col("data_code_id"),
      dataFrameCatalog.col("commercial_product_type"),
      dataFrameCatalog.col("commercial_subproduct_type"),
      dataFrameCieSub.col("analytic_concept_amount"),
      dataFrameCieSub.col("primary_key_type"))
    dataFrameSubCieCat
  }

  def dataFrameCieType(dataFrameSubCieCat: DataFrame ): DataFrame = {
    val dataFrameCieType = dataFrameSubCieCat.select(
      col("credit_internal_account_id"),
      col("commercial_product_type"),
      col("analytic_concept_amount").cast(DecimalType(17,2)).alias("analytic_concept_amount"),
      col("primary_key_type"))
    dataFrameCieType
  }

  def dataFrameCieTotl(dataFrameType: DataFrame ): DataFrame = {
    val cieTotlImp = lit("CIETOTLIMP").cast(StringType)
    val cieTotlOpe = lit("CIETOTLOPE").cast(StringType)

    val pk_condition_totl = when(col("commercial_product_type").isin("32"), cieTotlImp)
      .when(col("commercial_product_type").isin("52"), cieTotlOpe)

    val dataFrameCieTotl = dataFrameType.groupBy(col("credit_internal_account_id"),col("commercial_product_type")).agg(
      sum(col("analytic_concept_amount")).alias("analytic_concept_amount")).select(
      col("credit_internal_account_id"),
      col("commercial_product_type"),
      col("analytic_concept_amount").cast(DecimalType(17,2)).alias("analytic_concept_amount"),
      pk_condition_totl.alias("primary_key_type"))
    dataFrameCieTotl
  }

  def dataFrameCatCieTotl(dataFrameCatalog: DataFrame, dataFrameCieTotl: DataFrame ): DataFrame = {
    val dataFrameCatCieTotl = dataFrameCatalog.join(
      dataFrameCieTotl,dataFrameCieTotl.col("primary_key_type") === dataFrameCatalog.col("primary_key_type"),"inner").select(
      dataFrameCieTotl.col("credit_internal_account_id"),
      dataFrameCatalog.col("data_code_id"),
      dataFrameCatalog.col("commercial_product_type"),
      dataFrameCatalog.col("commercial_subproduct_type"),
      dataFrameCieTotl.col("analytic_concept_amount"),
      dataFrameCieTotl.col("primary_key_type"))
    dataFrameCatCieTotl
  }

  def dataFrameOutCieRenta(dataFrameCatalog: DataFrame, dataframeTotalCieUnion: DataFrame,odate: String ): DataFrame = {
    val cutoff_date = lit(odate).cast(DateType).alias("cutoff_date")
    val dataFrameOutCieRenta = dataFrameCatalog.join(
      dataframeTotalCieUnion,dataframeTotalCieUnion.col("primary_key_type") === dataFrameCatalog.col("primary_key_type"),"inner").select(
      dataframeTotalCieUnion.col("credit_internal_account_id").alias("account_id"),
      dataFrameCatalog.col("data_code_id").alias("analytic_concept_id"),
      dataFrameCatalog.col("sender_application_id"),
      dataFrameCatalog.col("commercial_product_type"),
      dataFrameCatalog.col("commercial_subproduct_type"),
      dataframeTotalCieUnion.col("analytic_concept_amount"),
      cutoff_date)
    dataFrameOutCieRenta
  }


}

// SupplementaryNoFinancialProcess 
// ----------------------------------------------------------------------------
// package com.bbva.datioamproduct.mbmi_financial_profit_part2.process

import org.apache.spark.sql.DataFrame
import org.apache.spark.sql.functions._
import org.apache.spark.sql.types.{DateType, DecimalType, StringType}

class SupplementaryNoFinancialProcess {

  def getPayrollGroupAmount(dfNom: DataFrame): DataFrame = {
    val nomGroupImp = dfNom.select(
      col("commerce_account_id"),
      col("pk_nom_imp").as("primary_key_type"),
      col("analytic_concept_amount").cast(DecimalType(17, 2)).as("analytic_concept_amount"))
    nomGroupImp
  }

  def getPayrollCatalogueAmountOperation(dfNom: DataFrame, dfCatalog: DataFrame): DataFrame = {
    val nomCatImpOpe = dfCatalog.join(
      dfNom, dfNom.col("primary_key_type") === dfCatalog.col("primary_key_type"), "inner")
      .select(
        dfNom.col("commerce_account_id"),
        dfCatalog.col("data_code_id"),
        dfCatalog.col("commercial_product_type"),
        dfCatalog.col("commercial_subproduct_type"),
        dfNom.col("analytic_concept_amount"),
        dfNom.col("primary_key_type"))
    nomCatImpOpe
  }

  def getGroupPayrollSubtotal(dfNom: DataFrame): DataFrame = {
    val nomDigImp = lit("NOMNOMDIMP").cast(StringType)
    val nomDigOpe = lit("NOMNOMDOPE").cast(StringType)
    val nomManImp = lit("NOMNOMAIMP").cast(StringType)
    val nomManOpe = lit("NOMNOMAOPE").cast(StringType)
    val pkConditionSub = when(col("data_code_id") === "4310", nomDigImp)
      .when(col("data_code_id") === "6310", nomDigOpe)
      .when(col("data_code_id") === "4330", nomManImp)
      .when(col("data_code_id") === "6330", nomManOpe)
    val groupNomSub = dfNom.groupBy(col("commerce_account_id"), col("data_code_id")).agg(
      sum(col("analytic_concept_amount")).as("analytic_concept_amount")).select(
      col("commerce_account_id"),
      col("data_code_id"),
      col("analytic_concept_amount"),
      pkConditionSub.as("primary_key_type"))
    groupNomSub
  }

  def getGroupPayrollSubtotalCatalogue(dfNom: DataFrame, dfCatalog: DataFrame): DataFrame = {
    val groupNomSubCat = dfCatalog.join(
      dfNom, dfNom.col("primary_key_type") === dfCatalog.col("primary_key_type"), "inner").select(
      dfNom.col("commerce_account_id"),
      dfCatalog.col("data_code_id"),
      dfCatalog.col("commercial_product_type"),
      dfCatalog.col("commercial_subproduct_type"),
      dfNom.col("analytic_concept_amount"),
      dfNom.col("primary_key_type"))
    groupNomSubCat
  }

  def getPayrollType(dfNom: DataFrame): DataFrame = {
    val nomType = dfNom.select(
      col("commerce_account_id"),
      col("commercial_product_type"),
      col("analytic_concept_amount"),
      col("primary_key_type"))
    nomType
  }

  def getPayrollTotal(dfNom: DataFrame): DataFrame = {
    val nomTotlImp = lit("NOMNOMTIMP").cast(StringType)
    val nomTotlOpe = lit("NOMNOMTOPE").cast(StringType)
    val pkConditionTotl = when(col("commercial_product_type") === "43", nomTotlImp)
      .when(col("commercial_product_type") === "63", nomTotlOpe)
    val nomTotal = dfNom.groupBy(col("commerce_account_id"), col("commercial_product_type")).agg(
      sum(col("analytic_concept_amount")).as("analytic_concept_amount")).select(
      col("commerce_account_id"),
      col("commercial_product_type"),
      col("analytic_concept_amount"),
      pkConditionTotl.as("primary_key_type"))
    nomTotal
  }

  def getPayrollTotalCatalogue(dfNom: DataFrame, dfCatalog: DataFrame): DataFrame = {
    val nomTotalCat = dfCatalog.join(
      dfNom, dfNom.col("primary_key_type") === dfCatalog.col("primary_key_type"), "inner").select(
      dfNom.col("commerce_account_id"),
      dfCatalog.col("data_code_id"),
      dfCatalog.col("commercial_product_type"),
      dfCatalog.col("commercial_subproduct_type"),
      dfNom.col("analytic_concept_amount"),
      dfNom.col("primary_key_type"))
    nomTotalCat
  }

  def getPayrollFinalDF(dfCatalog: DataFrame, dfNom: DataFrame, odate: String): DataFrame = {
    val cutoffDate = lit(odate).cast(DateType).as("cutoff_date")
    val nomFinalDF = dfCatalog.join(
      dfNom, dfNom.col("primary_key_type") === dfCatalog.col("primary_key_type"), "inner").select(
      dfNom.col("commerce_account_id").as("account_id"),
      dfCatalog.col("data_code_id").as("analytic_concept_id"),
      dfCatalog.col("sender_application_id"),
      dfCatalog.col("commercial_product_type"),
      dfCatalog.col("commercial_subproduct_type"),
      dfNom.col("analytic_concept_amount"),
      cutoffDate)
    nomFinalDF
  }
}