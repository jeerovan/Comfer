package com.jeerovan.comfer.notes

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.util.Base64
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

/** Photos live inside the existing encrypted note payload, never in a plaintext disk cache. */
internal object NotesImages {
    fun read(context:Context,uri:Uri):NoteImage {
        val bytes=(context.contentResolver.openInputStream(uri)?:error("Image is unavailable")).use { input->
            val out=ByteArrayOutputStream();val buffer=ByteArray(8192)
            while(true){val count=input.read(buffer);if(count<0)break;require(out.size()+count<=20_000_000){"Choose an image smaller than 20 MB"};out.write(buffer,0,count)}
            out.toByteArray()
        }
        val bounds=BitmapFactory.Options().apply{inJustDecodeBounds=true}
        BitmapFactory.decodeByteArray(bytes,0,bytes.size,bounds)
        require(bounds.outWidth>0&&bounds.outHeight>0&&bounds.outWidth.toLong()*bounds.outHeight<=100_000_000){"Image is damaged or too large"}
        var sample=1
        while(maxOf(bounds.outWidth,bounds.outHeight)/sample>1440)sample*=2
        var bitmap=BitmapFactory.decodeByteArray(bytes,0,bytes.size,BitmapFactory.Options().apply{inSampleSize=sample})?:error("Image cannot be read")
        try {
            val orientation=runCatching{ExifInterface(ByteArrayInputStream(bytes)).getAttributeInt(ExifInterface.TAG_ORIENTATION,1)}.getOrDefault(1)
            val matrix=Matrix().apply{when(orientation){
                2->setScale(-1f,1f);3->setRotate(180f);4->setScale(1f,-1f)
                5->{setRotate(90f);postScale(-1f,1f)};6->setRotate(90f)
                7->{setRotate(-90f);postScale(-1f,1f)};8->setRotate(-90f)
            }}
            if(!matrix.isIdentity){val rotated=Bitmap.createBitmap(bitmap,0,0,bitmap.width,bitmap.height,matrix,true);if(rotated!==bitmap){bitmap.recycle();bitmap=rotated}}
            if(bitmap.hasAlpha()){
                val opaque=Bitmap.createBitmap(bitmap.width,bitmap.height,Bitmap.Config.ARGB_8888)
                android.graphics.Canvas(opaque).apply{drawColor(android.graphics.Color.WHITE);drawBitmap(bitmap,0f,0f,null)}
                bitmap.recycle();bitmap=opaque
            }
            var encoded:ByteArray
            while(true){
                val out=ByteArrayOutputStream();bitmap.compress(Bitmap.CompressFormat.JPEG,80,out);encoded=out.toByteArray()
                if(encoded.size<=250_000)break
                require(minOf(bitmap.width,bitmap.height)>128){"Image cannot fit in this note"}
                val reduced=Bitmap.createScaledBitmap(bitmap,(bitmap.width*.75f).toInt().coerceAtLeast(1),(bitmap.height*.75f).toInt().coerceAtLeast(1),true)
                bitmap.recycle();bitmap=reduced
            }
            return NoteImage(jpeg=Base64.encodeToString(encoded,Base64.NO_WRAP),width=bitmap.width,height=bitmap.height)
        } finally {bitmap.recycle()}
    }
    fun decode(image:NoteImage):Bitmap? = runCatching {
        require(image.jpeg.length<=400_000)
        val bytes=Base64.decode(image.jpeg,Base64.NO_WRAP)
        val bounds=BitmapFactory.Options().apply{inJustDecodeBounds=true}
        BitmapFactory.decodeByteArray(bytes,0,bytes.size,bounds)
        require(bounds.outWidth in 1..1440&&bounds.outHeight in 1..1440)
        BitmapFactory.decodeByteArray(bytes,0,bytes.size)
    }.getOrNull()
}
