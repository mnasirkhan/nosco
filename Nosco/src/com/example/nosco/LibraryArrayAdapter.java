package com.example.nosco;

import java.util.List;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Build;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AlphaAnimation;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

public class LibraryArrayAdapter extends ArrayAdapter<Person> {

	private LayoutInflater inflater;
	private List<Person> data;
	private Context context;
	private PeopleDataSource datasource;
	private View tempView;

	public LibraryArrayAdapter(Context context, List<Person> objects) {
		super(context, R.layout.faces_library_li, objects);
		this.context = context;

		inflater = LayoutInflater.from(context);
		this.data = objects;
		datasource = new PeopleDataSource(this.context);
		datasource.open();
	}

	@Override
	public View getView(int position, View convertView, ViewGroup parent) {
		ViewHolder holder;
		// if it's not create convertView yet create new one and consume it
		if (convertView == null) {
			// instantiate convertView using our employee_list_item
			convertView = inflater.inflate(R.layout.faces_library_li, null);
			// get new ViewHolder
			holder = new ViewHolder();
			// get all item in ListView item to corresponding fields in our
			// ViewHolder class
			holder.image = (ImageView) convertView
					.findViewById(R.id.imageViewFace);
			holder.name = (TextView) convertView
					.findViewById(R.id.textViewName);
			// set tag of convertView to the holder
			convertView.setTag(holder);
		}
		// if it's exist convertView then consume it
		else {
			holder = (ViewHolder) convertView.getTag();
		}

		// int img = context.getResources().getIdentifier(
		// "com.example.nosco:drawable/" + data.get(position).getId(), null,
		// null);
		// // set data to holder
		// holder.image.setImageResource(img);
		holder.name.setText((CharSequence) data.get(position).toString());

		ImageView deleteButton = (ImageView) convertView
				.findViewById(R.id.remove);
		deleteButton.setTag(position);
		
		deleteButton.setOnTouchListener(new Button.OnTouchListener() {
			@SuppressLint("ClickableViewAccessibility")
			@Override
			public boolean onTouch(View view, MotionEvent event) {
				if (event.getAction() == MotionEvent.ACTION_DOWN) {
					Utility.setAlpha(view, 0.5f);
				} else if (event.getAction() == MotionEvent.ACTION_UP) {
					//view.performClick();
					Utility.setAlpha(view, 1f);
				}
				return false;
			}
		});

		deleteButton.setOnClickListener(new Button.OnClickListener() {
			@Override
			public void onClick(View v) {
				// to avoid scope issues with non-fianl variables
				tempView = v;
				
				new AlertDialog.Builder(v.getContext())
			    .setTitle("Delete entry")
			    .setMessage("Are you sure you want to delete this entry?")
			    .setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener() {
			        public void onClick(DialogInterface dialog, int which) { 
						Integer index = (Integer) tempView.getTag();
						Person toDelete = data.get(index.intValue());
						datasource.deletePerson(toDelete);
						data.remove(index.intValue());
						notifyDataSetChanged();
			        }
			     })
			    .setNegativeButton(android.R.string.no, new DialogInterface.OnClickListener() {
			        public void onClick(DialogInterface dialog, int which) { 
			        	// Do nothing, don't delete the entry
			        }
			     })
			    .setIcon(android.R.drawable.ic_dialog_alert)
			     .show();
			}
		});
		// return ListView item
		return convertView;
	}
	
	@Override
	public boolean isEnabled (int position) {
		return false;
	}

	// ViewHolder class that hold over ListView Item
	static class ViewHolder {
		ImageView image;
		TextView name;
	}
}
