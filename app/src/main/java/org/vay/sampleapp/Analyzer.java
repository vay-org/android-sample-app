package org.vay.sampleapp;

import android.annotation.SuppressLint;
import android.util.Log;

import androidx.camera.core.ImageProxy;

import com.vaysports.vup.Client;
import com.vaysports.vup.ClientListener;
import com.vaysports.vup.VaysportsVup;
import com.vaysports.vup.VupErrorEvent;
import com.vaysports.vup.VupImageInterpolatedResponseEvent;
import com.vaysports.vup.VupImageResponseEvent;
import com.vaysports.vup.VupMetadataResponseEvent;
import com.vaysports.vup.VupReadyEvent;
import com.vaysports.vup.VupRepetitionEvent;

import java.io.IOException;
import java.net.URI;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Analyzer class responsible for creating and closing the client,
 * as well as sending the current image to the server. **/
public class Analyzer {
	private final String TAG = this.getClass().getSimpleName();
	private final Client client;
	private GraphicOverlay overlay;
	private final MainActivity activity;
	private final int exerciseKey;
	private boolean isShutdown = false;
	private byte[] pendingImage = null;
	private final Object analysisLock = new Object();

	public Analyzer(URI uri, GraphicOverlay overlay, MainActivity activity, int exerciseKey) throws IOException {
		this.overlay = overlay;
		this.activity = activity;
		this.exerciseKey = exerciseKey;
		this.client = Client.create(uri, listener);
	}

	/** Used to set a new GraphicOverlay in case configurations change. **/
	public void setOverlay(GraphicOverlay overlay) {
		this.overlay = overlay;
	}

	/** Closes the client and prevents sending further images. **/
	public void close() {
		new Thread(()-> {
			try {
				client.close();
			} catch (IOException e) {
				Log.d(TAG, "close: failed" + e.getMessage());
			}
		}).start();
		isShutdown = true;
	}

	/** Sets and prepares the current image. Converts the imageProxy (received by the cameraX
	 * analyzer function) to byte array, rotating it upright if needed. **/
	@SuppressLint("UnsafeExperimentalUsageError")
	public void setPendingImage(ImageProxy imageProxy) {
		if (isShutdown) {
			return;
		}
		synchronized (analysisLock) {
			pendingImage = null;
			pendingImage = ImageConverter.getByteArrayFromImageProxy(imageProxy);
			analysisLock.notifyAll();
		}
	}

	/** Publicly accessible function used to send the most current image. **/
	public void processImage() {
		sendImage(getPendingImage());
	}

	/** Sends the byte array to the server. **/
	private void sendImage(byte[] bytes) {
		if (isShutdown) {
			return;
		}
		try {
			// Note: Images sent to the server should be upright, compressed to JPEG or PNG format
			// and should not have a high resolution (as this would result in too large image sizes),
			// we recommend not to exceed 640x480.
			client.sendImage(bytes);
		} catch (IOException e) {
			Log.d(TAG, "Send Image failed. " + e.getMessage());
		}
	}

