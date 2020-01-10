package com.enachescurobert.googlemaps2019.ui;

import android.Manifest;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.Fragment;
import android.support.v4.content.ContextCompat;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;


import com.enachescurobert.googlemaps2019.R;
import com.enachescurobert.googlemaps2019.models.ClusterMarker;
import com.enachescurobert.googlemaps2019.models.PolylineData;
import com.enachescurobert.googlemaps2019.models.User;
import com.enachescurobert.googlemaps2019.models.UserLocation;
import com.enachescurobert.googlemaps2019.util.MyClusterManagerRenderer;
import com.enachescurobert.googlemaps2019.util.ThingSpeakApi;
import com.google.android.gms.maps.CameraUpdate;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.MapView;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;
import com.google.android.gms.maps.model.MapStyleOptions;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.PolygonOptions;
import com.google.android.gms.maps.model.Polyline;
import com.google.android.gms.maps.model.PolylineOptions;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.EventListener;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreException;
import com.google.firebase.firestore.FirebaseFirestoreSettings;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.firestore.QuerySnapshot;
import com.google.firebase.firestore.SetOptions;
import com.google.maps.DirectionsApiRequest;
import com.google.maps.GeoApiContext;
import com.google.maps.PendingResult;
import com.google.maps.android.PolyUtil;
import com.google.maps.android.clustering.ClusterManager;
import com.google.maps.internal.PolylineEncoding;
import com.google.maps.model.DirectionsResult;
import com.google.maps.model.DirectionsRoute;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Timer;
import java.util.TimerTask;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

import static android.content.Context.MODE_PRIVATE;
import static com.enachescurobert.googlemaps2019.Constants.MAPVIEW_BUNDLE_KEY;

