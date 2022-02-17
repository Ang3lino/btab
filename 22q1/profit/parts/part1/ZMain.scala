
// package com.bbva.datioamproduct.mbmi_financial_profit_part1

// import com.bbva.datioamproduct.mbmi_financial_profit_part1.transform.FinancialProfitTransform
import com.datio.kirby.api.exceptions.KirbyException
import com.datio.kirby.{CheckFlow, errors}
// import com.bbva.datioamproduct.util.io.output.Writer
import com.datio.spark.InitSpark
import com.datio.spark.metric.model.BusinessInformation
import com.typesafe.config.Config
import org.apache.commons.lang.exception.ExceptionUtils
import org.apache.spark.sql.SparkSession

import scala.util.{Failure, Success, Try}

/**
  * Main file for Launcher process.
  * Implements InitSpark which includes metrics and SparkSession.
  *
  * Configuration for this class should be expressed in HOCON like this:
  *
  * Launcher {
  * ...
  * }
  *
  */
// private[mbmi_financial_profit_part1] 
trait LauncherTrait extends CheckFlow with InitSpark {
  this: InitSpark =>
  /**
    * @param spark  Initialized SparkSession
    * @param config Config retrieved from args
    */
  override def runProcess(sparkT: SparkSession, config: Config): Int = {
    Try {
      implicit val spark: SparkSession = sparkT
      lazy val transform = new FinancialProfitTransform(sparkT,config)

      // val writer = new Writer(config)

      //   logger.info("Apply transformations")
      println("Apply transformations")

      val dataFrame = transform.runFinancialProcess()

      // logger.info("write Dataframe")
      println("write Dataframe")
      // writer(dataFrame, "kirby.output")
      
      val pathSandbox = "/intelligence/inbmin/analytic/users/XMX2250/workspace/mbmi/"
      dataFrame.write.mode("overwrite").parquet(pathSandbox + "t_mbmi_beyg_financial_profit_tmp1")
    }
    match {
      case Success(_) => 0
      case Failure(ex: Throwable) => {
        logger.warn(s"Input Args: ${config.toString}")
        logger.error("Exception: {}", ExceptionUtils.getStackTrace(ex))
        ex match {
          case kex: KirbyException => throw kex
          case other: Exception => throw new KirbyException(errors.GENERIC_LAUNCHER_ERROR, other)
        }
      }
    }

  }

  override def defineBusinessInfo(config: Config): BusinessInformation =
    BusinessInformation(exitCode = 0, entity = "", path = "", mode = "",
      schema = "", schemaVersion = "", reprocessing = "")

}

object FinancialLauncherPart1 extends LauncherTrait with InitSpark


import com.typesafe.config.ConfigFactory
// Runner

val config = s""" kirby {
  input {
    odate=2022-01-31
    paths=[]
    pathAccounts = "/data/master/mbmi/data/catalogos/t_mbmi_beyg_accounts/"
    pathExchangeRate = "/data/master/mdco/data/finance/t_mdco_tkidswlm/"
    pathCatalogBg = "/data/master/mbmi/data/catalogos/t_mbmi_persl_acct/"
    pathB40W = "/data/master/mbmi/data/transaccionalidad/t_mbmi_transact_bg"
    pathSicoco = "/data/master/mbmi/data/transaccionalidad/t_mbmi_beyg_sicoco/"
    pathSicoco5 = "/data/master/mmfi/data/t_mmfi_tmidssicf/"
    pathProfitTmp0 = "/data/master/mbmi/datatmp/globalMarket/t_mbmi_beyg_financial_profit_tmp0"

    type=parquet
  }
  output {
    mode=reprocess
    reprocess=["cutoff_date=2022-01-31"]
    coalesce {
      partitions=50
    }
    partition=[
      "cutoff_date"
    ]
    path="/data/master/mbmi/datatmp/globalMarket/t_mbmi_beyg_financial_profit_tmp1"
    type=parquet
  }
}
"""

FinancialLauncher.runProcess(spark, ConfigFactory.parseString(config))