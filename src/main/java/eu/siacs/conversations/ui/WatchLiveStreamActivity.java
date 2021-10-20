package eu.siacs.conversations.ui;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.view.Gravity;
import android.view.View;
import android.widget.Toast;

import androidx.databinding.DataBindingUtil;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.bumptech.glide.Glide;
import com.google.android.exoplayer2.DefaultLoadControl;
import com.google.android.exoplayer2.DefaultRenderersFactory;
import com.google.android.exoplayer2.MediaItem;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.SimpleExoPlayer;
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector;
import com.google.android.exoplayer2.trackselection.TrackSelector;
import com.google.android.exoplayer2.util.MimeTypes;

import java.util.UUID;

import eu.siacs.conversations.R;
import eu.siacs.conversations.databinding.ActivityWatchLiveStreamBinding;
import eu.siacs.conversations.entities.Conversation;
import eu.siacs.conversations.entities.Message;
import eu.siacs.conversations.services.XmppConnectionService;
import eu.siacs.conversations.ui.adapter.StreamChatAdapter;
import eu.siacs.conversations.xmpp.chatstate.ChatState;

public class WatchLiveStreamActivity extends XmppActivity implements XmppConnectionService.OnStreamChatMessageReceived {

    Player player;
    Conversation mConversation;
    ActivityWatchLiveStreamBinding binding;

    StreamChatAdapter streamChatAdapter = new StreamChatAdapter();

    @Override
    protected void refreshUiReal() {

    }

    @Override
    void onBackendConnected() {
        xmppConnectionService.setonStreamChatMessageReceivedListener(WatchLiveStreamActivity.this);
        String uuid = getIntent().getExtras().getString("uuid");
        if (uuid != null && xmppConnectionService != null) {
            for (Conversation conversation : xmppConnectionService.getConversations()) {
                if (conversation.getJid().asBareJid().toString().equals(uuid))
                    this.mConversation = conversation;
            }
        }
        if (mConversation == null) {
            muxToast("No Conversation found in this activity");
            return;
        } else
            initializeChatView();

        TrackSelector trackSelector = new DefaultTrackSelector(this);
//        DefaultLoadControl.Builder().setBufferDurationsMs(minBufferMS, maxBufferMs, bufferForPlaybackMS, bufferForPlaybackAfterRebufferMs).createDefaultLoadControl();
        DefaultLoadControl loadControl = new DefaultLoadControl.Builder().setBufferDurationsMs(40 * 1000, 60 * 1000, 40 * 1000, 40 * 1000).build();
        @DefaultRenderersFactory.ExtensionRendererMode int extensionRendererMode = DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER;
        DefaultRenderersFactory renderersFactory = new DefaultRenderersFactory(this).setExtensionRendererMode(extensionRendererMode);
        player = new SimpleExoPlayer.Builder(this, renderersFactory).setTrackSelector(trackSelector).setLoadControl(loadControl).build();
        MediaItem mediaItem = new MediaItem.Builder()
                .setUri(getIntent().getExtras().getString("liveStreamLink"))
                .setMimeType(MimeTypes.APPLICATION_M3U8)
                .build();
        player.setMediaItem(mediaItem);
        player.prepare();
        player.play();
        binding.videoView.setPlayer(player);
    }

    @Override
    public void onPause() {
        super.onPause();
        if (player != null)
            player.setPlayWhenReady(false);
    }

    @Override
    public void onResume() {
        super.onResume();
        if (player != null)
            player.setPlayWhenReady(true);
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (player != null) {
            player.setPlayWhenReady(false);
            player.release();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (player != null) {
            player.setPlayWhenReady(false);
            player.release();
        }
    }

    @Override
    public void onStreamChatMessageReceived(Message message) {
        runOnUiThread(() -> {
            //listen for uuid of a stream chat
            message.setEncryption(Message.ENCRYPTION_NONE);
            if (!message.getBody().equals("joiintlike"))
                streamChatAdapter.updateMessage(message);
        });
    }

    @Override
    public void onStreamLiked() {
        runOnUiThread(() -> {
            binding.ivLike.setVisibility(View.VISIBLE);
            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                binding.ivLike.setVisibility(View.GONE);
            }, 5000);
            Glide.with(this).load(R.drawable.ic_heart).into(binding.ivLike);
        });
    }

    @Override
    public void onUuidReceived(String uuid) {

    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (xmppConnectionService == null)
            connectToBackend();
        binding = DataBindingUtil.setContentView(this, R.layout.activity_watch_live_stream);
        binding.textSendButton.setOnClickListener(view -> {
            sendMessage();
        });
    }

    private void sendLikeToAll() {
        if (mConversation == null) {
            //request for uuid
            return;
        }
        mConversation.setOutgoingChatState(ChatState.ACTIVE);
        xmppConnectionService.sendChatState(mConversation);

        final String body = "joiintlike";
        final Message message;
        if (mConversation.getCorrectingMessage() == null) {
            message = new Message(mConversation, body, mConversation.getNextEncryption());
            Message.configurePrivateMessage(message);
        } else {
            message = mConversation.getCorrectingMessage();
            message.setBody("joiintlike");
            message.putEdited(message.getUuid(), message.getServerMsgId());
            message.setServerMsgId(null);
            message.setUuid(UUID.randomUUID().toString());
        }
        message.setEncryption(Message.ENCRYPTION_NONE);
        xmppConnectionService.sendMessage(message);
    }

    // Little wrapper to relocate and re-pad the toast a little
    private void muxToast(String message) {
        Toast t = Toast.makeText(this, message, Toast.LENGTH_SHORT);
        t.setGravity(Gravity.TOP | Gravity.CENTER_HORIZONTAL, 0, 5);
        t.show();
    }

    private void sendMessage() {
        if (mConversation == null) {
            //request for uuid
            return;
        }
        mConversation.setOutgoingChatState(ChatState.ACTIVE);
        xmppConnectionService.sendChatState(mConversation);

        final Editable text = this.binding.textinput.getText();
        final String body = text == null ? "" : text.toString();
        if (body.length() == 0 || mConversation == null) {
            return;
        }
        final Message message;
        if (mConversation.getCorrectingMessage() == null) {
            message = new Message(mConversation, body, mConversation.getNextEncryption());
            Message.configurePrivateMessage(message);
        } else {
            message = mConversation.getCorrectingMessage();
            message.setBody(body);
            message.putEdited(message.getUuid(), message.getServerMsgId());
            message.setServerMsgId(null);
            message.setUuid(UUID.randomUUID().toString());
        }
        message.setEncryption(Message.ENCRYPTION_NONE);
        xmppConnectionService.sendMessage(message);
        streamChatAdapter.updateMessage(message);
        messageSent();
    }

    protected void messageSent() {
        this.binding.textinput.setText("");
        if (mConversation.setCorrectingMessage(null)) {
            this.binding.textinput.append(mConversation.getDraftMessage());
            mConversation.setDraftMessage(null);
        }
    }

    private void initializeChatView() {
        binding.rvSteamChat.setLayoutManager(new LinearLayoutManager(this));
        binding.rvSteamChat.setAdapter(streamChatAdapter);

        binding.ivLike.setOnClickListener(view -> {
            sendLikeToAll();
            binding.ivLike.setImageResource(R.drawable.ic_like);
            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                binding.ivLike.setImageResource(R.drawable.ic_un_like);
            }, 2000);
        });
    }
}
