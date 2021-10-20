package eu.siacs.conversations.ui.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

import eu.siacs.conversations.R;
import eu.siacs.conversations.entities.Message;

public class StreamChatAdapter extends RecyclerView.Adapter<StreamChatAdapter.StreamChatViewHolder> {

    private List<Message> messageList = new ArrayList<>();

    class StreamChatViewHolder extends RecyclerView.ViewHolder {

        ImageView iv_user_profile;
        TextView tv_user_name;
        TextView tv_message;

        public StreamChatViewHolder(@NonNull View itemView) {
            super(itemView);
            iv_user_profile = itemView.findViewById(R.id.iv_user_profile);
            tv_user_name = itemView.findViewById(R.id.tv_user_name);
            tv_message = itemView.findViewById(R.id.tv_message);
        }
    }

    @NonNull
    @Override
    public StreamChatViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new StreamChatViewHolder(LayoutInflater.from(parent.getContext()).inflate(R.layout.stream_chat, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull StreamChatViewHolder holder, int position) {
//        holder.tv_user_name.setText(messageList.get(position).get);
        holder.tv_message.setText(messageList.get(position).getBody());
    }

    @Override
    public int getItemCount() {
        return messageList.size();
    }

    @Override
    public int getItemViewType(int position) {
        return super.getItemViewType(position);
    }

    public void updateMessage(Message newData) {
        messageList.add(newData);

        notifyDataSetChanged();
    }
}
