package com.example.schema

import net.corda.core.schemas.MappedSchema
import net.corda.core.schemas.PersistentState
import java.time.Instant
import java.util.*
import javax.persistence.Column
import javax.persistence.Entity
import javax.persistence.Table

object SellingArtSchema

object SellingArtSchemaV1 : MappedSchema(
        schemaFamily = SellingArtSchema.javaClass,
        version = 1,
        mappedTypes = listOf(PersistentSellingArt::class.java)){
         @Entity
         @Table(name = "selling_art_table")
         class PersistentSellingArt(
                 @Column(name = "venditore")
                 var venditoreName : String,

                 @Column(name = "acquirente")
                 var acquirenteName: String,

                 @Column(name = "titolo")
                 var titolo : String,

                 @Column(name = "prezzo")
                 var prezzo : Double,

                 @Column(name = "descrizione")
                 var descrizione : String,

                 @Column(name = "data")
                 var data : Instant,

                 @Column(name = "linear_id")
                 var linearId: UUID
         ): PersistentState() {
             // Default constructor required by hibernate.
             constructor(): this("", "", "", 0.0, "", Instant.now(), UUID.randomUUID())
         }
        }

