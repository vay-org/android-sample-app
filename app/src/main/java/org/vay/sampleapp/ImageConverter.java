package org.vay.sampleapp;

import android.annotation.SuppressLint;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.graphics.YuvImage;
import android.media.Image;
import android.media.Image.Plane;
import android.os.Build.VERSION_CODES;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.camera.core.ImageProxy;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;

public final class ImageConverter {
	private static final String TAG = "ImageConverter";

	/** Converts imageProxy to byte array, rotating the image upright if it's not already. **/
	public static byte[] getByteArrayFromImageProxy(ImageProxy image) {
		if (image.getImageInfo().getRotationDegrees() == 0) {
			return getArrayFromImageProxy(image);
		} else {
			return getArrayFromBitmap(getBitmapFromImageProxy(image));
		}
	}
	/** Convert bitmap to byte array with JPEG compression. **/
	private static byte[] getArrayFromBitmap(Bitmap bmp) {
		ByteArrayOutputStream stream = new ByteArrayOutputStream();
		bmp.compress(Bitmap.CompressFormat.JPEG, 80, stream);
		byte[] byteArray = stream.toByteArray();
		bmp.recycle();
		return byteArray;
	}

	/** Converts a YUV_420_888 image from CameraX API to byte array. */
	@RequiresApi(VERSION_CODES.KITKAT)
	@Nullable
	@SuppressLint("UnsafeOptInUsageError")
	private static byte[] getArrayFromImageProxy(ImageProxy image) {
		ByteBuffer nv21Buffer = yuv420ThreePlanesToNV21(
				image.getImage().getPlanes(), image.getWidth(), image.getHeight());
		return getByteArray(nv21Buffer, image.getWidth(), image.getHeight());
	}

	/** Converts a YUV_420_888 image from CameraX API to a bitmap. */
	@RequiresApi(VERSION_CODES.KITKAT)
	@Nullable
	@SuppressLint("UnsafeOptInUsageError")
	private static Bitmap getBitmapFromImageProxy(ImageProxy image) {
		ByteBuffer nv21Buffer = yuv420ThreePlanesToNV21(
				image.getImage().getPlanes(), image.getWidth(), image.getHeight());
		return getBitmap(nv21Buffer, image.getWidth(), image.getHeight(), image.getImageInfo().getRotationDegrees());
	}

	/** Converts NV21 format byte buffer to bitmap. */
	@Nullable
	private static Bitmap getBitmap(ByteBuffer data, int width, int height, int rotation) {
		data.rewind();
		byte[] imageInBuffer = new byte[data.limit()];
		data.get(imageInBuffer, 0, imageInBuffer.length);
		try {
			YuvImage image =
					new YuvImage(
							imageInBuffer, ImageFormat.NV21, width, height, null);
			ByteArrayOutputStream stream = new ByteArrayOutputStream();
			image.compressToJpeg(new Rect(0, 0, width, height), 80, stream);

			Bitmap bmp = BitmapFactory.decodeByteArray(stream.toByteArray(), 0, stream.size());

			stream.close();
			return rotateBitmap(bmp, rotation, false, false);
		} catch (Exception e) {
			Log.e(TAG, "Error: " + e.getMessage());
		}
		return null;
	}

	/** Converts NV21 format byte buffer to byte array. */
	@Nullable
	private static byte[] getByteArray(ByteBuffer data, int width, int height) {
		data.rewind();
		byte[] imageInBuffer = new byte[data.limit()];
		data.get(imageInBuffer, 0, imageInBuffer.length);
		try {
			YuvImage image =
					new YuvImage(
							imageInBuffer, ImageFormat.NV21, width, height, null);
			ByteArrayOutputStream stream = new ByteArrayOutputStream();
			image.compressToJpeg(new Rect(0, 0, width, height), 80, stream);
			stream.close();
			return stream.toByteArray();
		} catch (Exception e) {
			Log.e(TAG, "Error: " + e.getMessage());
		}
		return null;
	}

	/** Rotates a bitmap if it is converted from a bytebuffer. */
	private static Bitmap rotateBitmap(
			Bitmap bitmap, int rotationDegrees, boolean flipX, boolean flipY) {
		Matrix matrix = new Matrix();

		// Rotate the image back to straight.
		matrix.postRotate(rotationDegrees);

		// Mirror the image along the X or Y axis.
		matrix.postScale(flipX ? -1.0f : 1.0f, flipY ? -1.0f : 1.0f);
		Bitmap rotatedBitmap =
				Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);

