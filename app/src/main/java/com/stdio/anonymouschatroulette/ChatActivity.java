package com.stdio.anonymouschatroulette;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.bumptech.glide.Glide;
import com.bumptech.glide.request.RequestOptions;
import com.firebase.ui.database.FirebaseRecyclerAdapter;
import com.firebase.ui.database.FirebaseRecyclerOptions;
import com.firebase.ui.database.SnapshotParser;
import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.InterstitialAd;
import com.google.android.gms.ads.MobileAds;
import com.google.android.gms.ads.initialization.InitializationStatus;
import com.google.android.gms.ads.initialization.OnInitializationCompleteListener;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.ChildEventListener;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.Query;
import com.google.firebase.database.ValueEventListener;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.OnProgressListener;
import com.google.firebase.storage.StorageReference;
import com.google.firebase.storage.UploadTask;

public class ChatActivity extends AppCompatActivity implements GoogleApiClient.OnConnectionFailedListener {

    private static final String TAG = "MainActivity";
    public static final String MESSAGES_CHILD = "messages";
    private static final int REQUEST_IMAGE = 2;
    private static final String LOADING_IMAGE_URL = "https://i.ibb.co/QM8XrF2/placeholder.jpg";
    public static final String ANONYMOUS = "anonymous";
    private String mUsername;
    private String mPhotoUrl;
    private RecyclerView mMessageRecyclerView;
    private LinearLayoutManager mLinearLayoutManager;
    private EditText mMessageEditText;
    private FirebaseUser mFirebaseUser;
    private DatabaseReference userRef;
    private DatabaseReference interlocutorRef;
    private FirebaseRecyclerAdapter<FriendlyMessage, MessageViewHolder> mFirebaseAdapter;
    ProgressDialog dialog;
    FirebaseDatabase database;
    boolean dialogIsStopped = false;
    InterstitialAd mInterstitialAd;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_chat);

        MobileAds.initialize(this, new OnInitializationCompleteListener() {
            @Override
            public void onInitializationComplete(InitializationStatus initializationStatus) {
            }
        });
        mInterstitialAd = new InterstitialAd(this);
        mInterstitialAd.setAdUnitId("ca-app-pub-9390944171328953/6799366627");
        mInterstitialAd.loadAd(new AdRequest.Builder().build());
        // Set default username is anonymous.
        mUsername = ANONYMOUS;

        auth();
        initRV();
        database = FirebaseDatabase.getInstance();
        userRef = database.getReference(mFirebaseUser.getUid());
        findCompanion();
        mMessageEditText = findViewById(R.id.messageEditText);
    }

    private void findCompanion() {

        Query myQuery = userRef.child("interlocutor");
        myQuery.addChildEventListener(new ChildEventListener() {
            @Override
            public void onChildAdded(DataSnapshot dataSnapshot, String s) {
                String uid = dataSnapshot.getValue(String.class);
                if (!uid.equals(mFirebaseUser.getUid())) {
                    interlocutorRef = database.getReference(uid);
                    SnapshotParser<FriendlyMessage> parser = new SnapshotParser<FriendlyMessage>() {
                        @Override
                        public FriendlyMessage parseSnapshot(DataSnapshot dataSnapshot) {
                            FriendlyMessage friendlyMessage = dataSnapshot.getValue(FriendlyMessage.class);
                            if (friendlyMessage != null) {
                                friendlyMessage.setId(dataSnapshot.getKey());
                            }
                            return friendlyMessage;
                        }
                    };

                    DatabaseReference messagesRef = userRef.child(MESSAGES_CHILD);
                    FirebaseRecyclerOptions<FriendlyMessage> options = new FirebaseRecyclerOptions.Builder<FriendlyMessage>()
                            .setQuery(messagesRef, parser)
                            .build();
                    initFirebaseAdapter(options);

                    mFirebaseAdapter.registerAdapterDataObserver(new RecyclerView.AdapterDataObserver() {
                        @Override
                        public void onItemRangeInserted(int positionStart, int itemCount) {
                            super.onItemRangeInserted(positionStart, itemCount);
                            int friendlyMessageCount = mFirebaseAdapter.getItemCount();
                            int lastVisiblePosition = mLinearLayoutManager.findLastCompletelyVisibleItemPosition();
                            // If the recycler view is initially being loaded or the
                            // user is at the bottom of the list, scroll to the bottom
                            // of the list to show the newly added message.
                            if (lastVisiblePosition == -1 ||
                                    (positionStart >= (friendlyMessageCount - 1) &&
                                            lastVisiblePosition == (positionStart - 1))) {
                                mMessageRecyclerView.scrollToPosition(positionStart);
                            }
                        }
                    });

                    mMessageRecyclerView.setAdapter(mFirebaseAdapter);
                    mFirebaseAdapter.startListening();
                }
            }

            @Override
            public void onChildChanged(DataSnapshot dataSnapshot, String s) {
            }

            @Override
            public void onChildRemoved(DataSnapshot dataSnapshot) {
                System.out.println("REMOOOOVED");
                dialogIsStopped = true;
                mFirebaseAdapter.notifyDataSetChanged();
            }

            @Override
            public void onChildMoved(DataSnapshot dataSnapshot, String s) {
            }

            @Override
            public void onCancelled(DatabaseError databaseError) {
            }
        });
    }

    private void infoDialog(String s) throws Exception{
        AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(
                this);
        alertDialogBuilder
                .setMessage(s)
                .setCancelable(false)
                .setPositiveButton("Ок", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        finish();
                    }
                });
        AlertDialog alertDialog = alertDialogBuilder.create();
        alertDialog.show();
    }

    private void auth() {
        FirebaseAuth mFirebaseAuth = FirebaseAuth.getInstance();
        mFirebaseUser = mFirebaseAuth.getCurrentUser();
        if (mFirebaseUser == null) {
            startActivity(new Intent(this, MainActivity.class));
            finish();
            return;
        } else {
            mUsername = mFirebaseUser.getDisplayName();
            if (mFirebaseUser.getPhotoUrl() != null) {
                mPhotoUrl = mFirebaseUser.getPhotoUrl().toString();
            }
        }
    }

    private void initRV() {
        mMessageRecyclerView = findViewById(R.id.rvChat);
        mLinearLayoutManager = new LinearLayoutManager(this);
        mLinearLayoutManager.setStackFromEnd(true);
        mMessageRecyclerView.setLayoutManager(mLinearLayoutManager);
    }

    private void initFirebaseAdapter(FirebaseRecyclerOptions<FriendlyMessage> options) {
        mFirebaseAdapter = new FirebaseRecyclerAdapter<FriendlyMessage, MessageViewHolder>(options) {
            @Override
            public MessageViewHolder onCreateViewHolder(ViewGroup viewGroup, int i) {
                LayoutInflater inflater = LayoutInflater.from(viewGroup.getContext());
                return new MessageViewHolder(inflater.inflate(R.layout.item_message, viewGroup, false));
            }

            @Override
            protected void onBindViewHolder(final MessageViewHolder viewHolder, int position, FriendlyMessage friendlyMessage) {
                final RequestOptions requestOptions = new RequestOptions()
                        .placeholder(R.drawable.progress_animation)
                        .dontAnimate()
                        .dontTransform();
                hideAllItemLayouts(viewHolder);
                if (friendlyMessage.getText() != null) {
                    if (friendlyMessage.getUid().equals(mFirebaseUser.getUid())) {
                        viewHolder.flMessage.setVisibility(TextView.VISIBLE);
                        viewHolder.tvMessage.setText(friendlyMessage.getText());
                    }
                    else {
                        viewHolder.flMessageLeft.setVisibility(TextView.VISIBLE);
                        viewHolder.tvMessageLeft.setText(friendlyMessage.getText());
                    }
                } else if (friendlyMessage.getImageUrl() != null) {
                    boolean imageIsNotLoaded = friendlyMessage.getImageUrl().equals(LOADING_IMAGE_URL);
                    if (friendlyMessage.getUid().equals(mFirebaseUser.getUid())) {
                        Glide.with(viewHolder.messageImageView.getContext())
                                .load((imageIsNotLoaded) ? R.drawable.progress_animation : friendlyMessage.getImageUrl())
                                .apply(requestOptions)
                                .into(viewHolder.messageImageView);
                        viewHolder.flImageLayout.setVisibility(ImageView.VISIBLE);
                    }
                    else {
                        Glide.with(viewHolder.messageImageView.getContext())
                                .load((imageIsNotLoaded) ? R.drawable.progress_animation : friendlyMessage.getImageUrl())
                                .apply(requestOptions)
                                .into(viewHolder.messageImageViewLeft);
                        viewHolder.flImageLayoutLeft.setVisibility(ImageView.VISIBLE);
                    }
                }
            }

            // override getItemId and getItemViewType to fix "RecyclerView items duplicate and constantly changing"
            @Override
            public long getItemId(int position) {
                return position;
            }

            @Override
            public int getItemViewType(int position) {
                return position;
            }

            @Override
            public int getItemCount() {
                if (dialogIsStopped) {
                    try {
                        infoDialog("Диалог остановлен");
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                    dialogIsStopped = false;
                }
                return super.getItemCount();
            }
        };
    }

    private void hideAllItemLayouts(MessageViewHolder viewHolder) {
        viewHolder.flImageLayoutLeft.setVisibility(ImageView.GONE);
        viewHolder.flImageLayout.setVisibility(ImageView.GONE);
        viewHolder.flMessage.setVisibility(TextView.GONE);
        viewHolder.flMessageLeft.setVisibility(TextView.GONE);
    }

    public void onClick(View view) {
        switch (view.getId()) {
            case R.id.ivBack:
                mInterstitialAd.show();
                finish();
                break;
            case R.id.tvStop:
                stopMessaging();
                break;
            case R.id.addMessageImageView:
                Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                intent.addCategory(Intent.CATEGORY_OPENABLE);
                intent.setType("image/*");
                startActivityForResult(intent, REQUEST_IMAGE);
                break;
            case R.id.sendButton:
                if (!mMessageEditText.getText().toString().isEmpty()) {
                    FriendlyMessage friendlyMessage = new
                            FriendlyMessage(mMessageEditText.getText().toString(),
                            mUsername,
                            mPhotoUrl,
                            null /* no image */);
                    friendlyMessage.setUid(mFirebaseUser.getUid());
                    userRef.child(MESSAGES_CHILD).push().setValue(friendlyMessage);
                    interlocutorRef.child(MESSAGES_CHILD).push().setValue(friendlyMessage);
                    mMessageEditText.setText("");
                }
                break;
        }
    }

    private void stopMessaging() {
        AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(
                this);
        alertDialogBuilder
                .setMessage("Прекратить диалог?")
                .setPositiveButton("Да", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        userRef.removeValue();
                        interlocutorRef.child("dialogIsStopped").push().setValue(true);
                        interlocutorRef.removeValue();
                        finish();
                    }
                })
                .setNegativeButton("Нет", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        dialog.cancel();
                    }
                });
        AlertDialog alertDialog = alertDialogBuilder.create();
        alertDialog.show();
    }

    @Override
    public void onPause() {
        if (mFirebaseAdapter != null) {
            mFirebaseAdapter.stopListening();
        }
        super.onPause();
    }

    @Override
    public void onResume() {
        super.onResume();
        if (mFirebaseAdapter != null) {
            mFirebaseAdapter.startListening();
        }
    }

    @Override
    public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {
        // An unresolvable error has occurred and Google APIs (including Sign-In) will not
        // be available.
        Log.d(TAG, "onConnectionFailed:" + connectionResult);
        Toast.makeText(this, "Google Play Services error.", Toast.LENGTH_SHORT).show();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        Log.d(TAG, "onActivityResult: requestCode=" + requestCode + ", resultCode=" + resultCode);

        if (resultCode == RESULT_OK && requestCode == REQUEST_IMAGE) {
            if (data != null) {
                final Uri uri = data.getData();
                Log.d(TAG, "Uri: " + uri.toString());

                FriendlyMessage tempMessage = new FriendlyMessage(null, mUsername, mPhotoUrl,
                        LOADING_IMAGE_URL);
                tempMessage.setUid(mFirebaseUser.getUid());
                String key = userRef.child(MESSAGES_CHILD).push().getKey();
                userRef.child(MESSAGES_CHILD).child(key)
                        .setValue(tempMessage, new DatabaseReference.CompletionListener() {
                            @Override
                            public void onComplete(DatabaseError databaseError,
                                                   DatabaseReference databaseReference) {
                                if (databaseError == null) {
                                    String key = databaseReference.getKey();
                                    StorageReference storageReference = FirebaseStorage.getInstance()
                                            .getReference(mFirebaseUser.getUid())
                                            .child(key)
                                            .child(uri.getLastPathSegment());

                                    putImageInStorage(storageReference, uri, key);
                                } else {
                                    Log.w(TAG, "Unable to write message to database.",
                                            databaseError.toException());
                                }
                            }
                        });
                userRef.child(MESSAGES_CHILD).child(key)
                        .setValue(tempMessage, new DatabaseReference.CompletionListener() {
                            @Override
                            public void onComplete(DatabaseError databaseError,
                                                   DatabaseReference databaseReference) {
                                if (databaseError == null) {
                                    String key = databaseReference.getKey();
                                    StorageReference storageReference = FirebaseStorage.getInstance()
                                            .getReference(mFirebaseUser.getUid())
                                            .child(key)
                                            .child(uri.getLastPathSegment());
                                } else {
                                    Log.w(TAG, "Unable to write message to database.",
                                            databaseError.toException());
                                }
                            }
                        });
            }
        }
    }

    private void showProgressDialog(final String key, final UploadTask uploadTask) {
        dialog = new ProgressDialog(this);
        dialog.setTitle("Загрузка изображения...");
        dialog.setCancelable(false);
        dialog.setButton(Dialog.BUTTON_POSITIVE, "Отмена", new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int which) {
                userRef.child(MESSAGES_CHILD).child(key).removeValue();
                interlocutorRef.child(MESSAGES_CHILD).child(key).removeValue();
                uploadTask.cancel();
            }
        });
        dialog.show();
    }

    private void putImageInStorage(StorageReference storageReference, Uri uri, final String key) {
        final UploadTask uploadTask = storageReference.putFile(uri);
        showProgressDialog(key, uploadTask);
        uploadTask.addOnProgressListener(new OnProgressListener<UploadTask.TaskSnapshot>() {
            @Override
            public void onProgress(@NonNull UploadTask.TaskSnapshot taskSnapshot) {
                final double progress = (100.0 * taskSnapshot.getBytesTransferred() / taskSnapshot.getTotalByteCount());
                Log.i("Load","Upload is " + String.format("%.1f", progress) + "% done");
                Runnable changeMessage = new Runnable() {
                    @Override
                    public void run() {
                        //Log.v(TAG, strCharacters);
                        dialog.setMessage(String.format("%.1f", progress) + "% done");
                    }
                };
                runOnUiThread(changeMessage);
            }
        }).addOnCompleteListener(this,
                new OnCompleteListener<UploadTask.TaskSnapshot>() {
                    @Override
                    public void onComplete(@NonNull Task<UploadTask.TaskSnapshot> task) {
                        if (task.isSuccessful()) {
                            task.getResult().getMetadata().getReference().getDownloadUrl()
                                    .addOnCompleteListener(ChatActivity.this,
                                            new OnCompleteListener<Uri>() {
                                                @Override
                                                public void onComplete(@NonNull Task<Uri> task) {
                                                    if (task.isSuccessful()) {
                                                        FriendlyMessage friendlyMessage =
                                                                new FriendlyMessage(null, mUsername, mPhotoUrl,
                                                                        task.getResult().toString());
                                                        friendlyMessage.setUid(mFirebaseUser.getUid());
                                                        userRef.child(MESSAGES_CHILD).child(key).setValue(friendlyMessage);
                                                        interlocutorRef.child(MESSAGES_CHILD).child(key).setValue(friendlyMessage);
                                                        dialog.cancel();
                                                    }
                                                }
                                            });
                        } else {
                            Log.w(TAG, "Image upload task was not successful.",
                                    task.getException());
                        }
                    }
                });
    }
}