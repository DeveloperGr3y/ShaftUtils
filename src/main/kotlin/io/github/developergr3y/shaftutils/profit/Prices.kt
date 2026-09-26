package io.github.developergr3y.shaftutils.profit

import com.google.gson.JsonParser
import io.github.developergr3y.shaftutils.ShaftUtils
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

/**
 * Bazaar prices from Hypixel's public bazaar data (no API key needed), refreshed every few minutes when asked for.
 * [sellOffer] is what you'd get listing a sell offer (the lowest current offer); [instantSell] is what buyers pay
 * right now (the highest buy order). Items that aren't on the bazaar have no price.
 */
object Prices {
    private const val URL = "https://api.hypixel.net/v2/skyblock/bazaar"
    private const val REFRESH_MS = 5 * 60_000L

    private class Quote(val instantSell: Double, val sellOffer: Double)

    @Volatile private var quotes: Map<String, Quote> = emptyMap()
    @Volatile private var fetchedAt = 0L
    @Volatile private var fetching = false
    private val http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build()

    val loaded get() = quotes.isNotEmpty()

    /** Fetches in the background if the prices are missing or stale. Safe to call often. */
    fun refreshIfStale() {
        val now = System.currentTimeMillis()
        if (fetching || now - fetchedAt < REFRESH_MS) return
        fetching = true
        val request = HttpRequest.newBuilder(URI(URL)).timeout(Duration.ofSeconds(20))
            .header("User-Agent", "ShaftUtils/${ShaftUtils.version}").GET().build()
        http.sendAsync(request, HttpResponse.BodyHandlers.ofString())
            .thenAccept { response ->
                if (response.statusCode() == 200) {
                    val products = JsonParser.parseString(response.body()).asJsonObject.getAsJsonObject("products")
                    quotes = products.entrySet().associate { (id, product) ->
                        val q = product.asJsonObject.getAsJsonObject("quick_status")
                        id to Quote(instantSell = q["sellPrice"].asDouble, sellOffer = q["buyPrice"].asDouble)
                    }
                    fetchedAt = System.currentTimeMillis()
                } else {
                    ShaftUtils.logger.warn("Bazaar prices: HTTP ${response.statusCode()}")
                    fetchedAt = System.currentTimeMillis() - REFRESH_MS + 60_000 // try again in a minute
                }
            }
            .exceptionally { e ->
                ShaftUtils.logger.warn("Bazaar prices failed", e)
                fetchedAt = System.currentTimeMillis() - REFRESH_MS + 60_000
                null
            }
            .whenComplete { _, _ -> fetching = false }
    }

    /** Value of one [itemId] when selling, or null if it isn't on the bazaar. */
    fun sellValue(itemId: String): Double? {
        val q = quotes[itemId] ?: return null
        return when (ShaftUtils.config.profit.priceType) {
            PriceType.SELL_OFFER -> q.sellOffer
            PriceType.INSTANT_SELL -> q.instantSell
        }
    }

    /** What one [itemId] costs to buy (for keys), or null if it isn't on the bazaar. */
    fun buyCost(itemId: String): Double? {
        val q = quotes[itemId] ?: return null
        return when (ShaftUtils.config.profit.keyPriceType) {
            KeyPriceType.INSTANT_BUY -> q.sellOffer
            KeyPriceType.BUY_ORDER -> q.instantSell
        }
    }
}

enum class PriceType(private val label: String) {
    // Names kept for saved settings: SELL_OFFER is the insta-buy price (what a sell offer gets), INSTANT_SELL insta-sell.
    SELL_OFFER("Insta-buy"),
    INSTANT_SELL("Insta-sell"),
    ;

    override fun toString() = label
}

enum class KeyPriceType(private val label: String) {
    INSTANT_BUY("Instant buy"),
    BUY_ORDER("Buy order"),
    ;

    override fun toString() = label
}
