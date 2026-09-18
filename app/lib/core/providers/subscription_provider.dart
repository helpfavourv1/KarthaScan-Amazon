// lib/core/providers/subscription_provider.dart
//
// ValueNotifier<bool adsRemoved> + purchase/restore state (Section 16 file
// #23).
//
// This provider is the AUTHORITATIVE runtime source of truth for ad removal
// entitlement. UserSettings.adsRemoved (settings_provider.dart) is only a
// cached convenience flag for instant cold-start UI, per that model's own
// doc comment — whenever this provider determines entitlement has
// changed (a purchase completes, a restore succeeds), it pushes the new
// value into settings_provider so that cache stays correct.
//
// REACTIVITY: ValueNotifier + ListenableBuilder only, per the MANDATORY
// constraint in constants.dart.

import 'dart:async' show unawaited;

import 'package:flutter/foundation.dart' show ValueNotifier;

import '../../platform/iap_service.dart';
import '../utils/constants.dart';
import 'settings_provider.dart';

enum PurchaseFlowState { idle, inProgress, success, error, cancelled }

class SubscriptionProvider {
  SubscriptionProvider(this._iapService, this._settingsProvider) {
    adsRemoved = ValueNotifier<bool>(_settingsProvider.settings.value.adsRemoved);
    unawaited(_initialize());
  }

  final IapService _iapService;
  final SettingsProvider _settingsProvider;

  late final ValueNotifier<bool> adsRemoved;

  final ValueNotifier<List<AmazonProductDetails>> products =
      ValueNotifier<List<AmazonProductDetails>>(const <AmazonProductDetails>[]);
  final ValueNotifier<PurchaseFlowState> purchaseFlowState =
      ValueNotifier<PurchaseFlowState>(PurchaseFlowState.idle);
  final ValueNotifier<String?> lastError = ValueNotifier<String?>(null);

  Future<void> _initialize() async {
    final bool available = await _iapService.initialize(
      onPurchaseUpdate: _handlePurchaseUpdate,
    );
    if (!available) {
      lastError.value = AppPluginFailureCopy.billingUnavailableMessage;
      return;
    }
    products.value = await _iapService.queryProducts();
  }

  void _handlePurchaseUpdate(AmazonPurchaseDetails purchase) {
    switch (purchase.status) {
      case AmazonPurchaseStatus.pending:
        purchaseFlowState.value = PurchaseFlowState.inProgress;
        break;
      case AmazonPurchaseStatus.purchased:
      case AmazonPurchaseStatus.restored:
        purchaseFlowState.value = PurchaseFlowState.success;
        unawaited(_setEntitled(true));
        break;
      case AmazonPurchaseStatus.error:
        purchaseFlowState.value = PurchaseFlowState.error;
        lastError.value = purchase.errorMessage ??
            AppPluginFailureCopy.billingUnavailableMessage;
        break;
      case AmazonPurchaseStatus.canceled:
        purchaseFlowState.value = PurchaseFlowState.cancelled;
        break;
    }
  }

  Future<void> _setEntitled(bool entitled) async {
    adsRemoved.value = entitled;
    await _settingsProvider.setAdsRemoved(entitled);
  }

  /// The single Remove Ads non-consumable product, if loaded from the store.
  AmazonProductDetails? get removeAdsProduct => _findProduct(IapService.removeAdsProductId);

  AmazonProductDetails? _findProduct(String id) {
    for (final AmazonProductDetails product in products.value) {
      if (product.id == id) return product;
    }
    return null;
  }

  /// Initiates a purchase. The actual success/failure arrives
  /// asynchronously via [_handlePurchaseUpdate] and is reflected in
  /// [purchaseFlowState] and [adsRemoved] — paywall_screen.dart should listen
  /// to those rather than awaiting this call for the final result.
  Future<void> purchase(AmazonProductDetails product) async {
    purchaseFlowState.value = PurchaseFlowState.inProgress;
    lastError.value = null;
    try {
      await _iapService.purchase(product);
    } on IapUnavailableException catch (error) {
      purchaseFlowState.value = PurchaseFlowState.error;
      lastError.value = error.message;
    }
  }

  Future<void> restore() async {
    purchaseFlowState.value = PurchaseFlowState.inProgress;
    lastError.value = null;
    final bool success = await _iapService.restorePurchases();
    if (!success) {
      purchaseFlowState.value = PurchaseFlowState.error;
      lastError.value = AppPluginFailureCopy.billingUnavailableMessage;
    }
    // On success, any restored purchases arrive via _handlePurchaseUpdate
    // with status == AmazonPurchaseStatus.restored — if the store genuinely has
    // nothing to restore, purchaseFlowState simply never advances past
    // inProgress here, so paywall_screen.dart should pair this with a
    // reasonable timeout/idle-state check rather than waiting forever.
  }

  void dispose() {
    unawaited(_iapService.dispose());
    adsRemoved.dispose();
    products.dispose();
    purchaseFlowState.dispose();
    lastError.dispose();
  }
}
