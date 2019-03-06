package com.sixgill.beaconbeeper.com.sixgill.beaconbeepr.db;

import android.arch.persistence.room.ColumnInfo;
import android.arch.persistence.room.Entity;
import android.arch.persistence.room.PrimaryKey;

@Entity(tableName = "event")
public class Event {
    @PrimaryKey(autoGenerate = true)
    public int id;

    @ColumnInfo(name = "uuid")
    public String uuid;

    @ColumnInfo(name = "major")
    public String major;

    @ColumnInfo(name = "minor")
    public String minor;

    @ColumnInfo(name = "rssi")
    public Integer rssi;

    @ColumnInfo(name = "distance")
    public Double distance;

    @ColumnInfo(name = "vibration")
    public Boolean vibration;

    @ColumnInfo(name = "sound")
    public Boolean sound;

    @ColumnInfo(name = "date")
    public String date;
}