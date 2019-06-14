package pojo

import net.corda.core.serialization.CordaSerializable
import java.time.Instant

@CordaSerializable
data class SellingArtPojo (
    val titolo : String = "",
    val prezzo : Double = 0.0,
    val descrizione : String = "",
    val data : Instant = Instant.now(),
    val venditore : String = "",
    val acquirente : String = ""
)