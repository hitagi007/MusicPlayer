package itto.pl.musicplayer.utils;

import android.content.Context;
import android.content.SharedPreferences;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.ArrayList;

import itto.pl.musicplayer.data.model.Audio;

public class StorageUtil {
    private final String STORAGE = "itto.pl.musicplayer.STORAGE";
    private SharedPreferences mPreferences;
    private Context mContext;

    public StorageUtil(Context context) {
        mContext = context;
    }

    public void storeAudio(ArrayList<Audio> audioList) {
        mPreferences = mContext.getSharedPreferences(STORAGE, Context.MODE_PRIVATE);

        SharedPreferences.Editor editor = mPreferences.edit();
        Gson gson = new Gson();
        String json = gson.toJson(audioList);
        editor.putString("audioArrayList", json);
        editor.apply();
    }

    public ArrayList<Audio> loadAudio() {
        mPreferences = mContext.getSharedPreferences(STORAGE, Context.MODE_PRIVATE);
        Gson gson = new Gson();
        String json = mPreferences.getString("audioArrayList", null);
        Type type = new TypeToken<ArrayList<Audio>>() {
        }.getType();
        return gson.fromJson(json, type);
    }

    public void storeAudioIndex(int index) {
        mPreferences = mContext.getSharedPreferences(STORAGE, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = mPreferences.edit();
        editor.putInt("audioIndex", index);
        editor.apply();
    }

    public int loadAudioIndex() {
        mPreferences = mContext.getSharedPreferences(STORAGE, Context.MODE_PRIVATE);
        return mPreferences.getInt("audioIndex", -1); // return -1 mean no data found
    }

    public void clearCachedAudioPlaylist() {
        mPreferences = mContext.getSharedPreferences(STORAGE, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = mPreferences.edit();
        editor.clear();
        editor.commit();
    }
}
