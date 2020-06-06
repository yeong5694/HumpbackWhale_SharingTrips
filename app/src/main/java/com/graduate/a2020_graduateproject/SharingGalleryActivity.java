package com.graduate.a2020_graduateproject;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Log;
import android.view.View;
import android.webkit.MimeTypeMap;
import android.widget.Button;
import android.widget.Toast;

import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;
import com.google.firebase.storage.StorageTask;
import com.google.firebase.storage.UploadTask;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Objects;

import static android.Manifest.permission.READ_EXTERNAL_STORAGE;
import static android.Manifest.permission.WRITE_EXTERNAL_STORAGE;

public class SharingGalleryActivity extends AppCompatActivity {//implements GalleryAdapter.OnItemClickListener {

    private static final int PICK_IMAGE_REQUEST = 1;

    private OutputStream outputStream;
    private Uri image_uri;

    private Toolbar toolbar;
    private FloatingActionButton uploadButton;
    private Button firebaseUploadButton;
    private Button saveImageButton;

    private RecyclerView recyclerView;
    private GalleryAdapter galleryAdapter;
    //private ArrayList<Bitmap> imageList = new ArrayList<Bitmap>();  // 2020-06-04 09:46
    private ArrayList<Upload> imageList;

    private FirebaseStorage firebaseStorage;
    private StorageReference storageReference;
    private DatabaseReference databaseReference;
    private ValueEventListener dbListener;

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

        recyclerView = findViewById(R.id.recyclerView);
        recyclerView.setHasFixedSize(true);
        recyclerView.setLayoutManager(new GridLayoutManager(this, 3));

        imageList = new ArrayList<>();

        galleryAdapter = new GalleryAdapter(SharingGalleryActivity.this, imageList);
        recyclerView.setAdapter(galleryAdapter);

        uploadButton = findViewById(R.id.uploadFab);
        uploadButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                openFileChooser();  // 저장소(갤러리) 연결해서 이미지 선택

            }
        });

        storageReference = FirebaseStorage.getInstance().getReference("upload_images").child(selected_room_name); // Storage에 upload_images 폴더 만듦
        databaseReference = FirebaseDatabase.getInstance().getReference("sharing_trips/gallery_list").child(selected_room_id);
    }
    // onCreate() 여기까지 //




    //////// 핸드폰 기기의 저장소에서 사진을 선택하여 현재 여행방 갤러리에 선택한 이미지 보이기

    // 저장소 액세스 프레임워크(SAF)를 사용하여 갤러리 호출
    private void openFileChooser() {

        Intent intent = new Intent();
        intent.setType("image/*");  // 이미지 타입의 intent
        intent.setAction(Intent.ACTION_GET_CONTENT); // intent 액션 지정
        startActivityForResult(intent, PICK_IMAGE_REQUEST); // 액티비티를 실행하고 그 액티비티로부터 결과를 수신


        /*
        // 이렇게 하면 여행방 갤러리를 누르면 /Pictures/SharingTrips 경로의 모든 이미지가 새로운 인텐트로 열림
        Uri targetUri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI;
        //String targetDir = Environment.getExternalStorageDirectory().toString() + "/DCIM/Camera";   // 특정 경로!!
        String targetDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES) + "/SharingTrips";
        targetUri = targetUri.buildUpon().appendQueryParameter("bucketId", String.valueOf(targetDir.toLowerCase().hashCode())).build();
        Intent intent = new Intent(Intent.ACTION_VIEW, targetUri);
        startActivity(intent);
        */
    }

    // startActivityForResult()로부터 수신한 결과는 onActivityResult()에 전달
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == PICK_IMAGE_REQUEST && resultCode == RESULT_OK && data != null && data.getData() != null) {
            image_uri = data.getData();


            Upload upload = new Upload(image_uri.toString());
            imageList.add(upload);
              //2020-06-06 12:55
            galleryAdapter.notifyDataSetChanged();

            try {
                InputStream in = getContentResolver().openInputStream(image_uri);   // Open uri inputStream to read bitmap
                Bitmap imageToBitmap = BitmapFactory.decodeStream(in);  // Save to bitmap

                in.close();
                //saveImage(imageToBitmap, "demo");   // 핸드폰 저장소에 이미지를 저장하는 함수 호출
//                saveImage(imageToBitmap);   // 핸드폰 저장소에 이미지를 저장하는 함수 호출
            } catch (Exception e) {
                e.printStackTrace();
            }

            if(uploadTask != null && uploadTask.isInProgress()) {
                Toast.makeText(SharingGalleryActivity.this, "Upload in progress", Toast.LENGTH_SHORT).show();
            }
            else {
                uploadFile();
            }
    //2020-06-04 12:20
        }
    }

    ////////
    // Firebase Storage - /upload_images 폴더 아래, '여행방 이름 폴더' 안에 업로드됨
    // Firebase Realtime Database - sharing_trips 아래 'gallery_list'에 여행방 별로 업로드한 사진 저장됨

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
                    Toast.makeText(SharingGalleryActivity.this, "Upload successful", Toast.LENGTH_SHORT).show();
                    Upload upload = new Upload(taskSnapshot.getMetadata().getReference().getDownloadUrl().toString());
                    //Upload upload = new Upload(taskSnapshot.getStorage().getDownloadUrl().toString());
                    String uploadId = databaseReference.push().getKey();
                    databaseReference.child(uploadId).setValue(upload);
                }
            }).addOnFailureListener(new OnFailureListener() {
                @Override
                public void onFailure(@NonNull Exception e) {
                    Toast.makeText(SharingGalleryActivity.this, e.getMessage(), Toast.LENGTH_SHORT).show();
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

/*
    public void getImageList() {

        Uri externalUri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI;

        String relativePath = Environment.DIRECTORY_PICTURES + "/SharingTrips";
        String sortOrder = MediaStore.Images.Media.DATE_ADDED + " DESC";
        //String sortOrder = MediaStore.Images.Media.DATE_TAKEN + " DESC";

        String[] projection = new String[]{
                MediaStore.Images.Media._ID,
                MediaStore.Images.Media.DATA,
                MediaStore.Images.Media.DATE_ADDED,
                MediaStore.Images.Media.DISPLAY_NAME,
                MediaStore.Images.Media.MIME_TYPE,
                MediaStore.Images.Media.RELATIVE_PATH
        };

        // ContentResolver 인스턴스를 가져와서 쿼리를 실행한 뒤 생성한 Cursor 인스턴스에 저장
        Cursor cursor = getContentResolver().query(externalUri, projection, relativePath, null, sortOrder);

        if (cursor != null && cursor.moveToFirst()) {

            do {
                Upload upload = new Upload(image_uri.toString());
                imageList.add(upload);

                galleryAdapter.notifyDataSetChanged();

            } while (cursor.moveToNext());

            cursor.close();
        }
        else if(cursor == null) {
            Log.e("TAG", "cursor is null");
        }

    }
*/
}