package com.example.nosco;

import java.util.List;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.TextView;

public class LibraryArrayAdapter extends ArrayAdapter<Person> {

	private LayoutInflater inflater;
	private List<Person> data;
	private Context context;

	public LibraryArrayAdapter(Context context, List<Person> objects) {
		super(context, R.layout.faces_library_li, objects);
		this.context = context;

		inflater = LayoutInflater.from(context);
		this.data = objects;
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

//		int img = context.getResources().getIdentifier(
//				"com.example.nosco:drawable/" + data.get(position).getId(), null, null);
//		// set data to holder
//		holder.image.setImageResource(img);
		holder.name.setText((CharSequence) data.get(position).toString());
		// return ListView item
		return convertView;
	}

	// ViewHolder class that hold over ListView Item
	static class ViewHolder {
		ImageView image;
		TextView name;
	}
}
