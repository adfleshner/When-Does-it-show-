package com.flesh.questions.whendoesitshow

import android.app.Activity
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.flesh.questions.whendoesitshow.databinding.ActivityMainBinding
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

class MainActivity : AppCompatActivity(), PurchasesUpdatedListener {

    val TAG = "BILLING"
    //,"us_only","canada_only"
    private val LIST_OF_PRODUCTS = listOf("not_as_short")//"short_sub","us_only","bronze_sub","was_in_both_but_now_in_one")

    lateinit var billingClient : BillingClient

    val binding : ActivityMainBinding by lazy {
        ActivityMainBinding.inflate(layoutInflater)
    }

    var productDetails : ProductDetails? = null

    val currentOffer = MutableStateFlow("")
    val currentPromo = MutableStateFlow("")


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        start()
        binding.button.setOnClickListener {
            if(productDetails == null){
                Toast.makeText(this@MainActivity, "Product is null", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if(currentOffer.value.isEmpty()){
                Toast.makeText(this@MainActivity, "OfferToken is empty", Toast.LENGTH_SHORT).show()
            }
            normalPurchase(currentOffer.value, productDetails!!, this@MainActivity)
        }

        binding.updatePromo.setOnClickListener {
            updateOffer(binding.promo.text.toString())
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                currentOffer.collectLatest {
                    binding.currentOffer.text = "Least Priced Token:\n$it"
                }
            }
        }
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                currentPromo.collectLatest {
                    binding.currentPromo.text = "Current Promo:\n$it"
                }
            }
        }


    }



    fun start() {
        billingClient = BillingClient.newBuilder(this)
            .setListener(this)
            .enablePendingPurchases()
            .build()
        billingClient.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(billingResult: BillingResult) {
                when {
                    billingResult.isOk -> {
                        Log.i(TAG, "Billing response OK")
                        // The BillingClient is ready. You can query purchases and product details here
                        this@MainActivity.lifecycleScope.launch {
                            queryProductDetails()
                        }
                    }
                    else -> {
                        Log.e(TAG, billingResult.debugMessage)
                        // FIXME this should show some sort of error I would presume.
                    }
                }

            }

            override fun onBillingServiceDisconnected() {
                Log.i(TAG, "Billing connection disconnected")
                // You should create your own retry policy.
            }
        })
    }

    suspend fun queryProductDetails() {
        val params = QueryProductDetailsParams.newBuilder()
        val productList = mutableListOf<QueryProductDetailsParams.Product>()
        // maybe the LIST_OF_PRODUCTS could come from the backend?
        LIST_OF_PRODUCTS.forEach { product ->
            productList.add(
                QueryProductDetailsParams.Product.newBuilder()
                    .setProductId(product)
                    .setProductType(BillingClient.ProductType.SUBS)
                    .build()
            )
        }
        // FIXME I changed this This was originally in the for loop but I believe it should not be
        //  in the for loop, move it back if things don't look right.
        params.setProductList(productList).let { productDetailsParams ->
            Log.i(TAG, "queryProductDetailsAsync")
            val products :MutableList<ProductDetails> = suspendCoroutine {
                billingClient.queryProductDetailsAsync(productDetailsParams.build()
                ) { result, prods -> it.resume(prods) }
            }

            Log.d(TAG, "Available from the store:  ${products.joinToString { product ->
                "Title: " + product.title + " " + product.subscriptionOfferDetails?.joinToString(prefix = "[", postfix = "]")
                { subOffer -> "SubOffer" +" " + subOffer.offerId + " " + subOffer.offerTags.joinToString(prefix = "{", postfix = "}") {tag -> tag  }}?:"Nothing" }}")
            if (products.isEmpty()){
                Toast.makeText(this,"None",Toast.LENGTH_SHORT).show()
            }

            productDetails = products.getOrNull(0)

            updateOffer("")

        }
    }

    private fun updateOffer(text : String){
        currentPromo.value = text
        val subscriptionOfferDetails = productDetails?.subscriptionOfferDetails

        subscriptionOfferDetails?.let {

            // get all the offers for the tag provided
            val offers = retrieveEligibleOffers(offerDetails = subscriptionOfferDetails, tag = "check ")

            Log.d(TAG, "Available offers from the ${productDetails!!.title}: ${offers.toReadableString()}")

            val offerToken = leastPricedOfferToken(offers, text)

            this.currentOffer.value = offerToken

            Log.d(TAG, "Least Priced Token: ${this.currentOffer.value}")
        } ?: run{
            Toast.makeText(this, "NO OFFERS", Toast.LENGTH_SHORT).show()
        }
    }
    fun List<ProductDetails.SubscriptionOfferDetails>.toReadableString() : String {
        return this.joinToString(prefix = "\n[\n", postfix = "\n]\n", separator = ",\n")
        { offer -> (offer.offerId?:"Nothing") + " tags ${offer.offerTags} : ${offer.pricingPhases.pricingPhaseList.toReadableString3()} : " + " token: " + offer.offerToken}
    }

    private fun List<ProductDetails.PricingPhase>.toReadableString3() :String {
        return this.joinToString(prefix = "\n[", postfix = "]\n", separator = ",")
        { phase ->"Period: ${phase.billingPeriod} Price: ${phase.formattedPrice}, Mode:${phase.recurrenceMode} Cycle Count ${phase.billingCycleCount}"}
    }

