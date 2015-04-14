package scorex.block

import java.math.BigDecimal

import com.google.common.primitives.{Bytes, Ints, Longs}
import org.joda.time.DateTime
import scorex.account.{Account, PublicKeyAccount}
import scorex.crypto.Crypto
import scorex.database.blockchain.PrunableBlockchainStorage
import scorex.transaction.GenesisTransaction
import scorex.transaction.Transaction.ValidationResult


object GenesisBlockParams {
  lazy val generatorSignature = {
    val versionBytes = Ints.toByteArray(genesisVersion)
    val referenceBytes = Bytes.ensureCapacity(genesisReference, 64, 0)
    val generatingBalanceBytes = Longs.toByteArray(generatingBalance)
    val generatorBytes = Bytes.ensureCapacity(genesisGenerator.publicKey, 32, 0)

    val data = Bytes.concat(versionBytes, referenceBytes, generatingBalanceBytes, generatorBytes)
    val digest = Crypto.sha256(data)
    Bytes.concat(digest, digest)
  }.ensuring(sig => sig.size == BlockGenerationData.GENERATOR_SIGNATURE_LENGTH)

  val genesisVersion = 1
  val genesisReference = Array[Byte](1, 1, 1, 1, 1, 1, 1, 1)
  val genesisTimestamp = new DateTime(2015, 4, 13, 10, 35).getMillis
  val generatingBalance = 10000000
  val genesisGenerator = new PublicKeyAccount(Array[Byte](1, 1, 1, 1, 1, 1, 1, 1))
  val ipoMembers = List(
    "2UyntBprhFgZPJ1tCtKBAwryiSnDSk9Xmh8",
    "Y2BXLjiAhPUMSo8iBbDEhv81VwKnytTXsH",
    "a8zcZPAj4HJjNLCmfRPVxurcB4REj8YNse",
    "hWUV4cjcGPgKjaNuRWAYyFMDZKadSPuwfP",
    "2etPX8BRivVTqr3vBvaCBeebhhGipbuzBNW",
    "dNKdbrqeykhxsnUpLjFTDHtTWHquiCcBGe",
    "5MkGmznxmA1Jm2F5KtxYVaf2Bfa6sy2XS1",
    "2Cqn5vN5iv7jDMehTiXTv3SGpxrCDAkAnBT",
    "2ihjht1NWTv2T8nKDMzx2RMmp7ZDEchXJus",
    "2kx3DyWJpYYfLErWpRMLHwkL1ZGyKHAPNKr"
  )
  val genesisTransactions = ipoMembers.map { addr =>
    val recipient = new Account(addr)
    GenesisTransaction(recipient, new BigDecimal("1000000000").setScale(8), genesisTimestamp)
  }
}

object GenesisBlock extends Block(version = GenesisBlockParams.genesisVersion,
  reference = GenesisBlockParams.genesisReference,
  timestamp = GenesisBlockParams.genesisTimestamp,
  generator = GenesisBlockParams.genesisGenerator,
  new BlockGenerationData(GenesisBlockParams.generatingBalance, GenesisBlockParams.generatorSignature),
  transactions = GenesisBlockParams.genesisTransactions,
  transactionsSignature = GenesisBlockParams.generatorSignature) {

  override def parent() = None

  override def isSignatureValid() = {
    val versionBytes = Bytes.ensureCapacity(Longs.toByteArray(version), 4, 0)
    val referenceBytes = Bytes.ensureCapacity(reference, 64, 0)
    val generatingBalanceBytes = Bytes.ensureCapacity(Longs.toByteArray(generationData.generatingBalance), 8, 0)
    val generatorBytes = Bytes.ensureCapacity(generator.publicKey, 32, 0)

    val data = Bytes.concat(versionBytes, referenceBytes, generatingBalanceBytes, generatorBytes)
    val digest0 = Crypto.sha256(data)
    val digest = Bytes.concat(digest0, digest0)

    digest.sameElements(generationData.generatorSignature) && digest.sameElements(transactionsSignature)
  }

  override def isValid() =
    PrunableBlockchainStorage.isEmpty() && transactions.forall(_.isValid() == ValidationResult.VALIDATE_OKE)
}
