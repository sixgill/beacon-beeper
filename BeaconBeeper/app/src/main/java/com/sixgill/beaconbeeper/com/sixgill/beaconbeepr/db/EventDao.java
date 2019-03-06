package com.sixgill.beaconbeeper.com.sixgill.beaconbeepr.db;

import android.arch.persistence.room.Dao;
import android.arch.persistence.room.Delete;
import android.arch.persistence.room.Insert;
import android.arch.persistence.room.Query;

import java.util.List;

@Dao
public interface EventDao {
    @Query("SELECT * FROM event")
    List<Event> getEvents();

    @Query("SELECT * FROM event limit 1")
    Event getNextEvent();

    @Insert
    void insertAll(Event... users);

    @Delete
    void delete(Event user);
}