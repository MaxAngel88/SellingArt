package com.example.state

import com.example.contract.SellingArtContract
import com.example.schema.SellingArtSchemaV1
import net.corda.core.contracts.BelongsToContract
import net.corda.core.contracts.LinearState
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.identity.AbstractParty
import net.corda.core.identity.Party
import net.corda.core.schemas.MappedSchema
import net.corda.core.schemas.PersistentState
import net.corda.core.schemas.QueryableState
import java.time.Instant

@BelongsToContract(SellingArtContract::class)
data class SellingArtState (val titolo: String,
                            val prezzo: Double,
                            val descrizione: String,
                            val data: Instant,
                            val venditore: Party,
                            val acquirente: Party,
                            override val linearId: UniqueIdentifier = UniqueIdentifier()):
        LinearState, QueryableState{

    override val participants: List<AbstractParty> get() = listOf(venditore, acquirente)

    override fun generateMappedObject(schema: MappedSchema): PersistentState {
        return when (schema) {
            is SellingArtSchemaV1 -> SellingArtSchemaV1.PersistentSellingArt(
                    this.venditore.name.toString(),
                    this.acquirente.name.toString(),
                    this.titolo,
                    this.prezzo,
                    this.descrizione,
                    this.data,
                    this.linearId.id
            )
            else -> throw IllegalArgumentException("Unrecognised schema $schema")
        }
    }

    override fun supportedSchemas(): Iterable<MappedSchema> = listOf(SellingArtSchemaV1)

}