package com.example.nosco;

import java.io.FileOutputStream;
import java.util.List;

import org.opencv.android.JavaCameraView;
import org.opencv.android.Utils;
import org.opencv.core.Core;
import org.opencv.core.Mat;
import org.opencv.core.MatOfRect;
import org.opencv.core.Rect;
import org.opencv.objdetect.CascadeClassifier;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.hardware.Camera;
import android.hardware.Camera.PictureCallback;
import android.hardware.Camera.Size;
import android.util.AttributeSet;
import android.util.Log;

public class TrainCameraView extends JavaCameraView implements PictureCallback {
	private final String TAG = "TrainCameraView";
	private String mPictureFileName;

	private float mRelativeFaceSize = 0.8f;
	private int mAbsoluteFaceSize = 0;

	private Rect roi;

	private CascadeClassifier mJavaDetector;

	public TrainCameraView(Context context, AttributeSet attrs) {
		super(context, attrs);

	}

	public List<String> getEffectList() {
		return mCamera.getParameters().getSupportedColorEffects();
	}

	public boolean isEffectSupported() {
		return (mCamera.getParameters().getColorEffect() != null);
	}

	public String getEffect() {
		return mCamera.getParameters().getColorEffect();
	}

	public void setEffect(String effect) {
		Camera.Parameters params = mCamera.getParameters();
		params.setColorEffect(effect);
		mCamera.setParameters(params);
	}

	public List<Size> getResolutionList() {
		return mCamera.getParameters().getSupportedPreviewSizes();
	}

	public void setResolution(Size resolution) {
		disconnectCamera();
		mMaxHeight = resolution.height;
		mMaxWidth = resolution.width;
		connectCamera(getWidth(), getHeight());
	}

	public Size getResolution() {
		return mCamera.getParameters().getPreviewSize();
	}

	public void takePicture(final String fileName, Rect roi) {
		Log.i(TAG, "Taking picture");
		this.mPictureFileName = fileName;
		this.roi = roi;
		// Postview and jpeg are sent in the same buffers if the queue is not
		// empty when performing a capture.
		// Clear up buffers to avoid mCamera.takePicture to be stuck because of
		// a memory issue
		mCamera.setPreviewCallback(null);

		// PictureCallback is implemented by the current class
		mCamera.takePicture(null, null, this);
	}

	@Override
	public void onPictureTaken(byte[] data, Camera camera) {
		Log.i(TAG, "Saving a bitmap to file");
		// The camera preview was automatically stopped. Start it again.
		mCamera.startPreview();
		mCamera.setPreviewCallback(this);

		Mat m = new Mat();
		Bitmap bmp = BitmapFactory.decodeByteArray(data, 0, data.length);
		Utils.bitmapToMat(bmp, m);
		Log.i(TAG, "ROI dims. Height: " + roi.height + " Width: " + roi.width + 
				" X: " + roi.x + " Y: " + roi.y);
		Log.i(TAG, "Mat dims. Height: " + m.height() + " Width: " + m.width());
		Mat m_new = m.submat(roi);

		byte[] return_buff = new byte[(int) (m_new.total() * m_new
				.channels())];
		m_new.get(0, 0, return_buff);

		// Write the image in a file (in jpeg format)
		try {
			FileOutputStream fos = new FileOutputStream(mPictureFileName);

			fos.write(data);
			fos.close();

		} catch (java.io.IOException e) {
			Log.e(TAG, "Exception in onPictureTaken", e);
		}

	}

}
