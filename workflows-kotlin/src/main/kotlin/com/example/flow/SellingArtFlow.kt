package com.example.flow

import co.paralleluniverse.fibers.Suspendable
import com.example.contract.SellingArtContract
import com.example.state.SellingArtState
import net.corda.core.contracts.Command
import net.corda.core.contracts.requireThat
import net.corda.core.flows.*
import net.corda.core.identity.Party
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.ProgressTracker
import net.corda.core.utilities.ProgressTracker.Step
import java.time.Instant
import java.util.*

/**
 * This flow allows two parties (the [Starter] and the [Receiver]) to come to an agreement about the SellingArt encapsulated
 * within an [SellingArtState].
 *
 * In our simple example, the [Receiver] always accepts a valid SellingArt.
 *
 * These flows have deliberately been implemented by using only the call() method for ease of understanding. In
 * practice we would recommend splitting up the various stages of the flow into sub-routines.
 *
 * All methods called within the [FlowLogic] sub-class need to be annotated with the @Suspendable annotation.
 */

object SellingArtFlow {
    @InitiatingFlow
    @StartableByRPC
    class Starter(val acquirente: Party,
                    val titolo: String,
                    val prezzo: Double,
                    val descrizione: String) : FlowLogic<SellingArtState>(){
        /**
         * The progress tracker checkpoints each stage of the flow and outputs the specified messages when each
         * checkpoint is reached in the code. See the 'progressTracker.currentStep' expressions within the call() function.
         */
        companion object{
            object GENERATING_TRANSACTION : Step("Generating transaction based on new SellingArt.")
            object VERIFYING_TRANSACTION : Step("Verifying contract constraints.")
            object SIGNING_TRANSACTION : Step("Signing transaction with our private key.")
            object GATHERING_SIGS : Step("Gathering the counterparty's signature.") {
                override fun childProgressTracker() = CollectSignaturesFlow.tracker()
            }

            object FINALISING_TRANSACTION : Step("Obtaining notary signature and recording transaction.") {
                override fun childProgressTracker() = FinalityFlow.tracker()
            }

            fun tracker() = ProgressTracker(
                    GENERATING_TRANSACTION,
                    VERIFYING_TRANSACTION,
                    SIGNING_TRANSACTION,
                    GATHERING_SIGS,
                    FINALISING_TRANSACTION
            )

        }

        override val progressTracker = tracker()

        /**
         * The flow logic is encapsulated within the call() method.
         */
        @Suspendable
        override fun call(): SellingArtState {
            // Obtain a reference to the notary we want to use.
            val notary = serviceHub.networkMapCache.notaryIdentities[0]

            // Stage 1.
            progressTracker.currentStep = GENERATING_TRANSACTION
            // Generate an unsigned transaction
            val sellingArt = SellingArtState(
                    titolo,
                    prezzo,
                    descrizione,
                    Instant.now(),
                    serviceHub.myInfo.legalIdentities.first(),
                    acquirente)
            val txCommand = Command(SellingArtContract.Commands.Create(), sellingArt.participants.map { it.owningKey })
            val txBuilder = TransactionBuilder(notary)
                    .addOutputState(sellingArt, SellingArtContract.ID)
                    .addCommand(txCommand)

            // Stage 2
            progressTracker.currentStep = VERIFYING_TRANSACTION
            // Verify that the transaction is valid.
            txBuilder.verify(serviceHub)

            // Stage 3.
            progressTracker.currentStep = SIGNING_TRANSACTION
            // Sign the transaction.
            val partSinedTx = serviceHub.signInitialTransaction(txBuilder)

            // Stage 4.
            progressTracker.currentStep = GATHERING_SIGS
            // Send the state to the counterparty, and receive it back with their signature.
            val otherPartySession = initiateFlow(acquirente)
            val fullSignedTx = subFlow(CollectSignaturesFlow(partSinedTx, setOf(otherPartySession), GATHERING_SIGS.childProgressTracker()))

            // Stage 5.
            progressTracker.currentStep = FINALISING_TRANSACTION
            // Notarise and record the transaction in both parties' vaults.
            subFlow(FinalityFlow(fullSignedTx, setOf(otherPartySession), FINALISING_TRANSACTION.childProgressTracker()))
            return sellingArt
        }

        @InitiatedBy(Starter::class)
        class Receiver(val otherPartySession: FlowSession): FlowLogic<SignedTransaction>(){
            @Suspendable
            override fun call(): SignedTransaction {
                val signTransactionFlow = object : SignTransactionFlow(otherPartySession) {
                    override fun checkTransaction(stx: SignedTransaction) = requireThat {
                        val output = stx.tx.outputs.single().data
                        "This must be an SellingArt transaction." using (output is SellingArtState)
                    }
                }
                val txId = subFlow(signTransactionFlow).id

                return subFlow(ReceiveFinalityFlow(otherPartySession, expectedTxId = txId))
            }
        }
    }
}