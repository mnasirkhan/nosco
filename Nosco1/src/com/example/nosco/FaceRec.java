package com.example.nosco;

import java.io.File;
import java.io.FilenameFilter;
import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.List;


// JavaCV
import static org.bytedeco.javacpp.opencv_core.*;
import static org.bytedeco.javacpp.opencv_highgui.*;
import static org.bytedeco.javacpp.opencv_contrib.*;
import static org.bytedeco.javacpp.opencv_imgproc.*;

import org.bytedeco.javacpp.opencv_imgproc;
// OpenCv4Android
import org.opencv.android.Utils;

import android.graphics.Bitmap;
import android.os.Environment;
import android.util.Log;

/**
 * Wraps the JavaCV FaceRecognizer as an OpenCV class
 * @author hwar
 *
 */
public class FaceRec {
	private FaceRecognizer faceRecognizer;
	
	private static final String TAG = "FaceRec";
	
	private static final String imgPath = Environment.DIRECTORY_PICTURES;
	
	private static final int WIDTH = 				100;
	private static final int HEIGHT =				100;
	
	private List<Integer> seenIds;
	
	private boolean emptyTrainingSet;

	public FaceRec() {
		faceRecognizer = createLBPHFaceRecognizer(2, 8, 8, 8, 200);
		emptyTrainingSet = false;
	}

	public void train() {
		File path = Environment.getExternalStoragePublicDirectory(imgPath);

		FilenameFilter imgFilter = new FilenameFilter() {
			public boolean accept(File dir, String name) {
				name = name.toLowerCase();
				return name.endsWith(".jpg") || name.endsWith(".pgm")
						|| name.endsWith(".png");
			}
		};

		File[] imageFiles = path.listFiles(imgFilter);
		
		if (imageFiles.length == 0) {
			emptyTrainingSet = true;
			return;
		}

		MatVector images = new MatVector(imageFiles.length);
		Mat labels = new Mat((int) images.size(), 1, CV_32SC1);
		IntBuffer labelsBuff = labels.getIntBuffer();
		seenIds = new ArrayList<Integer>();

		for (int i = 0; i < imageFiles.length; ++i) {
			Mat img = imread(imageFiles[i].getAbsolutePath(),
					CV_LOAD_IMAGE_GRAYSCALE);
			int cur_id = Integer
					.parseInt(imageFiles[i].getName().split("\\-")[0]);
			int label = -1;
			// If we have seen this label before
			if (inArray(cur_id, seenIds)) {
				label = seenIds.indexOf(cur_id);
			// Make a new label
			} else {
				seenIds.add(cur_id);
				label = seenIds.size() - 1;
			}
			images.put(i, img);

			labelsBuff.put(i, label);
		}
		faceRecognizer.train(images, labels);
	}
	
	// Returns the ID of the discovered person
	public int predict(org.opencv.core.Mat face) {
		if (emptyTrainingSet) {
			return -2;
		}
		// A hack to allow for modifiable method arguments
		int n[] = new int[1];
		double p[] = new double[1];
		
		Mat convertedFace = MatToJavaCvMat(face, WIDTH, HEIGHT);
		
		faceRecognizer.predict(convertedFace, n, p);
		Log.e(TAG, "Confidence = " + Double.toString(p[0]));
		// Confidence is opposite to what you would intuit
		if (n[0] == -1 || p[0] > 70) {
			return -1;
		} else {
			return seenIds.get(n[0]);
		}
	}
	
	// ***************** Utility Functions **********************
	// Credit for the following 2 functions Github: ayuso2013
	// **********************************************************
	private Mat MatToJavaCvMat(org.opencv.core.Mat m, int width, int heigth) {

		Bitmap bmp = Bitmap.createBitmap(m.width(), m.height(),
				Bitmap.Config.ARGB_8888);

		Utils.matToBitmap(m, bmp);
		return BitmapToJavaCvMat(bmp, width, heigth);

	}
	
	Mat BitmapToJavaCvMat(Bitmap bmp, int width, int height) {

		if ((width != -1) || (height != -1)) {
			Bitmap bmp2 = Bitmap.createScaledBitmap(bmp, width, height, false);
			bmp = bmp2;
		}

		IplImage image = IplImage.create(bmp.getWidth(), bmp.getHeight(),
				IPL_DEPTH_8U, 4);

		bmp.copyPixelsToBuffer(image.getByteBuffer());
		
		IplImage grayImg = IplImage.create(image.width(), image.height(),
				IPL_DEPTH_8U, 1);

		cvCvtColor(image, grayImg, opencv_imgproc.CV_BGR2GRAY);

		Mat imgMat = new Mat(grayImg, true);
		return imgMat;
	}
	
	// Determines whether the value x is in arr
	public static boolean inArray(int x, List<Integer> arr) {
		for (Integer cur_int : arr) {
			if (cur_int == x)
				return true;
		}
		return false;
	}
}
