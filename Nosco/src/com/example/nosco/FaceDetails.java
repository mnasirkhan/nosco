package com.example.nosco;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.EditText;

public class FaceDetails extends Activity{
	private PeopleDataSource datasource;
	
	private static final String TAG = "FaceDetails::";

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_face_details);
		
		// Interface with the database
		datasource = new PeopleDataSource(this);
		datasource.open();
	}
	
	public void writeDetails(View view) {
		EditText firstname = (EditText) findViewById(R.id.firstname);
		EditText lastname = (EditText) findViewById(R.id.lastname);
		
		String fn_str = firstname.getText().toString();
		String ln_str = lastname.getText().toString();
		
		// Check if the user left out either of the fields
		if (fn_str.length() == 0 || ln_str.length() == 0) {
			//TODO: Handle this case!
		}
		// Ad the new person to the database
		Person p = datasource.createPerson(fn_str, ln_str);
		Intent intent = new Intent(FaceDetails.this, SnapFace.class);
		intent.putExtra("firstname", p.getFirstname());
		intent.putExtra("lastname", p.getLastname());
		intent.putExtra("personid", Long.toString(p.getId()));
		startActivity(intent);
	}
}
