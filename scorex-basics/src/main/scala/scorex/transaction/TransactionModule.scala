package scorex.transaction

import scorex.block.{BlockProcessingModule, Block}

trait TransactionModule[TransactionBlockData] extends BlockProcessingModule[TransactionBlockData]{

  val state: State

  val history: History

  def isValid(block: Block):Boolean

  def transactions(block: Block): Seq[Transaction]

  def process(block: Block): Unit = state.processBlock(block, reversal = false)

  def popOff(block: Block): Unit = state.processBlock(block, reversal = true)

  def packUnconfirmed(): TransactionBlockData

  def clearFromUnconfirmed(data: TransactionBlockData): Unit

  lazy val balancesSupport: Boolean = state match {
    case _: State with BalanceSheet => true
    case _ => false
  }

  lazy val accountWatchingSupport: Boolean = state match {
    case _: State with AccountTransactionsHistory => true
    case _ => false
  }
}