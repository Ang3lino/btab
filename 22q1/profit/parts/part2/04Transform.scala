
// FinancialProfitTransform
// ----------------------------------------------------------------------------
// package com.bbva.datioamproduct.mbmi_financial_profit_part2.transform
// import com.bbva.datioamproduct.mbmi_financial_profit_part1.process.FinancialProfitProcess
// import com.bbva.datioamproduct.mbmi_financial_profit_part2.data.GetDataFPSource
// import com.bbva.datioamproduct.mbmi_financial_profit_part2.data.DataFPConstants._
// import com.bbva.datioamproduct.mbmi_financial_profit_part2.process._
import com.typesafe.config.{Config, ConfigFactory}
import com.typesafe.scalalogging.LazyLogging
import org.apache.spark.sql.{DataFrame, SparkSession}

class FinancialProfitTransform(spark: SparkSession, config: Config) extends LazyLogging{
  private val conf = ConfigFactory.load(config).getConfig("kirby")
  private val odate = conf.getConfig("input").getString("odate")
  private val date = conf.getConfig("input").getString("datem")
  private val pathAccount = conf.getConfig("input").getString("pathAccounts")
  private val pathExchangeRate = conf.getConfig("input").getString("pathExchangeRate")
  private val pathCatalogBg = conf.getConfig("input").getString("pathCatalogBg")
  private val pathSit = conf.getConfig("input").getString("pathSit")
  private val pathCie = conf.getConfig("input").getString("pathCie")
  private val pathCheck = conf.getConfig("input").getString("pathCheck")
  private val pathDep = conf.getConfig("input").getString("pathDep")
  private val pathDie = conf.getConfig("input").getString("pathDie")
  private val pathDomEmi = conf.getConfig("input").getString("pathDomEmi")
  private val pathDomMov = conf.getConfig("input").getString("pathDomMov")
  private val pathH2H = conf.getConfig("input").getString("pathH2H")
  private val pathNom = conf.getConfig("input").getString("pathNom")
  private val pathProfitTmp1 = conf.getConfig("input").getString("pathProfitTmp1")

  private val getDataFPSource = new GetDataFPSource(spark)
  private val noFinancialProcess = new NoFinancialProcess(spark)
  private val financialProfitProcess = new FinancialProfitProcess(spark)
  private val complementNoFinancialProcess = new ComplementNoFinancialProcess(spark)
  private val additionalNoFinancialProcess = new AdditionalNoFinancialProcess(spark)
  private val supNoFinancialProcess = new SupplementaryNoFinancialProcess()

  private val dfCatalogClone = getDataFPSource.getDataCatalog(pathCatalogBg,odate)
  private val outputRentability1 = getDataFPSource.getDataPart1(pathProfitTmp1)
  private val inimes = financialProfitProcess.calIniMes(odate)
  private val dfAccount = getDataFPSource.getAccountsData(pathAccount,odate)
  private val dfCatalog = getDataFPSource.getDataCatalog(pathCatalogBg,odate)
  private val dfSit =getDataFPSource.getDataSit(pathSit,odate,inimes,date)
  private val dfCie =getDataFPSource.getDataCie(pathCie,odate,inimes,date)
  private val dfTCDataDaily =  getDataFPSource.getTCDataDaily(pathExchangeRate,odate,inimes)
  private val dfCheck =getDataFPSource.getDataCheck(pathCheck,odate,inimes,date)
  private val dfDep =getDataFPSource.getDataDep(pathDep,odate,inimes,date)
  private val dfDie = getDataFPSource.getDataDie(pathDie,inimes,odate)
  private val dfDomEmi = getDataFPSource.getDataDomEmi(pathDomEmi,odate,inimes)
  private val dfDomMov = getDataFPSource.getDataDomMov(pathDomMov,odate,inimes)
  private val dfH2H = getDataFPSource.getDataH2H(pathH2H,odate,inimes)
  private val dfNom = getDataFPSource.getDataNom(pathNom,inimes,odate)