public class MapFragment extends Fragment implements
        OnMapReadyCallback,
        View.OnClickListener,
        GoogleMap.OnInfoWindowClickListener,
        GoogleMap.OnPolylineClickListener {

    //constants
    private static final String TAG = "MapFragment";
    private static final int LOCATION_UPDATE_INTERVAL = 3000; //3s
    private static final String SCOOTER_PREFS = "scooterPrefs";
    private static final String ACTIVE_SCOOTER_USERNAME = "usernameOfStartedScooter";
    private static final String NO_ACTIVE_SCOOTER = "no active scooter";
    public LatLng testPoint = new LatLng(44.477, 26.161);
    Dialog myDialog;
    List<LatLng> polygonList = new ArrayList<LatLng>();
    //widgets
    private RelativeLayout mMapContainer;
    private RelativeLayout mTimeAndTotal;
    private Button mStopTime;
    //UI component
    private MapView mMapView;
    private ListenerRegistration mUserListEventListener;
    //vars
    private ArrayList<User> mUserList = new ArrayList<>();
    private ArrayList<UserLocation> mUserLocations = new ArrayList<>();
    private GoogleMap mGoogleMap;
    private LatLngBounds mMapBoundary;
    private UserLocation mUserPosition;
    private ClusterManager mClusterManager;
    private MyClusterManagerRenderer mClusterManagerRenderer;
    private ArrayList<ClusterMarker> mClusterMarkers = new ArrayList<>();
    private boolean isActive = false;
    //Handler + Runnable -> Responsible for making requests every 3 seconds
    private Handler mHandler = new Handler();
    private Runnable mRunnable;
    private ArrayList<PolylineData> mPolyLinesData = new ArrayList<>();
    private Marker mSelectedMarker = null;
    private ArrayList<Marker> mTripMarkers = new ArrayList<>();
    private int minutes = 0;
    private int seconds = 0;
    private Timer myTimer;
    private ThingSpeakApi thingSpeakApi;
    //Paying info
    private TextView minutesPassed;
    private TextView totalPrice;

    //for Google Directions API
    private GeoApiContext mGeoApiContext = null;

    private String[] array;

    private FirebaseFirestore mDb;


    public static MapFragment newInstance() {
        return new MapFragment();
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mDb = FirebaseFirestore.getInstance();
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_map, container, false);
        mMapView = (MapView) view.findViewById(R.id.user_list_map);

        minutesPassed = (TextView) view.findViewById(R.id.time_passed_value);
        totalPrice = (TextView) view.findViewById(R.id.total_amount_to_pay);

        mStopTime = (Button) view.findViewById(R.id.stop_time);


        array = getResources().getStringArray(R.array.array_codes_parking);

        view.findViewById(R.id.btn_reset_map).setOnClickListener(this);



        myDialog = new Dialog(getActivity());
        myDialog.setCanceledOnTouchOutside(false);

        initGoogleMap(savedInstanceState);

        try {
            for (UserLocation userLocation : mUserLocations) {
                Log.d(TAG,"onCreateView: user location: " +
                        userLocation.getUser().getUsername());
                Log.d(TAG,"onCreateView: geopoint: " +
                        userLocation.getGeoPoint().getLatitude() + ", " +
                        userLocation.getGeoPoint().getLongitude());
            }
        } catch (Exception e) {
            Log.d(TAG, "onCreateView: ERROR -> " + e.getLocalizedMessage());
        }

        setUserPosition();

        mStopTime.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (PolyUtil.containsLocation(testPoint, polygonList, false)) {

                    isActive = false;
                    stopTimer();

                    SharedPreferences prefs = getActivity().getSharedPreferences(SCOOTER_PREFS, MODE_PRIVATE);
                    String tappedMarkerUsername = prefs.getString(ACTIVE_SCOOTER_USERNAME, NO_ACTIVE_SCOOTER);//"no active scooter is the default value.

                    updateTimerOnServer(false, tappedMarkerUsername);

                    SharedPreferences.Editor editor = prefs.edit();
                    editor.putString(ACTIVE_SCOOTER_USERNAME, NO_ACTIVE_SCOOTER);
                    editor.apply();

                    mTimeAndTotal = (RelativeLayout) getActivity().findViewById(R.id.time_and_total);
                    mTimeAndTotal.setVisibility(View.GONE);
                    showPopup();
                } else {
                    Toast.makeText(getActivity(), "You are not in the green area", Toast.LENGTH_SHORT).show();
                }
            }
        });

        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl("https://api.thingspeak.com/")
                .addConverterFactory(GsonConverterFactory.create())
                .build();

        thingSpeakApi = retrofit.create(ThingSpeakApi.class);

        return view;
    }

    public void zoomRoute(List<LatLng> lstLatLngRoute) {

        if (mGoogleMap == null || lstLatLngRoute == null || lstLatLngRoute.isEmpty()) return;

        LatLngBounds.Builder boundsBuilder = new LatLngBounds.Builder();
        for (LatLng latLngPoint : lstLatLngRoute)
            boundsBuilder.include(latLngPoint);

        int routePadding = 120;
        LatLngBounds latLngBounds = boundsBuilder.build();

        mGoogleMap.animateCamera(
                CameraUpdateFactory.newLatLngBounds(latLngBounds, routePadding),
                600,
                null
        );
    }

    private void removeTripMarkers() {
        for (Marker marker: mTripMarkers) {
            marker.remove();
        }
    }

    private void resetSelectedMarker() {
        if(mSelectedMarker != null) {
            mSelectedMarker.setVisible(true);
            mSelectedMarker = null;
            removeTripMarkers();
        }
    }

    private void calculateDirections(Marker marker) {
        Log.d(TAG, "calculateDirections: calculating directions.");

//        TODO -> if (registered)
//        setUserPosition();

        com.google.maps.model.LatLng destination = new com.google.maps.model.LatLng(
                marker.getPosition().latitude,
                marker.getPosition().longitude
        );

        DirectionsApiRequest directions = new DirectionsApiRequest(mGeoApiContext);

        //if you want to be able to show all the possible routes
        directions.alternatives(true);
        directions.origin(
                new com.google.maps.model.LatLng(
                        mUserPosition.getGeoPoint().getLatitude(),
                        mUserPosition.getGeoPoint().getLongitude()
                )
        );
        Log.d(TAG, "calculateDirections: destination: " + destination.toString());
        directions.destination(destination).setCallback(new PendingResult.Callback<DirectionsResult>() {
            @Override
            public void onResult(DirectionsResult result) {
                Log.d(TAG, "calculateDirections: routes: " + result.routes[0].toString());
                Log.d(TAG, "calculateDirections: duration: " + result.routes[0].legs[0].duration);
                Log.d(TAG, "calculateDirections: distance: " + result.routes[0].legs[0].distance);
                Log.d(TAG, "calculateDirections: geocodedWayPoints: " + result.geocodedWaypoints[0].toString());

                addPolylinesToMap(result);
            }

            @Override
            public void onFailure(Throwable e) {
                Log.e(TAG, "calculateDirections: Failed to get directions: " + e.getMessage() );

            }
        });
    }

    //This will be posted on the Main Thread
    private void addPolylinesToMap(final DirectionsResult result) {
        new Handler(Looper.getMainLooper()).post(new Runnable() {
            @Override
            public void run() {
                Log.d(TAG, "run: result routes: " + result.routes.length);

                if (mPolyLinesData.size()>0) {
                    for (PolylineData polylineData: mPolyLinesData) {
                        polylineData.getPolyline().remove();
                    }
                    mPolyLinesData.clear();
                    mPolyLinesData = new ArrayList<>();
                }

                double duration = 999999;

                //we get a google DirectionsRoute object
                for (DirectionsRoute route: result.routes) {
                    Log.d(TAG, "run: leg: " + route.legs[0].toString());

                    //we get a decodedPath and that's going to have all the little points for each of the Polylines
                    List<com.google.maps.model.LatLng> decodedPath = PolylineEncoding.decode(route.overviewPolyline.getEncodedPath());

                    List<LatLng> newDecodedPath = new ArrayList<>();

                    // This loops through all the LatLng coordinates of ONE polyline.
                    for (com.google.maps.model.LatLng latLng: decodedPath) {

                        newDecodedPath.add(new LatLng(
                                latLng.lat,
                                latLng.lng
                        ));
                    }

                    Polyline polyline = mGoogleMap.addPolyline(new PolylineOptions().addAll(newDecodedPath));
                    polyline.setColor(ContextCompat.getColor(getActivity(), R.color.darkGrey));
                    polyline.setClickable(true);

                    mPolyLinesData.add(new PolylineData(polyline, route.legs[0]));


                    double tempDuration = route.legs[0].duration.inSeconds;
                    if(tempDuration < duration) {
                        duration = tempDuration;
                        onPolylineClick(polyline);
                        zoomRoute(polyline.getPoints());
                    }

                    mSelectedMarker.setVisible(false);

                }
            }
        });
    }


    //responsible for actually start the Runnable
    private void startUserLocationsRunnable() {
        Log.d(TAG, "startUserLocationsRunnable: starting runnable for retrieving updated locations.");
        mHandler.postDelayed(mRunnable = new Runnable() {
            @Override
            public void run() {
                retrieveUserLocations();
                //to recursively call the method << retrieveUserLocations() >> every 3 seconds
                mHandler.postDelayed(mRunnable, LOCATION_UPDATE_INTERVAL);
            }
        }, LOCATION_UPDATE_INTERVAL);
    }

    private void stopLocationUpdates() {
        mHandler.removeCallbacks(mRunnable);
    }

    private void retrieveUserLocations() {
        Log.d(TAG, "retrieveUserLocations: retrieving location of all users in the chatroom.");

        try{
            for (final ClusterMarker clusterMarker: mClusterMarkers) {

                DocumentReference userLocationRef = FirebaseFirestore.getInstance()
                        .collection(getString(R.string.collection_user_locations))
                        .document(clusterMarker.getUser().getUser_id());

                userLocationRef.get().addOnCompleteListener(new OnCompleteListener<DocumentSnapshot>() {
                    @Override
                    public void onComplete(@NonNull Task<DocumentSnapshot> task) {
                        if(task.isSuccessful()) {

                            final UserLocation updatedUserLocation = task.getResult().toObject(UserLocation.class);

                            // update the location
                            for (int i = 0; i < mClusterMarkers.size(); i++) {
                                try {
                                    if (mClusterMarkers.get(i).getUser().getUser_id().equals(updatedUserLocation.getUser().getUser_id())) {

                                        LatLng updatedLatLng = new LatLng(
                                                updatedUserLocation.getGeoPoint().getLatitude(),
                                                updatedUserLocation.getGeoPoint().getLongitude()
                                        );

                                        mClusterMarkers.get(i).setPosition(updatedLatLng);
                                        mClusterManagerRenderer.setUpdateMarker(mClusterMarkers.get(i));

                                    }


                                } catch (NullPointerException e) {
                                    Log.e(TAG, "retrieveUserLocations: NullPointerException: " + e.getMessage());
                                }
                            }
                        }
                    }
                });
            }
        }catch (IllegalStateException e) {
            Log.e(TAG, "retrieveUserLocations: Fragment was destroyed during Firestore query. Ending query." + e.getMessage() );
        }

    }

    private void resetMap() {
        if(mGoogleMap != null) {
            mGoogleMap.clear();

            if(mClusterManager != null) {
                mClusterManager.clearItems();
            }

            if (mClusterMarkers.size() > 0) {
                mClusterMarkers.clear();
                mClusterMarkers = new ArrayList<>();
            }

            if(mPolyLinesData.size() > 0) {
                mPolyLinesData.clear();
                mPolyLinesData = new ArrayList<>();
            }

            addMapPolygon();

//            mGoogleMap.addCircle(new CircleOptions()
//            .center(new LatLng(44.5068333, 26.2080217))
//            .radius(900)
//            .strokeWidth(1)
//            .fillColor(Color.BLUE));

        }
    }

    private void addMapPolygon() {
        if(mGoogleMap != null) {

            try {
                for (int i = polygonList.size() - 1; i > 0; i--) {
                    polygonList.remove(i);
                }
            } catch (Exception e) {
                Log.d(TAG, "addMapMarkers: " + e.getLocalizedMessage());
            }

            polygonList.add(new LatLng(44.5045861, 26.0606003));
            polygonList.add(new LatLng(44.5048310, 26.1622238));
            polygonList.add(new LatLng(44.3830111, 26.1711502));
            polygonList.add(new LatLng(44.3842379, 26.0595703));
            polygonList.add(new LatLng(44.5055655, 26.0595703));



            // Instantiates a new Polygon object and adds points to define a rectangle
            PolygonOptions rectOptions = new PolygonOptions()
                    //                    .add(new LatLng(44.5045861, 26.0606003),
                    //                            new LatLng(44.5048310, 26.1622238),
                    //                            new LatLng(44.3830111, 26.1711502),
                    //                            new LatLng(44.3842379, 26.0595703),
                    //                            new LatLng(44.5055655, 26.0595703))
                    .add(polygonList.get(0),
                            polygonList.get(1),
                            polygonList.get(2),
                            polygonList.get(3),
                            polygonList.get(4)
                    )
                    .fillColor(Color.argb(20, 0, 252, 0))
                    .strokeColor(Color.rgb(0, 50, 100))
                    .strokeWidth(3);

            mGoogleMap.addPolygon(rectOptions);
        }
    }

    private void addMapMarkers() {

        resetMap();

        if(mClusterManager == null) {
            mClusterManager = new ClusterManager<ClusterMarker>(getActivity().getApplicationContext(), mGoogleMap);
        }
        if(mClusterManagerRenderer == null) {
            mClusterManagerRenderer = new MyClusterManagerRenderer(
                    getActivity(),
                    mGoogleMap,
                    mClusterManager
            );
            mClusterManager.setRenderer(mClusterManagerRenderer);
        }

        for (UserLocation userLocation: mUserLocations) {
            Log.d(TAG, "addMapMarkers: locations: " +
                    userLocation.getGeoPoint().toString());
            try{
                String snippet = "";
                if(userLocation.getUser().getUser_id().equals(FirebaseAuth.getInstance().getUid())) {
                    snippet = "this is you";
                }
                else{
                    snippet = "Determine route to " +
                            userLocation.getUser().getUsername() + "?";
                }

                //int avatar = R.drawable.cwm_logo; //set the default avatar
                int avatar = R.drawable.scooter; //set the default avatar
                try{
                    avatar = Integer.parseInt(userLocation.getUser().getAvatar());
                } catch (NumberFormatException e) {
                    Log.d(TAG, "addMapMarkers: no avatar for " + userLocation.getUser().getUsername() +
                            ", setting default.");
                }
                ClusterMarker newClusterMarker = new ClusterMarker(
                        new LatLng(userLocation.getGeoPoint().getLatitude(), userLocation.getGeoPoint().getLongitude()),
                        userLocation.getUser().getUsername(),
                        snippet,
                        avatar,
                        userLocation.getUser()

                );

                String scuterSauUtilizator = userLocation.getUser().getUsername();

                if(scuterSauUtilizator.toString().contains("scuter")) {
                    //to actually add the marker
                    mClusterManager.addItem(newClusterMarker);
                    //the cluster manager is the one who is actually displayed on the map
                    mClusterMarkers.add(newClusterMarker);
                    //the cluster marker is just a tool for us to keep the track of the markers on the map

                    Log.d(TAG, "addMapMarkers: Added: " + userLocation.getUser().getUsername());

                }

            }catch (NullPointerException e) {
                Log.d(TAG, "addMapMarkers: NullPointerException: " + e.getMessage());
            }
        }
        //to add everything to the map
        try {
            mClusterManager.cluster();
        } catch (Exception e) {
            Log.e(TAG, "mClusterManager issue: " + e.getLocalizedMessage());
        }
    }

    private void setCameraView() {

        //we want to define the top right and bottom left for our boundary
        //Overall map view window: 0.2 * 0.2 = 0.04
        double bottomBoundary = mUserPosition.getGeoPoint().getLatitude() - .1;
        double leftBoundary = mUserPosition.getGeoPoint().getLongitude() - .1;

        double topBoundary = mUserPosition.getGeoPoint().getLatitude() + .1;
        double rightBoundary = mUserPosition.getGeoPoint().getLongitude() + .1;

        mMapBoundary = new LatLngBounds(
                new LatLng(bottomBoundary, leftBoundary),
                new LatLng(topBoundary, rightBoundary)
        );

        mGoogleMap.moveCamera(CameraUpdateFactory.newLatLngBounds(mMapBoundary,0));
    }

