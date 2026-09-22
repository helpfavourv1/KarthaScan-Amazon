// lib/core/services/doc_scanner_service.dart
//
// Native document scanning via MethodChannel to AutoScanActivity.
//
// Android: CameraX for camera + OpenCV for edge detection and perspective
// correction. No Google Play Services dependency. Works on Fire OS,
// Huawei HMS devices, and any GMS-free Android fork.
//
// This replaces the previous ML Kit wrapper (Google
// Document Scanner). ML Kit required GMS, which Fire OS does not ship,
// so Auto Scan silently failed on the Amazon submission and was rejected
// for "scanned images are not getting saved".
//
// Public API is preserved: DocScannerService.scan() returns DocScanResult
// and throws DocScannerUnsupportedException / DocScannerFailedException.
// Callers (manual_crop_screen.dart, scan_provider.dart) do not change.

import 'package:flutter/services.dart' show MethodChannel, PlatformException;

import 'debug_log_service.dart';

class DocScannerUnsupportedException implements Exception {
  DocScannerUnsupportedException(this.message);
  final String message;
  @override
  String toString() => 'DocScannerUnsupportedException: $message';
}

class DocScannerFailedException implements Exception {
  DocScannerFailedException(this.message);
  final String message;
  @override
  String toString() => 'DocScannerFailedException: $message';
}

class DocScanResult {
  const DocScanResult({required this.pageImagePaths});
  final List<String> pageImagePaths;
}

class DocScannerService {
  static const MethodChannel _channel =
      MethodChannel('com.zdmgold.katharscan/autoscan');

  final DebugLogService _log = DebugLogService();

  /// Opens the native fullscreen Auto Scan Activity.
  ///
  /// Returns an empty DocScanResult if the user cancels.
  /// Throws DocScannerFailedException if the native side reports an error.
  ///
  /// NOTE: the current native Activity captures one page per call. Batch
  /// scanning (multiple pages in one flow) is a follow-up; for now, each
  /// invocation of scan() produces exactly one page image path.
  Future<DocScanResult> scan({int maxPages = 20}) async {
    _log.log('SCANNER', 'Invoking native autoscan channel');

    try {
      final String? path =
          await _channel.invokeMethod<String>('scan');

      if (path == null || path.isEmpty) {
        _log.log('SCANNER', 'User cancelled or no result');
        return const DocScanResult(pageImagePaths: <String>[]);
      }

      _log.log('SCANNER', 'Native scan SUCCESS: $path');
      return DocScanResult(pageImagePaths: <String>[path]);
    } on PlatformException catch (error) {
      _log.log(
        'SCANNER',
        'PlatformException: ${error.code} | ${error.message}',
      );
      throw DocScannerFailedException(
        error.message ?? error.code,
      );
    } catch (error) {
      _log.log('SCANNER', 'Scan failed: $error');
      throw DocScannerFailedException('$error');
    }
  }
}