  def runFinanProcess(): DataFrame = {
    val crossSitAccount = noFinancialProcess.crossAccountsSit(dfAccount,dfSit)
    val crossAccSitExch = noFinancialProcess.crossAccSitExchDaily(crossSitAccount,dfTCDataDaily)
    val sitCodeSit = noFinancialProcess.sitCode(crossAccSitExch)
    val sitGroupSit = noFinancialProcess.groupSit(sitCodeSit)
    val sitGroupSitOpe = noFinancialProcess.groupSitOpe(sitGroupSit)
    val sitGroupSitImp = noFinancialProcess.groupSitImp(sitGroupSit)
    val sitUnionSitImpOpe = financialProfitProcess.unionLayoutRenta(sitGroupSitOpe,sitGroupSitImp)
    val sitGetSitCatImpOpe = noFinancialProcess.getSitCatImpOpe(dfCatalog,sitUnionSitImpOpe)
    val sitDataFrameSitSub = noFinancialProcess.dataFrameSitSub(sitGetSitCatImpOpe)
    val sitDataFrameSubSitCat = noFinancialProcess.dataFrameSubSitCat(dfCatalog,sitDataFrameSitSub)
    val sitDataFrameImpOpeSitSub = financialProfitProcess.unionLayoutRenta(sitGetSitCatImpOpe,sitDataFrameSubSitCat)
    val sitDataFrameSitType = noFinancialProcess.dataFrameSitType(sitDataFrameSubSitCat)
    val sitDataFrameSitTotl = noFinancialProcess.dataFrameSitTotl(sitDataFrameSitType)
    val sitDataFrameCatSitTotl = noFinancialProcess.dataFrameCatSitTotl(dfCatalog,sitDataFrameSitTotl)
    val sitDataFrameTotalSitUnion = financialProfitProcess.unionLayoutRenta(sitDataFrameCatSitTotl, sitDataFrameImpOpeSitSub)

    val dataFrameFinal = noFinancialProcess.dataFrameOutSitRenta(dfCatalog,sitDataFrameTotalSitUnion,odate)

    val crossCieAccount = noFinancialProcess.crossAccountsCie(dfAccount,dfCie)
    val crossAccCieExch = noFinancialProcess.crossAccCieExchDaily(crossCieAccount,dfTCDataDaily)
    val cieCodeCie = noFinancialProcess.cieCode(crossAccCieExch)
    val cieGroupCie = noFinancialProcess.groupCie(cieCodeCie)
    val cieGroupCieOpe = noFinancialProcess.groupCieOpe(cieGroupCie)
    val cieGroupCieImp = noFinancialProcess.groupCieImp(cieGroupCie)
    val cieUnionCieImpOpe = financialProfitProcess.unionLayoutRenta(cieGroupCieOpe,cieGroupCieImp)
    val cieGetCieCatImpOpe = noFinancialProcess.getCieCatImpOpe(dfCatalog,cieUnionCieImpOpe)
    val cieDataFrameCieSub = noFinancialProcess.dataFrameCieSub(cieGetCieCatImpOpe)
    val cieDataFrameSubCieCat = noFinancialProcess.dataFrameSubCieCat(dfCatalog,cieDataFrameCieSub)
    val cieDataFrameImpOpeCieSub = financialProfitProcess.unionLayoutRenta(cieGetCieCatImpOpe,cieDataFrameSubCieCat)
    val cieDataFrameCieType = noFinancialProcess.dataFrameCieType(cieDataFrameSubCieCat)
    val cieDataFrameCieTotl = noFinancialProcess.dataFrameCieTotl(cieDataFrameCieType)
    val cieDataFrameCatCieTotl = noFinancialProcess.dataFrameCatCieTotl(dfCatalog,cieDataFrameCieTotl)
    val cieDataFrameTotalCieUnion = financialProfitProcess.unionLayoutRenta(cieDataFrameCatCieTotl, cieDataFrameImpOpeCieSub)
    val dataFrameFinalCie = noFinancialProcess.dataFrameOutCieRenta(dfCatalog,cieDataFrameTotalCieUnion,odate)

    val unionSitComplement = financialProfitProcess.unionLayoutRenta(outputRentability1, dataFrameFinal)
    val unionSitCieComplement = financialProfitProcess.unionLayoutRenta(dataFrameFinalCie, unionSitComplement)
    val finalData = financialProfitProcess.selectFinalRentability(unionSitCieComplement)

    finalData.repartition(PARTITION_DF)
  }

