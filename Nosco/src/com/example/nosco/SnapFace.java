package com.example.nosco;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import org.opencv.android.BaseLoaderCallback;
import org.opencv.android.CameraBridgeViewBase;
import org.opencv.android.CameraBridgeViewBase.CvCameraViewFrame;
import org.opencv.android.CameraBridgeViewBase.CvCameraViewListener2;
import org.opencv.android.LoaderCallbackInterface;
import org.opencv.android.OpenCVLoader;
import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfRect;
import org.opencv.core.Point;
import org.opencv.core.Rect;
import org.opencv.core.RotatedRect;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.highgui.Highgui;
import org.opencv.imgproc.Imgproc;
import org.opencv.objdetect.CascadeClassifier;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.Toast;
import android.view.View.OnTouchListener;
import android.view.animation.AlphaAnimation;

public class SnapFace extends Activity implements CvCameraViewListener2,
		OnTouchListener {

	private final String imgPath = Environment.DIRECTORY_PICTURES;

	private static final String TAG = "OCVSample::Activity";
	private static final Scalar FACE_RECT_COLOR = new Scalar(0, 255, 0, 255);
	private static final Scalar ELLIPSE_COLOUR = new Scalar(255, 0, 0, 255);
	public static final int JAVA_DETECTOR = 0;
	public static final int NATIVE_DETECTOR = 1;

	private Mat mRgba;
	private Mat mCurFace;
	private Mat mGray;
	private File mCascadeFile;
	private CascadeClassifier mJavaDetector;
	private DetectionBasedTracker mNativeDetector;

	private String[] mDetectorName;

	private float mRelativeFaceSize = 0.8f;
	private int mAbsoluteFaceSize = 0;

	private String mFirstname;
	private String mLastname;
	private String mPersonId;

	private Rect roi;

	private CameraBridgeViewBase mOpenCvCameraView;

	private ImageView trigger;

	private BaseLoaderCallback mLoaderCallback = new BaseLoaderCallback(this) {
		@Override
		public void onManagerConnected(int status) {
			switch (status) {
			case LoaderCallbackInterface.SUCCESS: {
				Log.i(TAG, "OpenCV loaded successfully");

				// Load native library after(!) OpenCV initialization
				System.loadLibrary("detection_based_tracker");

				try {
					// load cascade file from application resources
					InputStream is = getResources().openRawResource(
							R.raw.lbpcascade_frontalface);
					File cascadeDir = getDir("cascade", Context.MODE_PRIVATE);
					mCascadeFile = new File(cascadeDir,
							"lbpcascade_frontalface.xml");
					FileOutputStream os = new FileOutputStream(mCascadeFile);

					byte[] buffer = new byte[4096];
					int bytesRead;
					while ((bytesRead = is.read(buffer)) != -1) {
						os.write(buffer, 0, bytesRead);
					}
					is.close();
					os.close();

					mJavaDetector = new CascadeClassifier(
							mCascadeFile.getAbsolutePath());
					if (mJavaDetector.empty()) {
						Log.e(TAG, "Failed to load cascade classifier");
						mJavaDetector = null;
					} else
						Log.i(TAG, "Loaded cascade classifier from "
								+ mCascadeFile.getAbsolutePath());

					mNativeDetector = new DetectionBasedTracker(
							mCascadeFile.getAbsolutePath(), 0);

					cascadeDir.delete();

				} catch (IOException e) {
					e.printStackTrace();
					Log.e(TAG, "Failed to load cascade. Exception thrown: " + e);
				}

				mOpenCvCameraView.enableView();
			}
				break;
			default: {
				super.onManagerConnected(status);
			}
				break;
			}
		}
	};

	public SnapFace() {
		mDetectorName = new String[2];
		mDetectorName[JAVA_DETECTOR] = "Java";
		mDetectorName[NATIVE_DETECTOR] = "Native (tracking)";

		roi = null;

		Log.i(TAG, "Instantiated new " + this.getClass());
	}

	/** Called when the activity is first created. */
	@Override
	public void onCreate(Bundle savedInstanceState) {
		Log.i(TAG, "called onCreate");
		super.onCreate(savedInstanceState);

		getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

		setContentView(R.layout.activity_snap_face);

		mOpenCvCameraView = (TrainCameraView) findViewById(R.id.activity_train_camera_view);
		mOpenCvCameraView.setCvCameraViewListener(this);

		trigger = (ImageView) findViewById(R.id.snap_pic_button);
		trigger.setOnTouchListener(SnapFace.this);

		mFirstname = getIntent().getStringExtra("firstname");
		mLastname = getIntent().getStringExtra("lastname");
		mPersonId = getIntent().getStringExtra("personid");		
		mCurFace = null;
	}

	@Override
	public void onPause() {
		super.onPause();
		if (mOpenCvCameraView != null)
			mOpenCvCameraView.disableView();
	}

	@Override
	public void onResume() {
		super.onResume();
		OpenCVLoader.initAsync(OpenCVLoader.OPENCV_VERSION_2_4_3, this,
				mLoaderCallback);
	}

	public void onDestroy() {
		super.onDestroy();
		mOpenCvCameraView.disableView();
	}

	public void onCameraViewStarted(int width, int height) {
		mGray = new Mat();
		mRgba = new Mat();
	}

	public void onCameraViewStopped() {
		mGray.release();
		mRgba.release();
	}

	public Mat onCameraFrame(CvCameraViewFrame inputFrame) {
		
		mRgba = inputFrame.rgba();
		mGray = inputFrame.gray();

		if (mAbsoluteFaceSize == 0) {
			int height = mGray.rows();
			if (Math.round(height * mRelativeFaceSize) > 0) {
				mAbsoluteFaceSize = Math.round(height * mRelativeFaceSize);
			}
		}

		MatOfRect faces = new MatOfRect();

		if (mJavaDetector != null)
			mJavaDetector.detectMultiScale(mGray, faces, 1.1, 2, 2, new Size(
					mAbsoluteFaceSize, mAbsoluteFaceSize), new Size());

		Rect[] facesArray = faces.toArray();
		if (facesArray.length > 0) {
			// Store the current face
			if (Utility.roiSizeOk(mRgba, facesArray[0])) {
				mCurFace = new Mat();
				mRgba.submat(facesArray[0]).copyTo(mCurFace);
				Imgproc.cvtColor(mCurFace, mCurFace, Imgproc.COLOR_RGBA2BGRA);
			} else {
				mCurFace = null;
			}
			// Draws the rectangle tl = top left, br = bottom right
			Core.rectangle(mRgba, facesArray[0].tl(), facesArray[0].br(),
					FACE_RECT_COLOR, 3);

			if (roiSizeOk(mRgba, facesArray[0])) {
				roi = facesArray[0];
			}
		} else { // No face
			mCurFace = null;
		}

		double w = mRgba.width();
		double h = mRgba.height();
		int thickness = 2;
		int lineType = 1;
		// Left eye
		RotatedRect box = new RotatedRect(new Point(w / 3.0, h / 2.2),
				new Size(w / 15, h / 15), 0);
		Core.ellipse(mRgba, box, ELLIPSE_COLOUR, thickness, lineType);
		// Right eye
		box = new RotatedRect(new Point(w / 3.0 + w / 5.0, h / 2.2), new Size(
				w / 15, h / 15), 0);
		Core.ellipse(mRgba, box, ELLIPSE_COLOUR, thickness, lineType);
		// Face outline
		box = new RotatedRect(new Point(w / 3.0 + w / 10.0, h / 2.0), new Size(
				w / 2.2, h / 1.2), 0);
		Core.ellipse(mRgba, box, ELLIPSE_COLOUR, thickness, lineType);
		return mRgba;
	}

	private void setMinFaceSize(float faceSize) {
		mRelativeFaceSize = faceSize;
		mAbsoluteFaceSize = 0;
	}

	private Mat crop(Rect roi, Mat toCrop) {
		Mat cropped = new Mat(toCrop, roi);
		return cropped;
	}

	// Checks if roi is smaller than mat
	public static boolean roiSizeOk(Mat mat, Rect roi) {
		if (roi.x >= 0 && roi.y >= 0 && roi.x + roi.width < mat.cols()
				&& roi.y + roi.height < mat.rows()) {
			return true;
		} else {
			return false;
		}
	}

	public void picSnapped(View view) {
		// If there is no face detected
		if (mCurFace == null) {
			Toast toast = Toast.makeText(this, "No face detected!", Toast.LENGTH_SHORT);
			toast.show();
			return;
		}
		
		Log.i(TAG, "Picture taken event");
		SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss",
				Locale.US);
		String currentDateandTime = sdf.format(new Date());
		String fileName = Environment
				.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
				+ "/" + mPersonId + "_" + currentDateandTime + ".jpg";
		// Resize image to 100x100
		Mat resizedImg = new Mat();
		Size size = new Size(100, 100);
		Imgproc.resize(mCurFace, resizedImg, size);
		// Save img to file
		Boolean bool = null;
		bool = Highgui.imwrite(fileName, resizedImg);
		if (bool == true) {
			Log.i(TAG, "SUCCESS writing image to external storage");
		} else {
			Log.i(TAG, "Failure writing image to external storage");
		}
		// Display popup
		Toast toast = Toast.makeText(this, "Image of " + mFirstname + " "
				+ mLastname + " captured", Toast.LENGTH_SHORT);
		toast.setGravity(Gravity.BOTTOM | Gravity.RIGHT, 0, 0);
		toast.show();
		// Show the new file in the filesystem
		sendBroadcast(new Intent(
				Intent.ACTION_MEDIA_MOUNTED,
				Uri.parse("file://" + Environment.getExternalStorageDirectory())));
	}

	public boolean onTouch(View view, MotionEvent event) {
		if (event.getAction() == MotionEvent.ACTION_DOWN) {
			Utility.setAlpha(view, 0.5f);
		} else if (event.getAction() == MotionEvent.ACTION_UP) {
			// view.performClick();
			Utility.setAlpha(view, 1f);
		}
		return false;
	}

}
