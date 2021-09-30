package org.vay.sampleapp;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.Manifest;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.Color;
import android.os.Bundle;
import android.util.Log;
import android.util.Size;
import android.widget.TextView;
import android.widget.Toast;

import com.google.common.util.concurrent.ListenableFuture;

import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {
	private static final String TAG = MainActivity.class.getSimpleName();

	// Views
	private PreviewView previewView;
	private GraphicOverlay graphicOverlay;
	private TextView repetitionsText;
	private TextView feedbackBox;
	private TextView currentMovementText;

	// Camera
	private final int MY_PERMISSION_REQUEST_CAMERA = 8;
	private ListenableFuture<ProcessCameraProvider> cameraProviderFuture;
	private final int lensFacing = CameraSelector.LENS_FACING_FRONT;
	private boolean needUpdateGraphicOverlayImageSourceInfo;
	private final Size targetResolution = null; // Override the default resolution here. CameraX
	// finds the closest supported resolution of the device.

	//Analysis
	private AnalyserWrapper analyserWrapper;
	private final int exerciseKey = 1; // Key 1 = Squat
	private final String url = "Insert correct server url here!"; // The servers url.

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);

		previewView = findViewById(R.id.preview_View);
		graphicOverlay = findViewById(R.id.overlay_view);
		repetitionsText = findViewById(R.id.rep_count);
		feedbackBox = findViewById(R.id.feedback_text);
		currentMovementText = findViewById(R.id.phase_info);

		// As creating the analyserWrapper contains network operations, it must be done on a separate thread.
		new Thread(this::createAnalyserWrapper).start();

		// Check camera permission. If not granted, ask for permission. Handle response in onRequestPermissionsResult.
		if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
				== PackageManager.PERMISSION_DENIED) {
			// If camera permission is not granted, ask for it.
			ActivityCompat.requestPermissions(this, new String[] {
					Manifest.permission.CAMERA }, MY_PERMISSION_REQUEST_CAMERA);
		} else {
			setupCameraXUseCases();
		}
	}

	// Instantiates the analyserWrapper.
	private void createAnalyserWrapper() {
		try {
			analyserWrapper = new AnalyserWrapper(url, graphicOverlay, this, exerciseKey);
		} catch (IOException e) {
			Log.e(TAG, "Creating VAY analyser failed.");
			e.printStackTrace();
		}
	}

	// Handles the result of asking for camera permission. If the user accepted the request, the same
	// setup as in the onCreate is continued, otherwise a toast is displayed.
	@Override
	public void onRequestPermissionsResult(int requestCode,
			@NonNull String[] permissions, @NonNull int[] grantResults) {
		super.onRequestPermissionsResult(requestCode, permissions, grantResults);
		if (requestCode == MY_PERMISSION_REQUEST_CAMERA) {
			if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
				setupCameraXUseCases();
			} else {
				Toast.makeText(this, "Camera permission denied.", Toast.LENGTH_LONG).show();
			}
		}
	}

	// Sets up the cameraX preview and analysis use cases.
	private void setupCameraXUseCases() {
		cameraProviderFuture = ProcessCameraProvider.getInstance(this);
		cameraProviderFuture.addListener(() -> {
			try {
				ProcessCameraProvider cameraProvider = cameraProviderFuture.get();
				CameraSelector cameraSelector = new CameraSelector.Builder()
						.requireLensFacing(lensFacing).build();
				bindPreview(cameraProvider, cameraSelector);
				bindAnalysis(cameraProvider, cameraSelector);
			} catch (ExecutionException | InterruptedException e) {
				// No errors need to be handled for this Future.
				// This should never be reached.
				Log.d(TAG, "CameraProvider or bindPreviewAndAnalysis failed.");
			}
		}, ContextCompat.getMainExecutor(this));
	}

	void bindPreview(@NonNull ProcessCameraProvider cameraProvider,
			@NonNull CameraSelector cameraSelector) {
		cameraProvider.unbindAll();
		// Preview setup.
		Preview.Builder builder = new Preview.Builder();
		if (targetResolution != null) {
			builder.setTargetResolution(targetResolution);
		}
		final Preview previewUseCase = builder.build();

		previewView.setImplementationMode(PreviewView.ImplementationMode.PERFORMANCE);
		previewUseCase.setSurfaceProvider(previewView.getSurfaceProvider());

		// Bind preview to lifecycle.
		cameraProvider.bindToLifecycle(this, cameraSelector, previewUseCase);
	}

	private void bindAnalysis(@NonNull ProcessCameraProvider cameraProvider,
			@NonNull CameraSelector cameraSelector) {
		ImageAnalysis.Builder builder = new ImageAnalysis.Builder()
				.setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST);
		if (targetResolution != null) {
			builder.setTargetResolution(targetResolution);
		}
		final ImageAnalysis analysisUseCase = builder.build();

		needUpdateGraphicOverlayImageSourceInfo = true;
		analysisUseCase.setAnalyzer(Executors.newSingleThreadExecutor(), imageProxy -> { // ContextCompat.getMainExecutor(this)
			// AnalyzerWrapper could be null, if it has not yet been created. In that case we do nothing.
			if (analyserWrapper != null) {
				if (needUpdateGraphicOverlayImageSourceInfo) {
					boolean isImageFlipped = lensFacing == CameraSelector.LENS_FACING_FRONT;
					int rotationDegrees = imageProxy.getImageInfo().getRotationDegrees();
					if (rotationDegrees == 0 || rotationDegrees == 180) {
						graphicOverlay.setImageSourceInfo(
								imageProxy.getWidth(), imageProxy.getHeight(), isImageFlipped);
					} else {
						graphicOverlay.setImageSourceInfo(
								imageProxy.getHeight(), imageProxy.getWidth(), isImageFlipped);
					}
					analyserWrapper.setOverlay(graphicOverlay);
					needUpdateGraphicOverlayImageSourceInfo = false;
				}
				analyserWrapper.setPendingImage(imageProxy); // Passes the image to the analyser.
			}
			imageProxy.close();
		});
		cameraProvider.bindToLifecycle(this, cameraSelector, analysisUseCase);
	}

	@Override
	protected void onDestroy() {
		super.onDestroy();
		// Close the analyser.
		if (analyserWrapper != null) {
			analyserWrapper.close();
		}
	}

	// Gets called when the device rotates.
	@Override
	public void onConfigurationChanged(@NotNull Configuration newConfig) {
		super.onConfigurationChanged(newConfig);
		needUpdateGraphicOverlayImageSourceInfo = true;
	}

	// Functions to update views.

	public void setRepetitionsText(int reps) {
		setViewText(repetitionsText, String.format(getString(R.string.repetitions_text), reps));
	}

	public void setCurrentMovementText(String text) {
		setViewText(currentMovementText, text);
	}

	public void displayCorrection(String feedback) {
		runOnUiThread(()->{
			feedbackBox.setText(feedback);
			feedbackBox.setBackgroundColor(Color.rgb(225, 48, 48));
		});
	}

	public void displayPositiveMessage() {
		runOnUiThread(()->{
			feedbackBox.setText(R.string.good_job);
			feedbackBox.setBackgroundColor(Color.rgb(105, 255, 115));
		});
	}

	private void setViewText(TextView view, String text) {
		runOnUiThread(() -> view.setText(text));
	}
}
