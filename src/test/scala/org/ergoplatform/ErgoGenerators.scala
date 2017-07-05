package org.ergoplatform

import org.ergoplatform.modifiers.history.{Header, MinimalHeader}
import org.ergoplatform.modifiers.mempool.AnyoneCanSpendTransaction
import org.ergoplatform.modifiers.mempool.proposition.{AnyoneCanSpendNoncedBox, AnyoneCanSpendProposition}
import org.ergoplatform.nodeView.history.ErgoSyncInfo
import org.ergoplatform.settings.Constants
import org.scalacheck.{Arbitrary, Gen}
import scorex.core.block.Block
import scorex.core.block.Block._
import scorex.core.transaction.state.{Insertion, StateChanges}
import scorex.testkit.CoreGenerators

trait ErgoGenerators extends CoreGenerators {

  val anyoneCanSpendProposition = new AnyoneCanSpendProposition

  lazy val pGen: Gen[(AnyoneCanSpendProposition, Long)] = for {
    long <- positiveLongGen
  } yield (anyoneCanSpendProposition, long)

  lazy val anyoneCanSpendTransactionGen: Gen[AnyoneCanSpendTransaction] = for {
    timestamp <- positiveLongGen
    from: IndexedSeq[(AnyoneCanSpendProposition, Long)] <- smallInt.flatMap(i => Gen.listOfN(i + 1, pGen).map(_.toIndexedSeq))
    to: IndexedSeq[(AnyoneCanSpendProposition, Long)] <- smallInt.flatMap(i => Gen.listOfN(i, pGen).map(_.toIndexedSeq))
  } yield AnyoneCanSpendTransaction(from, to, timestamp)

  lazy val anyoneCanSpendBoxGen: Gen[AnyoneCanSpendNoncedBox] = for {
    nonce <- positiveLongGen
    value <- positiveLongGen
  } yield AnyoneCanSpendNoncedBox(anyoneCanSpendProposition, nonce, value)

  lazy val stateChangesGen: Gen[StateChanges[AnyoneCanSpendProposition, AnyoneCanSpendNoncedBox]] = anyoneCanSpendBoxGen
    .map(b => StateChanges[AnyoneCanSpendProposition, AnyoneCanSpendNoncedBox](Seq(Insertion(b))))

  lazy val ergoSyncInfoGen: Gen[ErgoSyncInfo] = for {
    answer <- Arbitrary.arbitrary[Boolean]
    idGenerator <- genBytesList(Constants.ModifierIdSize)
    ids <- Gen.nonEmptyListOf(genBytesList(Constants.ModifierIdSize)).map(_.take(ErgoSyncInfo.MaxBlockIds))
  } yield ErgoSyncInfo(answer, ids)

  lazy val ergoHeaderGen: Gen[Header] = for {
    version <- Arbitrary.arbitrary[Byte]
    parentId <- genBytesList(Constants.ModifierIdSize)
    stateRoot <- genBytesList(Constants.ModifierIdSize)
    adRoot <- genBytesList(Constants.ModifierIdSize)
    transactionsRoot <- genBytesList(Constants.ModifierIdSize)
    nonce <- Arbitrary.arbitrary[Int]
    interlinks <- Gen.nonEmptyListOf(genBytesList(Constants.ModifierIdSize)).map(_.take(128))
    timestamp <- positiveLongGen
  } yield Header(version, parentId, interlinks, adRoot, stateRoot, transactionsRoot, timestamp, nonce)

  lazy val ergoMinimalHeaderGen: Gen[MinimalHeader] = for {
    payloadHash <- genBytesList(Constants.ModifierIdSize)
    nonce <- Arbitrary.arbitrary[Int]
  } yield MinimalHeader(payloadHash, nonce)

}
