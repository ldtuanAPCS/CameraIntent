package com.example.ethan.cameraintent;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.support.annotation.NonNull;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import java.io.File;

public class ImageAdapter extends RecyclerView.Adapter<ImageAdapter.ViewHolder>{
    private File imagesFile;

    public ImageAdapter(File folderFile){
        imagesFile = folderFile;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup viewGroup, int viewType) {
        View view = LayoutInflater.from(viewGroup.getContext())
                .inflate(R.layout.gallery_images_relative_layout, viewGroup, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder viewHolder, int position) {
        File imageFile = imagesFile.listFiles()[position];
//        Bitmap imageBitmap = BitmapFactory.decodeFile(imageFile.getAbsolutePath());
//        viewHolder.getImageView().setImageBitmap(imageBitmap);
        BitmapWorkerTask workerTask = new BitmapWorkerTask(viewHolder.getImageView());
        workerTask.execute(imageFile);
    }

    @Override
    public int getItemCount() {
        return imagesFile.listFiles().length;
    }

    public static class ViewHolder extends RecyclerView.ViewHolder{
        private ImageView imageView;

        public ViewHolder(View view){
            super(view);
            imageView = (ImageView) view.findViewById(R.id.imageGalleryView);
        }

        public ImageView getImageView(){
            return imageView;
        }
    }
}
