package com.wosux.qrscanner;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.Image;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Size;
import android.view.HapticFeedbackConstants;
import android.view.View;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.Camera;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ExperimentalGetImage;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.core.resolutionselector.ResolutionSelector;
import androidx.camera.core.resolutionselector.ResolutionStrategy;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.mlkit.vision.barcode.BarcodeScanner;
import com.google.mlkit.vision.barcode.BarcodeScannerOptions;
import com.google.mlkit.vision.barcode.BarcodeScanning;
import com.google.mlkit.vision.barcode.common.Barcode;
import com.google.mlkit.vision.common.InputImage;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Full-screen camera page. Reads a Wosux product QR, unlocks it, and closes with RESULT_OK.
 * Read the result with {@link #itemFrom(Intent)}. Codes that are not ours only show a short message
 * and the camera keeps looking.
 */
public class QrScanActivity extends AppCompatActivity {

    private static final String X_ID = "qr_id";
    private static final String X_SKU = "qr_sku";
    private static final String X_NUMBER = "qr_number";
    private static final String X_BILL = "qr_bill";
    private static final String X_NAME = "qr_name";
    private static final String X_BUY = "qr_buy";
    private static final String X_MIN = "qr_min";
    private static final String X_MAX = "qr_max";
    private static final String X_DATE = "qr_date";
    private static final String X_CATEGORY = "qr_category";
    private static final String X_STORE = "qr_store";

    /** Packs a scanned item into the result Intent. */
    public static Intent resultIntent(QrItem it) {
        return new Intent()
                .putExtra(X_ID, it.id).putExtra(X_SKU, it.sku).putExtra(X_NUMBER, it.number)
                .putExtra(X_BILL, it.billId).putExtra(X_NAME, it.name).putExtra(X_BUY, it.buyPrice)
                .putExtra(X_MIN, it.minPrice).putExtra(X_MAX, it.maxPrice).putExtra(X_DATE, it.date)
                .putExtra(X_CATEGORY, it.categoryId).putExtra(X_STORE, it.storeId);
    }

    /** Reads the scanned item back from the result Intent. */
    public static QrItem itemFrom(Intent d) {
        return new QrItem(s(d, X_ID), s(d, X_SKU), s(d, X_NUMBER), s(d, X_BILL), s(d, X_NAME),
                s(d, X_BUY), s(d, X_MIN), s(d, X_MAX), s(d, X_DATE), s(d, X_CATEGORY), s(d, X_STORE));
    }

    private static String s(Intent d, String key) {
        String v = d.getStringExtra(key);
        return v == null ? "" : v;
    }

    // -------------------------------------------------------------------------------------------

    private PreviewView previewView;
    private TextView tvMessage;
    private TextView tvHint;
    private ImageButton btnTorch;

    private ExecutorService cameraExecutor;
    private BarcodeScanner scanner;
    private ProcessCameraProvider cameraProvider;
    private Camera camera;

    private final AtomicBoolean finished = new AtomicBoolean(false);
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable hideMessage = () -> tvMessage.setVisibility(View.GONE);

    private final ActivityResultLauncher<String> permissionLauncher = registerForActivityResult(
            new ActivityResultContracts.RequestPermission(), granted -> {
                if (granted) {
                    startCamera();
                } else {
                    tvHint.setVisibility(View.GONE);
                    showMessage(getString(R.string.err_camera_permission), true);
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_scan);

        previewView = findViewById(R.id.previewView);
        tvMessage = findViewById(R.id.tvMessage);
        tvHint = findViewById(R.id.tvHint);
        btnTorch = findViewById(R.id.btnTorch);
        findViewById(R.id.btnClose).setOnClickListener(v -> finish());

        btnTorch.setOnClickListener(v -> {
            if (camera == null) return;
            boolean on = !btnTorch.isActivated();
            camera.getCameraControl().enableTorch(on);
            btnTorch.setActivated(on);
        });

        cameraExecutor = Executors.newSingleThreadExecutor();
        scanner = BarcodeScanning.getClient(new BarcodeScannerOptions.Builder()
                .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
                .build());

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED) {
            startCamera();
        } else {
            permissionLauncher.launch(Manifest.permission.CAMERA);
        }
    }

    private void startCamera() {
        ListenableFuture<ProcessCameraProvider> future = ProcessCameraProvider.getInstance(this);
        future.addListener(() -> {
            try {
                cameraProvider = future.get();
                bindCamera();
            } catch (Exception e) {
                showMessage(getString(R.string.err_camera_start), true);
            }
        }, ContextCompat.getMainExecutor(this));
    }

    private void bindCamera() {
        if (cameraProvider == null || isFinishing()) return;

        Preview preview = new Preview.Builder().build();
        preview.setSurfaceProvider(previewView.getSurfaceProvider());

        // Our codes are dense (encrypted data), so read frames at a good size.
        ResolutionSelector selector = new ResolutionSelector.Builder()
                .setResolutionStrategy(new ResolutionStrategy(new Size(1920, 1080),
                        ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER))
                .build();
        ImageAnalysis analysis = new ImageAnalysis.Builder()
                .setResolutionSelector(selector)
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build();
        analysis.setAnalyzer(cameraExecutor, new QrAnalyzer());

        try {
            cameraProvider.unbindAll();
            camera = cameraProvider.bindToLifecycle(this, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis);
            if (camera.getCameraInfo().hasFlashUnit()) {
                btnTorch.setVisibility(View.VISIBLE);
            }
        } catch (Exception e) {
            showMessage(getString(R.string.err_camera_start), true);
        }
    }

    /** Runs on the camera thread for every frame. */
    private final class QrAnalyzer implements ImageAnalysis.Analyzer {
        @Override
        @ExperimentalGetImage
        public void analyze(@NonNull ImageProxy proxy) {
            Image media = proxy.getImage();
            if (media == null || finished.get()) {
                proxy.close();
                return;
            }
            InputImage input = InputImage.fromMediaImage(media, proxy.getImageInfo().getRotationDegrees());
            scanner.process(input)
                    .addOnSuccessListener(codes -> onCodes(codes))
                    .addOnCompleteListener(task -> proxy.close());
        }
    }

    /** Main thread. */
    private void onCodes(List<Barcode> codes) {
        if (finished.get()) return;
        String lastError = null;
        for (Barcode b : codes) {
            String raw = b.getRawValue();
            if (raw == null) continue;
            try {
                accept(QrCrypto.parse(raw, BuildConfig.QR_SECRET_KEY_HEX));
                return;
            } catch (QrCrypto.QrException e) {
                lastError = e.getMessage();
            }
        }
        if (lastError != null) showMessage(lastError, false);
    }

    private void accept(QrItem item) {
        if (!finished.compareAndSet(false, true)) return;
        previewView.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
        setResult(RESULT_OK, resultIntent(item));
        finish();
    }

    private void showMessage(String text, boolean sticky) {
        tvMessage.setText(text);
        tvMessage.setVisibility(View.VISIBLE);
        handler.removeCallbacks(hideMessage);
        if (!sticky) handler.postDelayed(hideMessage, 3000);
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        if (cameraExecutor != null) cameraExecutor.shutdown();
        if (scanner != null) {
            try {
                scanner.close();
            } catch (Exception ignored) {
                // nothing to do, the page is closing
            }
        }
        super.onDestroy();
    }
}