	/** Retrieves the most current image. **/
	private byte[] getPendingImage() {
		synchronized (analysisLock) {
			while (pendingImage == null && !isShutdown) {
				try {
					analysisLock.wait();
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
			}
			return this.pendingImage;
		}
	}

	/** Anonymous inner listener class that reacts to server responses. Handles sending of images,
	 *  metadata, updates views and redraws the skeleton. **/
	private final ClientListener listener = new ClientListener() {
		private final String TAG = this.getClass().getSimpleName();
		private int correctReps = 0;
		private final ExecutorService executor = Executors.newSingleThreadExecutor();
		private final Object lock = new Object();

		/** Gets called after the connection has been established. Sends the appropriate metadata, to
		 *  configure the exercise session. **/
		@Override
		public void onReady(VupReadyEvent vupReadyEvent) {
			Client client = vupReadyEvent.getClient();
			String uid = "sampleApp"; // Change this to something that identifies you.
			Client.KeyType taskType = Client.KeyType.Exercise; // Should always be of type exercise.
			try {
				client.sendMetadata(uid, taskType, exerciseKey);
			} catch (IOException e) {
				Log.d(TAG, "Sending metadata failed! " + e.getMessage());
			}
		}

		/** Gets called when the session has been successfully configured, indicating readiness to
		 *  process images. Hence the first image should be sent here. **/
		@Override
		public void onMetadataResponse(VupMetadataResponseEvent vupMetadataResponseEvent) {
			executor.execute(()-> processImage()); // Sends the first image.
		}

		/** The results of the image analysis are received here. The appropriate place to send a new
		 *  image. If you visualize the points, they should be updated here. **/
		@Override
		public void onImageResponse(VupImageResponseEvent vupImageResponseEvent) {
			executor.execute(()-> processImage()); // Send a new image.
			VaysportsVup.HumanPoints points = vupImageResponseEvent.getMessage().getPoints();
			resetGraphic(points); // Redraws the skeleton.
			activity.setCurrentMovementText(vupImageResponseEvent.getMessage().getCurrentMovement());
			// Current movement will be 'positioning' until the exercises starting position criteria
			// has been met, then it will display the exercise name.
		}

		/** The server extrapolates key points in order to allow smoother skeleton visualisation.
		 *  Therefore this listener should only be used to redraw the skeleton. **/
		@Override
		public void onImageInterpolatedResponse(VupImageInterpolatedResponseEvent vupImageInterpolatedResponseEvent) {
			VaysportsVup.HumanPoints points = vupImageInterpolatedResponseEvent.getMessage().getPoints();
			resetGraphic(points);
		}

		/** Whenever a repetition is completed (with or without metric violations) this listener gets
		 *  called. Use this listener to count reps (here only correct reps are counted) and get the
		 *  corresponding corrections for faulty reps. **/
		@Override
		public void onRepetition(VupRepetitionEvent vupRepetitionEvent) {
			if (vupRepetitionEvent.getMessage().getViolatedMetricKeysCount() < 1) {
				correctReps++;
				activity.setRepetitionsText(correctReps);
				activity.displayPositiveMessage();
			} else {
				// A list of violated metrics for this rep.
				List<VaysportsVup.MetricMessage> violatedMetricsList = vupRepetitionEvent.getMetricMessages();
				activity.displayCorrection(violatedMetricsList.get(0).getCorrection());
				// Here we simply display the first correction from the list of violated metrics.
			}
		}

		/** Gets called when the client has been closed. **/
		@Override
		public void onClose() {
			executor.shutdownNow(); // Shuts down the image sending threads.
		}

		/** Gets called when an error occurs. **/
		@Override
		public void onError(VupErrorEvent vupErrorEvent) {
			switch (vupErrorEvent.getType()) {
				case EXCEPTION: // Exception on the server side.
					Log.d(TAG, "Exception occurred: " + vupErrorEvent.getException().getMessage());
					break;
				case CONFIGURATION_ERROR: // Indicates that an image has been sent before the session was configured via send metadata.
					Log.d(TAG, "Error received: CONFIGURATION_ERROR!");
					break;
				case NO_HUMAN: // Can occur when the image contains no human.
					Log.d(TAG, "Error received: NO_HUMAN!");
					break;
				case LOW_QUALITY: // Indicates too low resolution.
					Log.d(TAG, "Error received: LOW_QUALITY!");
					break;
				case SCORES_TOO_LOW: // If all the points have a very low confidence score.
					Log.d(TAG, "Error received: SCORES_TOO_LOW!");
					break;
				case MESSAGE_DECODE_ERROR: // Indicates wrong image data format.
					Log.d(TAG, "Error received: MESSAGE_DECODE_ERROR!");
					break;
				case ID_NOT_FOUND: // The key sent with the metadata was not found.
					Log.d(TAG, "Error received: ID_NOT_FOUND!");
					break;
				case LOST_PHASE: // The system failed to track the movement.
					Log.d(TAG, "Error received: LOST_PHASE!");
					break;
				default:
					Log.d(TAG, "Unhandled error received!");
			}
		}

		/** Clears the old graphic and sets a new one. Used to redraw the skeleton. **/
		private void resetGraphic(VaysportsVup.HumanPoints points) {
			synchronized (lock) {
				overlay.clear();
				overlay.add(new PoseGraphic(overlay, points, false));
				overlay.postInvalidate();
				Log.d(TAG, "GraphicOverlay invalidated.");
			}

		}
	};
}
