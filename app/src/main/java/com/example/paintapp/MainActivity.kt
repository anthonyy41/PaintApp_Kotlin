package com.example.paintapp

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Matrix
import android.net.Uri
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.util.DisplayMetrics
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.MotionEvent
import android.view.View
import android.widget.ImageView
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.FileProvider
import androidx.core.graphics.drawable.toBitmap
import kotlinx.android.synthetic.main.activity_main.*
import org.opencv.android.Utils
import org.opencv.core.*
import org.opencv.imgproc.Imgproc
import yuku.ambilwarna.AmbilWarnaDialog
import java.io.File
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*
import kotlin.collections.ArrayList

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        tl = Point()
        openCamera()
    }

    private enum class LoadImage {
        PICK_FROM_CAMERA,
        PICK_FROM_GALLERY
    }

    companion object {
        init {
//            System.loadLibrary("opencv_java")
            System.loadLibrary("opencv_java3")
        }
    }

    var touchCount = 0
    lateinit var tl: Point
    lateinit var bitmap: Bitmap
    var chosenColor = Color.RED
    private lateinit var imageFilePath: String
    private var texture = false
    private val TAG = MainActivity::class.java.simpleName
    private val PERMISSIONS = arrayOf<String>(
        Manifest.permission.READ_EXTERNAL_STORAGE,
        Manifest.permission.WRITE_EXTERNAL_STORAGE,
        Manifest.permission.CAMERA
    )

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        return super.onCreateOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.action_open_img -> {
                showImage()
            }
            R.id.action_process_image -> {
                showResultLayouts()
            }
            R.id.action_take_photo -> {
                openCamera()
            }
            R.id.action_get_gallery -> {
                openGallery()
            }
            R.id.action_get_color -> {
                chooseColor()
            }
            R.id.action_get_texture -> {
                chooseTexture()
            }
        }
        return true
    }

    public override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        when (requestCode) {
            LoadImage.PICK_FROM_CAMERA.ordinal -> if (resultCode == Activity.RESULT_OK) {
                try {
                    imageFromData.setImageURI(Uri.parse(imageFilePath))
                    bitmap = imageFromData.drawable.toBitmap()
                    bitmap = getResizedBitmap(bitmap, bitmap.width / 5, bitmap.height / 5)
                    showImage()
                } catch (e: IOException) {
                    e.printStackTrace()
                }
            }
            LoadImage.PICK_FROM_GALLERY.ordinal -> if (resultCode == Activity.RESULT_OK) {
                loadFromGallery(data)
            }
        }
        imageFromData.setOnTouchListener(object : View.OnTouchListener {
            override fun onTouch(v: View, event: MotionEvent): Boolean {
                if (event.action == MotionEvent.ACTION_DOWN) {
                    if (touchCount == 0) {
                        tl.x = event.x.toDouble()
                        tl.y = event.y.toDouble()
                        if (texture) {
                            applyTexture(bitmap, tl)
                        } else {
                            rpPaintHSV(bitmap, tl)
                        }
                    }
                }
                return true
            }
        })
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        when (requestCode) {
            LoadImage.PICK_FROM_CAMERA.ordinal -> {
                if (grantResults.isEmpty() || grantResults[0] != PackageManager.PERMISSION_GRANTED) {
                    Log.e(TAG, " Permission has been denied by user")
                } else {
                    openCamera()
                    Log.e(TAG, " Permission has been granted by user")
                }
            }
        }
    }

    private fun showImage(image: Mat, view: ImageView) {
        val mBitmap = Bitmap.createBitmap(image.cols(), image.rows(), Bitmap.Config.ARGB_8888);
        Utils.matToBitmap(image, mBitmap)
        view.setImageBitmap(mBitmap)
        bitmap = mBitmap
        saveImage(bitmap)
    }

    private fun showResultLayouts() {
        imageFromData.visibility = View.GONE
        topLayout.visibility = View.VISIBLE
        middleLayout.visibility = View.VISIBLE
        bottomLayout.visibility = View.VISIBLE
    }

    private fun showImage() {
        imageFromData.visibility = View.VISIBLE
        topLayout.visibility = View.GONE
        middleLayout.visibility = View.GONE
        bottomLayout.visibility = View.GONE
        try {
            imageFromData.setImageBitmap(bitmap)
        } catch (e: Exception) {
            Toast.makeText(this@MainActivity, " No image selected ", Toast.LENGTH_SHORT).show()
        }
    }

    private fun chooseColor() {
        texture = false
        val colorPicker = AmbilWarnaDialog(
            this@MainActivity,
            chosenColor,
            object : AmbilWarnaDialog.OnAmbilWarnaListener {
                override fun onCancel(dialog: AmbilWarnaDialog) {
                }

                override fun onOk(dialog: AmbilWarnaDialog, color: Int) {
                    chosenColor = color
                }
            })
        colorPicker.show()
    }

    private fun chooseTexture() {
        texture = true
    }

    fun getResizedBitmap(bm: Bitmap, newWidth: Int, newHeight: Int): Bitmap {
        val width = bm.getWidth()
        val height = bm.getHeight()
        val scaleWidth = newWidth / width.toFloat()
        val scaleHeight = newHeight / height.toFloat()
        // CREATE A MATRIX FOR THE MANIPULATION
        val matrix = Matrix()
        // RESIZE THE BIT MAP
        matrix.postScale(scaleWidth, scaleHeight)
        // "RECREATE” THE NEW BITMAP
        val resizedBitmap = Bitmap.createBitmap(bm, 0, 0, width, height, matrix, true)
        return resizedBitmap
    }

    private fun getTextureImage(): Mat {
        var textureImage =
            BitmapFactory.decodeResource(getResources(), R.drawable.texture_test)
        textureImage = getResizedBitmap(textureImage, bitmap.width, bitmap.height)
        val texture = Mat()
        Utils.bitmapToMat(textureImage, texture)
        Imgproc.cvtColor(texture, texture, Imgproc.COLOR_RGBA2RGB)
        return texture
    }

    private fun openCamera() {
        if (ActivityCompat.checkSelfPermission(baseContext, Manifest.permission.CAMERA)
            != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(this, PERMISSIONS, LoadImage.PICK_FROM_CAMERA.ordinal)
        } else {
            val captureIntent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
            var photoFile: File? = null
            try {
                photoFile = createImageFile()
            } catch (ex: IOException) {
                // Error occurred while creating the File
            }
            if (photoFile != null) {
                val photoURI =
                    FileProvider.getUriForFile(
                        this,
                        "com.example.paintapp.provider",
                        photoFile
                    )
                captureIntent.putExtra(MediaStore.EXTRA_OUTPUT, photoURI)
                startActivityForResult(captureIntent, LoadImage.PICK_FROM_CAMERA.ordinal)
            }
        }
    }

    private fun openGallery() {
        val i = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
        startActivityForResult(i, LoadImage.PICK_FROM_GALLERY.ordinal)
    }

    private fun createImageFile(): File {
        val timeStamp = SimpleDateFormat(
            "yyyyMMdd_HHmmss", Locale.getDefault()
        ).format(Date())
        val imageFileName = "IMG_" + timeStamp + "_"
        val storageDir = getExternalFilesDir(Environment.DIRECTORY_PICTURES)
        val image = File.createTempFile(
            imageFileName, /* prefix */
            ".jpg", /* suffix */
            storageDir /* directory */
        )
        imageFilePath = image.getAbsolutePath()
        return image;
    }

    private fun saveImage(image: Bitmap) {
        val pictureFile = createImageFile()
        if (pictureFile == null) {
            Log.e(TAG, "Error creating media file, check storage permissions: ")
            return
        }
        try {
            val fos = FileOutputStream(pictureFile);
            image.compress(Bitmap.CompressFormat.PNG, 90, fos)
            fos.close()
        } catch (e: FileNotFoundException) {
            Log.e(TAG, "File not found: " + e.message)
        } catch (e: IOException) {
            Log.e(TAG, "Error accessing file: " + e.message)
        }
    }

    private fun loadFromGallery(data: Intent?) {
        val selectedImage = data?.data
        val filePathColumn: Array<String> = arrayOf(MediaStore.Images.Media.DATA)
        val cursor = getContentResolver().query(selectedImage!!, filePathColumn, null, null, null)
        cursor?.moveToFirst()
        val columnIndex = cursor?.getColumnIndex(filePathColumn[0])
        val picturePath = cursor?.getString(columnIndex!!)
        cursor?.close()
        bitmap = BitmapFactory.decodeFile(picturePath)
        bitmap = getResizedBitmap(bitmap, bitmap.width / 5, bitmap.height / 5)
        showImage()
    }

    private fun rpPaintHSV(bitmap: Bitmap, p: Point): Mat {
        val cannyMinThres = 30.0
        val ratio = 2.5
        // show intermediate step results
        // grid created here to do that
        showResultLayouts()
        val mRgbMat = Mat()
        Utils.bitmapToMat(bitmap, mRgbMat)
        showImage(mRgbMat, inputImage)
        Imgproc.cvtColor(mRgbMat, mRgbMat, Imgproc.COLOR_RGBA2RGB)
        val mask =
            Mat(Size(mRgbMat.width() / 8.0, mRgbMat.height() / 8.0), CvType.CV_8UC1, Scalar(0.0))
        // Imgproc.dilate(mRgbMat, mRgbMat,mask, Point(0.0,0.0), 5)
        val img = Mat()
        mRgbMat.copyTo(img)
        // grayscale
        val mGreyScaleMat = Mat()
        Imgproc.cvtColor(mRgbMat, mGreyScaleMat, Imgproc.COLOR_RGB2GRAY, 3)
        Imgproc.medianBlur(mGreyScaleMat, mGreyScaleMat, 3)
        val cannyGreyMat = Mat()
        Imgproc.Canny(mGreyScaleMat, cannyGreyMat, cannyMinThres, cannyMinThres * ratio, 3)
        showImage(cannyGreyMat, greyScaleImage)
        //hsv
        val hsvImage = Mat()
        Imgproc.cvtColor(img, hsvImage, Imgproc.COLOR_RGB2HSV)
        //got the hsv values
        val list = ArrayList<Mat>(3)
        Core.split(hsvImage, list)
        val sChannelMat = Mat()
        Core.merge(listOf(list.get(1)), sChannelMat)
        Imgproc.medianBlur(sChannelMat, sChannelMat, 3)
        showImage(sChannelMat, floodFillImage)
        // canny
        val cannyMat = Mat()
        Imgproc.Canny(sChannelMat, cannyMat, cannyMinThres, cannyMinThres * ratio, 3)
        showImage(cannyMat, HSVImage)
        Core.addWeighted(cannyMat, 0.5, cannyGreyMat, 0.5, 0.0, cannyMat)
        Imgproc.dilate(cannyMat, cannyMat, mask, Point(0.0, 0.0), 5)
        showImage(cannyMat, cannyEdgeImage)
        val displayMetrics = DisplayMetrics()
        windowManager.defaultDisplay.getMetrics(displayMetrics)
        val height = displayMetrics.heightPixels
        val width = displayMetrics.widthPixels
        val seedPoint = Point(
            p.x * (mRgbMat.width() / width.toDouble()),
            p.y * (mRgbMat.height() / height.toDouble())
        )
        Imgproc.resize(cannyMat, cannyMat, Size(cannyMat.width() + 2.0, cannyMat.height() + 2.0))
        Imgproc.medianBlur(mRgbMat, mRgbMat, 15)
        val floodFillFlag = 8
        Imgproc.floodFill(
            mRgbMat,
            cannyMat,
            seedPoint,
            Scalar(
                Color.red(chosenColor).toDouble(),
                Color.green(chosenColor).toDouble(),
                Color.blue(chosenColor).toDouble()
            ),
            Rect(),
            Scalar(5.0, 5.0, 5.0),
            Scalar(5.0, 5.0, 5.0),
            floodFillFlag
        )
        // showImage(mRgbMat,floodFillImage)
        Imgproc.dilate(mRgbMat, mRgbMat, mask, Point(0.0, 0.0), 5)
        //got the hsv of the mask image
        val rgbHsvImage = Mat()
        Imgproc.cvtColor(mRgbMat, rgbHsvImage, Imgproc.COLOR_RGB2HSV)
        val list1 = ArrayList<Mat>(3)
        Core.split(rgbHsvImage, list1)
        //merged the "v” of original image with mRgb mat
        val result = Mat()
        Core.merge(listOf(list1.get(0), list1.get(1), list.get(2)), result)
        // converted to rgb
        Imgproc.cvtColor(result, result, Imgproc.COLOR_HSV2RGB)
        Core.addWeighted(result, 0.7, img, 0.3, 0.0, result)
        showImage(result, outputImage)
        return result
    }

    private fun applyTexture(bitmap: Bitmap, p: Point) {
        val cannyMinThres = 30.0
        val ratio = 2.5
        // show intermediate step results
        // grid created here to do that
        showResultLayouts()
        val mRgbMat = Mat()
        Utils.bitmapToMat(bitmap, mRgbMat)
        showImage(mRgbMat, inputImage)
        Imgproc.cvtColor(mRgbMat, mRgbMat, Imgproc.COLOR_RGBA2RGB)
        val mask =
            Mat(Size(mRgbMat.width() / 8.0, mRgbMat.height() / 8.0), CvType.CV_8UC1, Scalar(0.0))
        // Imgproc.dilate(mRgbMat, mRgbMat,mask, Point(0.0,0.0), 5)
        val img = Mat()
        mRgbMat.copyTo(img)
        // grayscale
        val mGreyScaleMat = Mat()
        Imgproc.cvtColor(mRgbMat, mGreyScaleMat, Imgproc.COLOR_RGB2GRAY, 3)
        Imgproc.medianBlur(mGreyScaleMat, mGreyScaleMat, 3)
        val cannyGreyMat = Mat()
        Imgproc.Canny(mGreyScaleMat, cannyGreyMat, cannyMinThres, cannyMinThres * ratio, 3)
        showImage(cannyGreyMat, greyScaleImage)
        //hsv
        val hsvImage = Mat()
        Imgproc.cvtColor(img, hsvImage, Imgproc.COLOR_RGB2HSV)
        //got the hsv values
        val list = ArrayList<Mat>(3)
        Core.split(hsvImage, list)
        val sChannelMat = Mat()
        Core.merge(listOf(list.get(1)), sChannelMat)
        Imgproc.medianBlur(sChannelMat, sChannelMat, 3)
        showImage(sChannelMat, floodFillImage)
        // canny
        val cannyMat = Mat()
        Imgproc.Canny(sChannelMat, cannyMat, cannyMinThres, cannyMinThres * ratio, 3)
        showImage(cannyMat, HSVImage)
        Core.addWeighted(cannyMat, 0.5, cannyGreyMat, 0.5, 0.0, cannyMat)
        Imgproc.dilate(cannyMat, cannyMat, mask, Point(0.0, 0.0), 5)
        val displayMetrics = DisplayMetrics()
        windowManager.defaultDisplay.getMetrics(displayMetrics)
        val height = displayMetrics.heightPixels
        val width = displayMetrics.widthPixels
        val seedPoint = Point(
            p.x * (mRgbMat.width() / width.toDouble()),
            p.y * (mRgbMat.height() / height.toDouble())
        )
        Imgproc.resize(cannyMat, cannyMat, Size(cannyMat.width() + 2.0, cannyMat.height() + 2.0))
        val cannyMat1 = Mat()
        cannyMat.copyTo(cannyMat1)
        val wallMask = Mat(mRgbMat.size(), mRgbMat.type())
        val floodFillFlag = 8
        Imgproc.floodFill(
            wallMask,
            cannyMat,
            seedPoint,
            Scalar(255.0, 255.0, 255.0),
            Rect(),
            Scalar(5.0, 5.0, 5.0),
            Scalar(5.0, 5.0, 5.0),
            floodFillFlag
        )
        showImage(wallMask, greyScaleImage)
        showImage(cannyMat, cannyEdgeImage)
        Imgproc.floodFill(
            mRgbMat,
            cannyMat1,
            seedPoint,
            Scalar(0.0, 0.0, 0.0),
            Rect(),
            Scalar(5.0, 5.0, 5.0),
            Scalar(5.0, 5.0, 5.0),
            floodFillFlag
        )
        showImage(mRgbMat, HSVImage)
        val texture = getTextureImage()
        val textureImgMat = Mat()
        Core.bitwise_and(wallMask, texture, textureImgMat)
        showImage(textureImgMat, floodFillImage)
        val resultImage = Mat()
        Core.bitwise_or(textureImgMat, mRgbMat, resultImage)
        showImage(resultImage, outputImage)
        ////alpha blending
        //got the hsv of the mask image
        val rgbHsvImage = Mat()
        Imgproc.cvtColor(resultImage, rgbHsvImage, Imgproc.COLOR_RGB2HSV)
        val list1 = ArrayList<Mat>(3)
        Core.split(rgbHsvImage, list1)
        //merged the “v” of original image with mRgb mat
        val result = Mat()
        Core.merge(listOf(list1.get(0), list1.get(1), list.get(2)), result)
        // converted to rgb
        Imgproc.cvtColor(result, result, Imgproc.COLOR_HSV2RGB)
        Core.addWeighted(result, 0.8, img, 0.2, 0.0, result)
        showImage(result, outputImage)
    }

}