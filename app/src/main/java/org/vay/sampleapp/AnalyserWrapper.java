package org.vay.sampleapp;

import android.annotation.SuppressLint;
import android.util.Log;

import androidx.camera.core.ImageProxy;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import ai.vay.client.api.Analyser;
import ai.vay.client.api.Listener;
import ai.vay.client.api.events.ErrorEvent;
import ai.vay.client.api.events.FormFeedbackEvent;
import ai.vay.client.api.events.FormMetricValuesEvent;
import ai.vay.client.api.events.PoseEvent;
import ai.vay.client.api.events.ReadyEvent;
import ai.vay.client.api.events.RepetitionEvent;
import ai.vay.client.api.events.SessionQualityChangedEvent;
import ai.vay.client.api.events.SessionStateChangedEvent;
import ai.vay.client.impl.AnalyserFactory;
import ai.vay.client.model.human.BodyPointType;
import ai.vay.client.model.human.Point;
import ai.vay.client.model.motion.FormMetricCheck;

/** Analyzer class responsible for creating and closing the client,
 * as well as sending the current image to the server. **/
public class AnalyserWrapper {
	private final String TAG = this.getClass().getSimpleName();
	private final Analyser analyser;
	private GraphicOverlay overlay;
	private final MainActivity activity;
	private boolean isShutdown = false;

	public AnalyserWrapper(String url, GraphicOverlay overlay, MainActivity activity, int exerciseKey) throws IOException {
		this.overlay = overlay;
		this.activity = activity;
		this.analyser = AnalyserFactory.createStreamingAnalyser(
				url, "AndroidSampleApp", exerciseKey, listener);
	}

	/** Used to set a new GraphicOverlay in case configurations change. **/
	public void setOverlay(GraphicOverlay overlay) {
		this.overlay = overlay;
	}

	/** Closes the client and prevents sending further images. **/
	public void close() {
		new Thread(analyser::stop).start();
		isShutdown = true;
	}

	/** Sets and prepares the current image. Converts the imageProxy (received by the cameraX
	 * analyzer function) to byte array, rotating it upright if needed. **/
	@SuppressLint("UnsafeExperimentalUsageError")
	public void setPendingImage(ImageProxy imageProxy) {
		if (isShutdown) {
			return;
		}
		analyser.enqueueInput(AnalyserFactory.createInput(ImageConverter.getByteArrayFromImageProxy(imageProxy)));
	}

	/** Anonymous inner listener class that reacts to server responses. Handles sending of images,
	 *  metadata, updates views and redraws the skeleton. **/
	private final Listener listener = new Listener() {
		private final String TAG = this.getClass().getSimpleName();
		private int correctReps = 0;
		private final Object lock = new Object();

		/** Gets called after the connection has been established. Sends the appropriate metadata, to
		 *  configure the exercise session. **/
		@Override
		public void onReady(ReadyEvent ReadyEvent) {
		}

		/** The results of the image analysis are received here. If you visualize the points,
		 * they should be updated here. **/
		@Override
		public void onPose(PoseEvent ImageResponseEvent) {
			Map<BodyPointType, Point> points = ImageResponseEvent.getPose().getPoints();
			resetGraphic(points); // Redraws the skeleton.
			// Current movement will be 'positioning' until the exercises starting position criteria
			// has been met, then it will display the exercise name.
		}

		/** Whenever a repetition is completed (with or without metric violations) this listener gets
		 *  called. Use this listener to count reps (here only correct reps are counted) and get the
		 *  corresponding corrections for faulty reps. **/
		@Override
		public void onRepetition(RepetitionEvent RepetitionEvent) {
			List<FormMetricCheck> violatedMetricChecks = RepetitionEvent.getRepetition()
					.getViolatedFormMetricChecks();
			if (violatedMetricChecks.isEmpty()) {
				correctReps++;
				activity.setRepetitionsText(correctReps);
				activity.displayPositiveMessage();
			} else {
				// A list of violated metrics for this rep.
				activity.displayCorrection(violatedMetricChecks.get(0).getFormCorrections().get(0));
				// Here we simply display the first correction from the list of violated metrics.
			}
		}

		/** Gets called when the client has been closed. **/
		@Override
		public void onStop() {
		}

		/** Gets called when an error occurs. **/
		@Override
		public void onError(ErrorEvent errorEvent) {
			Log.d(TAG, errorEvent.getErrorText());
		}

		@Override
		public void onSessionStateChanged(SessionStateChangedEvent event) {
			activity.setCurrentMovementText(event.getSessionState().name());
		}

		@Override
		public void onFormMetricValues(FormMetricValuesEvent event) {
		}

		@Override
		public void onFormFeedback(FormFeedbackEvent event) {
		}

		@Override
		public void onSessionQualityChanged(SessionQualityChangedEvent event) {
		}

		/** Clears the old graphic and sets a new one. Used to redraw the skeleton. **/
		private void resetGraphic(Map<BodyPointType, Point> points) {
			synchronized (lock) {
				overlay.clear();
				overlay.add(new PoseGraphic(overlay, points, false));
				overlay.postInvalidate();
			}
		}
	};
}