private fun retrieveEligibleOffers(
        offerDetails: MutableList<ProductDetails.SubscriptionOfferDetails>,
        tag: String
    ): List<ProductDetails.SubscriptionOfferDetails> {
        val eligibleOffers = emptyList<ProductDetails.SubscriptionOfferDetails>().toMutableList()
        offerDetails.forEach { offerDetail ->
            if (offerDetail.offerTags.contains(tag)) {
                eligibleOffers.add(offerDetail)
            }
        }
        return eligibleOffers
    }

    private fun leastPricedOfferToken(offerDetails: List<ProductDetails.SubscriptionOfferDetails>, offerTag : String): String {
        return if (offerTag.isNotEmpty()){
            Log.d(TAG, "Has Tag: $offerTag ")
            Log.d(TAG, "Checking offers ${offerDetails.filter { it.offerTags.contains("code") }.toReadableString()}")
            lookForDeveloperDeteriminedOffers(offerDetails.filter { it.offerTags.contains("code") }, offerTag)
        }else{
            Log.d(TAG, "No tag")
            Log.d(TAG, "Checking offers ${offerDetails.filter { !it.offerTags.contains("code") }.toReadableString()}")
            lookForNONDeveloperDeteriminedOffers(offerDetails.filter { !it.offerTags.contains("code") })
        }
    }

    private fun lookForNONDeveloperDeteriminedOffers(offerDetails: List<ProductDetails.SubscriptionOfferDetails>) : String{
        var offerToken = String()
        var leastPricedOffer: ProductDetails.SubscriptionOfferDetails
        var lowestPrice = Int.MAX_VALUE
        for (offer in offerDetails) {
            Log.d(TAG, "OfferID : ${offer.offerId}")
            for (price in offer.pricingPhases.pricingPhaseList) {
                if (price.priceAmountMicros < lowestPrice) {
                    lowestPrice = price.priceAmountMicros.toInt()
                    leastPricedOffer = offer
                    offerToken = leastPricedOffer.offerToken
                }
            }
        }
        return offerToken
    }
    private fun lookForDeveloperDeteriminedOffers(offerDetails: List<ProductDetails.SubscriptionOfferDetails>, offerTag :String) : String{
        var offerToken = String()
        var leastPricedOffer: ProductDetails.SubscriptionOfferDetails
        var lowestPrice = Int.MAX_VALUE
        for (offer in offerDetails) {
            Log.d(TAG, "OfferID : ${offer.offerId}")
            for (price in offer.pricingPhases.pricingPhaseList) {
                if (offer.offerTags.contains(offerTag)) {
                    lowestPrice = price.priceAmountMicros.toInt()
                    leastPricedOffer = offer
                    offerToken = leastPricedOffer.offerToken
                }
            }
        }
        return offerToken
    }

    private fun normalPurchase(
        offerToken: String,
        productDetails: ProductDetails,
        activity: Activity
    ) {
        // This is a normal purchase.
        val billingParams = normalBillingFlowParamsBuilder(
            productDetails = productDetails,
            offerToken = offerToken
        )

        billingClient.launchBillingFlow(
            activity,
            billingParams.build()
        )
    }

    private fun normalBillingFlowParamsBuilder(productDetails: ProductDetails, offerToken: String): BillingFlowParams.Builder {
        val productsToPurchase = listOf(
            BillingFlowParams.ProductDetailsParams.newBuilder()
                .setProductDetails(productDetails)
                .setOfferToken(offerToken)
                .build()
        )

        return BillingFlowParams
            .newBuilder()
            .setProductDetailsParamsList(productsToPurchase)
    }

    // Launch Purchase flow
    fun launchBillingFlow(activity: Activity, params: BillingFlowParams) {
        if (!billingClient.isReady) {
            Log.e(TAG, "launchBillingFlow: BillingClient is not ready")
        }
        billingClient.launchBillingFlow(activity, params)
    }


    override fun onPurchasesUpdated(p0: BillingResult, p1: MutableList<Purchase>?) {
        Toast.makeText(this, "p0", Toast.LENGTH_SHORT).show()
        acknowledge(p0,p1)
    }

    private fun acknowledge(result: BillingResult, purchases: MutableList<Purchase>?) {
        purchases?.forEach {

            if (!it.isAcknowledged) {
                val purchaseParams = AcknowledgePurchaseParams.newBuilder()
                    .setPurchaseToken(it.purchaseToken)
                    .build()


                billingClient.acknowledgePurchase(purchaseParams) { billingResult ->
                    if (billingResult.isOk && it.isPurchased) {
                        Log.i(TAG, "Purchase ${it.purchaseToken} was acknowledged.")
                        Toast.makeText(this, "=Purchase ${it.purchaseToken} was acknowledged.", Toast.LENGTH_SHORT).show()
                    } else {
                        Log.e(TAG, "Purchase Acknowledgement failed")
                        // FIXME this should show some sort of error I would presume.
                    }
                }
            }
        }?:run{
            Toast.makeText(this, "nothing!", Toast.LENGTH_SHORT).show()
        }
    }

}

// Helpful extensions
val BillingResult.isOk: Boolean
    get() = responseCode == BillingClient.BillingResponseCode.OK

val Purchase.isPurchased: Boolean
    get() = purchaseState == Purchase.PurchaseState.PURCHASED