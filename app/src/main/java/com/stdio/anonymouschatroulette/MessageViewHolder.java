package com.stdio.anonymouschatroulette;

import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.recyclerview.widget.RecyclerView;

public class MessageViewHolder extends RecyclerView.ViewHolder {
    TextView tvMessage;
    TextView tvMessageLeft;
    FrameLayout flMessage, flMessageLeft, flImageLayout, flImageLayoutLeft;
    ImageView messageImageView, messageImageViewLeft;

    public MessageViewHolder(View v) {
        super(v);
        tvMessage = itemView.findViewById(R.id.textMessage);
        tvMessageLeft = itemView.findViewById(R.id.textMessageLeft);
        flMessage = itemView.findViewById(R.id.flMessage);
        flMessageLeft = itemView.findViewById(R.id.flMessageLeft);
        flImageLayout = itemView.findViewById(R.id.imageLayout);
        flImageLayoutLeft = itemView.findViewById(R.id.imageLayoutLeft);
        messageImageView = itemView.findViewById(R.id.imageView);
        messageImageViewLeft = itemView.findViewById(R.id.imageViewLeft);
    }
}