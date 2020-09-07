package com.stdio.anonymouschatroulette;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.AdView;
import com.google.android.gms.ads.MobileAds;
import com.google.android.gms.ads.initialization.InitializationStatus;
import com.google.android.gms.ads.initialization.OnInitializationCompleteListener;
import com.google.android.gms.auth.api.Auth;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.auth.api.signin.GoogleSignInResult;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.AuthResult;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.GoogleAuthProvider;
import com.google.firebase.database.ChildEventListener;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.Query;
import com.google.firebase.database.ValueEventListener;
import com.google.firebase.storage.UploadTask;

public class MainActivity extends AppCompatActivity implements GoogleApiClient.OnConnectionFailedListener{

    private static final String TAG = "SignInActivity";
    private static final int RC_SIGN_IN = 9001;
    private GoogleApiClient mGoogleApiClient;
    // Firebase instance variables
    private FirebaseAuth mFirebaseAuth;
    private DatabaseReference inSearchingRef;
    private DatabaseReference userRef;
    private FirebaseUser mFirebaseUser;
    String fbKey = "tmp";
    FirebaseDatabase database;
    boolean inSearching = false;
    ProgressDialog dialog;
    AdView mAdView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        MobileAds.initialize(this, new OnInitializationCompleteListener() {
            @Override
            public void onInitializationComplete(InitializationStatus initializationStatus) {
            }
        });

        mAdView = findViewById(R.id.adView);
        AdRequest adRequest = new AdRequest.Builder().build();
        mAdView.loadAd(adRequest);

        database = FirebaseDatabase.getInstance();
        inSearchingRef = database.getReference("inSearching");
        // Configure Google Sign In
        GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestIdToken(getString(R.string.default_web_client_id))
                .requestEmail()
                .build();
        mGoogleApiClient = new GoogleApiClient.Builder(this)
                .enableAutoManage(this /* FragmentActivity */, this /* OnConnectionFailedListener */)
                .addApi(Auth.GOOGLE_SIGN_IN_API, gso)
                .build();

        // Initialize FirebaseAuth
        mFirebaseAuth = FirebaseAuth.getInstance();
        mFirebaseUser = mFirebaseAuth.getCurrentUser();
        if (mFirebaseUser != null) {
            userRef = database.getReference(mFirebaseUser.getUid());
        }
        fbKey = inSearchingRef.push().getKey();
    }

    public void onClick(View v) {
        signIn();
    }

    private void signIn() {
        if (mFirebaseUser == null) {
            Intent signInIntent = Auth.GoogleSignInApi.getSignInIntent(mGoogleApiClient);
            startActivityForResult(signInIntent, RC_SIGN_IN);
        }
        else {
            findInterlocutorContinueMessaging();
        }
    }

    private void findInterlocutorContinueMessaging() {
        userRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot snapshot) {
                if (snapshot.hasChild("interlocutor")) {
                    startActivity(new Intent(MainActivity.this, ChatActivity.class));
                }
                else {
                    startSearching();
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {

            }
        });
    }

    @Override
    public void onPause() {
        if (dialog != null) {
            dialog.cancel();
        }
        inSearchingRef.child(fbKey).removeValue();
        super.onPause();
    }

    @Override
    public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {
        // An unresolvable error has occurred and Google APIs (including Sign-In) will not
        // be available.
        Log.d(TAG, "onConnectionFailed:" + connectionResult);
        Toast.makeText(this, "Google Play Services error.", Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        // Result returned from launching the Intent from GoogleSignInApi.getSignInIntent(...);
        if (requestCode == RC_SIGN_IN) {
            GoogleSignInResult result = Auth.GoogleSignInApi.getSignInResultFromIntent(data);
            if (result.isSuccess()) {
                // Google Sign-In was successful, authenticate with Firebase
                GoogleSignInAccount account = result.getSignInAccount();
                firebaseAuthWithGoogle(account);
            } else {
                // Google Sign-In failed
                Log.e(TAG, "Google Sign-In failed.");
            }
        }
    }

    private void firebaseAuthWithGoogle(GoogleSignInAccount acct) {
        Log.d(TAG, "firebaseAuthWithGoogle:" + acct.getId());
        AuthCredential credential = GoogleAuthProvider.getCredential(acct.getIdToken(), null);
        mFirebaseAuth.signInWithCredential(credential)
                .addOnCompleteListener(this, new OnCompleteListener<AuthResult>() {
                    @Override
                    public void onComplete(@NonNull Task<AuthResult> task) {
                        if (!task.isSuccessful()) {
                            Log.d(TAG, "WAT");
                        } else {
                            Log.d(TAG, "i am working");
                        }
                        Log.d(TAG, "signInWithCredential:onComplete:" + task.isSuccessful());

                        // If sign in fails, display a message to the user. If sign in succeeds
                        // the auth state listener will be notified and logic to handle the
                        // signed in user can be handled in the listener.
                        if (!task.isSuccessful()) {
                            Log.w(TAG, "signInWithCredential", task.getException());
                            Toast.makeText(MainActivity.this, "Authentication failed.",
                                    Toast.LENGTH_SHORT).show();
                        } else {
                            mFirebaseUser = mFirebaseAuth.getCurrentUser();
                            userRef = database.getReference(mFirebaseUser.getUid());
                            findInterlocutorContinueMessaging();
                        }
                    }
                });
    }

    private void startSearching() {
        inSearching = true;
        inSearchingRef.child(fbKey).removeValue();
        inSearchingRef.child(fbKey).setValue(mFirebaseUser.getUid());
        findInterlocutor();
        showProgressDialog();
    }

    private void showProgressDialog() {
        dialog = new ProgressDialog(this);
        dialog.setTitle("Поиск собеседника...");
        dialog.setMessage("Пожалуйста, оставайтесь в приложении");
        dialog.setCancelable(false);
        dialog.setButton(Dialog.BUTTON_POSITIVE, "Отмена", new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int which) {
                inSearchingRef.child(fbKey).removeValue();
            }
        });
        dialog.show();
    }

    private void findInterlocutor() {

        Query myQuery = inSearchingRef;
        myQuery.addChildEventListener(new ChildEventListener() {
            @Override
            public void onChildAdded(DataSnapshot dataSnapshot, String s) {
                String uid = dataSnapshot.getValue(String.class);
                if (!uid.equals(mFirebaseUser.getUid())) {
                    if (inSearching) {
                        userRef.child("interlocutor").push().setValue(uid);
                        createDialogWithAFKUser(uid, dataSnapshot.getKey());
                        dialog.cancel();
                        startActivity(new Intent(MainActivity.this, ChatActivity.class));
                    }
                    inSearching = false;
                }
            }

            @Override
            public void onChildChanged(DataSnapshot dataSnapshot, String s) {
            }

            @Override
            public void onChildRemoved(DataSnapshot dataSnapshot) {
            }

            @Override
            public void onChildMoved(DataSnapshot dataSnapshot, String s) {
            }

            @Override
            public void onCancelled(DatabaseError databaseError) {
            }
        });
    }

    private void createDialogWithAFKUser(String uid, final String interlocutorSearchingKey) {
        final DatabaseReference interlocutorRef = database.getReference(uid);
        interlocutorRef.child("interlocutor").addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot snapshot) {
                if (!snapshot.exists()) {
                    interlocutorRef.child("interlocutor").push().setValue(mFirebaseUser.getUid());
                    inSearchingRef.child(interlocutorSearchingKey).removeValue();
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {

            }
        });
    }
}