package org.vay.sampleapp;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PointF;

import java.util.AbstractMap;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import ai.vay.client.model.human.BodyPointType;
import ai.vay.client.model.human.Point;

/** Custom Graphic class to visualize and connect the received points, rendering a skeleton. **/
public class PoseGraphic extends GraphicOverlay.Graphic {
	private static final float DOT_RADIUS = 8.0f;
	private static final float STROKE_WIDTH = 10.0f;
	private final Paint whitePaint;
	private final Map<BodyPointType, Point> points;
	// Defines the connections between the different points to draw the skeleton.
	private final List<Map.Entry<BodyPointType, BodyPointType>> lineConnections = Arrays.asList(
			new AbstractMap.SimpleEntry<>(BodyPointType.NOSE, BodyPointType.NECK),
			new AbstractMap.SimpleEntry<>(BodyPointType.NECK, BodyPointType.LEFT_SHOULDER),
			new AbstractMap.SimpleEntry<>(BodyPointType.LEFT_SHOULDER, BodyPointType.LEFT_ELBOW),
			new AbstractMap.SimpleEntry<>(BodyPointType.LEFT_ELBOW, BodyPointType.LEFT_WRIST),
			new AbstractMap.SimpleEntry<>(BodyPointType.NECK, BodyPointType.RIGHT_SHOULDER),
			new AbstractMap.SimpleEntry<>(BodyPointType.RIGHT_SHOULDER, BodyPointType.RIGHT_ELBOW),
			new AbstractMap.SimpleEntry<>(BodyPointType.RIGHT_ELBOW, BodyPointType.RIGHT_WRIST),
			new AbstractMap.SimpleEntry<>(BodyPointType.NECK, BodyPointType.MID_HIP),
			new AbstractMap.SimpleEntry<>(BodyPointType.LEFT_HIP, BodyPointType.MID_HIP),
			new AbstractMap.SimpleEntry<>(BodyPointType.RIGHT_HIP, BodyPointType.MID_HIP),
			new AbstractMap.SimpleEntry<>(BodyPointType.LEFT_HIP, BodyPointType.LEFT_KNEE),
			new AbstractMap.SimpleEntry<>(BodyPointType.LEFT_KNEE, BodyPointType.LEFT_ANKLE),
			new AbstractMap.SimpleEntry<>(BodyPointType.RIGHT_HIP, BodyPointType.RIGHT_KNEE),
			new AbstractMap.SimpleEntry<>(BodyPointType.RIGHT_KNEE, BodyPointType.RIGHT_ANKLE)
	);

	public PoseGraphic(GraphicOverlay overlay,
					   Map<BodyPointType, Point> pointsMap) {
		super(overlay);
		this.points = pointsMap;
		whitePaint = new Paint();
		whitePaint.setStrokeWidth(STROKE_WIDTH);
		whitePaint.setColor(Color.WHITE);
	}

	@Override
	public void draw(Canvas canvas) {
		drawPoints(canvas);
		drawLines(canvas);
	}

	private void drawPoints(Canvas canvas) {
		for (Point point : points.values()) {
			if (point.isAccurate()) {
				// Draw circle needs the coordinates of its center and its radius combined with a paint.
				canvas.drawCircle(
						translateX((float) point.getX()), translateY((float) point.getY()),
						DOT_RADIUS, whitePaint);
			}
		}
	}

	private void drawLines(Canvas canvas) {
		for (Map.Entry<BodyPointType, BodyPointType> entry : lineConnections) {
			Point startPoint = points.get(entry.getKey());
			Point endPoint = points.get(entry.getValue());
			if (startPoint == null || endPoint == null) {
				continue;
			}
			PointF start = new PointF(
					translateX((float) startPoint.getX()),
					translateY((float) startPoint.getY()));
			PointF end = new PointF(
					translateX((float) endPoint.getX()),
					translateY((float) endPoint.getY()));
			if (startPoint.isAccurate() && endPoint.isAccurate()) {
				canvas.drawLine(
						start.x, start.y, end.x, end.y,
						whitePaint);
			}
		}
	}
}
