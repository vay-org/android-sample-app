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
import ai.vay.client.api.events.PoseEvent;
import ai.vay.client.api.events.ReadyEvent;
import ai.vay.client.api.events.RepetitionEvent;
import ai.vay.client.api.events.SessionQualityChangedEvent;
import ai.vay.client.api.events.SessionStateChangedEvent;
import ai.vay.client.impl.AnalyserFactory;
import ai.vay.client.model.human.BodyPointType;
import ai.vay.client.model.human.Point;
import ai.vay.client.model.motion.Feedback;

/** Analyser  wrapper class responsible for creating and closing the analyser,
 * as well as enqueueing the current image. **/
public class AnalyserWrapper {
	private final String TAG = this.getClass().getSimpleName();
	private final Analyser analyser;
	private GraphicOverlay overlay;
	private final MainActivity activity;
	private boolean isShutdown = false;
	private final String token = "DUMMY-ANDROID-API-KEY";

	public AnalyserWrapper(String url, GraphicOverlay overlay, MainActivity activity, int exerciseKey) throws IOException {
		this.overlay = overlay;
		this.activity = activity;
		this.analyser = AnalyserFactory.createStreamingAnalyser(
				url, token, exerciseKey, listener);
	}

	/** Used to set a new GraphicOverlay in case configurations change. **/
	public void setOverlay(GraphicOverlay overlay) {
		this.overlay = overlay;
	}

	/** Closes the analyser and prevents sending further images. **/
	public void close() {
		new Thread(analyser::stop).start();
		isShutdown = true;
	}

	/** Prepares and enqueues the current image. Converts the imageProxy (received by the cameraX
	 * analyser function) to byte array, rotating it upright if needed. **/
	@SuppressLint("UnsafeExperimentalUsageError")
	public void setPendingImage(ImageProxy imageProxy) {
		if (isShutdown) {
			return;
		}
		analyser.enqueueInput(AnalyserFactory.createInput(ImageConverter.getByteArrayFromImageProxy(imageProxy)));
	}

	/** Anonymous inner listener class where custom behaviour upon various events can be defined. **/
	private final Listener listener = new Listener() {
		private final String TAG = this.getClass().getSimpleName();
		private int correctReps = 0;
		private final Object lock = new Object();

		/** Gets called after the connection has been established. **/
		@Override
		public void onReady(ReadyEvent ReadyEvent) {
		}

		/** The results of the image analysis (keypoints) - for each sent frame - are received here.
		 *  If you visualize the points, they should be updated here. **/
		@Override
		public void onPose(PoseEvent ImageResponseEvent) {
			Map<BodyPointType, Point> points = ImageResponseEvent.getPose().getPoints();
			resetGraphic(points); // Redraws the skeleton.
		}

		/** Whenever a repetition is completed (with or without metric violations) this listener gets
		 *  called. Use this listener to count reps (here only correct reps are counted) and get the
		 *  corresponding corrections for faulty reps. **/
		@Override
		public void onRepetition(RepetitionEvent RepetitionEvent) {
			// A list of violated feedbacks for this rep.
			List<Feedback> violatedFeedbacks = RepetitionEvent.getRepetition()
					.getFeedbacks();
			if (violatedFeedbacks.isEmpty()) {
				correctReps++;
				activity.setRepetitionsText(correctReps);
				activity.displayPositiveMessage();
			} else {
				// Here we simply display the first correction from the list of violated feedbacks.
				activity.displayCorrection(violatedFeedbacks.get(0).getName());
			}
		}

		/** Gets called when the analyser has been closed. **/
		@Override
		public void onStop() {
		}

		/** Gets called when an error occurs. **/
		@Override
		public void onError(ErrorEvent errorEvent) {
			Log.d(TAG, errorEvent.getErrorText());
		}

		/** Gets called when the session state changes. There are three states: NO_HUMAN, POSITIONING
		 * and EXERCISING **/
		@Override
		public void onSessionStateChanged(SessionStateChangedEvent event) {
			activity.setCurrentMovementText(event.getSessionState().name());
		}

		/** NOT YET IMPLEMENTED **/
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
