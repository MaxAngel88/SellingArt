package com.example.server

import com.example.flow.ExampleFlow.Initiator
import com.example.flow.SellingArtFlow
import com.example.state.IOUState
import com.example.state.SellingArtState
import net.corda.core.contracts.StateAndRef
import net.corda.core.identity.CordaX500Name
import net.corda.core.messaging.startTrackedFlow
import net.corda.core.messaging.vaultQueryBy
import net.corda.core.utilities.getOrThrow
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType.*
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import pojo.ResponsePojo
import javax.servlet.http.HttpServletRequest
import javax.xml.ws.Response

val SERVICE_NAMES = listOf("Notary", "Network Map Service")

/**
 *  A Spring Boot Server API controller for interacting with the node via RPC.
 */

@RestController
@RequestMapping("/api/example/") // The paths for GET and POST requests are relative to this base path.
class MainController(rpc: NodeRPCConnection) {

    companion object {
        private val logger = LoggerFactory.getLogger(RestController::class.java)
    }

    private val myLegalName = rpc.proxy.nodeInfo().legalIdentities.first().name
    private val proxy = rpc.proxy

    /**
     * Returns the node's name.
     */
    @GetMapping(value = [ "me" ], produces = [ APPLICATION_JSON_VALUE ])
    fun whoami() = mapOf("me" to myLegalName)

    /**
     * Returns all parties registered with the network map service. These names can be used to look up identities using
     * the identity service.
     */
    @GetMapping(value = [ "peers" ], produces = [ APPLICATION_JSON_VALUE ])
    fun getPeers(): Map<String, List<CordaX500Name>> {
        val nodeInfo = proxy.networkMapSnapshot()
        return mapOf("peers" to nodeInfo
                .map { it.legalIdentities.first().name }
                //filter out myself, notary and eventual network map started by driver
                .filter { it.organisation !in (SERVICE_NAMES + myLegalName.organisation) })
    }

    /**
     * Displays all IOU states that exist in the node's vault.
     */
    @GetMapping(value = [ "ious" ], produces = [ APPLICATION_JSON_VALUE ])
    fun getIOUs() : ResponseEntity<List<StateAndRef<IOUState>>> {
        return ResponseEntity.ok(proxy.vaultQueryBy<IOUState>().states)
    }

    /**
     * Initiates a flow to agree an IOU between two parties.
     *
     * Once the flow finishes it will have written the IOU to ledger. Both the lender and the borrower will be able to
     * see it when calling /spring/api/ious on their respective nodes.
     *
     * This end-point takes a Party name parameter as part of the path. If the serving node can't find the other party
     * in its network map cache, it will return an HTTP bad request.
     *
     * The flow is invoked asynchronously. It returns a future when the flow's call() method returns.
     */

    @PostMapping(value = [ "create-iou" ], produces = [ TEXT_PLAIN_VALUE ], headers = [ "Content-Type=application/x-www-form-urlencoded" ])
    fun createIOU(request: HttpServletRequest): ResponseEntity<String> {
        val iouValue = request.getParameter("iouValue").toInt()
        val partyName = request.getParameter("partyName")
        if(partyName == null){
            return ResponseEntity.badRequest().body("Query parameter 'partyName' must not be null.\n")
        }
        if (iouValue <= 0 ) {
            return ResponseEntity.badRequest().body("Query parameter 'iouValue' must be non-negative.\n")
        }
        val partyX500Name = CordaX500Name.parse(partyName)
        val otherParty = proxy.wellKnownPartyFromX500Name(partyX500Name) ?: return ResponseEntity.badRequest().body("Party named $partyName cannot be found.\n")

        return try {
            val signedTx = proxy.startTrackedFlow(::Initiator, iouValue, otherParty).returnValue.getOrThrow()
            ResponseEntity.status(HttpStatus.CREATED).body("Transaction id ${signedTx.id} committed to ledger.\n")

        } catch (ex: Throwable) {
            logger.error(ex.message, ex)
            ResponseEntity.badRequest().body(ex.message!!)
        }
    }

    /**
     * Displays all IOU states that only this node has been involved in.
     */
    @GetMapping(value = [ "my-ious" ], produces = [ APPLICATION_JSON_VALUE ])
    fun getMyIOUs(): ResponseEntity<List<StateAndRef<IOUState>>>  {
        val myious = proxy.vaultQueryBy<IOUState>().states.filter { it.state.data.lender.equals(proxy.nodeInfo().legalIdentities.first()) }
        return ResponseEntity.ok(myious)
    }

    /****************************************************************************************/

    /**
     * Displays all SellingArt states that exist in the node's vault.
     */
    @GetMapping(value = [ "all-selling" ], produces = [ APPLICATION_JSON_VALUE ])
    fun getSellings() : ResponseEntity<List<StateAndRef<SellingArtState>>> {
        return ResponseEntity.ok(proxy.vaultQueryBy<SellingArtState>().states)
    }

    /**
     * Initiates a flow to agree an SellingArt between two parties.
     *
     * Once the flow finishes it will have written the SellingArt to ledger. Both the venditore and the acquirente will be able to
     * see it when calling /spring/api/all-selling on their respective nodes.
     *
     * This end-point takes a Party name parameter as part of the path. If the serving node can't find the other party
     * in its network map cache, it will return an HTTP bad request.
     *
     * The flow is invoked asynchronously. It returns a future when the flow's call() method returns.
     */


    @PostMapping(value = [ "create-sellingArt" ], produces = [ TEXT_PLAIN_VALUE ], headers = [ "Content-Type=application/x-www-form-urlencoded" ])
    fun createSellingArt(request: HttpServletRequest): ResponseEntity<String> {

        val titolo = request.getParameter("titolo")
        val prezzo = request.getParameter("prezzo").toDouble()
        val descrizione = request.getParameter("descrizione")
        val acquirente = request.getParameter("acquirente")

        if(acquirente == null){
            return ResponseEntity.badRequest().body("Query parameter 'partyName' must not be null.\n")
        }
        if (prezzo <= 0 ) {
            return ResponseEntity.badRequest().body("Query parameter 'prezzo' must be non-negative.\n")
        }
        val partyX500Name = CordaX500Name.parse(acquirente)
        val otherParty = proxy.wellKnownPartyFromX500Name(partyX500Name) ?: return ResponseEntity.badRequest().body("Party named $acquirente cannot be found.\n")

        return try {
            val signedTx = proxy.startTrackedFlow(SellingArtFlow::Starter, otherParty, titolo, prezzo, descrizione).returnValue.getOrThrow()
            ResponseEntity.status(HttpStatus.CREATED).body("Transaction id ${signedTx.linearId} committed to ledger.\n")

        } catch (ex: Throwable) {
            logger.error(ex.message, ex)
            ResponseEntity.badRequest().body(ex.message!!)
        }

    }

    /**
     * Displays all SellingArt states that only this node has been involved in.
     */
    @GetMapping(value = [ "my-sellingArt" ], produces = [ APPLICATION_JSON_VALUE ])
    fun getMySellingArt(): ResponseEntity<List<StateAndRef<SellingArtState>>>  {
        val mySellingArt = proxy.vaultQueryBy<SellingArtState>().states.filter { it.state.data.venditore.equals(proxy.nodeInfo().legalIdentities.first()) }
        return ResponseEntity.ok(mySellingArt)
    }

}
