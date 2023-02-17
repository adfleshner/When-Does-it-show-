package com.flesh.questions.whendoesitshow

import android.content.Context
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.Toast
import androidx.activity.viewModels
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.lifecycleScope
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.ProductDetails.SubscriptionOfferDetails
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {


    val v : BillingCode by viewModels()


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        v.start(this)
            v.subToBuy.observe(this){ sub ->
                findViewById<Button>(R.id.buy).apply {
                    isEnabled = true
                    setOnClickListener {
                        v.buy(this@MainActivity,sub){
                            Toast.makeText(this@MainActivity,"Product null",Toast.LENGTH_SHORT).show()
                        }
                    }
                }

            }
    }
}


class BillingCode() : ViewModel(){

    private val productIDs = listOf("bronze_sub")


    private lateinit var billingClient: BillingClient

    val subToBuy = MutableLiveData<SubscriptionOfferDetails>()
    private var productDetails : ProductDetails? = null


    private val billingClientStateListener = object : BillingClientStateListener {
        override fun onBillingSetupFinished(billingResult: BillingResult) {
            Log.d("BillingCode", "onBillingSetupFinished")
            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                // The BillingClient is ready. You can query purchases here.
                Log.d("BillingCode", " Ready Now")
                querySubscriptions()
                return
            }
            Log.d("BillingCode", " Still not ready")
        }

        override fun onBillingServiceDisconnected() {
            Log.d("BillingCode", "Disconnected")
            billingClient.startConnection(this)
        }
    }
    fun start(context: Context) {
        Log.d("BillingCode", "Built")
        billingClient= BillingClient.newBuilder(context).setListener { billingResult, purchases ->
            Log.d("BillingCode Listener:","Purchases: $billingResult $purchases")
        }.enablePendingPurchases()
            .build()
        Log.d("BillingCode", "Started")
        billingClient.startConnection(billingClientStateListener)
    }

    // When querying for product details, pass an instance of QueryProductDetailsParams that
    // specifies a list of product ID strings created in Google Play Console along with a
    // ProductType. The ProductType can be either ProductType.INAPP for one-time products
    // or ProductType.SUBS for subscriptions
    fun querySubscriptions() {
        Log.d("BillingCode", "querySubscriptions")
        val queryProductDetailsParams =
            QueryProductDetailsParams.newBuilder()
                .setProductList(productIDs.mapToProductBaseSubscriptions())
                .build()

        billingClient.queryProductDetailsAsync(queryProductDetailsParams) { billingResult,
                                                                            productDetailsList ->
            // check billingResult
            // process returned productDetailsList

            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                Log.d("BillingCode", "\"Subscription Query Result OK: ${productDetailsList.joinToString { it.productId }}")
                val bronze = productDetailsList.first { it.productId == "bronze_sub" }
                productDetails = bronze
                subToBuy.postValue(bronze.subscriptionOfferDetails?.first())
            } else {
                Log.d("BillingCode", "\"Subscription Query Did not work ${billingResult.responseCode}\n" +
                        "Message${billingResult.debugMessage}")
            }
        }
    }

    // Maps Product Id strings to Products
    private fun List<String>.mapToProductBaseSubscriptions(): List<QueryProductDetailsParams.Product> {
        return map {
            QueryProductDetailsParams.Product.newBuilder()
                .setProductId(it)
                .setProductType(BillingClient.ProductType.SUBS)
                .build()
        }
    }

    fun buy(activity: MainActivity, subscriptionOfferDetails: SubscriptionOfferDetails, notReady:()->Unit){
        if(productDetails == null){
            notReady.invoke()
            return
        }
        val selectedOfferToken = subscriptionOfferDetails.offerToken
        val productDetailsParamsList = listOf(
            BillingFlowParams.ProductDetailsParams.newBuilder()
                // retrieve a value for "productDetails" by calling queryProductDetailsAsync()
                .setProductDetails(productDetails!!)
                // to get an offer token, call ProductDetails.subscriptionOfferDetails()
                // for a list of offers that are available to the user
                .setOfferToken(selectedOfferToken)
                .build()
        )

        val billingFlowParams = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(productDetailsParamsList)
            .build()

        // Launch the billing flow
        val billingResult = billingClient.launchBillingFlow(activity, billingFlowParams)
        if(billingResult.responseCode == BillingClient.BillingResponseCode.OK){
            Log.d("BillingCode", "Billing Flow Launched Successfully")
        }
    }


}