package org.ergoplatform.network

import akka.actor.ActorRef
import org.ergoplatform.modifiers.ErgoFullBlock
import org.ergoplatform.modifiers.mempool.AnyoneCanSpendTransaction
import org.ergoplatform.modifiers.mempool.proposition.AnyoneCanSpendProposition
import org.ergoplatform.network.ErgoNodeViewSynchronizer.{CheckModifiersToDownload, MissedModifiers}
import org.ergoplatform.nodeView.history.{ErgoHistory, ErgoSyncInfo, ErgoSyncInfoMessageSpec}
import org.ergoplatform.nodeView.mempool.ErgoMemPool
import org.ergoplatform.nodeView.state.UtxoState
import org.ergoplatform.nodeView.wallet.ErgoWallet
import scorex.core.NodeViewHolder._
import scorex.core.network.NetworkController.SendToNetwork
import scorex.core.network.message.Message
import scorex.core.network.{NodeViewSynchronizer, SendToRandom}
import scorex.core.settings.NetworkSettings
import scorex.core.{ModifierId, ModifierTypeId, NodeViewHolder}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._

class ErgoNodeViewSynchronizer(networkControllerRef: ActorRef,
                               viewHolderRef: ActorRef,
                               localInterfaceRef: ActorRef,
                               syncInfoSpec: ErgoSyncInfoMessageSpec.type,
                               networkSettings: NetworkSettings) extends NodeViewSynchronizer[AnyoneCanSpendProposition.type, AnyoneCanSpendTransaction,
  ErgoSyncInfo, ErgoSyncInfoMessageSpec.type](networkControllerRef, viewHolderRef, localInterfaceRef,
  syncInfoSpec, networkSettings) {
  override protected val deliveryTracker = new ErgoDeliveryTracker

  private val toDownloadCheckInterval = 3.seconds

  override def preStart(): Unit = {
    viewHolderRef ! Subscribe(Seq(NodeViewHolder.EventType.DownloadNeeded))
    super.preStart()
    context.system.scheduler.schedule(toDownloadCheckInterval, toDownloadCheckInterval)(self ! CheckModifiersToDownload)
    initializeToDownload()
  }

  protected def initializeToDownload(): Unit = {
    viewHolderRef ! GetDataFromCurrentView[ErgoHistory, UtxoState, ErgoWallet, ErgoMemPool, MissedModifiers] { v =>
      MissedModifiers(v.history.missedModifiersForFullChain())
    }
  }

  protected def onMissedModifiers(): Receive = {
    case MissedModifiers(ids) =>
      ids.foreach(id => requestDownload(id._1, id._2))

  }

  protected val onSemanticallySuccessfulModifier: Receive = {
    case SemanticallySuccessfulModifier(mod: ErgoFullBlock) =>
    //Do nothing, other nodes will request required modifiers via ProgressInfo.toDownload
    case SemanticallySuccessfulModifier(mod) =>
      broadcastModifierInv(mod)
  }

  override protected def viewHolderEvents: Receive = onSemanticallySuccessfulModifier orElse onDownloadRequest orElse
    onCheckModifiersToDownload orElse onMissedModifiers orElse super.viewHolderEvents

  def onDownloadRequest: Receive = {
    case DownloadRequest(modifierTypeId: ModifierTypeId, modifierId: ModifierId) =>
      requestDownload(modifierTypeId, modifierId)
  }

  protected val onCheckModifiersToDownload: Receive = {
    case CheckModifiersToDownload =>
      deliveryTracker.removeOutdatedToDownload()
      deliveryTracker.downloadRetry().foreach(i => requestDownload(i._2.tp, i._1))

  }


  def requestDownload(modifierTypeId: ModifierTypeId, modifierId: ModifierId): Unit = {
    val msg = Message(requestModifierSpec, Right(modifierTypeId -> Seq(modifierId)), None)
    //Full nodes should be here, not a random peer
    networkControllerRef ! SendToNetwork(msg, SendToRandom)
    deliveryTracker.downloadRequested(modifierTypeId, modifierId)
  }
}

object ErgoNodeViewSynchronizer {

  case object CheckModifiersToDownload

  case class MissedModifiers(m: Seq[(ModifierTypeId, ModifierId)])

}