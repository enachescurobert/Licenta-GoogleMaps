package com.enachescurobert.googlemaps2019.util;

import java.util.List;

import retrofit2.Call;
import retrofit2.http.GET;

public interface ThingSpeakApi {

    @GET("https://api.thingspeak.com/update?api_key=DRKXWIWO97OGNP6H&field1=1")
    Call<Void> turnOnEngine();

    @GET("https://api.thingspeak.com/update?api_key=DRKXWIWO97OGNP6H&field1=0")
    Call<Void> turnOffEngine();


}
