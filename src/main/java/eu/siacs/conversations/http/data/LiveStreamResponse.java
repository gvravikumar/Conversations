package eu.siacs.conversations.http.data;

public class LiveStreamResponse {
    String _playback_url;
    String _skey;

    public String get_playback_url() {
        return _playback_url;
    }

    public void set_playback_url(String _playback_url) {
        this._playback_url = _playback_url;
    }

    public String get_skey() {
        return _skey;
    }

    public void set_skey(String _skey) {
        this._skey = _skey;
    }
}