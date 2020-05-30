package com.graduate.a2020_graduateproject;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.Manifest;
import android.app.Activity;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.ParcelFileDescriptor;
import android.provider.MediaStore;
import android.util.Log;
import android.view.View;
import android.webkit.MimeTypeMap;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.Toast;

import com.bumptech.glide.Glide;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.firebase.FirebaseApiNotAvailableException;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.OnProgressListener;
import com.google.firebase.storage.StorageReference;
import com.google.firebase.storage.StorageTask;
import com.google.firebase.storage.UploadTask;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileDescriptor;
import java.io.FileFilter;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOError;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Locale;
import java.util.Objects;

import static android.Manifest.permission.READ_EXTERNAL_STORAGE;
import static android.Manifest.permission.WRITE_EXTERNAL_STORAGE;

public class ShareGalleryActivity extends AppCompatActivity {

    private static final int PICK_IMAGE_REQUEST = 1;

    private OutputStream outputStream;
    private Uri image_uri;

    private Toolbar toolbar;
    private FloatingActionButton uploadButton;
    //private ImageView imageView;
    //private ProgressBar progressBar;
    //private Button firebaseUploadButton;
    private Button saveImageButton;

    //private GridView gridView;
    private RecyclerView recyclerView;
    private GalleryAdapter galleryAdapter;
    //private ArrayList<Bitmap> imageList = new ArrayList<Bitmap>();
    private ArrayList<Upload> imageList;

    private StorageReference storageReference;
    private DatabaseReference databaseReference;

    private StorageTask uploadTask;

