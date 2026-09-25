package com.zdmgold.katharscan

import android.util.Log
import com.amazon.device.iap.PurchasingListener
import com.amazon.device.iap.model.UserDataResponse
import com.amazon.device.iap.PurchasingService
import com.amazon.device.iap.model.ProductDataResponse
import com.amazon.device.iap.model.ProductType
import com.amazon.device.iap.model.PurchaseResponse
import com.amazon.device.iap.model.PurchaseUpdatesResponse
import com.amazon.device.iap.model.Receipt
import io.flutter.plugin.common.MethodChannel

class AmazonIapHandler : PurchasingListener {
    private val TAG = "AmazonIapHandler"

    override fun onUserDataResponse(response: UserDataResponse) {
        Log.d(TAG, "onUserDataResponse: " + response.requestStatus)
    }
    private var methodChannel: MethodChannel? = null

    fun setMethodChannel(channel: MethodChannel?) {
        this.methodChannel = channel
    }

    override fun onProductDataResponse(response: ProductDataResponse) {
        Log.d(TAG, "onProductDataResponse: " + response.requestStatus)
        when (response.requestStatus) {
            ProductDataResponse.RequestStatus.SUCCESSFUL -> {
                val product = response.productData.values.firstOrNull()
                if (product != null) {
                    val productMap = mapOf(
                        "id" to product.sku,
                        "title" to product.title,
                        "description" to product.description,
                        "price" to product.price
                    )
                    methodChannel?.invokeMethod("onProductDataSuccess", productMap)
                } else {
                    methodChannel?.invokeMethod("onProductDataError", "Product not found")
                }
            }
            else -> methodChannel?.invokeMethod("onProductDataError", response.requestStatus.toString())
        }
    }

    override fun onPurchaseResponse(response: PurchaseResponse) {
        Log.d(TAG, "onPurchaseResponse: " + response.requestStatus)
        when (response.requestStatus) {
            PurchaseResponse.RequestStatus.SUCCESSFUL -> {
                val receipt = response.receipt
                if (receipt != null) {
                    PurchasingService.notifyFulfillment(receipt.receiptId, com.amazon.device.iap.model.FulfillmentResult.UNAVAILABLE)
                    val purchaseMap = mapOf(
                        "productId" to receipt.sku,
                        "purchaseToken" to receipt.receiptId,
                        "status" to "purchased"
                    )
                    methodChannel?.invokeMethod("onPurchaseSuccess", purchaseMap)
                }
            }
            PurchaseResponse.RequestStatus.ALREADY_PURCHASED -> {
                val receipt = response.receipt
                if (receipt != null) {
                    val purchaseMap = mapOf(
                        "productId" to receipt.sku,
                        "purchaseToken" to receipt.receiptId,
                        "status" to "restored"
                    )
                    methodChannel?.invokeMethod("onPurchaseSuccess", purchaseMap)
                }
            }
            else -> methodChannel?.invokeMethod("onPurchaseError", response.requestStatus.toString())
        }
    }

    override fun onPurchaseUpdatesResponse(response: PurchaseUpdatesResponse) {
        Log.d(TAG, "onPurchaseUpdatesResponse: " + response.requestStatus)
        if (response.requestStatus == PurchaseUpdatesResponse.RequestStatus.SUCCESSFUL) {
            for (receipt in response.receipts) {
                PurchasingService.notifyFulfillment(receipt.receiptId, com.amazon.device.iap.model.FulfillmentResult.UNAVAILABLE)
                val purchaseMap = mapOf(
                    "productId" to receipt.sku,
                    "purchaseToken" to receipt.receiptId,
                    "status" to "restored"
                )
                methodChannel?.invokeMethod("onPurchaseSuccess", purchaseMap)
            }
        } else {
            methodChannel?.invokeMethod("onPurchaseError", "Restore failed: " + response.requestStatus)
        }
    }
}
