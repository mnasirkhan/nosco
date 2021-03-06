package com.example.nosco;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

import org.opencv.android.BaseLoaderCallback;
import org.opencv.android.CameraBridgeViewBase.CvCameraViewFrame;
import org.opencv.android.LoaderCallbackInterface;
import org.opencv.android.OpenCVLoader;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfRect;
import org.opencv.core.Rect;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.highgui.Highgui;
import org.opencv.imgproc.Imgproc;
import org.opencv.core.Core;
import org.opencv.android.CameraBridgeViewBase;
import org.opencv.android.CameraBridgeViewBase.CvCameraViewListener2;
import org.opencv.objdetect.CascadeClassifier;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.speech.tts.TextToSpeech;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.WindowManager;

import java.util.List;
import java.util.Locale;

public class FdActivity extends Activity implements CvCameraViewListener2,
		TextToSpeech.OnInitListener {

	private TextToSpeech myTTS;

	private final String imgPath = Environment.DIRECTORY_PICTURES;

	private static final String TAG = "FaceDetection::Activity";
	private static final Scalar FACE_RECT_COLOR = new Scalar(0, 255, 0, 255);
	public static final int JAVA_DETECTOR = 0;
	public static final int NATIVE_DETECTOR = 1;

	private MenuItem mItemFace50;
	private MenuItem mItemFace40;
	private MenuItem mItemFace30;
	private MenuItem mItemFace20;
	private MenuItem mItemType;

	// Counts how many times we have recognised the
	// same person in a row.
	private int seenCnt;

	// Who should we speak the name of
	private String speakName;

	private Mat mRgba;
	private Mat mGray;
	private File mCascadeFile;
	private CascadeClassifier mJavaDetector;
	private DetectionBasedTracker mNativeDetector;

	private int mDetectorType = NATIVE_DETECTOR;
	private String[] mDetectorName;

	private float mRelativeFaceSize = 0.2f;
	private int mAbsoluteFaceSize = 0;

	private int picSuffix = 0;

	private FaceRec faceRecognizer;

	// The database
	private PeopleDataSource datasource;
	private List<Person> allPeople;

	private int MY_DATA_CHECK_CODE = 0;

	private CameraBridgeViewBase mOpenCvCameraView;

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

	public FdActivity() {
		mDetectorName = new String[2];
		mDetectorName[JAVA_DETECTOR] = "Java";
		mDetectorName[NATIVE_DETECTOR] = "Native (tracking)";

		Log.i(TAG, "Instantiated new " + this.getClass());
	}

	/** Called when the activity is first created. */
	@Override
	public void onCreate(Bundle savedInstanceState) {
		Log.i(TAG, "called onCreate");
		super.onCreate(savedInstanceState);
		
		myTTS = new TextToSpeech(this, this);

		faceRecognizer = new FaceRec();

		faceRecognizer.train();

		// Interface with the database
		datasource = new PeopleDataSource(this);
		datasource.open();
		allPeople = datasource.getAllPeople();

		// Reset seen count
		seenCnt = 0;

		speakName = "";

		// TTS setup
		Intent checkTTSIntent = new Intent();
		checkTTSIntent.setAction(TextToSpeech.Engine.ACTION_CHECK_TTS_DATA);
		startActivityForResult(checkTTSIntent, MY_DATA_CHECK_CODE);

		getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

		setContentView(R.layout.activity_fd);

		mOpenCvCameraView = (CameraBridgeViewBase) findViewById(R.id.fd_activity_surface_view);
		mOpenCvCameraView.setCvCameraViewListener(this);
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
			mNativeDetector.setMinFaceSize(mAbsoluteFaceSize);
		}

		MatOfRect faces = new MatOfRect();

		if (mDetectorType == JAVA_DETECTOR) {
			if (mJavaDetector != null)
				mJavaDetector.detectMultiScale(mGray, faces, 1.1, 2,
						2, // TODO: objdetect.CV_HAAR_SCALE_IMAGE
						new Size(mAbsoluteFaceSize, mAbsoluteFaceSize),
						new Size());
		} else if (mDetectorType == NATIVE_DETECTOR) {
			if (mNativeDetector != null)
				mNativeDetector.detect(mGray, faces);
		} else {
			Log.e(TAG, "Detection method is not selected!");
		}

		Rect[] facesArray = faces.toArray();
		for (int i = 0; i < facesArray.length; i++) {
			// Check roi isn't bigger than frame & save img
			if (Utility.roiSizeOk(mRgba, facesArray[i]))
				// saveMatToImg(mRgba.submat(facesArray[i]));
				// Draws the rectangle tl = top left, br = bottom right
				Core.rectangle(mRgba, facesArray[i].tl(), facesArray[i].br(),
						FACE_RECT_COLOR, 3);
			// Core.putText(mRgba, "Recognised Henry Warhurst", new Point(30,
			// 30),
			// 3, 1, new Scalar(200, 200, 250), 1);
		}
		// TODO: Change this so it checks if it knows everyone in an image
		if (facesArray.length != 0 && Utility.roiSizeOk(mRgba, facesArray[0])) {
			int curId = faceRecognizer.predict(mGray.submat(facesArray[0]));
			String firstName = null;
			String lastName = null;
			for (Person p : allPeople) {
				if ((int) p.getId() == curId) {
					firstName = p.getFirstname();
					lastName = p.getLastname();
				}
			}
			Log.e(TAG, "Firstname" + firstName);
			if (firstName != null) {
				Log.e(TAG, "Person is " + firstName + " " + lastName);
				Log.e(TAG, "ID of above is " + Integer.toString(curId));

				if (!speakName.equals(firstName)) {
					speakName = firstName;
					seenCnt = 0;
					myTTS.stop();
				} else if (seenCnt < 5) {
					seenCnt++;
				} else {
					speakText("Recognised " + speakName);
					seenCnt = 0;
				}
			} else {
				Log.e(TAG, "Unknown person");
				speakName = "";
				seenCnt = 0;
			}
		}
		return mRgba;
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		Log.i(TAG, "called onCreateOptionsMenu");
		mItemFace50 = menu.add("Face size 50%");
		mItemFace40 = menu.add("Face size 40%");
		mItemFace30 = menu.add("Face size 30%");
		mItemFace20 = menu.add("Face size 20%");
		mItemType = menu.add(mDetectorName[mDetectorType]);
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		Log.i(TAG, "called onOptionsItemSelected; selected item: " + item);
		if (item == mItemFace50)
			setMinFaceSize(0.5f);
		else if (item == mItemFace40)
			setMinFaceSize(0.4f);
		else if (item == mItemFace30)
			setMinFaceSize(0.3f);
		else if (item == mItemFace20)
			setMinFaceSize(0.2f);
		else if (item == mItemType) {
			int tmpDetectorType = (mDetectorType + 1) % mDetectorName.length;
			item.setTitle(mDetectorName[tmpDetectorType]);
			setDetectorType(tmpDetectorType);
		}
		return true;
	}

	private void setMinFaceSize(float faceSize) {
		mRelativeFaceSize = faceSize;
		mAbsoluteFaceSize = 0;
	}

	private void setDetectorType(int type) {
		if (mDetectorType != type) {
			mDetectorType = type;

			if (type == NATIVE_DETECTOR) {
				Log.i(TAG, "Detection Based Tracker enabled");
				mNativeDetector.start();
			} else {
				Log.i(TAG, "Cascade detector enabled");
				mNativeDetector.stop();
			}
		}
	}

	private Mat crop(Rect roi, Mat toCrop) {
		Mat cropped = new Mat(toCrop, roi);
		return cropped;
	}

	private void saveMatToImg(Mat mat) {
		// Resize image to 100x100
		Mat resizedImg = new Mat();
		Size size = new Size(100, 100);
		Imgproc.resize(mat, resizedImg, size);
		File path = Environment.getExternalStoragePublicDirectory(imgPath);
		String filename = "pic" + picSuffix + ".jpg";
		picSuffix++;
		File file = new File(path, filename);

		Boolean bool = null;
		filename = file.toString();
		bool = Highgui.imwrite(filename, resizedImg);

		if (bool == true)
			Log.i(TAG, "SUCCESS writing image to external storage");
		else
			Log.i(TAG, "Failure writing image to external storage");

		Intent mediaScanIntent = new Intent(
				Intent.ACTION_MEDIA_SCANNER_SCAN_FILE);
		Uri contentUri = Uri.fromFile(file);
		mediaScanIntent.setData(contentUri);
		this.sendBroadcast(mediaScanIntent);
	}

	private void speakText(String text) {
		myTTS.speak(text, TextToSpeech.QUEUE_ADD, null);
	}

	public void trainFaces(View view) {
		Intent intent = new Intent(this, FacesLibrary.class);
		startActivity(intent);
	}

	// setup TTS
	public void onInit(int initStatus) {
		// check for successful instantiation
		if (initStatus == TextToSpeech.SUCCESS) {
			if (myTTS.isLanguageAvailable(Locale.ENGLISH) == TextToSpeech.LANG_AVAILABLE)
				myTTS.setLanguage(Locale.ENGLISH);
		} else if (initStatus == TextToSpeech.ERROR) {
			Log.e(TAG, "TTS onInit Error!");
		}
	}
}
