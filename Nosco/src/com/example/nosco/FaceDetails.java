package com.example.nosco;

import android.app.Activity;
import android.os.Bundle;
import android.view.View;

public class FaceDetails extends Activity{
	private PeopleDataSource datasource;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_face_details);
		
		// Interface with the database
		datasource = new PeopleDataSource(this);
		datasource.open();
	}
	
	private void writeDetails(View view) {
		
	}
}
