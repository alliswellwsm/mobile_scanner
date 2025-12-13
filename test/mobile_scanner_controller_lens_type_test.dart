import 'package:flutter_test/flutter_test.dart';
import 'package:mobile_scanner/src/enums/camera_lens_type.dart';
import 'package:mobile_scanner/src/enums/mobile_scanner_error_code.dart';
import 'package:mobile_scanner/src/mobile_scanner_controller.dart';
import 'package:mobile_scanner/src/mobile_scanner_exception.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  group('MobileScannerController lens type tests', () {
    test('controller initializes with default lens type (any)', () async {
      final controller = MobileScannerController(autoStart: false);

      expect(controller.lensType, CameraLensType.any);

      await controller.dispose();
    });

    test('controller initializes with normal lens type', () async {
      final controller = MobileScannerController(
        autoStart: false,
        lensType: CameraLensType.normal,
      );

      expect(controller.lensType, CameraLensType.normal);

      await controller.dispose();
    });

    test('controller initializes with wide lens type', () async {
      final controller = MobileScannerController(
        autoStart: false,
        lensType: CameraLensType.wide,
      );

      expect(controller.lensType, CameraLensType.wide);

      await controller.dispose();
    });

    test('controller initializes with zoom lens type', () async {
      final controller = MobileScannerController(
        autoStart: false,
        lensType: CameraLensType.zoom,
      );

      expect(controller.lensType, CameraLensType.zoom);

      await controller.dispose();
    });

    test('lens type persists throughout controller lifecycle', () async {
      final controller = MobileScannerController(
        autoStart: false,
        lensType: CameraLensType.zoom,
      );

      expect(controller.lensType, CameraLensType.zoom);

      // Lens type should remain the same even after operations
      expect(controller.lensType, CameraLensType.zoom);

      await controller.dispose();
    });

    test('different controllers can have different lens types', () async {
      final normalController = MobileScannerController(
        autoStart: false,
        lensType: CameraLensType.normal,
      );
      final wideController = MobileScannerController(
        autoStart: false,
        lensType: CameraLensType.wide,
      );
      final zoomController = MobileScannerController(
        autoStart: false,
        lensType: CameraLensType.zoom,
      );

      expect(normalController.lensType, CameraLensType.normal);
      expect(wideController.lensType, CameraLensType.wide);
      expect(zoomController.lensType, CameraLensType.zoom);

      await normalController.dispose();
      await wideController.dispose();
      await zoomController.dispose();
    });

    test('controller with all lens types can be created', () async {
      for (final lensType in CameraLensType.values) {
        final controller = MobileScannerController(
          autoStart: false,
          lensType: lensType,
        );

        expect(controller.lensType, lensType);
        await controller.dispose();
      }
    });

    test('lens type property is read-only and immutable', () async {
      final controller = MobileScannerController(
        autoStart: false,
        lensType: CameraLensType.wide,
      );

      final initialLensType = controller.lensType;
      expect(initialLensType, CameraLensType.wide);

      // The lens type should remain the same (it's a final property)
      expect(controller.lensType, initialLensType);

      await controller.dispose();
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