  def runFinanceProcess(finalData: DataFrame): DataFrame = {
    val checkCrossCheckAccounts = complementNoFinancialProcess.crossCheckAccounts(dfAccount,dfCheck)
    val depCrossDepAccounts = complementNoFinancialProcess.crossDepAccounts(dfAccount,dfDep)
    val checkCrossChqTpc = complementNoFinancialProcess.crossChqTpc(checkCrossCheckAccounts,dfTCDataDaily)
    val depCrossDepTpc = complementNoFinancialProcess.crossDepTpc(depCrossDepAccounts,dfTCDataDaily)
    val checkGetTibChqChq = complementNoFinancialProcess.getTibChqChq(checkCrossChqTpc)
    val checkGetTibEfeChq = complementNoFinancialProcess.getTibEfeChq(checkCrossChqTpc)
    val depGetTibChqEfeDep = complementNoFinancialProcess.getTibChqEfeDep(depCrossDepTpc)
    val checkGetDemChqChq = complementNoFinancialProcess.getDemChqChq(checkCrossChqTpc)
    val checkGetDemEfeChq = complementNoFinancialProcess.getDemEfeChq(checkCrossChqTpc)
    val depGetDemChqEfeDep = complementNoFinancialProcess.getDemChqEfeDep(depCrossDepTpc)
    val unionCheckTibEfeChq = financialProfitProcess.unionLayoutRenta(checkGetTibChqChq, checkGetTibEfeChq)
    val unionDepTibChqEfeDep = financialProfitProcess.unionLayoutRenta(unionCheckTibEfeChq, depGetTibChqEfeDep)
    val unionCheckDemChqChq = financialProfitProcess.unionLayoutRenta(unionDepTibChqEfeDep, checkGetDemChqChq)
    val unionCheckDemEfeChq = financialProfitProcess.unionLayoutRenta(unionCheckDemChqChq, checkGetDemEfeChq)
    val demGetTibDem = financialProfitProcess.unionLayoutRenta(unionCheckDemEfeChq, depGetDemChqEfeDep)
    val demGetGroupCtaTibDem = complementNoFinancialProcess.getGroupCtaTibDem(demGetTibDem)
    val demGetImpTibDemCat = complementNoFinancialProcess.getImpTibDemCat(demGetGroupCtaTibDem,dfCatalog)
    val getOpeTibDemCat = complementNoFinancialProcess.getOpeTibDemCat(demGetGroupCtaTibDem,dfCatalog)
    val demGetCtaRntaTibDem = financialProfitProcess.unionLayoutRenta(demGetImpTibDemCat, getOpeTibDemCat)
    val demGetSubtRntaTibDem = complementNoFinancialProcess.getSubtRntaTibDem(demGetCtaRntaTibDem)
    val demGetTotRntaTibDem = complementNoFinancialProcess.getTotRntaTibDem(demGetSubtRntaTibDem)
    val groupGetTotRntaTibDemGroup = complementNoFinancialProcess.getTotRntaTibDemGroup(demGetTotRntaTibDem)
    val getSubCtaRntaTibDem = financialProfitProcess.unionLayoutRenta(demGetCtaRntaTibDem, demGetSubtRntaTibDem)
    val getPrevRntaTibDem = financialProfitProcess.unionLayoutRenta(getSubCtaRntaTibDem, groupGetTotRntaTibDemGroup)
    val dataFrameOutTibDem = complementNoFinancialProcess.dataFrameOutTibDem(getPrevRntaTibDem,dfCatalogClone,odate)
    val outDataFrameOutTibDem = financialProfitProcess.selectFinalRentability(dataFrameOutTibDem)

    val dFrameTibDem = financialProfitProcess.unionLayoutRenta(finalData, outDataFrameOutTibDem)

    dFrameTibDem.repartition(PARTITION_DF)
  }

