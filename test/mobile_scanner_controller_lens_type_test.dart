import 'package:flutter_test/flutter_test.dart';
import 'package:mobile_scanner/src/enums/camera_lens_type.dart';
import 'package:mobile_scanner/src/enums/mobile_scanner_error_code.dart';
import 'package:mobile_scanner/src/mobile_scanner_controller.dart';
import 'package:mobile_scanner/src/mobile_scanner_exception.dart';

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
}
