package org.vay.sampleapp;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Point;
import android.graphics.PointF;

import com.vaysports.vup.VaysportsVup;

import java.util.Locale;

/** Custom Graphic class to visualize and connect the received points, rendering a skeleton. **/
public class PoseGraphic extends GraphicOverlay.Graphic {
	private static final float DOT_RADIUS = 8.0f;
	private static final float IN_FRAME_LIKELIHOOD_TEXT_SIZE = 30.0f;
	private static final float STROKE_WIDTH = 10.0f;

	private final boolean showLikelihood;

	private final Paint whitePaint;
	private final VaysportsVup.Point[] points;
	private final Point[] lineCoordinates = new Point[]{ // Coordinates to connect points.
			new Point(0, 1), // Nose to neck.
			new Point(1, 6), // Neck to left shoulder.
			new Point(6, 8), // Left shoulder to left elbow.
			new Point(8, 10), // Left elbow to left  wrist.
			new Point(1, 7), // Neck to right shoulder.
			new Point(7, 9), // Right shoulder to right elbow.
			new Point(9, 11), // Right elbow to right wrist.
			new Point(1, 18), // Neck to middle hip.
			new Point(12, 18), // Left and middle hip.
			new Point(13, 18), // Right and middle hip.
			new Point(12, 14), // Left hip to left knee.
			new Point(14, 16), // Left knee to left ankle.
			new Point(13, 15), // Right hip to right knee.
			new Point(15, 17)}; // Right knee to right ankle.
	private final double mScore = 0.6; // Set confidentiality score threshold here.

	// If showLikelihood is true, the score of each point will be displayed.
	public PoseGraphic(GraphicOverlay overlay, VaysportsVup.HumanPoints humanPoints, boolean showLikelihood) {
		super(overlay);
		this.points = assignPoints(humanPoints);
		this.showLikelihood = showLikelihood;
		whitePaint = new Paint();
		whitePaint.setStrokeWidth(STROKE_WIDTH);
		whitePaint.setColor(Color.WHITE);
		whitePaint.setTextSize(IN_FRAME_LIKELIHOOD_TEXT_SIZE);
	}

	@Override
	public void draw(Canvas canvas) {
		drawPoints(canvas);
		drawLines(canvas);
	}

	private void drawPoints(Canvas canvas) {
		for (VaysportsVup.Point point : points) {
			if (point.getScore() > mScore) {
				// Draw circle needs the coordinates of its center and its radius combined with a paint.
				canvas.drawCircle(
						translateX((float) point.getX()), translateY((float) point.getY()),
						DOT_RADIUS, whitePaint);
				if (showLikelihood) {
					canvas.drawText(
							String.format(Locale.ENGLISH, "%.2f", point.getScore()),
							translateX((float) point.getX()),
							translateY((float) point.getY()),
							whitePaint
					);
				}
			}
		}
	}

	private void drawLines(Canvas canvas) {
		for (Point linePoint : lineCoordinates) {
			PointF start = new PointF(translateX((float) points[linePoint.x].getX()), translateY((float) points[linePoint.x].getY()));
			PointF end = new PointF(translateX((float) points[linePoint.y].getX()), translateY((float) points[linePoint.y].getY()));
			if (points[linePoint.x].getScore() > mScore && points[linePoint.y].getScore() > mScore) {
				canvas.drawLine(
						start.x, start.y, end.x, end.y,
						whitePaint);
			}
		}
	}

	/** Assigns individual points into a (vup)point array. **/
	private VaysportsVup.Point[] assignPoints(VaysportsVup.HumanPoints humanPoints) {
		return new VaysportsVup.Point[]{
				humanPoints.getNose(),
				humanPoints.getNeck(),
				humanPoints.getLeftEar(),
				humanPoints.getRightEar(),
				humanPoints.getLeftEye(),
				humanPoints.getRightEye(),
				humanPoints.getLeftShoulder(),
				humanPoints.getRightShoulder(),
				humanPoints.getLeftElbow(),
				humanPoints.getRightElbow(),
				humanPoints.getLeftWrist(),
				humanPoints.getRightWrist(),
				humanPoints.getLeftHip(),
				humanPoints.getRightHip(),
				humanPoints.getLeftKnee(),
				humanPoints.getRightKnee(),
				humanPoints.getLeftAnkle(),
				humanPoints.getRightAnkle(),
				humanPoints.getMidHip()};
	}
}
