package com.enachescurobert.googlemaps2019.models;

import com.google.android.gms.maps.model.Polyline;
import com.google.maps.model.DirectionsLeg;

//this will hold the reference of a Polyline
//and then a reference for the DiretionsLeg
//a DirectionsLeg is what outlines the direction for the route itself
public class PolylineData {

    private Polyline polyline;
    private DirectionsLeg leg;

    public PolylineData(Polyline polyline, DirectionsLeg leg) {
        this.polyline = polyline;
        this.leg = leg;
    }

    public Polyline getPolyline() {
        return polyline;
    }

    public void setPolyline(Polyline polyline) {
        this.polyline = polyline;
    }

    public DirectionsLeg getLeg() {
        return leg;
    }

    public void setLeg(DirectionsLeg leg) {
        this.leg = leg;
    }

    @Override
    public String toString() {
        return "PolylineData{" +
                "polyline=" + polyline +
                ", leg=" + leg +
                '}';
    }
}