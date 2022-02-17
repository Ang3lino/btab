
// FinancialProfitProcess

// package com.bbva.datioamproduct.mbmi_financial_profit_part2
// LauncherTrait 

// import com.bbva.datioamproduct.mbmi_financial_profit_part2.transform.FinancialProfitTransform
// import com.bbva.datioamproduct.mbmi_financial_profit_part2.data.DataFPConstants._
import com.datio.kirby.api.exceptions.KirbyException
import com.datio.kirby.{CheckFlow, errors}
import com.bbva.datioamproduct.util.validate.Validate
import com.bbva.datioamproduct.util.io.output.Writer
import com.datio.fds.amlib.utils.HdfsUtils
import com.datio.spark.InitSpark
import com.datio.spark.metric.model.BusinessInformation
import com.typesafe.config.Config
import org.apache.commons.lang.exception.ExceptionUtils
import org.apache.spark.sql.SparkSession
import com.datio.fds.amlib.implicits._

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

// private[mbmi_financial_profit_part2] 
trait LauncherTrait extends CheckFlow with InitSpark {
  this: InitSpark =>
  /**
    * @param spark  Initialized SparkSession
    * @param config Config retrieved from args
    */

  def deleteTemporals(config: Config) (implicit spark: SparkSession): Unit = {
    spark.sqlContext.clearCache ()
    HdfsUtils.delete(config.getString(TMP_0_FILE))
    HdfsUtils.delete(config.getString(TMP_1_FILE))
  }
  
  override def runProcess(sparkT: SparkSession, config: Config): Int = {
    Try {
      implicit val spark: SparkSession = sparkT
      lazy val transform = new FinancialProfitTransform(sparkT,config)

      val validateSchema = new Validate(config)
      val writer = new Writer(config)

      logger.info("Apply transformations")
      val dataFrameProcess = transform.runFinanProcess()
      val dataFrameProcessTwo = transform.runFinanceProcess(dataFrameProcess)
      val dataFrameProcessThree = transform.runAddFinanceProcess(dataFrameProcessTwo)
      val dataFrameProcessFour = transform.runLinkFinanceProcess(dataFrameProcessThree)

      val schema = config.kirby.parseSchema("kirby.output",includeMetadata = true) match {
        case Right(structType) => structType
        case Left(error) =>  throw error
      }

    //   dataFrameProcessFour.kirby.validate(schema) match {
    //     case Right(df) => df.kirby.write(config,"kirby.output")
    //     case Left(error) => throw error
      }

      logger.info("Delete temporals")
      deleteTemporals(config)

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

object FinancialLauncherPart2 extends LauncherTrait with InitSpark
