package com.example.contract

import com.example.state.SellingArtState
import net.corda.core.contracts.CommandData
import net.corda.core.contracts.Contract
import net.corda.core.contracts.TypeOnlyCommandData
import net.corda.core.contracts.requireThat
import net.corda.core.transactions.LedgerTransaction
import java.lang.IllegalArgumentException
import java.security.PublicKey
import java.time.Instant

/**
 * A implementation of a basic smart contract in Corda.
 *
 * This contract enforces rules regarding the creation of a valid [SellingArtState], which in turn encapsulates an [SellingArtState].
 *
 * For a new [SellingArtState] to be issued onto the ledger, a transaction is required which takes:
 * - Zero input states.
 * - One output state: the new [SellingArtState].
 * - An Create() command with the public keys of both the venditore and the acquirente.
 *
 * All contracts must sub-class the [Contract] interface.
 */

class SellingArtContract : Contract {
    companion object {
        @JvmStatic
        val ID = "com.example.contract.SellingArtContract"
    }

    /**
     * The verify() function of all the states' contracts must not throw an exception for a transaction to be
     * considered valid.
     */
    override fun verify(tx: LedgerTransaction) {
        val commands = tx.commandsOfType<Commands>()
        for(command in commands){
            val setOfSigners = command.signers.toSet()
            when(command.value){
                is Commands.Create -> verifyCreate(tx, setOfSigners)
                else -> throw IllegalArgumentException("Unrecognised command.")
            }
        }
    }

    private fun verifyCreate(tx: LedgerTransaction, signers: Set<PublicKey>) = requireThat{
        // Generic constraints around the SellingArt transaction.
        "No inputs should be consumed when creating a transaction." using (tx.inputStates.isEmpty())
        "Only one transaction state should be created." using (tx.outputStates.size == 1)
        val sellingArtState = tx.outputsOfType<SellingArtState>().single()
        "Venditore and Acquirente cannot be the same entity" using (sellingArtState.venditore != sellingArtState.acquirente)
        "All of the participants must be signers." using (signers.containsAll(sellingArtState.participants.map { it.owningKey }))

        // SellingArt-specific constraints.
        "The SellingArt's prezzo must be non-negative" using (sellingArtState.prezzo > 0)
        "The SellingArt's titolo must be non-empty" using (sellingArtState.titolo.isNotEmpty())
        "The SellingArt's data cannot be in the future" using (sellingArtState.data < Instant.now())
    }

    interface Commands : CommandData {
        class Create : Commands, TypeOnlyCommandData()
    }
}