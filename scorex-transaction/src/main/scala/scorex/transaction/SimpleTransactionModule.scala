package scorex.transaction

import com.google.common.primitives.{Bytes, Ints}
import play.api.libs.json.{JsObject, Json}
import scorex.account.Account
import scorex.block.{Block, BlockField}
import scorex.consensus.ConsensusModule
import scorex.transaction.LagonakiTransaction.ValidationResult
import scorex.transaction.state.database.UnconfirmedTransactionsDatabaseImpl
import scorex.transaction.state.database.blockchain.{StoredBlockchain, StoredState}
import scorex.utils.ScorexLogging

case class TransactionsBlockField(override val value: Seq[Transaction])
  extends BlockField[Seq[Transaction]] {

  import SimpleTransactionModule.MaxTransactionsPerBlock

  override val name = "transactions"

  override lazy val json: JsObject = Json.obj(name -> Json.arr(value.map(_.json())))

  override lazy val bytes: Array[Byte] = {
    val txCount = value.size.ensuring(_ <= MaxTransactionsPerBlock).toByte
    value.foldLeft(Array(txCount)) { case (bs, tx) =>
      val txBytes = tx.bytes()
      bs ++ Bytes.ensureCapacity(Ints.toByteArray(txBytes.length), 4, 0) ++ txBytes
    }
  }
}

class SimpleTransactionModule(implicit val settings: TransactionSettings,
                              consensusModule: ConsensusModule[_])
  extends TransactionModule[SimpleTransactionModule.StoredInBlock] with ScorexLogging {

  import SimpleTransactionModule._

  val TransactionSizeLength = 4

  override val history = new StoredBlockchain(settings.dataDirOpt)(consensusModule, this)
  override val state = new StoredState(settings.dataDirOpt)

  /**
   * In Lagonaki, transaction-related data is just sequence of transactions. No Merkle-tree root of txs / state etc
   * @param bytes - serialized sequence of transaction
   * @return
   */
  override def parseBlockData(bytes: Array[Byte]): TransactionsBlockField = {
    bytes.isEmpty match {
      case true => TransactionsBlockField(Seq())
      case false =>
        val txCount = bytes.head // so 255 txs max
      val txData = bytes.tail
        formBlockData((1 to txCount).foldLeft((0: Int, Seq[LagonakiTransaction]())) { case ((pos, txs), _) =>
          val transactionLengthBytes = txData.slice(pos, pos + TransactionSizeLength)
          val transactionLength = Ints.fromByteArray(transactionLengthBytes)
          val transactionBytes = txData.slice(pos + TransactionSizeLength, pos + TransactionSizeLength + transactionLength)
          val transaction = LagonakiTransaction.parse(transactionBytes)

          (pos + TransactionSizeLength + transactionLength, txs :+ transaction)
        }._2)
    }
  }

  override def formBlockData(transactions: SimpleTransactionModule.StoredInBlock): TransactionsBlockField =
    TransactionsBlockField(transactions)

  override def transactions(block: Block): SimpleTransactionModule.StoredInBlock =
    block.transactionDataField.asInstanceOf[TransactionsBlockField].value //todo: asInstanceOf

  override def packUnconfirmed(): SimpleTransactionModule.StoredInBlock =
    UnconfirmedTransactionsDatabaseImpl.all()

  //todo: clear unconfirmed txs on receiving a block
  override def clearFromUnconfirmed(data: SimpleTransactionModule.StoredInBlock): Unit = {
    data.foreach(tx => UnconfirmedTransactionsDatabaseImpl.getBySignature(tx.signature) match {
      case Some(unconfirmedTx) => UnconfirmedTransactionsDatabaseImpl.remove(unconfirmedTx)
      case None =>
    })

    val height = history.height()
    if (height > MaxBlocksForUnconfirmed + 1) {
      val time10 = history.blockAt(height - MaxBlocksForUnconfirmed).get.timestampField.value
      UnconfirmedTransactionsDatabaseImpl.all().foreach { tx =>
        if (tx.timestamp < time10) UnconfirmedTransactionsDatabaseImpl.remove(tx)
      }

    }
  }

  override def genesisData: BlockField[SimpleTransactionModule.StoredInBlock] = {
    val ipoMembers = List(
      "27ZjrCKQP66AZsUGwud8c3ahcj13cVaX4cF",
      "2KhD4L7tknTHe7yZoVbhujPUVkQDdhA3QGS",
      "2G7PDfxdJG8jTDX8mM5uSbW8JFqpCMgQ5Xk",
      "2GqbZk5UHagTFn7jhwohSCj3RaZrkj8Trcs",
      "22AVpTZnZEav2SWGBZAHR3Vdrxojd59ETkX",
      "61fcGqP9ULkoEhzdsRCbYmZmxzqK95b3Un",
      "NNtdNKMeASKPNxM22abQWv8LEw9NzXpthq",
      "2UNCFoaXLeZwpr1n6BfSi1qKhrVLMXPzedM",
      "UDiEjY7hHhobG5188YB7DfQ2eZgaDaU8jS",
      "XUU8gXPDyWGRPkRnLrR24cCryyRZhghdHo"
    )

    val timestamp = 0L

    val txs = ipoMembers.map { addr =>
      val recipient = new Account(addr)
      GenesisTransaction(recipient, 1000000000L, timestamp)
    }

    TransactionsBlockField(txs)
  }

  override def isValid(block: Block): Boolean = transactions(block).forall {
    case ptx: PaymentTransaction =>
      ptx.isSignatureValid() && ptx.validate()(this) == ValidationResult.ValidateOke
    case gtx: GenesisTransaction =>
      history.heightOf(block).getOrElse(0) == 1
    case otx: Any =>
      log.error(s"Wrong kind of tx: $otx")
      false
  }
}

object SimpleTransactionModule {
  type StoredInBlock = Seq[Transaction]

  val MaxBlocksForUnconfirmed = 10
  val MaxTransactionsPerBlock = 4096
}