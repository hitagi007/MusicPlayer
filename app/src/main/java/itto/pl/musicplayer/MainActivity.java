package itto.pl.musicplayer;

import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.database.Cursor;
import android.net.Uri;
import android.os.IBinder;
import android.provider.MediaStore;

import androidx.appcompat.app.AppCompatActivity;

import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;

import java.util.ArrayList;

import itto.pl.music_visualizer.VerticalBarVisualizer;
import itto.pl.musicplayer.data.model.Audio;
import itto.pl.musicplayer.service.MediaPlayerService;
import itto.pl.musicplayer.utils.StorageUtil;

import static itto.pl.musicplayer.utils.Constants.TAGG;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = TAGG + MainActivity.class.getSimpleName();
    private MediaPlayerService mPlayerService;
    private boolean mServiceBound = false;
    private ArrayList<Audio> mAudioList;
    public static final String BROADCAST_PLAY_NEW_AUDIO = "itto.pl.musicplayer.PlayNewAudio";
    VerticalBarVisualizer mVisualizer;

    private ServiceConnection mServiceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            Log.d(TAG, "onServiceConnected: ");
            // We've bound to localService, cast the Ibinder and get LocalService instance
            MediaPlayerService.LocalBinder binder = (MediaPlayerService.LocalBinder) service;
            mPlayerService = binder.getService();
            mServiceBound = true;
            Toast.makeText(MainActivity.this, "Service Bound", Toast.LENGTH_SHORT).show();
            Log.d(TAG, "Service Audio Session ID: "+mPlayerService.getSessionId());
            mVisualizer.setPlayer(mPlayerService.getSessionId());
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            Log.d(TAG, "onServiceDisconnected: ");
            mServiceBound = false;
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.frag_now_playing);
        findViewById(R.id.now_playing_img_preview).setClipToOutline(true);
        mVisualizer = findViewById(R.id.now_playing_visualizer);
        mVisualizer.setColor(getColor(R.color.colorAccent));
        loadAudio();
        playAudio(0);
//        playAudio("https://upload.wikimedia.org/wikipedia/commons/6/6c/Grieg_Lyric_Pieces_Kobold.ogg");

    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        outState.putBoolean("ServiceState", mServiceBound);
        super.onSaveInstanceState(outState);
    }

    @Override
    protected void onRestoreInstanceState(Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);
        mServiceBound = savedInstanceState.getBoolean("ServiceState");
    }

    @SuppressWarnings("unused")
    private void playAudio(String media) {
        //Check is service is active
        if (!mServiceBound) {
            Intent playerIntent = new Intent(this, MediaPlayerService.class);
            playerIntent.putExtra("media", media);
            startService(playerIntent);
            bindService(playerIntent, mServiceConnection, Context.BIND_AUTO_CREATE);
        } else {
            // Service is active
            // Send media with BroadcastReceiver
        }
    }

    private void playAudio(int audioIndex) {
        // Check is service is active
        if (!mServiceBound) {
            // Store Serialized audioList to SharedPreferences
            StorageUtil storageUtil = new StorageUtil(getApplicationContext());
            storageUtil.storeAudio(mAudioList);
            storageUtil.storeAudioIndex(audioIndex);

            Intent playerIntent = new Intent(this, MediaPlayerService.class);
            startService(playerIntent);
            bindService(playerIntent, mServiceConnection, Context.BIND_AUTO_CREATE);
        } else {
            // Store the new audio Index to SharedPreferences
            StorageUtil storageUtil = new StorageUtil(getApplicationContext());
            storageUtil.storeAudioIndex(audioIndex);

            // Service is active
            // Send a broadcast to the service -> PLAY_NEW_AUDIO
            Intent broadcastIntent = new Intent(BROADCAST_PLAY_NEW_AUDIO);
            sendBroadcast(broadcastIntent);
        }
    }

    private void loadAudio() {
        ContentResolver contentResolver = getContentResolver();
        Uri uri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
        String selection = MediaStore.Audio.Media.IS_MUSIC + "!=0";
        String sortOrder = MediaStore.Audio.Media.TITLE + " ASC";
        Cursor cursor = contentResolver.query(uri, null, selection, null, sortOrder);

        if (cursor != null && cursor.getCount() > 0) {
            mAudioList = new ArrayList<>();
            while (cursor.moveToNext()) {
                String data = cursor.getString(cursor.getColumnIndex(MediaStore.Audio.Media.DATA));
                Log.d(TAG, "loadAudio: ");
                String title = cursor.getString(cursor.getColumnIndex(MediaStore.Audio.Media.TITLE));
                String album = cursor.getString(cursor.getColumnIndex(MediaStore.Audio.Media.ALBUM));
                String artist = cursor.getString(cursor.getColumnIndex(MediaStore.Audio.Media.ARTIST));

                // Save to audio list
                mAudioList.add(new Audio(data, title, album, artist));
            }
        }

        cursor.close();
    }

}
