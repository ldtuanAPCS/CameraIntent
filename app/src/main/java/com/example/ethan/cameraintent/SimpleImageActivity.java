package com.example.ethan.cameraintent;

import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v7.app.AppCompatActivity;
import android.util.DisplayMetrics;
import android.widget.ImageView;
import android.widget.RelativeLayout;

import java.io.File;

public class SimpleImageActivity extends AppCompatActivity{
    private static final String IMAGE_FILE_LOCATION = "image_file_location";

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        DisplayMetrics displayMetrics = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getMetrics(displayMetrics);
        int width = displayMetrics.widthPixels;
        int height = displayMetrics.heightPixels;
        ImageView imageView = new ImageView(this);
        RelativeLayout.LayoutParams params = new RelativeLayout.LayoutParams(width, height);
        imageView.setLayoutParams(params);
        setContentView(imageView);
        File imageFile = new File(
                getIntent().getStringExtra(IMAGE_FILE_LOCATION)
        );
        SingleImageBitmapWorkerTask workerTask = new SingleImageBitmapWorkerTask(imageView, width, height);
        workerTask.execute(imageFile);
    }
}
