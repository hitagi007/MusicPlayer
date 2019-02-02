package itto.pl.musicplayer.service;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.session.MediaSessionManager;
import android.os.Binder;
import android.os.Build;
import android.os.IBinder;
import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.session.MediaControllerCompat;
import android.support.v4.media.session.MediaSessionCompat;
import android.telephony.PhoneStateListener;
import android.telephony.TelephonyManager;
import android.util.Log;

import java.util.ArrayList;

import androidx.core.app.NotificationCompat;
import itto.pl.musicplayer.MainActivity;
import itto.pl.musicplayer.R;
import itto.pl.musicplayer.data.model.Audio;
import itto.pl.musicplayer.utils.MediaUtil.PlaybackStatus;
import itto.pl.musicplayer.utils.StorageUtil;

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

    // Handling incoming phone calls
    private boolean mOnGoingCall = false;
    private PhoneStateListener mPhoneStateListener;
    private TelephonyManager mTelephonyManager;

    // List of available Audio files
    private ArrayList<Audio> mAudioList;
    private int mAudioIndex = -1;
    private Audio mActiveAudio; // an object of the currently playing audio

    public static final String ACTION_PLAY = "itto.pl.musicplayer.ACTION_PLAY";
    public static final String ACTION_PAUSE = "itto.pl.musicplayer.ACTION_PAUSE";
    public static final String ACTION_PREVIOUS = "itto.pl.musicplayer.ACTION_PREVIOUS";
    public static final String ACTION_NEXT = "itto.pl.musicplayer.ACTION_NEXT";
    public static final String ACTION_STOP = "itto.pl.musicplayer.ACTION_STOP";

    public static final String NOTIFICATION_CHANNEL = "itto.channel";

    // MediaSession
    // MediaSession allows interaction with media controllers, volume keys, media buttons, and transport controls.
    private MediaSessionManager mMediaSessionManager;
    private MediaSessionCompat mMediaSession;
    private MediaControllerCompat.TransportControls mTransportControls;

    // AudioPlayer notification ID
    private static final int NOTIFICATION_ID = 101;


    @Override
    public IBinder onBind(Intent intent) {
        return new LocalBinder();
    }

    /**
     * The onStartCommand() handles the initialization of the MediaPlayer
     * and the focus request to make sure there are no other apps playing media.
     * <p>
     * This method will handle the initialization of the MediaSession,
     * the MediaPlayer, loading the cached audio playlist and building the MediaStyle notification
     */
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "onStartCommand: ");
        try {
            // LOad data from SharedPReferences
            StorageUtil storageUtil = new StorageUtil(getApplicationContext());

            mAudioList = storageUtil.loadAudio();
            mAudioIndex = storageUtil.loadAudioIndex();

            if (mAudioIndex != -1 && mAudioIndex < mAudioList.size()) {
                // index is is a valid range
                mActiveAudio = mAudioList.get(mAudioIndex);
            } else {
                stopSelf();
            }
        } catch (NullPointerException e) {
            stopSelf();
        }
        // Request audio focus
        if (requestAudioFocus() == false) {
            // Could not gain focus
            stopSelf();
        }

        if (mMediaSessionManager == null) {
            try {
                initMediaSession();
                initMediaPlayer();
                buildNotification(PlaybackStatus.PLAYING, true);
            } catch (Exception e) {
                e.printStackTrace();
                stopSelf();
            }

        }

        // Handle Intent action from MediaSession.TransportControls
        handleIncomingActions(intent);

        return START_NOT_STICKY;
    }

    /**
     * The onDestroy() method also releases audio focus, this is more of a personal choice.
     * If you release the focus in this method the MediaPlayerService will have audio focus until destroyed,
     * if there are no interruptions from other media apps for audio focus.
     * <p>
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
        releaseNotification();
        removeAudioFocus();

        //You must unregister all the registered BroadcastReceivers when they are not needed anymore.
        // This happens in the Services onDestroy() method

        // Disable the PhoneStateListener
        if (mPhoneStateListener != null) {
            mTelephonyManager.listen(mPhoneStateListener, PhoneStateListener.LISTEN_NONE);
        }

        removeNotification();

        // Unregister BroadcastReceivers
        unregisterReceiver(mBecomingNoisyReceiver);
        unregisterReceiver(mPlayeNewAudio);

        // clear cached playlist
        new StorageUtil(getApplicationContext()).clearCachedAudioPlaylist();

    }

    private void releaseNotification() {
        NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        manager.cancel(NOTIFICATION_ID);
    }

    @Override
    public void onCreate() {
        super.onCreate();
        // Perform one-time setup procedures


        // Manage incoming calls during playback
        // Pause MediaPlayer on incoming call
        // Resume on hangup.
        callStateListener();
        // ACTION_AUDIO_BECOME_NOISY -- chane in audio outputs -- BroadcastReceiver
        registerBecomingNoisyReceiver();
        // Listen for new Audio to play -- BroadcastReceiver
        registerPlayNewAudio();
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
        Log.d(TAG, "onPrepared: ");
        //Invoked when the media source is ready for playback
        playMedia();
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
        Log.d(TAG, "initMediaPlayer: ");
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
            mMediaPlayer.setDataSource(mActiveAudio.getData());
        } catch (Exception e) {
            e.printStackTrace();
            Log.e(TAG, "Error in initMediaPlayer: \n" + e.toString());
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

    private void pauseMedia() {
        if (mMediaPlayer.isPlaying()) {
            mMediaPlayer.pause();
            mResumePosition = mMediaPlayer.getCurrentPosition();
        }
    }

    private void resumeMedia() {
        if (!mMediaPlayer.isPlaying()) {
            mMediaPlayer.seekTo(mResumePosition);
            mMediaPlayer.start();
        }
    }

    // Becoming noisy
    private BroadcastReceiver mBecomingNoisyReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            // pause audio on ACTION_AUDIO_BECOMING_NOISY
            pauseMedia();
            buildNotification(PlaybackStatus.PAUSED, true);
        }
    };

    private void registerBecomingNoisyReceiver() {
        // register after getting audio focus
        IntentFilter intentFilter = new IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY);
        registerReceiver(mBecomingNoisyReceiver, intentFilter);
    }

    // Handle incoming phone calls
    private void callStateListener() {
        // Get the telephony manager
        mTelephonyManager = (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);
        // Starting listening for PhoneState changes
        mPhoneStateListener = new PhoneStateListener() {
            @Override
            public void onCallStateChanged(int state, String phoneNumber) {
                switch (state) {
                    // If at least one call exists or the phone is ringing
                    // pause the MEdiaPlayer
                    case TelephonyManager.CALL_STATE_OFFHOOK:
                    case TelephonyManager.CALL_STATE_RINGING:
                        if (mMediaFile != null) {
                            pauseMedia();
                            mOnGoingCall = true;
                        }
                        break;
                    case TelephonyManager.CALL_STATE_IDLE:
                        // Phone idle. Start playing
                        if (mMediaFile != null) {
                            if (mOnGoingCall) {
                                mOnGoingCall = false;
                                resumeMedia();
                            }
                        }
                        break;
                }
            }
        };

        // Register the listener with the telephony manager
        // Listen for changes to device call state
        mTelephonyManager.listen(mPhoneStateListener, PhoneStateListener.LISTEN_CALL_STATE);
    }

    private BroadcastReceiver mPlayeNewAudio = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            // Get the new media index from SharedPreferences
            mAudioIndex = new StorageUtil(getApplicationContext()).loadAudioIndex();
            if (mAudioIndex != -1 && mAudioIndex < mAudioList.size()) {
                // index is in a valid range
                mActiveAudio = mAudioList.get(mAudioIndex);
            } else {
                stopSelf();
            }

            // A PLAY_NEW_AUDIO aciton received
            // reset mediaplayer to play new audio
            stopMedia();
            mMediaPlayer.reset();
            initMediaPlayer();
            updateMetaData();
            buildNotification(PlaybackStatus.PLAYING, true);
        }
    };

    private void registerPlayNewAudio() {
        // Register playMedia receiver
        IntentFilter filter = new IntentFilter(MainActivity.BROADCAST_PLAY_NEW_AUDIO);
        registerReceiver(mPlayeNewAudio, filter);
    }

    private void initMediaSession() {
        if (mMediaSessionManager != null) {
            return; // MediaSessionManager exists
        }
        mMediaSessionManager = (MediaSessionManager) getSystemService(Context.MEDIA_SESSION_SERVICE);
        // Crete a new MediaSession
        mMediaSession = new MediaSessionCompat(getApplicationContext(), "AudioPlayer");

        // Get MediaSessions transport controls
        mTransportControls = mMediaSession.getController().getTransportControls();

        // Set MEdiaSession -> ready to receive media commands
        mMediaSession.setActive(true);

        // Indicate that the MediaSession handles transport control commands
        // through its MediaSessionCompat.Callback
        mMediaSession.setFlags(MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS);

        // Set mediaSession's MetaData
        updateMetaData();

        // Attach Callback to receive MediaSession updates
        mMediaSession.setCallback(new MediaSessionCompat.Callback() {
            // Implement callbacks

            @Override
            public void onPlay() {
                super.onPlay();
                resumeMedia();
                buildNotification(PlaybackStatus.PLAYING, true);
            }

            @Override
            public void onPause() {
                super.onPause();
                pauseMedia();
                buildNotification(PlaybackStatus.PAUSED, false);
            }

            @Override
            public void onSkipToNext() {
                super.onSkipToNext();
                skipToNext();
                updateMetaData();
                buildNotification(PlaybackStatus.PLAYING, true);
            }

            @Override
            public void onStop() {
                super.onStop();
                removeNotification();
                // Stop the service
                stopSelf();
            }

            @Override
            public void onSeekTo(long pos) {
                super.onSeekTo(pos);
            }
        });
    }

    private void updateMetaData() {
        Bitmap albumArt = BitmapFactory.decodeResource(getResources(), R.drawable.ic_album); // replace with medias album art
        // Update the current metadata
        mMediaSession.setMetadata(new MediaMetadataCompat.Builder()
                .putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, albumArt)
                .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, mActiveAudio.getArtist())
                .putString(MediaMetadataCompat.METADATA_KEY_ALBUM, mActiveAudio.getAlbum())
                .putString(MediaMetadataCompat.METADATA_KEY_TITLE, mActiveAudio.getTitle())
                .build());
    }

    private void skipToNext() {
        if (mAudioIndex == mAudioList.size() - 1) {
            // if last in playlist
            mAudioIndex = 0;
            mActiveAudio = mAudioList.get(mAudioIndex);
        } else {
            // get next in playlist
            mActiveAudio = mAudioList.get(++mAudioIndex);
        }

        // Update stored index
        new StorageUtil(getApplicationContext()).storeAudioIndex(mAudioIndex);

        stopMedia();
        // Reset Media Player
        mMediaPlayer.reset();
        initMediaPlayer();
    }

    private void skipToPrevious() {
        if (mAudioIndex == 0) {
            // If first in playlist
            // set index to the last of audioList
            mAudioIndex = mAudioList.size() - 1;
            mActiveAudio = mAudioList.get(mAudioIndex);
        } else {
            // get previous in playlist
            mActiveAudio = mAudioList.get(--mAudioIndex);
        }

        // Update stored index
        new StorageUtil(getApplicationContext()).storeAudioIndex(mAudioIndex);

        stopMedia();
        // Reset mediaplayer
        mMediaPlayer.reset();
        initMediaPlayer();
    }

    private void buildNotification(PlaybackStatus playbackStatus, boolean onGoing) {
        int notificationAction = android.R.drawable.ic_media_pause;
        PendingIntent play_pauseAction = null;

        // Build a new notification according to the current state of the MediaPlayer
        if (playbackStatus == PlaybackStatus.PLAYING) {
            notificationAction = android.R.drawable.ic_media_pause;
            // create the pause action
            play_pauseAction = playbackAction(1);
        } else if (playbackStatus == PlaybackStatus.PAUSED) {
            notificationAction = android.R.drawable.ic_media_play;
            // create the play action
            play_pauseAction = playbackAction(0);
        }


        Bitmap largeIcon = BitmapFactory.decodeResource(getResources(), R.drawable.ic_album); //replace with your own image

        NotificationManager notificationManager = ((NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE));

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel notificationChannel =
                    new NotificationChannel(NOTIFICATION_CHANNEL, "Itto",
                            NotificationManager.IMPORTANCE_DEFAULT);
            notificationManager.createNotificationChannel(notificationChannel);
        }


        // Create a new Notification
        NotificationCompat.Builder notificationBuilder = new NotificationCompat.Builder(this, NOTIFICATION_CHANNEL)
                .setShowWhen(false)
                // Set the Notification style
                .setStyle(new androidx.media.app.NotificationCompat.MediaStyle()
                                // Attach our MediaSession token)
                                .setMediaSession(mMediaSession.getSessionToken())
                                // Show our playback controls in thge compact notification view)
                                .setShowActionsInCompactView(0, 1, 2)
                        // Set the Notification color
//                        .setColor(getResources().getColor(R.color.colorPrimary))

                )
                .setOngoing(onGoing)
                // Set the large and small icons
                .setLargeIcon(largeIcon)
                .setSmallIcon(android.R.drawable.stat_sys_headset)
                // Set Notification content information
                .setContentText(mActiveAudio.getArtist())
                .setContentTitle(mActiveAudio.getAlbum())
                .setContentInfo(mActiveAudio.getTitle())
                // Add playback actions
                .addAction(android.R.drawable.ic_media_previous, "previous", playbackAction(3))
                .addAction(notificationAction, "pause", play_pauseAction)
                .addAction(android.R.drawable.ic_media_next, "next", playbackAction(2));
        notificationManager.notify(NOTIFICATION_ID,
                notificationBuilder.build());

    }

    private void removeNotification() {
        NotificationManager notificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        notificationManager.cancel(NOTIFICATION_ID);
    }

    private PendingIntent playbackAction(int actionNumber) {
        Intent playbackAction = new Intent(this, MediaPlayerService.class);
        switch (actionNumber) {
            case 0: //Play
                playbackAction.setAction(ACTION_PLAY);
                return PendingIntent.getService(this, actionNumber, playbackAction, 0);
            case 1: //Pause
                playbackAction.setAction(ACTION_PAUSE);
                return PendingIntent.getService(this, actionNumber, playbackAction, 0);
            case 2: // Next track
                playbackAction.setAction(ACTION_NEXT);
                return PendingIntent.getService(this, actionNumber, playbackAction, 0);
            default:
                break;
        }
        return null;
    }

    private void handleIncomingActions(Intent playbackAction) {
        if (playbackAction == null || playbackAction.getAction() == null) {
            return;
        }
        Log.d(TAG, "handleIncomingActions: " + playbackAction.getAction());
        String actionString = playbackAction.getAction();
        if (actionString.equalsIgnoreCase(ACTION_PLAY)) {
            mTransportControls.play();
        } else if (actionString.equalsIgnoreCase(ACTION_PAUSE)) {
            mTransportControls.pause();
        } else if (actionString.equalsIgnoreCase(ACTION_NEXT)) {
            mTransportControls.skipToNext();
        } else if (actionString.equalsIgnoreCase(ACTION_PREVIOUS)) {
            mTransportControls.skipToPrevious();
        } else if (actionString.equalsIgnoreCase(ACTION_STOP)) {
            mTransportControls.stop();
        }

    }

    public int getSessionId() {
        if (mMediaPlayer != null) {
            return mMediaPlayer.getAudioSessionId();
        }
        return 0;
    }
}



























