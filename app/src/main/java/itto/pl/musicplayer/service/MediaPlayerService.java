package itto.pl.musicplayer.service;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.os.Binder;
import android.os.IBinder;
import android.text.TextUtils;
import android.util.Log;

import static itto.pl.musicplayer.utils.Constants.TAGG;

public class MediaPlayerService extends Service implements MediaPlayer.OnCompletionListener,
        MediaPlayer.OnPreparedListener, MediaPlayer.OnErrorListener, MediaPlayer.OnSeekCompleteListener,
        MediaPlayer.OnInfoListener, MediaPlayer.OnBufferingUpdateListener, AudioManager.OnAudioFocusChangeListener {
    private static final String TAG = TAGG + MediaPlayerService.class.getSimpleName();

    // Binder given to clients
    private final IBinder mBinder = new LocalBinder();

    private MediaPlayer mMediaPlayer;

    // path to the audio file
    private String mMediaFile;

    // User to pause/resume MediaPlayer
    private int mResumePosition;

    private AudioManager mAudioManager;

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    /**
     * The onStartCommand() handles the initialization of the MediaPlayer
     * and the focus request to make sure there are no other apps playing media.
     */
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        try {
            mMediaFile = intent.getExtras().getString("media");
        } catch (NullPointerException e) {
            stopSelf();
        }

        // Request audio focus
        if (!requestAudioFocus()) {
            // Could not gain focus
            stopSelf();
        }

        if (!TextUtils.isEmpty(mMediaFile)) {
            initMediaPlayer();
        }

        return super.onStartCommand(intent, flags, startId);
    }

    /**
     * The onDestroy() method also releases audio focus, this is more of a personal choice.
     * If you release the focus in this method the MediaPlayerService will have audio focus until destroyed,
     * if there are no interruptions from other media apps for audio focus.
     *
     * If you want a more dynamic focus control, you can request audio focus when new media
     * starts playing and release it in the {@link #onCompletion} method,
     * so the service will have focus control only while playing something.
     */
    @Override
    public void onDestroy() {
        super.onDestroy();
        if (mMediaPlayer != null) {
            stopMedia();
            mMediaPlayer.release();
        }

        removeAudioFocus();
    }

    @Override
    public void onCompletion(MediaPlayer mp) {
        //Invoked when playback of a media source has completed.
        stopMedia();
        // stop the service
        stopSelf();
    }

    //Handle errors
    @Override
    public boolean onError(MediaPlayer mp, int what, int extra) {
        // Invoked when there has been an error during an asynchronous operation
        switch (what) {
            case MediaPlayer.MEDIA_ERROR_NOT_VALID_FOR_PROGRESSIVE_PLAYBACK:
                Log.d("MediaPlayer Error", "MEDIA ERROR NOT VALID FOR PROGRESSIVE PLAYBACK " + extra);
                break;
            case MediaPlayer.MEDIA_ERROR_SERVER_DIED:
                Log.d(TAG, "MEDIA ERROR SERVER DIED " + extra);
                break;
            case MediaPlayer.MEDIA_ERROR_UNKNOWN:
                Log.d(TAG, "MEDIA ERROR UNKNOWN " + extra);
                break;
        }
        return false;
    }

    @Override
    public boolean onInfo(MediaPlayer mp, int what, int extra) {
        //Invoked to communicate some info.
        return false;
    }

    @Override
    public void onPrepared(MediaPlayer mp) {
        //Invoked when the media source is ready for playback
    }

    @Override
    public void onSeekComplete(MediaPlayer mp) {
        //Invoked indicating the completion of a seek operation.
    }

    @Override
    public void onBufferingUpdate(MediaPlayer mp, int percent) {
        //Invoked indicating buffering status of
        //a media resource being streamed over the network.
    }

    @Override
    public void onAudioFocusChange(int focusChange) {
        //Invoked when the audio focus of the system is updated.
        switch (focusChange) {
            case AudioManager.AUDIOFOCUS_GAIN:
                //The service gained audio focus, so it needs to start playing.

                //Resume Playback
                if (mMediaPlayer == null) {
                    initMediaPlayer();
                } else if (!mMediaPlayer.isPlaying()) {
                    mMediaPlayer.start();
                }
                mMediaPlayer.setVolume(1.0f, 1.0f);
                break;
            case AudioManager.AUDIOFOCUS_LOSS:
                //The service lost audio focus, the user probably moved to playing media on another app,
                // so release the media player.

                // Lost focus for an unbounded amount of time: stop playback
                // and release media player
                if (mMediaPlayer.isPlaying()) {
                    mMediaPlayer.stop();
                }
                mMediaPlayer.release();
                mMediaPlayer = null;
                break;
            case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT:
                // Focus lost for a short time, pause the MediaPlayer.

                // Lost focus for a short time, but we have to stop playback
                // We don't release the media player because playback is likely to resume
                if (mMediaPlayer.isPlaying()) {
                    mMediaPlayer.pause();
                }
                break;
            case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK:
                //Lost focus for a short time, probably a notification arrived on the device, lower the playback volume.

                // Lost focus for a short time, but it's ok to keep playing
                // at an attenuated level
                if (mMediaPlayer.isPlaying()) {
                    mMediaPlayer.setVolume(0.1f, 0.1f);
                }
                break;
        }
    }

    private boolean requestAudioFocus() {
        mAudioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        int result = mAudioManager.requestAudioFocus(this, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN);
        if (result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
            // Focus gained
            return true;
        }
        // Could not gain focus
        return false;
    }

    private boolean removeAudioFocus() {
        return AudioManager.AUDIOFOCUS_REQUEST_GRANTED == mAudioManager.abandonAudioFocus(this);
    }

    public class LocalBinder extends Binder {
        public MediaPlayerService getService() {
            return MediaPlayerService.this;
        }
    }

    private void initMediaPlayer() {
        mMediaPlayer = new MediaPlayer();
        // Set up MediaPlayer event listeners
        mMediaPlayer.setOnCompletionListener(this);
        mMediaPlayer.setOnErrorListener(this);
        mMediaPlayer.setOnPreparedListener(this);
        mMediaPlayer.setOnBufferingUpdateListener(this);
        mMediaPlayer.setOnSeekCompleteListener(this);
        mMediaPlayer.setOnInfoListener(this);

        // Reset so that the MediaPlayer is not pointing to another data source
        mMediaPlayer.reset();

        mMediaPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);

        try {
            // Set the data source to the mediaFile Location
            mMediaPlayer.setDataSource(mMediaFile);
        } catch (Exception e) {
            e.printStackTrace();
            stopSelf();
        }
        mMediaPlayer.prepareAsync();
    }

    private void playMedia() {
        if (!mMediaPlayer.isPlaying()) {
            mMediaPlayer.start();
        }
    }

    private void stopMedia() {
        if (mMediaPlayer != null) return;
        if (mMediaPlayer.isPlaying()) {
            mMediaPlayer.stop();
        }
    }

    private void resumeMedia() {
        if (!mMediaPlayer.isPlaying()) {
            mMediaPlayer.seekTo(mResumePosition);
            mMediaPlayer.start();
        }
    }
}
