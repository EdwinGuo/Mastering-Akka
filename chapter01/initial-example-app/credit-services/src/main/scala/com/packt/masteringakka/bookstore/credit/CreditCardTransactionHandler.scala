package com.packt.masteringakka.bookstore.credit

import java.util.Date

import akka.actor._
import com.packt.masteringakka.bookstore.common.{BookStoreActor, BookstoreDao}
import com.packt.masteringakka.bookstore.domain.credit.{ChargeCreditCard, CreditCardInfo, CreditCardTransaction, CreditTransactionStatus}
import dispatch._
import org.json4s._
import org.json4s.native.Serialization
import org.json4s.native.Serialization.{read, write}
import slick.driver.PostgresDriver.api._

import scala.concurrent.ExecutionContext

/**
  * Companion to the CreditCardTransactionHandler actor
  */
object CreditCardTransactionHandler {
  val Name = "credit-handler"

  def props = Props[CreditCardTransactionHandler]

  implicit val formats = Serialization.formats(NoTypeHints)

  case class ChargeRequest(cardHolder: String, cardType: String, cardNumber: String, expiration: Date, amount: Double)

  case class ChargeResponse(confirmationCode: String)

}

/**
  * Service actor for processing credit card transactions
  */
class CreditCardTransactionHandler extends BookStoreActor {

  import CreditCardTransactionHandler._
  import context.dispatcher

  val dao = new CreditCardTransactionHandlerDao
  val settings = CreditSettings(context.system)

  def receive = {
    case ChargeCreditCard(info, amount) =>
      val result =
        for {
          chargeResp <- chargeCard(info, amount)
          txn = CreditCardTransaction(0, info, amount, CreditTransactionStatus.Approved, Some(chargeResp.confirmationCode), new Date, new Date)
          daoResult <- dao.createCreditTransaction(txn)
        } yield daoResult
      pipeResponse(result)
  }

  /**
    * Calls the external service to charge the credit card
    *
    * @param info The card info to charge
    * @param amount The amount to charge
    * @return a Future wrapping the response from the charge request
    */
  def chargeCard(info: CreditCardInfo, amount: Double) = {
    val jsonBody = write(ChargeRequest(info.cardHolder, info.cardType, info.cardNumber, info.expiration, amount))
    val request = url(settings.creditChargeUrl) << jsonBody
    Http(request OK as.String).map(read[ChargeResponse])
  }
}

/**
  * Doa class for performing Postgres actions related to credit card processing
  */
class CreditCardTransactionHandlerDao(implicit ec: ExecutionContext) extends BookstoreDao {

  import DaoHelpers._

  /**
    * Creates a new credit card transaction record in the db
    *
    * @param txn The credit transaction to create
    * @return a Future wrapping that CreditCardTransaction with the id assigned
    */
  def createCreditTransaction(txn: CreditCardTransaction) = {
    val info = txn.cardInfo
    val insert =
      sqlu"""
      INSERT INTO CreditCardTransaction (cardHolder, cardType, cardNumber, expiration, amount, status, confirmationCode, createTs, modifyTs)
      VALUES (${info.cardHolder}, ${info.cardType}, ${info.cardNumber}, ${info.expiration.toSqlDate}, ${txn.amount}, ${txn.status.toString}, ${txn.confirmationCode}, ${txn.createTs.toSqlDate}, ${txn.modifyTs.toSqlDate})
    """
    val getId = lastIdSelect("creditcardtransaction")
    db.run(insert.andThen(getId).withPinnedSession).map(v => txn.copy(id = v.headOption.getOrElse(0)))
  }

}