//    FIXME -> IT CRASHES AFTER REGISTER WHEN USER TRIES TO CALCULATE DIRECTIONS
    // mUserPosition is null
    // setUserPosition won't work because
    // after registration, mUserLocations should be update !!!!!
    private void setUserPosition() {
        for (UserLocation userLocation : mUserLocations) {
            if(userLocation.getUser().getUser_id().equals(FirebaseAuth.getInstance().getUid())) {
                mUserPosition = userLocation;
            }
        }
    }

    private void initGoogleMap(Bundle savedInstanceState) {

        // *** IMPORTANT ***
        // MapView requires that the Bundle you pass contain _ONLY_ MapView SDK
        // objects or sub-Bundles.
        Bundle mapViewBundle = null;
        if (savedInstanceState != null) {
            mapViewBundle = savedInstanceState.getBundle(MAPVIEW_BUNDLE_KEY);
        }
        //mMapView = (MapView) findViewById(R.id.user_list_map);
        mMapView.onCreate(mapViewBundle);

        mMapView.getMapAsync(this);

        //that's going to instantiate our Google API context object
        //which is what we use to calculate directions
        if(mGeoApiContext == null) {
            mGeoApiContext = new GeoApiContext.Builder()
                    .apiKey(getString(R.string.google_map_api_key))
                    .build();
        }

    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);

        Bundle mapViewBundle = outState.getBundle(MAPVIEW_BUNDLE_KEY);
        if (mapViewBundle == null) {
            mapViewBundle = new Bundle();
            outState.putBundle(MAPVIEW_BUNDLE_KEY, mapViewBundle);
        }

        mMapView.onSaveInstanceState(mapViewBundle);
    }

    @Override
    public void onResume() {
        super.onResume();
        mMapView.onResume();

        //This is called to start the service runnable
        startUserLocationsRunnable();
    }

    @Override
    public void onStart() {
        super.onStart();
        mMapView.onStart();
    }

    @Override
    public void onStop() {
        super.onStop();
        mMapView.onStop();
    }

    @Override
    public void onMapReady(GoogleMap map) {

        try {
            // Customise the styling of the base map using a JSON object defined
            // in a raw resource file.
            boolean success = map.setMapStyle(
                    MapStyleOptions.loadRawResourceStyle(
                            getActivity().getApplicationContext(), R.raw.map_style));

            if (!success) {
                Log.e(TAG, "Style parsing failed.");
            }
        } catch (Resources.NotFoundException e) {
            Log.e(TAG, "Can't find style. Error: ", e);
        }

        map.addMarker(new MarkerOptions().position(new LatLng(44.4474731, 26.0489703)).title("P15 Regie"));
        map.getUiSettings().setZoomControlsEnabled(true);


        // !! In order to center the camera and zoom to a specific position : !!
        CameraUpdate center=
                CameraUpdateFactory.newLatLng(new LatLng(44.439663,
                        26.096306));
        CameraUpdate zoom=CameraUpdateFactory.zoomTo(11);


        map.moveCamera(center);
        map.animateCamera(zoom);



        //if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
        if (ActivityCompat.checkSelfPermission(getActivity(), Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(getActivity(), Manifest.permission.ACCESS_COARSE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        map.setMyLocationEnabled(true);
        //map.getUiSettings().setMyLocationButtonEnabled(false);

//        map.setTrafficEnabled(true);

        LatLngBounds latLngBounds= new LatLngBounds(
                        new LatLng(44.3400563,  25.9503937),
                        new LatLng(44.5393453, 26.2511444));

        map.setLatLngBoundsForCameraTarget(latLngBounds);

        mGoogleMap = map;
        //setCameraView();

        getChatroomUsers();

        //Now all the polyline clicks will be intercepted by the method 'onPolylineClick'
        mGoogleMap.setOnPolylineClickListener(this);


        //we pass 'this' to refer to the interface
        mGoogleMap.setOnInfoWindowClickListener(this);

        mGoogleMap.setOnMarkerClickListener(new GoogleMap.OnMarkerClickListener() {
            @Override
            public boolean onMarkerClick(Marker marker) {
                Log.d(TAG, "onMarkerClick: marker name: " + marker.getTitle());
                return false;
            }
        });

        mGoogleMap.setMinZoomPreference(10.0f); // Set a preference for minimum zoom (Zoom out).
        mGoogleMap.setMaxZoomPreference(17.0f); // Set a preference for maximum zoom (Zoom In).

        //        Polygon polygon = mGoogleMap.addPolygon(rectOptions);

        //        addMapMarkers();



    }


    @Override
    public void onPause() {
        mMapView.onPause();
        super.onPause();
    }

    @Override
    public void onDestroy() {
        mMapView.onDestroy();
        super.onDestroy();

        //This is called to stop the service runnable and the handler in the background
        // when the activity is closed
        stopLocationUpdates();
    }

    @Override
    public void onLowMemory() {
        super.onLowMemory();
        mMapView.onLowMemory();
    }


    @Override
    public void onClick(View v) {
        if (v.getId() == R.id.btn_reset_map) {
            if (mGoogleMap == null) {
                getActivity().recreate();
            } else {
                addMapMarkers();
            }
        }
    }

    @Override
    public void onInfoWindowClick(final Marker marker) {



        if(marker.getTitle().contains("Route #")) {
            final AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
            builder.setMessage("Do you really want to start the engine of the scooter? \n\nWe charge: 0.5$/minute + 1$ when engine starts")
                    .setCancelable(true)
                    .setPositiveButton("Yes", new DialogInterface.OnClickListener() {
                        public void onClick(@SuppressWarnings("unused") final DialogInterface dialog, @SuppressWarnings("unused") final int id) {


                            //                            startEngine();

                            if (isActive) {
                                Toast.makeText(getActivity(), "YOU ALREADY HAVE ONE RENTED MOPED", Toast.LENGTH_SHORT).show();
                            } else {


                                testPoint = new LatLng(marker.getPosition().latitude, marker.getPosition().longitude);

                                Log.d(TAG, "onClick: test1234 " + marker.getSnippet());

                                //    TODO -> THE USER SHOULD START THE ENGINE ONLY IF IT'S REALLY CLOSE TO THE SCOOTER
                                if (PolyUtil.containsLocation(testPoint, polygonList, false)) {

                                    isActive = true;

                                    if (marker.getSnippet().contains("scuter6")) {
                                        Log.d(TAG, "Scooter 6: STARTED");
                                        Toast.makeText(getActivity(), "YOU STARTED THE ENGINE \nOF SCOOTER 6", Toast.LENGTH_SHORT).show();
                                        turnOnEngine();
                                    } else {
                                        Toast.makeText(getActivity(), "YOU STARTED THE ENGINE", Toast.LENGTH_SHORT).show();
                                    }

                                    generateParkingCode();

                                    mTimeAndTotal = (RelativeLayout) getActivity().findViewById(R.id.time_and_total);
                                    mTimeAndTotal.setVisibility(View.VISIBLE);
                                    startTimer(marker);

                                    //                            String latitude = String.valueOf(marker.getPosition().latitude);
                                    //                            String longitude = String.valueOf(marker.getPosition().longitude);
                                    //                            Uri gmmIntentUri = Uri.parse("google.navigation:q=" + latitude + "," + longitude);
                                    //                            Intent mapIntent = new Intent(Intent.ACTION_VIEW, gmmIntentUri);
                                    //                            mapIntent.setPackage("com.google.android.apps.maps");
                                    //
                                    //                            try{
                                    //                                if (mapIntent.resolveActivity(getActivity().getPackageManager()) != null) {
                                    //                                    startActivity(mapIntent);
                                    //                                }
                                    //                            }catch (NullPointerException e) {
                                    //                                Log.e(TAG, "onClick: NullPointerException: Couldn't open map." + e.getMessage() );
                                    //                                Toast.makeText(getActivity(), "Couldn't open map", Toast.LENGTH_SHORT).show();
                                    //                            }
                                } else {
                                    Toast.makeText(getActivity(), "The moped is not in the green area", Toast.LENGTH_SHORT).show();
                                }
                            }
                        }
                    })
                    .setNegativeButton("No", new DialogInterface.OnClickListener() {
                        public void onClick(final DialogInterface dialog, @SuppressWarnings("unused") final int id) {
                            dialog.cancel();
                        }
                    });
            final AlertDialog alert = builder.create();
            alert.show();
        }
        else{
            if(marker.getSnippet().equals("This is you")) {
                marker.hideInfoWindow();
            }
            else{

                final AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
                //marker.getSnippet() is the dialogue "Determine route to five?"
                builder.setMessage(marker.getSnippet())
                        .setCancelable(true)
                        .setPositiveButton("Yes", new DialogInterface.OnClickListener() {
                            public void onClick(@SuppressWarnings("unused") final DialogInterface dialog, @SuppressWarnings("unused") final int id) {

                                //before the marker is set again
                                resetSelectedMarker();

                                setUserPosition();

                                mSelectedMarker = marker;
                                calculateDirections(marker);

                                //dialog.dismiss();
                            }
                        })
                        .setNegativeButton("No", new DialogInterface.OnClickListener() {
                            public void onClick(final DialogInterface dialog, @SuppressWarnings("unused") final int id) {
                                dialog.cancel();
                            }
                        });
                final AlertDialog alert = builder.create();
                alert.show();
            }
        }


    }

    @Override
    public void onPolylineClick(Polyline polyline) {

        int index = 0;
        for (PolylineData polylineData: mPolyLinesData) {
            index++;
            Log.d(TAG, "onPolylineClick: toString: " + polylineData.toString());
            if(polyline.getId().equals(polylineData.getPolyline().getId())) {
                polylineData.getPolyline().setColor(ContextCompat.getColor(getActivity(), R.color.blue1));
                polylineData.getPolyline().setZIndex(1);

                //we need to add a new marker
                LatLng endLocation = new LatLng(
                        polylineData.getLeg().endLocation.lat,
                        polylineData.getLeg().endLocation.lng
                );

                //                Marker marker = mGoogleMap.addMarker(new MarkerOptions()
                //                        .position(endLocation)
                //                        .title("Trip #" + index)
                //                        .snippet("Duration: " + polylineData.getLeg().duration)
                //                );

                Marker marker = mGoogleMap.addMarker(new MarkerOptions()
                        .position(endLocation)
                        .title("Route #" + index + ": " + polylineData.getLeg().duration)
                        .snippet("Start engine of " + mSelectedMarker.getTitle())
                );

                marker.showInfoWindow();

                //we need to add the markers to our list
                mTripMarkers.add(marker);
            }
            else{
                polylineData.getPolyline().setColor(ContextCompat.getColor(getActivity(), R.color.darkGrey));
                polylineData.getPolyline().setZIndex(0);
            }
        }
    }

    private void updateTimerOnServer(boolean shouldStartEngine, String markerUsername) {

        for (final ClusterMarker clusterMarker: mClusterMarkers) {

            if (clusterMarker.getUser().getUsername().equals(markerUsername)) {

                //Update the Users collection
                FirebaseFirestoreSettings settings = new FirebaseFirestoreSettings.Builder()
                        .setTimestampsInSnapshotsEnabled(true)
                        .build();
                mDb.setFirestoreSettings(settings);

                DocumentReference userRef = mDb
                        .collection(getString(R.string.collection_users))
                        .document(clusterMarker.getUser().getUser_id());

                userRef.update(getString(R.string.collection_field_engine_started), shouldStartEngine);
                Date currentDate = new Date();
                userRef.update(getString(R.string.collection_field_engine_started_at), shouldStartEngine ? currentDate : null);

                //Update the User Locations collection
                DocumentReference locationRef = mDb
                        .collection(getString(R.string.collection_user_locations))
                        .document(clusterMarker.getUser().getUser_id());

                Map<String, Object> userLocationsDocument = new HashMap<>();
                Map<String, Object> userField = new HashMap<>();
                userField.put(getString(R.string.collection_field_engine_started), shouldStartEngine);
                userField.put(getString(R.string.collection_field_engine_started_at), shouldStartEngine ? currentDate : null);
                userLocationsDocument.put("user", userField);
                locationRef.set(userLocationsDocument, SetOptions.merge());

            }

        }

    }

    private void startTimer(Marker marker) {

        SharedPreferences.Editor editor = getActivity().getSharedPreferences(SCOOTER_PREFS, MODE_PRIVATE).edit();

        String marketSnippet = marker.getSnippet();
        String markerUsername = marketSnippet.substring(marketSnippet.lastIndexOf(" ") + 1);

        editor.putString(ACTIVE_SCOOTER_USERNAME, markerUsername);
        editor.apply();

        updateTimerOnServer(true, markerUsername);

        myTimer = new Timer();
        myTimer.schedule(new TimerTask() {
            @Override
            public void run() {

                if (getActivity() == null) {
                    return;
                }

                getActivity().runOnUiThread(new Runnable() {
                    @Override
                    public void run() {

                        if(seconds==59) {
                            minutes++;
                            seconds =0;
                            minutesPassed.setText(minutes + ":" +seconds);
                            totalPrice.setText(1 + minutes*0.5 + "$");
                        }else{
                            seconds++;
                            minutesPassed.setText(minutes + ":" +seconds);
                            totalPrice.setText(1 + minutes*0.5 + "$");
                        }

                    }
                });
            }
        }, 2000, 1000);

    }


    private void stopTimer() {
        if(myTimer != null) {
            myTimer.cancel();
        }
    }

    public void showPopup() {

        TextView minutesPassedPopup;
        TextView totalToPay;
        TextView txtClose;
        Button btnContact;

        myDialog.setContentView(R.layout.custom_popup);

        minutesPassedPopup = (TextView) myDialog.findViewById(R.id.minutes_passed);

        if(minutes == 1) {
            minutesPassedPopup.setText(String.valueOf("One minute"));
        }else {
            minutesPassedPopup.setText(String.valueOf(minutes + " minutes"));
        }

        totalToPay = (TextView) myDialog.findViewById(R.id.total_to_pay);
        totalToPay.setText(String.valueOf(1 + minutes * 0.5 + "$"));


        //Close the dialog if "x" is pressed
        txtClose =(TextView) myDialog.findViewById(R.id.txt_close);
        txtClose.setText("x");
        btnContact = (Button) myDialog.findViewById(R.id.btn_contact);
        txtClose.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {



                Toast.makeText(getContext(),"Payment has been made.", Toast.LENGTH_LONG).show();
                minutes = 0;
                seconds = 0;
                minutesPassed.setText(minutes + ":" +seconds);
                totalPrice.setText("0$");

                myDialog.dismiss();
            }
        });

        btnContact.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                //get input from EditTexts and TextViews and save in variables
                String recipient = "enachescurobert@gmail.com";
                String subject = "EcoDrive report";
                String message = "My issues are:";

                //method call for email intent with these inputs as parameters
                sendEmail(recipient, subject, message);
            }
        });


        //Dialog background as transparent
        myDialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        //show the dialog
        myDialog.show();

        turnOffEngine();
    }

    private void sendEmail(String recipient, String subject, String message) {
        //Action_SEND action to launch an email client installed on our Android device
        Intent mEmailIntent = new Intent(Intent.ACTION_SEND);
        //To send an email, we need to specify milto: as URI using setData() method
        //and data type will be to text/plain using setType() method
        mEmailIntent.setData(Uri.parse("mailto:"));
        mEmailIntent.setType("text/plain");
        //put recipient email in intent
        /* recipient is put as array because we may want to send email to multiple emails so
        by using commas(,) separated emails, it will be stored in array
        */
        mEmailIntent.putExtra(Intent.EXTRA_EMAIL, new String[]{recipient});
        //we will put the subject of the email
        mEmailIntent.putExtra(Intent.EXTRA_SUBJECT, subject);
        //and now, the message
        mEmailIntent.putExtra(Intent.EXTRA_TEXT, message);

        try {
            //no error, so start intent
            startActivity(Intent.createChooser(mEmailIntent, "Send email"));
        }
        catch (Exception e) {
            //if anything goes wrong e.g no internet or email client
            //get and show exception
            Toast.makeText(getActivity(),e.getMessage(),Toast.LENGTH_SHORT).show();
        }
    }


    private void generateParkingCode() {
        String randomStr = array[new Random().nextInt(array.length)];

        TextView mParkingCode = (TextView) getActivity().findViewById(R.id.parking_code);

        try {
            mParkingCode.setText(randomStr);
        }catch (Exception e) {
            Log.e(TAG, "generateParkingCode: " + e.getMessage());
        }
        Log.d(TAG, "generateParkingCode: the generated code is " + randomStr);
    }


    private void turnOnEngine() {

        Call<Void> call = thingSpeakApi.turnOnEngine();


        call.enqueue(new Callback<Void>() {
            @Override
            public void onResponse(Call<Void> call, Response<Void> response) {
                Log.d(TAG, "onResponse: Status code: " + response.code());
            }

            @Override
            public void onFailure(Call<Void> call, Throwable t) {
                Log.d(TAG, "onFailure: " + t.getMessage());
            }
        });
    }

    private void turnOffEngine() {

        Call<Void> call = thingSpeakApi.turnOffEngine();


        call.enqueue(new Callback<Void>() {
            @Override
            public void onResponse(Call<Void> call, Response<Void> response) {
                Log.d(TAG, "onResponse: Status code: " + response.code());
            }

            @Override
            public void onFailure(Call<Void> call, Throwable t) {
                Log.d(TAG, "onFailure: " + t.getMessage());
            }
        });
    }

    //    FIXME -> IT WILL FREEZE WHEN A NEW SCOOTER IS ADDED ON MAP
    private void getChatroomUsers() {

        CollectionReference usersRef = mDb
                //.collection(getString(R.string.collection_chatrooms))
                //.document(mChatroom.getChatroom_id())
                //.document("xwT2T8sasZEaY5g0cwf2")
                //.collection(getString(R.string.collection_chatroom_user_list));
                .collection(getString(R.string.collection_users));

        mUserListEventListener = usersRef
                .addSnapshotListener(new EventListener<QuerySnapshot>() {
                    @Override
                    public void onEvent(@javax.annotation.Nullable QuerySnapshot queryDocumentSnapshots, @javax.annotation.Nullable FirebaseFirestoreException e) {
                        if (e != null) {
                            Log.e(TAG, "onEvent: Listen failed.", e);
                            return;
                        }

                        if(queryDocumentSnapshots != null) {

                            // Clear the list and add all the users again
                            mUserList.clear();
                            mUserList = new ArrayList<>();

                            for (QueryDocumentSnapshot doc : queryDocumentSnapshots) {
                                User user = doc.toObject(User.class);
                                mUserList.add(user);

                                //
                                getUserLocation(user);

                            }

                            Log.d(TAG, "onEvent: user list size: " + mUserList.size());
                        }
                    }
                });
    }

    private void getUserLocation(User user) {
        DocumentReference locationRef = mDb
                .collection(getString(R.string.collection_user_locations))
                .document(user.getUser_id());

        locationRef.get().addOnCompleteListener(new OnCompleteListener<DocumentSnapshot>() {
            @Override
            public void onComplete(@NonNull Task<DocumentSnapshot> task) {
                if(task.isSuccessful()) {
                    //if the task is successful
                    //we can retrieve a result
                    if(task.getResult().toObject(UserLocation.class) != null) {
                        //if there is actually a location coordinate of the user in the DB
                        //<<which it should have (because the user has to accept GPS)>>
                        //add that location
                        mUserLocations.add(task.getResult().toObject(UserLocation.class));

    /*                        FIXME -> THIS METHOD SHOULD NOT BE HERE
                                    BECAUSE IT WILL BE CALLED MULTIPLE TIMES */
                        addMapMarkers();
                        //now we need to pass those locations in the fragment
                        //that will be done in the inflateUserListFragment() method
                    }
                }
            }
        });
    }

}