  def runAddFinanceProcess(dFrameTibDem: DataFrame): DataFrame = {
    val crossDieAccount = complementNoFinancialProcess.getCrossDieAccount(dfDie, dfAccount)
    val dieCode = complementNoFinancialProcess.getDieCode(crossDieAccount)
    val groupDie = complementNoFinancialProcess.getGroupDie(dieCode)
    val groupDieOpe = complementNoFinancialProcess.getGroupDieOpe(groupDie)
    val groupDieImp = complementNoFinancialProcess.getGroupDieImp(groupDie)
    val unionDieImpOpe = financialProfitProcess.unionLayoutRenta(groupDieOpe, groupDieImp)
    val dieCatImpOpe = complementNoFinancialProcess.getDieCatImpOpe(unionDieImpOpe, dfCatalog)
    val groupDieSub = complementNoFinancialProcess.getGroupDieSub(dieCatImpOpe)
    val groupDieSubCat = complementNoFinancialProcess.getGruoupDieSubCat(groupDieSub, dfCatalog)
    val unionDieImpOpeSub = financialProfitProcess.unionLayoutRenta(dieCatImpOpe, groupDieSubCat)
    val dieType = complementNoFinancialProcess.getDieType(groupDieSubCat)
    val dieTotal = complementNoFinancialProcess.getDieTotal(dieType)
    val dieTotalCat = complementNoFinancialProcess.getDieTotalCat(dieTotal, dfCatalog)
    val dieTotalUnion = financialProfitProcess.unionLayoutRenta(unionDieImpOpeSub, dieTotalCat)
    val dieFinalDF = complementNoFinancialProcess.getFinalDieRenta(dfCatalog, dieTotalUnion, odate).coalesce(PARTITION_DF)

    val crossEmiMov = additionalNoFinancialProcess.getCrossEmiMov(dfDomEmi, dfDomMov)
    val crossDomAccount = additionalNoFinancialProcess.getCrossDomAccount(crossEmiMov, dfAccount)
    val domCode = additionalNoFinancialProcess.getDomCode(crossDomAccount)
    val groupDom = additionalNoFinancialProcess.getGroupDom(domCode)
    val groupDomOpe = additionalNoFinancialProcess.getGroupDomOpe(groupDom)
    val groupDomImp = additionalNoFinancialProcess.getGroupDomImp(groupDom)
    val unionDomImpOpe = financialProfitProcess.unionLayoutRenta(groupDomOpe, groupDomImp)
    val domCatImpOpe = additionalNoFinancialProcess.getDomCatImpOpe(unionDomImpOpe, dfCatalog)
    val groupDomSub = additionalNoFinancialProcess.getGroupDomSub(domCatImpOpe)
    val groupDomSubCat = additionalNoFinancialProcess.getGroupDomSubCat(groupDomSub, dfCatalog)
    val unionDomImpOpeSub = financialProfitProcess.unionLayoutRenta(domCatImpOpe, groupDomSubCat)
    val domType = additionalNoFinancialProcess.getDomType(groupDomSubCat)
    val domTotal = additionalNoFinancialProcess.getDomTotal(domType)
    val domTotalCat = additionalNoFinancialProcess.getDomTotalCat(domTotal, dfCatalog)
    val domTotalUnion = financialProfitProcess.unionLayoutRenta(unionDomImpOpeSub, domTotalCat)
    val domFinalDF = additionalNoFinancialProcess.getFinalDomRenta(dfCatalog, domTotalUnion, odate)

    val dfDieDom = financialProfitProcess.unionLayoutRenta(domFinalDF, dieFinalDF)
    val dfDieDomOut = financialProfitProcess.selectFinalRentability(dfDieDom)
    val dfDieProcess = financialProfitProcess.unionLayoutRenta(dFrameTibDem, dfDieDomOut)
    dfDieProcess.repartition(PARTITION_DF)
  }

