package app.cash.tempest2.musiclibrary.java;

import app.cash.tempest2.Attribute;
import java.time.Duration;
import java.time.LocalDate;

public class AlbumInfoOrTrack {

  @Attribute(name = "partition_key")
  public final String album_token;
  @Attribute(noPrefix = true)
  public final String sort_key;
  public final String album_title;
  public final String artist_name;
  public final LocalDate release_date;
  public final String genre_name;
  public final String track_title;
  public final Duration run_length;
  public final transient Key key;

  public AlbumInfoOrTrack(
      String album_token,
      String sort_key,
      String album_title,
      String artist_name,
      LocalDate release_date,
      String genre_name,
      String track_title,
      Duration run_length) {
    this.album_token = album_token;
    this.sort_key = sort_key;
    this.album_title = album_title;
    this.artist_name = artist_name;
    this.release_date = release_date;
    this.genre_name = genre_name;
    this.track_title = track_title;
    this.run_length = run_length;
    key = new Key(album_token);
  }

  public AlbumInfo albumInfo() {
    if (sort_key.equals("INFO_")) {
      return new AlbumInfo(album_token, album_title, artist_name, release_date, genre_name);
    }
    return null;
  }

  public AlbumTrack albumTrack() {
    if (sort_key.startsWith("TRACK_")) {
      return new AlbumTrack(album_token, sort_key.substring("TRACK_".length()), track_title, run_length);
    }
    return null;
  }

  public static class Key {
    public final String album_token;
    public final String sort_key = "";

    public Key(String album_token) {
      this.album_token = album_token;
    }
  }
}
