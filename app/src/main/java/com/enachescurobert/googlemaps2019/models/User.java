package com.enachescurobert.googlemaps2019.models;

import android.os.Parcel;
import android.os.Parcelable;

import com.google.firebase.firestore.ServerTimestamp;

import java.util.Date;

public class User implements Parcelable{

    private String email;
    private String user_id;
    private String username;
    private String avatar;
    private boolean scooter;
    private boolean engineStarted;

    //this is used for FireStore
    //if you pass NULL to the engineStartedAt when you insert the object to the DB,
    // it will insert a timestamp on the exactly time when it was created
    private @ServerTimestamp
    Date engineStartedAt;

    public User(String email, String user_id, String username, String avatar, boolean scooter, boolean engineStarted, Date engineStartedAt) {
        this.email = email;
        this.user_id = user_id;
        this.username = username;
        this.avatar = avatar;
        this.scooter = scooter;
        this.engineStarted = engineStarted;
        this.engineStartedAt = engineStartedAt;
    }

    public User() {

    }

    protected User(Parcel in) {
        email = in.readString();
        user_id = in.readString();
        username = in.readString();
        avatar = in.readString();
        scooter = in.readByte() != 0; // scooter -> true if byte != 0
        engineStarted = in.readByte() != 0;
        long tmpDate = in.readLong();
        this.engineStartedAt = tmpDate == -1 ? null : new Date(tmpDate);
    }

    public static final Creator<User> CREATOR = new Creator<User>() {
        @Override
        public User createFromParcel(Parcel in) {
            return new User(in);
        }

        @Override
        public User[] newArray(int size) {
            return new User[size];
        }
    };

    public String getAvatar() {
        return avatar;
    }

    public void setAvatar(String avatar) {
        this.avatar = avatar;
    }

    public static Creator<User> getCREATOR() {
        return CREATOR;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getUser_id() {
        return user_id;
    }

    public void setUser_id(String user_id) {
        this.user_id = user_id;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public boolean isScooter() {
        return scooter;
    }

    public void setScooter(boolean scooter) {
        this.scooter = scooter;
    }

    public boolean isEngineStarted() {
        return engineStarted;
    }

    public void setEngineStarted(boolean engineStarted) {
        this.engineStarted = engineStarted;
    }

    public Date getEngineStartedAt() {
        return engineStartedAt;
    }

    public void setEngineStartedAt(Date engineStartedAt) {
        this.engineStartedAt = engineStartedAt;
    }

    @Override
    public String toString() {
        return "User{" +
                "email='" + email + '\'' +
                ", user_id='" + user_id + '\'' +
                ", username='" + username + '\'' +
                ", avatar='" + avatar + '\'' +
                ", is scooter=" + scooter +
                ", engine started=" + engineStarted +
                '}';
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(email);
        dest.writeString(user_id);
        dest.writeString(username);
        dest.writeString(avatar);
        dest.writeByte((byte) (scooter ? 1 : 0)); // if scooter == true, byte  -> 1
        dest.writeByte((byte) (engineStarted ? 1 : 0));
        dest.writeLong(this.engineStartedAt != null ? this.engineStartedAt.getTime() : -1);
    }
}

