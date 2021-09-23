package eu.siacs.conversations.http.services;


import eu.siacs.conversations.http.data.LiveStreamResponse;
import retrofit2.Call;
import retrofit2.http.POST;


public interface JoiintNetworkService {

    @POST("/api/livestream?audio_only=true")
    Call<LiveStreamResponse> getSecretLiveStreamKeyWithOnlyAudio();

    @POST("/api/livestream?audio_only=false")
    Call<LiveStreamResponse> getSecretLiveStreamKeyWithAudioVideo();
}