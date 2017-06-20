package com.packt.masteringakka.bookstore.credit

import akka.actor.ActorSystem
import com.packt.masteringakka.bookstore.common.{BookstorePlan, Bootstrap}

/**
  * Boot class for the credit sub domain of the app
  */
class CreditBoot extends Bootstrap {
  def bootup(system: ActorSystem): List[BookstorePlan] = {
    system.actorOf(CreditCardTransactionHandler.props, CreditCardTransactionHandler.Name)
    Nil
  }
}