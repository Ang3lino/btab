
// package com.bbva.datioamproduct.mbmi_financial_profit_part1.transform

// import com.bbva.datioamproduct.mbmi_financial_profit_part1.process.FinancialProfitProcess
// import com.bbva.datioamproduct.mbmi_financial_profit_part1.data.GetDataFPSource
import com.typesafe.scalalogging.LazyLogging
import com.typesafe.config.{Config, ConfigFactory}
import org.apache.spark.sql.{DataFrame, SparkSession}

class FinancialProfitTransform(spark: SparkSession, config: Config) extends LazyLogging{

//   private val conf = ConfigFactory.load(config).getConfig("kirby")
//   private val odate = conf.getConfig("input").getString("odate")

//   private val pathAccount = conf.getConfig("input").getString("pathAccounts")
//   private val pathCatalogBg = conf.getConfig("input").getString("pathCatalogBg")
//   private val pathB40W = conf.getConfig("input").getString("pathB40W")
//   private val pathSicoco = conf.getConfig("input").getString("pathSicoco")
//   private val pathSicoco5 = conf.getConfig("input").getString("pathSicoco5")
//   private val pathProfitTmp0 = conf.getConfig("input").getString("pathProfitTmp0")

    private val odate = "2022-01-31"
    // private val paths=[]
    private val pathAccount = "/data/master/mbmi/data/catalogos/t_mbmi_beyg_accounts/"
    private val pathExchangeRate = "/data/master/mdco/data/finance/t_mdco_tkidswlm/"  // ?
    private val pathCatalogBg = "/data/master/mbmi/data/catalogos/t_mbmi_persl_acct/"
    private val pathB40W = "/data/master/mbmi/data/transaccionalidad/t_mbmi_transact_bg"
    private val pathSicoco = "/data/master/mbmi/data/transaccionalidad/t_mbmi_beyg_sicoco/"
    private val pathSicoco5 = "/data/master/mmfi/data/t_mmfi_tmidssicf/"
    // private val pathProfitTmp0 = "/data/master/mbmi/datatmp/globalMarket/t_mbmi_beyg_financial_profit_tmp0"
    private val pathProfitTmp0 = "/intelligence/inbmin/analytic/users/XMX2250/workspace/mbmi/t_mbmi_beyg_financial_profit_tmp0"

  private val getDataFPSource = new GetDataFPSource(spark)
  private val financialProfitProcess = new FinancialProfitProcess(spark)

  private val inimes = financialProfitProcess.calIniMes(odate)
  private val dfCatalog = getDataFPSource.getDataCatalog(pathCatalogBg,odate)
  private val dfAccount = getDataFPSource.getAccountsData(pathAccount,odate)
  private val dfB40w = getDataFPSource.getDataB40W(pathB40W,odate,inimes)
  private val dfSicoco =getDataFPSource.getDataSicoco(pathSicoco,odate)
  private val dfSicoco5 =getDataFPSource.getDataSicoco5(pathSicoco5,odate)
  private val outputRentability0 = getDataFPSource.getDataPart0(pathProfitTmp0)

  def runFinancialProcess(): DataFrame = {
    val crossB40withAccount = financialProfitProcess.crossB40withAccount(dfB40w,dfAccount)
    val filteredCatalogForB40W = financialProfitProcess.filterCatalog(dfCatalog)
    val crossNoFinancialWithCatalogo = financialProfitProcess.crossNoFinancialWithCatalogo(crossB40withAccount,filteredCatalogForB40W)
    val selectNoFinancial = financialProfitProcess.selectNoFinancial(crossNoFinancialWithCatalogo)
    val groupByFinancialProfit = financialProfitProcess.groupByFinancialProfit(selectNoFinancial)
    val outputNoFinancial = financialProfitProcess.outputNoFinancial(groupByFinancialProfit,odate)
    val rentaBalanceAndNoFinancial = financialProfitProcess.unionLayoutRenta(outputRentability0,outputNoFinancial)
    val crossSicocoAccount = financialProfitProcess.crossSicocoAccounts(dfSicoco,dfAccount)
  
    val crossSicoco5Account = financialProfitProcess.crossSicoco5Accounts(dfSicoco5,dfAccount)
    val crossSicAccCatag = financialProfitProcess.crossSicAccCatag(crossSicocoAccount,dfCatalog)
    val crossSic5AccCatag = financialProfitProcess.crossSic5AccCatag(crossSicoco5Account,dfCatalog)
    val groupByFinalSicoco = financialProfitProcess.groupByFinalSicoco(crossSicAccCatag,odate)
    val groupByFinalSicoco5 = financialProfitProcess.groupByFinalSicoco5(crossSic5AccCatag,odate)
    val sicoBalNoFinUnion = financialProfitProcess.unionLayoutRenta(rentaBalanceAndNoFinancial,groupByFinalSicoco)
  
    val BalanceAndNoFinancialsico5Union = financialProfitProcess.unionLayoutRenta(sicoBalNoFinUnion,groupByFinalSicoco5)
    BalanceAndNoFinancialsico5Union
  }
}
