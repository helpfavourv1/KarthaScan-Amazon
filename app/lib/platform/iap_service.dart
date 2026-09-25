// lib/platform/iap_service.dart
//
// Amazon Appstore IAP bridge via MethodChannel.
// Replaces Google Play Billing / StoreKit for the Amazon fork.

import 'dart:async';
import 'package:flutter/foundation.dart' show debugPrint;
import 'package:flutter/services.dart' show MethodChannel, PlatformException;
import '../core/utils/constants.dart';

class IapUnavailableException implements Exception {
  const IapUnavailableException(this.message);
  final String message;
  @override
  String toString() => 'IapUnavailableException: \$message';
}

// Local, store-agnostic types to replace in_app_purchase dependencies
enum AmazonPurchaseStatus { pending, purchased, restored, error, canceled }

class AmazonPurchaseDetails {
  final String productId;
  final AmazonPurchaseStatus status;
  final String? errorMessage;
  const AmazonPurchaseDetails({required this.productId, required this.status, this.errorMessage});
}

class AmazonProductDetails {
  final String id;
  final String title;
  final String description;
  final String price;
  const AmazonProductDetails({required this.id, required this.title, required this.description, required this.price});
}

class IapService {
  static const String removeAdsProductId = 'com.zdmgold.katharscan.removeads';
  static const Set<String> allProductIds = <String>{removeAdsProductId};

  final MethodChannel _channel = const MethodChannel('com.zdmgold.katharscan/amazon_iap');
  bool _isInitialized = false;
  void Function(AmazonPurchaseDetails)? _onPurchaseUpdate;

  Future<bool> initialize({
    required void Function(AmazonPurchaseDetails details) onPurchaseUpdate,
  }) async {
    if (_isInitialized) return true;
    try {
      _onPurchaseUpdate = onPurchaseUpdate;
      _channel.setMethodCallHandler((call) async {
        if (call.method == 'onPurchaseSuccess') {
          final args = Map<String, dynamic>.from(call.arguments);
          _onPurchaseUpdate?.call(AmazonPurchaseDetails(
            productId: args['productId'] ?? '',
            status: args['status'] == 'restored' ? AmazonPurchaseStatus.restored : AmazonPurchaseStatus.purchased,
            errorMessage: null,
          ));
        } else if (call.method == 'onPurchaseError') {
          _onPurchaseUpdate?.call(AmazonPurchaseDetails(
            productId: '',
            status: AmazonPurchaseStatus.error,
            errorMessage: call.arguments.toString(),
          ));
        } else if (call.method == 'onProductDataSuccess') {
          // Handled internally by queryProducts completer if needed
        }
        return null;
      });
      _isInitialized = true;
      return true;
    } catch (error) {
      debugPrint('[IapService] initialize failed: \$error');
      return false;
    }
  }

  Future<List<AmazonProductDetails>> queryProducts() async {
    // Return a static entry so the paywall screen can find the product.
    // The actual product data (title, price) is not used for the purchase
    // flow — PurchasingService.purchase(sku) only needs the SKU.
    // Price display is handled by the paywall screen's static fallback.
    return const <AmazonProductDetails>[
      AmazonProductDetails(
        id: removeAdsProductId,
        title: 'Remove Ads',
        description: 'Remove all ads forever with a single, one-time purchase.',
        price: '',
      ),
    ];
  }

  Future<void> purchase(AmazonProductDetails product) async {
    try {
      await _channel.invokeMethod('purchase', {'sku': product.id});
    } on PlatformException {
      throw IapUnavailableException(AppPluginFailureCopy.billingUnavailableMessage);
    } catch (error) {
      debugPrint('[IapService] purchase failed: \$error');
      throw IapUnavailableException(AppPluginFailureCopy.billingUnavailableMessage);
    }
  }

  Future<bool> restorePurchases() async {
    try {
      await _channel.invokeMethod('restorePurchases');
      return true;
    } catch (error) {
      debugPrint('[IapService] restorePurchases failed: \$error');
      return false;
    }
  }

  Future<void> dispose() async {
    _channel.setMethodCallHandler(null);
    _isInitialized = false;
  }
}
