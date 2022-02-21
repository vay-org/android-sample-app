package org.vay.sampleapp;

import android.util.Log;

import androidx.camera.core.ImageProxy;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import ai.vay.client.api.Analyser;
import ai.vay.client.api.AnalyserInput;
import ai.vay.client.api.Listener;
import ai.vay.client.api.SessionState;
import ai.vay.client.api.events.ErrorEvent;
import ai.vay.client.api.events.FeedbackEvent;
import ai.vay.client.api.events.PoseEvent;
import ai.vay.client.api.events.ReadyEvent;
import ai.vay.client.api.events.RepetitionEvent;
import ai.vay.client.api.events.SessionQualityChangedEvent;
import ai.vay.client.api.events.SessionStateChangedEvent;
import ai.vay.client.api.SessionQuality.Quality;
import ai.vay.client.api.SessionQuality.Subject;
import ai.vay.client.impl.AnalyserFactory;
import ai.vay.client.model.human.BodyPointType;
import ai.vay.client.model.human.Point;
import ai.vay.client.model.motion.Feedback;

/** Analyser wrapper class responsible for creating and closing the analyser,
 * as well as enqueueing the current image. **/
public class AnalyserWrapper {
	private final String TAG = this.getClass().getSimpleName();
	private final Analyser analyser;
	private GraphicOverlay overlay;
	private final MainActivity activity;
	private boolean isShutdown = false;
	private final String apiKey = "DUMMY-ANDROID-API-KEY";

	public AnalyserWrapper(String url, GraphicOverlay overlay, MainActivity activity, int exerciseKey)
			throws IOException {
		this.overlay = overlay;
		this.activity = activity;
		this.analyser = AnalyserFactory.createStreamingAnalyser(url, apiKey, exerciseKey, listener);
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
	public void setPendingImage(ImageProxy imageProxy) {
		if (isShutdown) {
			return;
		}
		byte[] bytes = ImageConverter.getByteArrayFromImageProxy(imageProxy);
		AnalyserInput input = AnalyserFactory.createInput(bytes);
		analyser.enqueueInput(input);
	}

	/** Anonymous inner listener class where custom behaviour upon various events can be defined. **/
	private final Listener listener = new Listener() {
		private final String TAG = this.getClass().getSimpleName();
		private SessionState sessionState = SessionState.NO_HUMAN;
		private int correctRepetitions = 0;
		private final Object lock = new Object();

		/** Gets called after the connection has been established. **/
		@Override
		public void onReady(ReadyEvent event) {
		}

		/** The results of the image analysis (keypoints) - for each sent frame - are received here.
		 *  If you visualize the points, they should be updated here. **/
		@Override
		public void onPose(PoseEvent event) {
			Map<BodyPointType, Point> points = event.getPose().getPoints();
			updateGraphic(points); // Redraws the skeleton.
		}

		/** Here real time feedback is received. During exercising, this event is called anytime
		 * feedback is generated. While not in exercising state, positioning guidance - which
		 * instructs the user on how to get into exercising state - is received here. In this
		 * sample, onFeedback is only used for positioning guidance. **/
		@Override
		public void onFeedback(FeedbackEvent event) {
			if (sessionState != SessionState.EXERCISING) {
				// In our simplified sample app, we only display the first feedback.
				activity.displayPositioningGuidance(event.getFeedbacks().get(0).getMessages().get(0));
			}
		}

		/** Whenever a repetition is completed (correct or incorrect) this event gets
		 *  called. Use this event to count repetitions (here only correct ones are counted) and get
		 *  the corresponding corrections for faulty repetitions. **/
		@Override
		public void onRepetition(RepetitionEvent event) {
			// A list of feedback for this repetition.
			List<Feedback> feedback = event.getRepetition().getFeedbacks();
			// If no feedback was generated, this means the repetition was performed correctly.
			if (feedback.isEmpty()) {
				correctRepetitions++;
				activity.setRepetitionsText(correctRepetitions);
				activity.displayPositiveMessage();
			} else {
				// Here we simply display the first correction from the list of feedback.
				activity.displayCorrection(feedback.get(0).getMessages().get(0));
			}
		}

		/** Gets called when the analyser has been closed. **/
		@Override
		public void onStop() {
		}

		/** Gets called when an error occurs. **/
		@Override
		public void onError(ErrorEvent event) {
			Log.d(TAG, "Error type: " + event.getErrorType().name() + " Error message: " +
					event.getErrorText());
		}

		/** Gets called when the session state changes. There are three states: NO_HUMAN, POSITIONING
		 * and EXERCISING **/
		@Override
		public void onSessionStateChanged(SessionStateChangedEvent event) {
			SessionState previousSessionState = sessionState;
			sessionState = event.getSessionState();
			activity.setCurrentStateText(sessionState.name());
			activity.setStateIndicationColor(sessionState);
			if (previousSessionState == SessionState.POSITIONING &&
				sessionState == SessionState.EXERCISING) {
				activity.displayPositioningGuidance("Positioning successful");
			}
		}

		/** The session quality quantifies the LATENCY and the ENVIRONMENT. If one of these subjects
		 *  changes, the SessionQualityChangedEvent is called, where the new ratings for both
		 *  subjects are provided. Possible qualities are:
		 * 	BAD - The quality is too bad to analyse images, no analysis is conducted.
		 * 	POOR - The quality is poor but still good enough to perform the movement analysis,
		 * 		the accuracy might be affected.
		 * 	GOOD - The quality is optimal.
		 * 	In this sample we only show a warning, if either quality is poor.	**/
		@Override
		public void onSessionQualityChanged(SessionQualityChangedEvent event) {
			Quality latency = event.getSessionQuality().getQuality().get(Subject.LATENCY);
			Quality environment = event.getSessionQuality().getQuality().get(Subject.ENVIRONMENT);
			if (latency == Quality.POOR || environment == Quality.POOR) {
				activity.setConnectivityWarningText("POOR SESSION QUALITY DETECTED!");
			} else {
				activity.setConnectivityWarningText("");
			}
		}

		/** Clears the old graphic and sets a new one. Used to redraw the skeleton. **/
		private void updateGraphic(Map<BodyPointType, Point> points) {
			synchronized (lock) {
				overlay.clear();
				overlay.add(new PoseGraphic(overlay, points));
				overlay.postInvalidate();
			}
		}
	};
}
