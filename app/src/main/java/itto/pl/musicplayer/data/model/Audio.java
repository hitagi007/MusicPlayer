package itto.pl.musicplayer.data.model;

import java.io.Serializable;

public class Audio implements Serializable {
    private String mData;
    private String mTitle;
    private String mAlbum;
    private String mArtist;

    public Audio(String data, String title, String album, String artist) {
        mData = data;
        mTitle = title;
        mAlbum = album;
        mArtist = artist;
    }

    public String getData() {
        return mData;
    }

    public void setData(String data) {
        mData = data;
    }

    public String getTitle() {
        return mTitle;
    }

    public void setTitle(String title) {
        mTitle = title;
    }

    public String getAlbum() {
        return mAlbum;
    }

    public void setAlbum(String album) {
        mAlbum = album;
    }

    public String getArtist() {
        return mArtist;
    }

    public void setArtist(String artist) {
        mArtist = artist;
    }
}
