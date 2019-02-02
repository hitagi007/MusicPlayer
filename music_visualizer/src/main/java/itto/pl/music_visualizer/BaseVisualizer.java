package itto.pl.music_visualizer;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Paint;
import android.media.audiofx.Visualizer;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;

public abstract class BaseVisualizer extends View {
    private static final String TAG = "PL_itto." + BaseVisualizer.class.getSimpleName();
    protected Paint mPaint;
    protected byte[] mBytes;
    protected Visualizer mVisualizer;
    protected int mColor = Color.BLUE;

    public BaseVisualizer(Context context) {
        this(context, null);
    }

    public BaseVisualizer(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public BaseVisualizer(Context context, AttributeSet attrs, int defStyleAttr) {
        this(context, attrs, defStyleAttr, 0);
    }

    public BaseVisualizer(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        init(null);
        init();
    }

    private void init(AttributeSet attributeSet) {
        mPaint = new Paint();
    }

    public abstract void init();

    /**
     * Set the color to visualizer by resource id
     *
     * @param color
     */
    public void setColor(int color) {
        mColor = color;
        mPaint.setColor(mColor);
    }

    /**
     * Set current MediaPlayer by the sessionID
     *
     * @param sessionId
     */
    public void setPlayer(int sessionId) {
        try {
            mVisualizer = new Visualizer(sessionId);
            mVisualizer.setCaptureSize(Visualizer.getCaptureSizeRange()[1]);
            mVisualizer.setDataCaptureListener(new Visualizer.OnDataCaptureListener() {
                @Override
                public void onWaveFormDataCapture(Visualizer visualizer, byte[] waveform, int samplingRate) {
                    Log.d(TAG, "onWaveFormDataCapture: " + waveform.length);
                    mBytes = waveform;
                    invalidate();
                }

                @Override
                public void onFftDataCapture(Visualizer visualizer, byte[] fft, int samplingRate) {
                }
            }, Visualizer.getMaxCaptureRate() / 2, true, false);
            mVisualizer.setEnabled(true);
        } catch (Exception e) {
            Log.e(TAG, "setPlayer Error: " + e.toString());
        }


    }

    public void release() {
        mVisualizer.release();
    }

    public Visualizer getVisualizer() {
        return mVisualizer;
    }
}
