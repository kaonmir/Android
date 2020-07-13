# Android
### Preview.kt
Camera preview class
Add TextureView in XML, change its id to `camera_preview`
``` kotlin
<!-- MainActivity -->
mCameraTextureView = findViewById(R.id.camera_preview)
mPreview = Preview(this, mCameraTextureView)
```
