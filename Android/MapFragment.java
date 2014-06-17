package ru.just_next.justandroid;

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.GoogleMap.OnMyLocationChangeListener;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.LatLng;
import android.location.Location;
import android.os.Bundle;
import android.os.Handler;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CompoundButton;
import android.widget.ToggleButton;
import android.widget.CompoundButton.OnCheckedChangeListener;

public class MapFragment extends Fragment implements OnMyLocationChangeListener{
	private View view;
	private GoogleMap map;
	private boolean goMe = false;
	private boolean firstMyCoordinate = true;
	private ToggleButton trafficButton;
	private ToggleButton goButton;
	private boolean removeMapFragment = true;
	HttpGetFriends friends = null;
	public void onPause() {
		super.onPause();  // Always call the superclass method first
		friends.close();
	}
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
	}

	@Override
	public void onSaveInstanceState(Bundle outState) {

		Fragment f = getFragmentManager().findFragmentById(R.id.map);
		removeMapFragment = false;
		super.onSaveInstanceState(outState);
		if ((f != null))
			f.onSaveInstanceState(outState);

	}
	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
		super.onCreateView(inflater, container, savedInstanceState);

		view = inflater.inflate(R.layout.fragment_map,  container, false);
		map = ((SupportMapFragment) getFragmentManager().findFragmentById(R.id.map)).getMap();

		goButton = (ToggleButton)view.findViewById(R.id.goButton);
		trafficButton = (ToggleButton)view.findViewById(R.id.trafficButton);
		goButton.setOnCheckedChangeListener(new OnCheckedChangeListener() {

			@Override
			public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
				if(isChecked)
				{
					goMe = true;
				}
				else
				{
					goMe = false;
				}

			}
		});
		trafficButton.setOnCheckedChangeListener(new OnCheckedChangeListener() {

			@Override
			public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
				if(isChecked)
				{
					map.setTrafficEnabled(true);
				}
				else
				{
					map.setTrafficEnabled(false);
				}

			}
		});
		return view;
	}

	@Override
	public void onResume() {
		super.onResume();  // Always call the superclass method first

		if(map != null) {
			Handler handler = new Handler();
			friends = new HttpGetFriends(getActivity().getBaseContext(),handler, map);
			Thread th = new Thread(friends);
			th.start();
			map.getUiSettings().setZoomControlsEnabled(true);
			map.getUiSettings().setCompassEnabled(true);
			map.getUiSettings().setMyLocationButtonEnabled(true);
			map.setMyLocationEnabled(true);
			map.setOnMyLocationChangeListener(this);
		}
	}

	@Override
	public void onDestroy() {
		super.onDestroy();
		map = null;

	}

	@Override
	public void onDestroyView() {
		super.onDestroyView();
				Fragment f = getFragmentManager().findFragmentById(R.id.map);
				if ((f != null) && (removeMapFragment)) 
					getFragmentManager().beginTransaction().remove(f).commit();
	}
	@Override
	public void onMyLocationChange(Location location) {
		if (goMe || firstMyCoordinate){
			LatLng locLatLng = new LatLng(location.getLatitude(), location.getLongitude());
			map.animateCamera(CameraUpdateFactory.newLatLng(locLatLng));
			firstMyCoordinate = false;
		}
	}
}