    // 선택한 여행방 정보
    private String selected_room_name; // 여행방 이름
    private String selected_room_id; // 여행방 id

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_share_gallery);

        Intent intent = getIntent();
        selected_room_id = intent.getExtras().getString("selected_room_id");
        selected_room_name = intent.getExtras().getString("selected_room_name");

        toolbar = findViewById(R.id.main_toolbar);
        setSupportActionBar(toolbar);
        setTitle("갤러리");

        //gridView = findViewById(R.id.recyclerView);

        //imageList = new  ArrayList<Upload>();

        recyclerView = findViewById(R.id.recyclerView);
        recyclerView.setHasFixedSize(true);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));

        imageList = new  ArrayList<Upload>();

        //galleryAdapter = new GalleryAdapter(ShareGalleryActivity.this, imageList);
        //recyclerView.setAdapter(galleryAdapter);

        storageReference = FirebaseStorage.getInstance().getReference("upload_images"); // Storage에 upload_images 폴더 만듦
        databaseReference = FirebaseDatabase.getInstance().getReference("sharing_trips/gallery_list").child(selected_room_id);

        databaseReference.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
            //public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                for(DataSnapshot postDataSnapshot : dataSnapshot.getChildren()) {
                    Upload upload = postDataSnapshot.getValue(Upload.class);
                    imageList.add(upload);
                }

                galleryAdapter = new GalleryAdapter(ShareGalleryActivity.this, imageList);
                recyclerView.setAdapter(galleryAdapter);
            }

            @Override
            public void onCancelled(DatabaseError databaseError) {
            //public void onCancelled(@NonNull DatabaseError databaseError) {
                Toast.makeText(ShareGalleryActivity.this, databaseError.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });

        //imageView = findViewById(R.id.selected_imageView);
        //progressBar = findViewById(R.id.progress_bar);
        //firebaseUploadButton = findViewById(R.id.upload_button);
        saveImageButton = findViewById(R.id.createFolder_button);


        //////// 외부저장소 공용 영역에 사진 업로드 버튼

        // 외부저장소 공용 영역에 사진 저장 버튼 이벤트리스너
        saveImageButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                // 저장소 접근 권한 체크
                if(checkPermission()) {
                    //new downloadImage().execute("https://i.picsum.photos/id/797/200/300.jpg"); // 이미지 다운로드
                    // uploadButton, saveImageButton 비활성화
                }
                // Ask for Permission
                else {
                    Toast.makeText(getApplicationContext(), "Grant Permission To Save Image", Toast.LENGTH_SHORT).show();
                    if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        requestPermissions(new String[] {WRITE_EXTERNAL_STORAGE, READ_EXTERNAL_STORAGE}, 111);
                    }
                }
            }
        });


        //////// 핸드폰 기기의 저장소에서 여행방 갤러리에 사진 업로드 버튼 (우측 하단의 동그란 + 버튼)

        uploadButton = findViewById(R.id.uploadFab);
        uploadButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                /*
                Intent intent = new Intent();
                intent.setType("image/*");
                intent.setAction(Intent.ACTION_GET_CONTENT);
                startActivityForResult(intent, PICK_IMAGE_REQUEST);
                */
                openFileChooser();  // 이미지 선택하면

                //uploadFile();
                // 바로 firebase에 업로드 되도록
                if(uploadTask != null && uploadTask.isInProgress()) {
                    Toast.makeText(ShareGalleryActivity.this, "Upload in progress", Toast.LENGTH_SHORT).show();
                }
                else {
                    uploadFile();   // ?
                }
            }
        });


        //////// Firebase Storage

        //storageReference = FirebaseStorage.getInstance().getReference("upload_images"); // Storage에 upload_images 폴더 만듦
        //databaseReference = FirebaseDatabase.getInstance().getReference("sharing_trips/gallery_list").child(selected_room_id);

        /*
        // Firebase Storage에 업로드 버튼 이벤트리스너
        firebaseUploadButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                //uploadFile();
            }
        });*/

        ////////

        /*
        imageList.add(BitmapFactory.decodeResource(getResources(), R.drawable.trip_image_1));
        imageList.add(BitmapFactory.decodeResource(getResources(), R.drawable.trip_image_2));
        imageList.add(BitmapFactory.decodeResource(getResources(), R.drawable.trip_image_3));
        imageList.add(BitmapFactory.decodeResource(getResources(), R.drawable.trip_image_4));
        imageList.add(BitmapFactory.decodeResource(getResources(), R.drawable.trip_image_5));*/

        // Adapter 연결
        //GalleryAdapter galleryAdapter = new GalleryAdapter(this, imageList);
        //galleryAdapter = new GalleryAdapter(this, imageList);
        //recyclerView.setAdapter(galleryAdapter);

        /*
        gridView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            public void onItemClick(AdapterView<?> parent, View v, int position, long id) {
                /*
                // Sending image id to FullScreenActivity
                Intent intent = new Intent(getApplicationContext(), GalleryImageViewerActivity.class);
                // passing array index
                intent.putExtra("id", position);
                startActivity(intent);
                * /
                Intent intent = new Intent(getApplicationContext(), GalleryImageViewerActivity.class);
                // passing array index
                intent.putExtra("id", position);
                intent.putExtra("uri", image_uri.toString());
                startActivity(intent);
            }
        });*/

    }
    // onCreate() 여기까지 //


    private void openFileChooser() {
        Intent intent = new Intent();
        intent.setType("image/*");
        intent.setAction(Intent.ACTION_GET_CONTENT);
        startActivityForResult(intent, PICK_IMAGE_REQUEST);
    }

    //////// 핸드폰 기기의 저장소에서 사진을 선택하여 현재 여행방 갤러리에 선택한 사진 이미지뷰로 보이기

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == PICK_IMAGE_REQUEST && resultCode == RESULT_OK && data != null && data.getData() != null) {
            image_uri = data.getData();

            /*
            Glide.with(this)
                    .load(image_uri)
                    .into(imageView);
            */

            /*
            if(uploadTask != null && uploadTask.isInProgress()) {
                Toast.makeText(ShareGalleryActivity.this, "Upload in progress", Toast.LENGTH_SHORT).show();
            }
            else {
                uploadFile();   // ?
            }*/

            try {
                InputStream in = getContentResolver().openInputStream(image_uri);   // Open uri inputStream to read bitmap
                Bitmap imageToBitmap = BitmapFactory.decodeStream(in);  // Save to bitmap

                //imageList.add(imageToBitmap);

                //galleryAdapter = new GalleryAdapter(this, imageList);
                //recyclerView.setAdapter(galleryAdapter);


                in.close();
                //saveImage(imageToBitmap, "demo");   // 핸드폰 저장소에 이미지를 저장하는 함수 호출
                saveImage(imageToBitmap);   // 핸드폰 저장소에 이미지를 저장하는 함수 호출
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }



    //////// Firebase - 현재는 선택한 이미지가 Firebase Storage에만 저장됨

    private String getFileExtension(Uri uri) {
        ContentResolver cr = getContentResolver();
        MimeTypeMap mime = MimeTypeMap.getSingleton();
        return mime.getExtensionFromMimeType(cr.getType(uri));
    }

    private void uploadFile() {
        if(image_uri != null) {
            StorageReference fileReference = storageReference.child(System.currentTimeMillis() + "." + getFileExtension(image_uri));

            uploadTask = fileReference.putFile(image_uri).addOnSuccessListener(new OnSuccessListener<UploadTask.TaskSnapshot>() {
                @Override
                public void onSuccess(UploadTask.TaskSnapshot taskSnapshot) {
                    Toast.makeText(ShareGalleryActivity.this, "Upload successful", Toast.LENGTH_SHORT).show();
                    Upload upload = new Upload(taskSnapshot.getMetadata().getReference().getDownloadUrl().toString());
                    //Upload upload = new Upload(taskSnapshot.getStorage().getDownloadUrl().toString());
                    String uploadId = databaseReference.push().getKey();
                    databaseReference.child(uploadId).setValue(upload);
                }
            }).addOnFailureListener(new OnFailureListener() {
                @Override
                public void onFailure(@NonNull Exception e) {
                    Toast.makeText(ShareGalleryActivity.this, e.getMessage(), Toast.LENGTH_SHORT).show();
                }
            });
        }
        else {
            Toast.makeText(this, "No file selected", Toast.LENGTH_SHORT).show();
        }
    }



    //////// 외부저장소 공용 영역(/Pictures 아래) 접근, 이미지 저장

    //private void saveImage(Bitmap bitmap, String name) throws IOException {
    private void saveImage(Bitmap bitmap) throws IOException {

        OutputStream fos; // File Output Stream

        // 타겟 SDK가 안드로이드 10(Q) (SDK 29) 이상일 때
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            /*
            MediaStore API를 사용하여 외부저장소의 공용 폴더 안의 미디어 파일(사진/동영상)에 접근 - 현재는 다운로드 받은 이미지 파일 저장
            ... 공용 폴더 안의 미디어 파일(사진/동영상/오디오)들은 MediaStore를 통해 읽을 수 있음
            ... 사진 파일을 찾고 싶으면 공용 폴더 아래의 모든 파일 탐색 X, MediaStore에 쿼리를 하여 Uri 객체를 얻어 사용
            ... 기본적으로 외부저장소 공용 영역의 /Pictures 아래 저장됨
            */
            final String relativePath = Environment.DIRECTORY_PICTURES + "/SharingTrips";
            // 해당 경로가 없을 때 생성해주는지.. 폴더를 만들어줘야 하는지!

            ContentResolver resolver = getContentResolver();    // ContentResolver 인스턴스를 가져옴
            ContentValues contentValues = new ContentValues();
            //contentValues.put(MediaStore.Images.Media.DISPLAY_NAME, name + ".jpg"); // Set image name
            contentValues.put(MediaStore.Images.Media.DISPLAY_NAME, ".jpg"); // Set image name
            //contentValues.put(MediaStore.Images.Media.MIME_TYPE, "image/jpg");
            contentValues.put(MediaStore.Images.Media.MIME_TYPE, "image/*");
            contentValues.put(MediaStore.Images.Media.RELATIVE_PATH, relativePath); // 파일을 저장할 구체적 위치 설정

            Uri imageUri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues);
            fos = resolver.openOutputStream(Objects.requireNonNull(imageUri));
        }
        // 타겟 SDK가 안드로이드 10(Q) (SDK 29) 이하일 때
        else {
            File galleryFolder = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES + "/SharingTrips");

            // Pictures 아래 SharingTrips 폴더가 없다면 생성
            if(!galleryFolder.exists()){
                galleryFolder.mkdir();
            }

            //File Image = new File(galleryFolder.toString(), name + ".jpg");
            File Image = new File(galleryFolder.toString(),".jpg");
            fos = new FileOutputStream(Image);
        }

        bitmap.compress(Bitmap.CompressFormat.JPEG, 100, fos);
        //bitmap.compress(Bitmap.CompressFormat.PNG, 100, fos);
        Objects.requireNonNull(fos).close();
    }

    // 외부저장소 접근 권한 체크 - 안드로이드 9 이하일 경우에는 쓰기 권한도 별도의 체크 필요
    private boolean checkPermission() {
        int write = ContextCompat.checkSelfPermission(this, WRITE_EXTERNAL_STORAGE);
        int read = ContextCompat.checkSelfPermission(this, READ_EXTERNAL_STORAGE);
        return write == PackageManager.PERMISSION_GRANTED && read == PackageManager.PERMISSION_GRANTED;
    }

    // Grant Permission
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        for(int grantResult : grantResults) {
            if(grantResult == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(getApplicationContext(), "Permission Granted", Toast.LENGTH_SHORT).show();
            }
        }
    }
}