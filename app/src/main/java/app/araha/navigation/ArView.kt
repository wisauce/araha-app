package app.araha.navigation

import android.app.Activity
import android.graphics.BitmapFactory
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.opengl.GLES11Ext
import android.opengl.GLES20.*
import android.opengl.GLSurfaceView
import android.opengl.Matrix
import android.os.Handler
import android.os.Looper
import com.google.ar.core.*
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import app.araha.navigation.core.*
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.atomic.AtomicBoolean
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.*

data class ArUpdate(val position: Vec?, val located: Boolean, val message: String, val terminal: Boolean=false)
data class Guidance(val route: Route, val segment: Int, val enabled: Boolean)

/** One ARCore session survives destination selection and arrival. No pose is reused after closing it. */
class ArView(private val activity: Activity, private val map: FloorMap, width: Float, height: Double,
             private val report: (ArUpdate) -> Unit) : GLSurfaceView(activity), GLSurfaceView.Renderer {
    private val main = Handler(Looper.getMainLooper())
    private val scanner = BarcodeScanning.getClient(BarcodeScannerOptions.Builder().setBarcodeFormats(Barcode.FORMAT_QR_CODE).build())
    private val scanning = AtomicBoolean(false)
    @Volatile private var closed = false
    @Volatile private var qrAt = 0L
    @Volatile var guidance: Guidance? = null
    private val session = Session(activity)
    private val floorFromImage = RigidTransform(map.marker.floorFromImage.toDoubleArray().also { it[13] = height })
    private var worldFromFloor: RigidTransform? = null
    private var floorAnchor: Anchor? = null
    private var stableAt = 0L
    private var stablePose: Pose? = null
    private var lastScan = 0L
    private var lastReport = 0L
    private var texture = 0
    private var cameraProgram = 0
    private var arrowProgram = 0
    private var viewportWidth = 1
    private var viewportHeight = 1
    private val quad = buffer(floatArrayOf(-1f,-1f, 1f,-1f, -1f,1f, 1f,1f))
    private val uv = buffer(FloatArray(8))

    init {
        try {
        val bitmap = activity.assets.open(map.marker.referenceImageName).use { BitmapFactory.decodeStream(it) }
        val database = AugmentedImageDatabase(session)
        try { database.addImage(map.marker.id, bitmap, width) } finally { bitmap.recycle() }
        session.configure(Config(session).apply {
            augmentedImageDatabase = database
            focusMode = Config.FocusMode.AUTO
            planeFindingMode = Config.PlaneFindingMode.DISABLED
            lightEstimationMode = Config.LightEstimationMode.DISABLED
            updateMode = Config.UpdateMode.LATEST_CAMERA_IMAGE
        })
        setEGLContextClientVersion(3)
        preserveEGLContextOnPause = true
        setRenderer(this)
        } catch(e: Exception) { scanner.close();session.close();throw e }
    }
    fun start() { session.resume(); onResume() }
    fun stop() {
        if (closed) return
        closed = true
        onPause() // Wait for the GL thread before pausing/closing the session.
        session.pause()
        if (!scanning.get()) finishClose()
    }
    private fun finishClose() { scanner.close(); session.close() }
    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        val ids = IntArray(1); glGenTextures(1,ids,0); texture=ids[0]
        glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, texture)
        glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,GL_TEXTURE_MIN_FILTER,GL_LINEAR)
        glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,GL_TEXTURE_MAG_FILTER,GL_LINEAR)
        glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,GL_TEXTURE_WRAP_S,GL_CLAMP_TO_EDGE)
        glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,GL_TEXTURE_WRAP_T,GL_CLAMP_TO_EDGE)
        cameraProgram=program("attribute vec2 p;attribute vec2 t;varying vec2 v;void main(){gl_Position=vec4(p,0.,1.);v=t;}",
            "#extension GL_OES_EGL_image_external : require\nprecision mediump float;uniform samplerExternalOES s;varying vec2 v;void main(){gl_FragColor=texture2D(s,v);}")
        arrowProgram=program("attribute vec3 p;uniform mat4 m;void main(){gl_Position=m*vec4(p,1.);}",
            "precision mediump float;void main(){gl_FragColor=vec4(0.20,0.87,0.44,1.);}")
    }
    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) { viewportWidth=width; viewportHeight=height; glViewport(0,0,width,height) }
    override fun onDrawFrame(gl: GL10?) {
        if (closed) return
        try {
            session.setCameraTextureName(texture)
            @Suppress("DEPRECATION") val rotation=activity.windowManager.defaultDisplay.rotation
            session.setDisplayGeometry(rotation,viewportWidth,viewportHeight)
            val frame=session.update(); val now=android.os.SystemClock.elapsedRealtime()
            glClear(GL_COLOR_BUFFER_BIT or GL_DEPTH_BUFFER_BIT)
            if (frame.timestamp != 0L) drawCamera(frame)
            val tracked=frame.camera.trackingState == TrackingState.TRACKING &&
                (floorAnchor == null || floorAnchor?.trackingState == TrackingState.TRACKING)
            if(!tracked) { stableAt=0;stablePose=null }
            // Keep alignment and the route during temporary tracking uncertainty.
            // Do not render against an unavailable camera pose; resume on tracked frames.
            if (worldFromFloor == null && tracked) {
                scan(frame,now)
                val target=session.getAllTrackables(AugmentedImage::class.java).firstOrNull {
                    it.name==map.marker.id && it.trackingState==TrackingState.TRACKING && it.trackingMethod==AugmentedImage.TrackingMethod.FULL_TRACKING
                }
                if(target != null && now-qrAt < 3000 && qrAt>0) {
                    val previous=stablePose
                    val pose=target.centerPose
                    val moved=previous==null || sqrt(previous.translation.zip(pose.translation).sumOf { (a,b)->((a-b)*(a-b)).toDouble() })>.05
                    val rotationDot=previous?.rotationQuaternion?.zip(pose.rotationQuaternion)?.sumOf { (a,b)->(a*b).toDouble() } ?: 0.0
                    val rotated=2*acos(abs(rotationDot).coerceIn(0.0,1.0))>Math.toRadians(3.0)
                    if(stableAt==0L || moved || rotated) { stableAt=now;stablePose=pose }
                    if(now-stableAt>=600) {
                        floorAnchor=session.createAnchor(target.centerPose)
                    }
                } else { stableAt=0;stablePose=null }
            }
            floorAnchor?.takeIf { it.trackingState==TrackingState.TRACKING }?.let {
                val matrix=FloatArray(16);it.pose.toMatrix(matrix,0)
                worldFromFloor=RigidTransform(DoubleArray(16){matrix[it].toDouble()})*floorFromImage.inverse()
            }
            val transform=worldFromFloor
            val position=if(tracked && transform!=null) {
                val p=frame.camera.pose.translation
                val f=transform.inverse().point(p[0].toDouble(),p[1].toDouble(),p[2].toDouble()); Vec(f[0],f[2])
            } else null
            if(position!=null) guidance?.takeIf { it.enabled }?.let { drawGuidance(frame,it,transform!!,position) }
            if(now-lastReport>=100) {
                lastReport=now
                val message=when { !tracked -> "Tracking paused. Hold the phone steady in a well-lit area."
                    transform!=null -> "Location found"
                    now-qrAt<3000 && qrAt>0 -> "QR recognised. Keep the entire sign visible and steady."
                    else -> "Point at the entire toilet start sign" }
                main.post { if(!closed) report(ArUpdate(position,transform!=null,message)) }
            }
        } catch(e: Exception) {
            guidance=null
            main.post { if(!closed) report(ArUpdate(null,false,"Camera error: ${e.message ?: e.javaClass.simpleName}. Begin again at the start sign.",true)) }
        }
    }
    private fun scan(frame: Frame, now: Long) {
        if(now-lastScan<450 || !scanning.compareAndSet(false,true)) return
        val image=try { frame.acquireCameraImage() } catch(_: Exception) { scanning.set(false); return }
        lastScan=now
        try {
            val manager=activity.getSystemService(Activity.CAMERA_SERVICE) as CameraManager
            val sensor=manager.getCameraCharacteristics(session.cameraConfig.cameraId).get(CameraCharacteristics.SENSOR_ORIENTATION) ?: 90
            @Suppress("DEPRECATION") val display=activity.windowManager.defaultDisplay.rotation*90
            scanner.process(InputImage.fromMediaImage(image,(sensor-display+360)%360))
                .addOnSuccessListener { codes -> if(codes.any { it.rawValue==map.marker.id }) qrAt=android.os.SystemClock.elapsedRealtime() }
                .addOnCompleteListener { image.close(); scanning.set(false); if(closed) finishClose() }
        } catch(_: Exception) { image.close(); scanning.set(false) }
    }
    private fun drawCamera(frame: Frame) {
        quad.position(0); uv.position(0)
        frame.transformCoordinates2d(Coordinates2d.OPENGL_NORMALIZED_DEVICE_COORDINATES,quad,Coordinates2d.TEXTURE_NORMALIZED,uv)
        glDisable(GL_DEPTH_TEST); glUseProgram(cameraProgram)
        glActiveTexture(GL_TEXTURE0); glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,texture)
        glUniform1i(glGetUniformLocation(cameraProgram,"s"),0)
        attribute(cameraProgram,"p",2,quad); attribute(cameraProgram,"t",2,uv)
        glDrawArrays(GL_TRIANGLE_STRIP,0,4)
    }
    private fun drawGuidance(frame: Frame, g: Guidance, transform: RigidTransform, position: Vec) {
        if(g.route.points.size<2) return
        val a=g.route.points[g.segment]; val b=g.route.points[g.segment+1]
        val projected=project(position,a,b)
        // Corridor distance is advisory. Keep the current segment visible despite drift.
        val direction=(b-a)*(1.0/a.distance(b)); val side=Vec(-direction.z,direction.x)
        val vertices=mutableListOf<Float>()
        var offset=0.5
        val remaining=projected.point.distance(b)
        while(offset < min(remaining,4.0)) {
            val centre=projected.point+direction*offset
            for(p in listOf(centre+direction*0.22,centre-direction*0.18+side*0.18,centre-direction*0.08,
                           centre+direction*0.22,centre-direction*0.08,centre-direction*0.18-side*0.18)) {
                vertices.add(p.x.toFloat());vertices.add(0.03f);vertices.add(p.z.toFloat())
            }
            offset+=0.8
        }
        if(g.segment==g.route.points.size-2 && remaining<4.0) {
            for(i in 0 until 24) {
                val angle=i*2*Math.PI/24;val next=(i+1)*2*Math.PI/24
                for(p in listOf(b, b+Vec(cos(angle),sin(angle))*.22, b+Vec(cos(next),sin(next))*.22)) {
                    vertices.add(p.x.toFloat());vertices.add(.035f);vertices.add(p.z.toFloat())
                }
            }
        }
        if(vertices.isEmpty()) return
        val projection=FloatArray(16); val view=FloatArray(16); val pv=FloatArray(16);val mvp=FloatArray(16)
        frame.camera.getProjectionMatrix(projection,0,0.1f,30f);frame.camera.getViewMatrix(view,0)
        Matrix.multiplyMM(pv,0,projection,0,view,0);Matrix.multiplyMM(mvp,0,pv,0,transform.floats(),0)
        glUseProgram(arrowProgram);glUniformMatrix4fv(glGetUniformLocation(arrowProgram,"m"),1,false,mvp,0)
        attribute(arrowProgram,"p",3,buffer(vertices.toFloatArray()));glDrawArrays(GL_TRIANGLES,0,vertices.size/3)
    }
    private fun attribute(program: Int, name: String, size: Int, data: java.nio.FloatBuffer) {
        val index=glGetAttribLocation(program,name);data.position(0);glEnableVertexAttribArray(index);glVertexAttribPointer(index,size,GL_FLOAT,false,0,data)
    }
    private fun program(vertex: String, fragment: String): Int {
        fun shader(type: Int, source: String): Int {
            val id=glCreateShader(type);glShaderSource(id,source);glCompileShader(id)
            val ok=IntArray(1);glGetShaderiv(id,GL_COMPILE_STATUS,ok,0);check(ok[0]!=0){glGetShaderInfoLog(id)};return id
        }
        val v=shader(GL_VERTEX_SHADER,vertex);val f=shader(GL_FRAGMENT_SHADER,fragment)
        val p=glCreateProgram();glAttachShader(p,v);glAttachShader(p,f);glLinkProgram(p)
        val ok=IntArray(1);glGetProgramiv(p,GL_LINK_STATUS,ok,0);check(ok[0]!=0){glGetProgramInfoLog(p)}
        glDeleteShader(v);glDeleteShader(f);return p
    }
    private fun buffer(values: FloatArray)=ByteBuffer.allocateDirect(values.size*4).order(ByteOrder.nativeOrder()).asFloatBuffer().apply { put(values);position(0) }
}