		// Recycle the old bitmap if it has changed.
		if (rotatedBitmap != bitmap) {
			bitmap.recycle();
		}
		return rotatedBitmap;
	}

	/**
	 * Converts YUV_420_888 to NV21 bytebuffer.
	 *
	 * <p>The NV21 format consists of a single byte array containing the Y, U and V values. For an
	 * image of size S, the first S positions of the array contain all the Y values. The remaining
	 * positions contain interleaved V and U values. U and V are subsampled by a factor of 2 in both
	 * dimensions, so there are S/4 U values and S/4 V values. In summary, the NV21 array will contain
	 * S Y values followed by S/4 VU values: YYYYYYYYYYYYYY(...)YVUVUVUVU(...)VU
	 *
	 * <p>YUV_420_888 is a generic format that can describe any YUV image where U and V are subsampled
	 * by a factor of 2 in both dimensions. {@link Image#getPlanes} returns an array with the Y, U and
	 * V planes. The Y plane is guaranteed not to be interleaved, so we can just copy its values into
	 * the first part of the NV21 array. The U and V planes may already have the representation in the
	 * NV21 format. This happens if the planes share the same buffer, the V buffer is one position
	 * before the U buffer and the planes have a pixelStride of 2. If this is case, we can just copy
	 * them to the NV21 array.
	 */
	@RequiresApi(VERSION_CODES.KITKAT)
	private static ByteBuffer yuv420ThreePlanesToNV21(
			Plane[] yuv420888planes, int width, int height) {
		int imageSize = width * height;
		byte[] out = new byte[imageSize + 2 * (imageSize / 4)];

		if (areUVPlanesNV21(yuv420888planes, width, height)) {
			// Copy the Y values.
			yuv420888planes[0].getBuffer().get(out, 0, imageSize);

			ByteBuffer uBuffer = yuv420888planes[1].getBuffer();
			ByteBuffer vBuffer = yuv420888planes[2].getBuffer();
			// Get the first V value from the V buffer, since the U buffer does not contain it.
			vBuffer.get(out, imageSize, 1);
			// Copy the first U value and the remaining VU values from the U buffer.
			uBuffer.get(out, imageSize + 1, 2 * imageSize / 4 - 1);
		} else {
			// Fallback to copying the UV values one by one, which is slower but also works.
			// Unpack Y.
			unpackPlane(yuv420888planes[0], width, height, out, 0, 1);
			// Unpack U.
			unpackPlane(yuv420888planes[1], width, height, out, imageSize + 1, 2);
			// Unpack V.
			unpackPlane(yuv420888planes[2], width, height, out, imageSize, 2);
		}

		return ByteBuffer.wrap(out);
	}

	/** Checks if the UV plane buffers of a YUV_420_888 image are in the NV21 format. */
	@RequiresApi(VERSION_CODES.KITKAT)
	private static boolean areUVPlanesNV21(Plane[] planes, int width, int height) {
		int imageSize = width * height;

		ByteBuffer uBuffer = planes[1].getBuffer();
		ByteBuffer vBuffer = planes[2].getBuffer();

		// Backup buffer properties.
		int vBufferPosition = vBuffer.position();
		int uBufferLimit = uBuffer.limit();

		// Advance the V buffer by 1 byte, since the U buffer will not contain the first V value.
		vBuffer.position(vBufferPosition + 1);
		// Chop off the last byte of the U buffer, since the V buffer will not contain the last U value.
		uBuffer.limit(uBufferLimit - 1);

		// Check that the buffers are equal and have the expected number of elements.
		boolean areNV21 =
				(vBuffer.remaining() == (2 * imageSize / 4 - 2)) && (vBuffer.compareTo(uBuffer) == 0);

		// Restore buffers to their initial state.
		vBuffer.position(vBufferPosition);
		uBuffer.limit(uBufferLimit);

		return areNV21;
	}

	/** Checks if the UV plane buffers of a YUV_420_888 image are in the NV21 format. */
	@RequiresApi(VERSION_CODES.KITKAT)
	private static void unpackPlane(
			Plane plane, int width, int height, byte[] out, int offset, int pixelStride) {
		ByteBuffer buffer = plane.getBuffer();
		buffer.rewind();

		// Compute the size of the current plane.
		// We assume that it has the aspect ratio as the original image.
		int numRow = (buffer.limit() + plane.getRowStride() - 1) / plane.getRowStride();
		if (numRow == 0) {
			return;
		}
		int scaleFactor = height / numRow;
		int numCol = width / scaleFactor;

		// Extract the data in the output buffer.
		int outputPos = offset;
		int rowStart = 0;
		for (int row = 0; row < numRow; row++) {
			int inputPos = rowStart;
			for (int col = 0; col < numCol; col++) {
				out[outputPos] = buffer.get(inputPos);
				outputPos += pixelStride;
				inputPos += plane.getPixelStride();
			}
			rowStart += plane.getRowStride();
		}
	}
}
