package com.enachescurobert.googlemaps2019.ui;

import android.Manifest;
import android.app.ActivityManager;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationManager;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.design.widget.Snackbar;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.FragmentTransaction;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.text.InputType;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.Toast;

import com.enachescurobert.googlemaps2019.R;
import com.enachescurobert.googlemaps2019.UserClient;
import com.enachescurobert.googlemaps2019.adapters.ChatroomRecyclerAdapter;
import com.enachescurobert.googlemaps2019.models.Chatroom;
import com.enachescurobert.googlemaps2019.models.User;
import com.enachescurobert.googlemaps2019.models.UserLocation;
import com.enachescurobert.googlemaps2019.services.LocationService;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
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
import com.google.firebase.firestore.GeoPoint;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.firestore.QuerySnapshot;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

import javax.annotation.Nullable;

import static com.enachescurobert.googlemaps2019.Constants.ERROR_DIALOG_REQUEST;
import static com.enachescurobert.googlemaps2019.Constants.PERMISSIONS_REQUEST_ACCESS_FINE_LOCATION;
import static com.enachescurobert.googlemaps2019.Constants.PERMISSIONS_REQUEST_ENABLE_GPS;


public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";

    private ListenerRegistration mUserListEventListener;

    private ArrayList<User> mUserList = new ArrayList<>();
    private ArrayList<UserLocation> mUserLocations = new ArrayList<>();

    private Chatroom mChatroom;

    //widgets
    private ProgressBar mProgressBar;
    Button mStartOurMap;
    Button mSearchCars;
    Button mSearchBykes;

    //vars
    private ArrayList<Chatroom> mChatrooms = new ArrayList<>();
    private Set<String> mChatroomIds = new HashSet<>();
    private ChatroomRecyclerAdapter mChatroomRecyclerAdapter;
    private RecyclerView mChatroomRecyclerView;
    private ListenerRegistration mChatroomEventListener;
    private FirebaseFirestore mDb;

    // this is going to be responsible for restricting
    // application access if location permissions are not accepted
    private boolean mLocationPermissionGranted = false;

    // for last known location
    private FusedLocationProviderClient mFusedLocationClient;

    private UserLocation mUserLocation;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        mProgressBar = findViewById(R.id.progressBar);
        mChatroomRecyclerView = findViewById(R.id.chatrooms_recycler_view);

        mFusedLocationClient = LocationServices.getFusedLocationProviderClient(this);

        mDb = FirebaseFirestore.getInstance();

        initSupportActionBar();

        getChatroomUsers();

        inflateUserListFragment();

        mStartOurMap=(Button)findViewById(R.id.start_our_map);
        mStartOurMap.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {

                inflateUserListFragment();

//                Toast.makeText(MainActivity.this, "Butonul Merge",
//                        Toast.LENGTH_LONG).show();
            }
        });

        mSearchCars=(Button)findViewById(R.id.search_cars);
        mSearchCars.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {

                Toast.makeText(MainActivity.this, "Electric cars coming soon",
                        Toast.LENGTH_LONG).show();
            }
        });

        mSearchBykes=(Button)findViewById(R.id.search_bykes);
        mSearchBykes.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                Toast.makeText(MainActivity.this, "Bykes coming soon",
                        Toast.LENGTH_LONG).show();
            }
        });
    }

    //
    private void startLocationService(){
        if(!isLocationServiceRunning()){
            //the way we start the service is with an intent
            Intent serviceIntent = new Intent(this, LocationService.class);
            //        this.startService(serviceIntent);


            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O){

                //if we don't call the startForegroundService
                //when the service goes to the background, it's going to stop
                MainActivity.this.startForegroundService(serviceIntent);
            }else{
                startService(serviceIntent);
            }
        }
    }

    //this methods just checks if the service is running and returns true/false
    private boolean isLocationServiceRunning() {
        ActivityManager manager = (ActivityManager) getSystemService(ACTIVITY_SERVICE);
        for (ActivityManager.RunningServiceInfo service : manager.getRunningServices(Integer.MAX_VALUE)){
            if("com.enachescurobert.googledirectionstest.services.LocationService".equals(service.service.getClassName())) {
                Log.d(TAG, "isLocationServiceRunning: location service is already running.");
                return true;
            }
        }
        Log.d(TAG, "isLocationServiceRunning: location service is not running.");
        return false;
    }

    //We need to retrieve the User details for the authenticated user and set it to
    // our userLocation object
    private void getUserDetails(){
        if(mUserLocation == null){
            mUserLocation = new UserLocation();

            DocumentReference userRef = mDb
                    .collection(getString(R.string.collection_users))
                    .document(FirebaseAuth.getInstance().getUid());

            userRef.get().addOnCompleteListener(new OnCompleteListener<DocumentSnapshot>() {
                @Override
                public void onComplete(@NonNull Task<DocumentSnapshot> task) {
                    if(task.isSuccessful()){
                        Log.d(TAG, "onComplete: successfully get the user details.");

                        //get the User object
                        User user = task.getResult().toObject(User.class);
                        mUserLocation.setUser(user);

                        ((UserClient)getApplicationContext()).setUser(user);

                        getLastKnownLocation();
                    }
                }
            });
        }
    }

    private void saveUserLocation(){
        if(mUserLocation != null) {
            //mDB is our FireStore instance
            //we need to reference the right collection
            //and we need to identify the document by it's ID
            DocumentReference locationRef = mDb
                    .collection(getString(R.string.collection_user_locations))
                    .document(FirebaseAuth.getInstance().getUid());
            //insead of
            // .collection(getString(R.string.collection_user_locations))
            //we can use
            //.collection("User Locations")

            //
            locationRef.set(mUserLocation).addOnCompleteListener(new OnCompleteListener<Void>() {
                @Override
                public void onComplete(@NonNull Task<Void> task) {
                    if(task.isSuccessful()){
                        Log.d(TAG, "saveUserLocation: \ninserted user location into database." +
                                "\n latitude: " + mUserLocation.getGeoPoint().getLatitude() +
                                "\n longitude: " + mUserLocation.getGeoPoint().getLongitude());
                    }
                }
            });

        }
    }

    private void getLastKnownLocation() {
        Log.d(TAG, "getLastKnownLocation: called.");

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        // This is part of the Google Maps SDK (not Google Directions API)
        mFusedLocationClient.getLastLocation().addOnCompleteListener(new OnCompleteListener<Location>() {
            @Override
            public void onComplete(@NonNull Task<Location> task) {
                if(task.isSuccessful()){
                    Location location = task.getResult();
                    GeoPoint geoPoint = new GeoPoint(location.getLatitude(), location.getLongitude());
                    Log.d(TAG, "onComplete: latitude: " + geoPoint.getLatitude());
                    Log.d(TAG,"onComplete: longitude: " + geoPoint.getLongitude());

                    //Now we need to save the user location
                    mUserLocation.setGeoPoint(geoPoint);
                    mUserLocation.setTimestamp(null);
                    saveUserLocation();
                    //we will start the service after get last known location is called
                    //because that means that we have all permissions and what we need
                    startLocationService();

                }
            }
        });
    }

    private boolean checkMapServices(){
        if(isServicesOK()){
            if(isMapsEnabled()){
                return true;
            }
        }
        return false;
    }

    private void buildAlertMessageNoGps() {
        final AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setMessage("This application requires GPS to work properly, do you want to enable it?")
                .setCancelable(false)
                .setPositiveButton("Yes", new DialogInterface.OnClickListener() {
                    public void onClick(@SuppressWarnings("unused") final DialogInterface dialog, @SuppressWarnings("unused") final int id) {
                        Intent enableGpsIntent = new Intent(android.provider.Settings.ACTION_LOCATION_SOURCE_SETTINGS);
                        //I need to know if the user accepted or denied the permission
                        startActivityForResult(enableGpsIntent, PERMISSIONS_REQUEST_ENABLE_GPS);
                        //after the result is retrieved, we'll get onActivityResult running
                        //after the user has either accepted or denied the permission
                    }
                });
        final AlertDialog alert = builder.create();
        alert.show();
    }

    //this method is used to check if GPS is enabled on the debice
    public boolean isMapsEnabled(){
        final LocationManager manager = (LocationManager) getSystemService( Context.LOCATION_SERVICE );

        if ( !manager.isProviderEnabled( LocationManager.GPS_PROVIDER ) ) {
            buildAlertMessageNoGps();
            return false;
        }
        return true;
    }

    private void getLocationPermission() {
        /*
         * Request location permission, so that we can get the location of the
         * device. The result of the permission request is handled by a callback,
         * onRequestPermissionsResult.
         */
        if (ContextCompat.checkSelfPermission(this.getApplicationContext(),
                android.Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED) {
            mLocationPermissionGranted = true;
            getChatrooms();
            getUserDetails();
        } else {
            ActivityCompat.requestPermissions(this,
                    new String[]{android.Manifest.permission.ACCESS_FINE_LOCATION},
                    PERMISSIONS_REQUEST_ACCESS_FINE_LOCATION);
        }
    }

    //this method is responsable for determining if the device is able to use google services
    //if not, you will get a popup to install it
    public boolean isServicesOK(){
        Log.d(TAG, "isServicesOK: checking google services version");

        int available = GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(MainActivity.this);

        if(available == ConnectionResult.SUCCESS){
            //everything is fine and the user can make map requests
            Log.d(TAG, "isServicesOK: Google Play Services is working");
            return true;
        }
        else if(GoogleApiAvailability.getInstance().isUserResolvableError(available)){
            //an error occured but we can resolve it
            Log.d(TAG, "isServicesOK: an error occured but we can fix it");
            Dialog dialog = GoogleApiAvailability.getInstance().getErrorDialog(MainActivity.this, available, ERROR_DIALOG_REQUEST);
            dialog.show();
        }else{
            Toast.makeText(this, "You can't make map requests", Toast.LENGTH_SHORT).show();
        }
        return false;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String permissions[],
                                           @NonNull int[] grantResults) {
        mLocationPermissionGranted = false;
        switch (requestCode) {
            case PERMISSIONS_REQUEST_ACCESS_FINE_LOCATION: {
                // If request is cancelled, the result arrays are empty.
                if (grantResults.length > 0
                        && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    mLocationPermissionGranted = true;
                }
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        Log.d(TAG, "onActivityResult: called.");
        switch (requestCode) {
            case PERMISSIONS_REQUEST_ENABLE_GPS: {
                if(mLocationPermissionGranted){
                    getChatrooms();
                    getUserDetails();
                }
                else{
                    getLocationPermission();
                }
            }
        }

    }

    private void initSupportActionBar(){
        setTitle("Licenta Enachescu Robert");
    }

    private void getChatrooms(){

        FirebaseFirestoreSettings settings = new FirebaseFirestoreSettings.Builder()
                .setTimestampsInSnapshotsEnabled(true)
                .build();
        mDb.setFirestoreSettings(settings);

        CollectionReference chatroomsCollection = mDb
                .collection(getString(R.string.collection_chatrooms));

        mChatroomEventListener = chatroomsCollection.addSnapshotListener(new EventListener<QuerySnapshot>() {
            @Override
            public void onEvent(@Nullable QuerySnapshot queryDocumentSnapshots, @Nullable FirebaseFirestoreException e) {
                Log.d(TAG, "onEvent: called.");

                if (e != null) {
                    Log.e(TAG, "onEvent: Listen failed.", e);
                    return;
                }

                if(queryDocumentSnapshots != null){
                    for (QueryDocumentSnapshot doc : queryDocumentSnapshots) {

                        Chatroom chatroom = doc.toObject(Chatroom.class);
                        if(!mChatroomIds.contains(chatroom.getChatroom_id())){
                            mChatroomIds.add(chatroom.getChatroom_id());
                            mChatrooms.add(chatroom);
                        }
                    }
                    Log.d(TAG, "onEvent: number of chatrooms: " + mChatrooms.size());
                }

            }
        });
    }


    @Override
    protected void onDestroy() {
        super.onDestroy();
        if(mChatroomEventListener != null){
            mChatroomEventListener.remove();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        getChatrooms();
        if(checkMapServices()){
            if(mLocationPermissionGranted){
                getChatrooms();
                getUserDetails();
            }
            else{
                getLocationPermission();
            }
        }
    }

    private void signOut(){
        FirebaseAuth.getInstance().signOut();
        Intent intent = new Intent(this, LoginActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return super.onCreateOptionsMenu(menu);
    }


    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch(item.getItemId()){
            case R.id.action_sign_out:{
                signOut();
                return true;
            }
            case R.id.action_profile:{
                startActivity(new Intent(this, ProfileActivity.class));
                return true;
            }
            default:{
                return super.onOptionsItemSelected(item);
            }
        }

    }

    private void showDialog(){
        mProgressBar.setVisibility(View.VISIBLE);
    }

    private void hideDialog(){
        mProgressBar.setVisibility(View.GONE);
    }

    public void inflateUserListFragment(){
        hideSoftKeyboard();

        UserListFragment fragment = UserListFragment.newInstance();
        Bundle bundle = new Bundle();
        bundle.putParcelableArrayList(getString(R.string.intent_user_list), mUserList);

        //in order to pass those locations to a fragment
        //we need to attach them to a bundle
        bundle.putParcelableArrayList(getString(R.string.intent_user_locations), mUserLocations);

        fragment.setArguments(bundle);

        FragmentTransaction transaction = getSupportFragmentManager().beginTransaction();
        transaction.setCustomAnimations(R.anim.slide_in_up, R.anim.slide_out_up);
        transaction.replace(R.id.user_maps_container, fragment, getString(R.string.fragment_user_list));
        transaction.addToBackStack(getString(R.string.fragment_user_list));
        transaction.commit();
    }

    private void hideSoftKeyboard(){
        this.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);
    }

    private void getChatroomUsers(){

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

                        if(queryDocumentSnapshots != null){

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

    private void getUserLocation(User user){
        DocumentReference locationRef = mDb
                .collection(getString(R.string.collection_user_locations))
                .document(user.getUser_id());

        locationRef.get().addOnCompleteListener(new OnCompleteListener<DocumentSnapshot>() {
            @Override
            public void onComplete(@NonNull Task<DocumentSnapshot> task) {
                if(task.isSuccessful()){
                    //if the task is successful
                    //we can retrieve a result
                    if(task.getResult().toObject(UserLocation.class) != null){
                        //if there is actually a location coordinate of the user in the DB
                        //<<which it should have (because the user has to accept GPS)>>
                        //add that location
                        mUserLocations.add(task.getResult().toObject(UserLocation.class));
                        //now we need to pass those locations in the fragment
                        //that will be done in the inflateUserListFragment() method
                    }
                }
            }
        });
    }


}