  def runLinkFinanceProcess(dfDieProcess: DataFrame): DataFrame = {

    val crossH2HAccount = additionalNoFinancialProcess.getCrossH2HAccount(dfH2H, dfAccount)
    val crossH2HExch = additionalNoFinancialProcess.getCrossH2HExch(crossH2HAccount, dfTCDataDaily)
    val h2HCode = additionalNoFinancialProcess.getH2HCode(crossH2HExch)
    val groupH2H = additionalNoFinancialProcess.getGroupH2H(h2HCode)
    val groupH2HOpe = additionalNoFinancialProcess.getGroupH2HOpe(groupH2H)
    val groupH2HImp = additionalNoFinancialProcess.getGroupH2HImp(groupH2H)
    val unionH2HImpOpe = financialProfitProcess.unionLayoutRenta(groupH2HOpe, groupH2HImp)
    val h2HCatImpOpe = additionalNoFinancialProcess.getH2HCatImpOpe(unionH2HImpOpe, dfCatalog)
    val groupH2HSub = additionalNoFinancialProcess.getGroupH2HSub(h2HCatImpOpe)
    val groupH2HSubCat = additionalNoFinancialProcess.getGroupH2HSubCat(groupH2HSub, dfCatalog)
    val unionH2HImpOpeSub = financialProfitProcess.unionLayoutRenta(h2HCatImpOpe, groupH2HSubCat)
    val h2HType = additionalNoFinancialProcess.getH2HType(groupH2HSubCat)
    val h2HTotal = additionalNoFinancialProcess.getH2HTotal(h2HType)
    val h2HTotalCat = additionalNoFinancialProcess.getH2HTotalCat(h2HTotal, dfCatalog)
    val h2HTotalUnion = financialProfitProcess.unionLayoutRenta(unionH2HImpOpeSub, h2HTotalCat)
    val h2FinalDF = additionalNoFinancialProcess.getH2FinalDF(dfCatalog, h2HTotalUnion, odate).coalesce(PARTITION_DF)

    val crossNomAccount = additionalNoFinancialProcess.getCrossPayrollAccount(dfNom, dfAccount)
    val nomCode = additionalNoFinancialProcess.getPayrollCode(crossNomAccount)
    val groupNom = additionalNoFinancialProcess.getGroupPayroll(nomCode)
    val nomGroupOpe = additionalNoFinancialProcess.getPayrollGroupOperation(groupNom)
    val nomGroupImp = supNoFinancialProcess.getPayrollGroupAmount(groupNom)
    val unionNomImpOpe = financialProfitProcess.unionLayoutRenta(nomGroupOpe, nomGroupImp)
    val nomCatImpOpe = supNoFinancialProcess.getPayrollCatalogueAmountOperation(unionNomImpOpe, dfCatalog)
    val groupNomSub = supNoFinancialProcess.getGroupPayrollSubtotal(nomCatImpOpe)
    val groupNomSubCat = supNoFinancialProcess.getGroupPayrollSubtotalCatalogue(groupNomSub, dfCatalog)
    val unionNomImpOpeSub = financialProfitProcess.unionLayoutRenta(nomCatImpOpe, groupNomSubCat)
    val nomType = supNoFinancialProcess.getPayrollType(groupNomSubCat)
    val nomTotal = supNoFinancialProcess.getPayrollTotal(nomType)
    val nomTotalCat = supNoFinancialProcess.getPayrollTotalCatalogue(nomTotal, dfCatalog)
    val nomTotalUnion = financialProfitProcess.unionLayoutRenta(unionNomImpOpeSub, nomTotalCat)
    val nomFinalDF = supNoFinancialProcess.getPayrollFinalDF(dfCatalog, nomTotalUnion, odate)

    val dfH2HNom = financialProfitProcess.unionLayoutRenta(h2FinalDF, nomFinalDF)
    val dfH2HNomOut = financialProfitProcess.selectFinalRentability(dfH2HNom)
    val dfH2HProcess = financialProfitProcess.unionLayoutRenta(dfDieProcess, dfH2HNomOut)
    dfH2HProcess.repartition(PARTITION_DF)
  }
}