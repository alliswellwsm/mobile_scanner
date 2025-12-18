import 'package:flutter_test/flutter_test.dart';
import 'package:mobile_scanner/src/enums/camera_lens_type.dart';
import 'package:mobile_scanner/src/enums/mobile_scanner_error_code.dart';
import 'package:mobile_scanner/src/mobile_scanner_controller.dart';
import 'package:mobile_scanner/src/mobile_scanner_exception.dart';
import 'package:mobile_scanner/src/objects/switch_camera_option.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  group('MobileScannerController lens type tests', () {
    test('controller initializes with default lens type (any)', () {
      final controller = MobileScannerController(autoStart: false);

      expect(controller.lensType, CameraLensType.any);
    });

    test('controller can be created with all lens types', () {
      for (final lensType in CameraLensType.values) {
        final controller = MobileScannerController(
          autoStart: false,
          lensType: lensType,
        );

        expect(controller.lensType, lensType);
      }
    });

    test('getSupportedLenses throws when controller is disposed', () async {
      final controller = MobileScannerController(autoStart: false);

      await controller.dispose();

      expect(
        controller.getSupportedLenses,
        throwsA(
          isA<MobileScannerException>().having(
            (e) => e.errorCode,
            'errorCode',
            MobileScannerErrorCode.controllerDisposed,
          ),
        ),
      );
    });
  });

  group('MobileScannerController switchCamera tests', () {
    test('switchCamera throws when controller is not initialized', () async {
      final controller = MobileScannerController(autoStart: false);

      expect(
        controller.switchCamera,
        throwsA(
          isA<MobileScannerException>().having(
            (e) => e.errorCode,
            'errorCode',
            MobileScannerErrorCode.controllerUninitialized,
          ),
        ),
      );
    });

    test(
      'switchCamera with ToggleDirection throws when not initialized',
      () async {
        final controller = MobileScannerController(autoStart: false);

        expect(
          // ToggleDirection is the default, same as switchCamera().
          controller.switchCamera,
          throwsA(
            isA<MobileScannerException>().having(
              (e) => e.errorCode,
              'errorCode',
              MobileScannerErrorCode.controllerUninitialized,
            ),
          ),
        );
      },
    );

    test(
      'switchCamera with ToggleLensType throws when not initialized',
      () async {
        final controller = MobileScannerController(autoStart: false);

        expect(
          () => controller.switchCamera(const ToggleLensType()),
          throwsA(
            isA<MobileScannerException>().having(
              (e) => e.errorCode,
              'errorCode',
              MobileScannerErrorCode.controllerUninitialized,
            ),
          ),
        );
      },
    );

    test(
      'switchCamera with SelectCamera throws when not initialized',
      () async {
        final controller = MobileScannerController(autoStart: false);

        expect(
          () => controller.switchCamera(
            const SelectCamera(lensType: CameraLensType.wide),
          ),
          throwsA(
            isA<MobileScannerException>().having(
              (e) => e.errorCode,
              'errorCode',
              MobileScannerErrorCode.controllerUninitialized,
            ),
          ),
        );
      },
    );

    test(
      'switchCamera throws when controller is disposed without init',
      () async {
        final controller = MobileScannerController(autoStart: false);

        await controller.dispose();

        // When disposed without being initialized first,
        // _throwIfNotInitialized() checks isInitialized before _isDisposed,
        // so it throws controllerUninitialized.
        expect(
          controller.switchCamera,
          throwsA(
            isA<MobileScannerException>().having(
              (e) => e.errorCode,
              'errorCode',
              MobileScannerErrorCode.controllerUninitialized,
            ),
          ),
        );
      },
    );
  });
}
