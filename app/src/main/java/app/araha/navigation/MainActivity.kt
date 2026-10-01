package app.araha.navigation

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import app.araha.navigation.core.*
import com.google.ar.core.ArCoreApk
import java.util.Locale
import kotlin.math.min

private val Orange=Color(0xFFFE6F42)
private val Ink=Color(0xFF374957)
private val Paper=Color(0xFFF5F6F6)
private val Green=Color(0xFF238650)
private enum class Screen { HOME, DESTINATIONS, SCAN, FOUND, NAVIGATE, ARRIVAL, SETTINGS }

class MainActivity : ComponentActivity() {
    private lateinit var map: FloorMap
    private var loadError: String? = null
    private var screen by mutableStateOf(Screen.HOME)
    private var arView by mutableStateOf<ArView?>(null)
    private var current by mutableStateOf<Vec?>(null)
    private var located by mutableStateOf(false)
    private var status by mutableStateOf("")
    private var error by mutableStateOf<String?>(null)
    private var destination by mutableStateOf<Destination?>(null)
    private var route by mutableStateOf<Route?>(null)
    private var progress by mutableStateOf<Progress?>(null)
    private var progressTracker: RouteProgress? = null
    private var waitingForInstall=false
    private var installRequested=false
    private val preferences by lazy { getSharedPreferences("marker-calibration",MODE_PRIVATE) }
    private val permission=registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if(granted) beginSession() else error="Camera permission is needed to scan and navigate. Enable it in Android settings."
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            map=assets.open("hotel-floor.json").bufferedReader().use { FloorMap.parse(it.readText()) }
            for(asset in listOf(map.marker.referenceImageName,map.mapImage.asset)) assets.open(asset).use { stream ->
                val options=BitmapFactory.Options().apply { inJustDecodeBounds=true }
                BitmapFactory.decodeStream(stream,null,options)
                require(options.outWidth>0 && options.outHeight>0) { "Invalid image asset: $asset" }
            }
        }
        catch(e: Exception) { loadError="The bundled floor map could not be loaded: ${e.message}" }
        setContent {
            MaterialTheme(colorScheme=lightColorScheme(primary=Orange,onPrimary=Color.White,background=Paper,surface=Color.White,onSurface=Ink,onBackground=Ink)) {
                Surface(Modifier.fillMaxSize(),color=Paper) {
                    if(loadError!=null) Box(Modifier.padding(24.dp)) { Text(loadError!!) } else App()
                }
            }
        }
    }
    override fun onResume() {
        super.onResume()
        if(waitingForInstall) { waitingForInstall=false;beginSession() }
    }
    override fun onPause() {
        // A fresh scan is required after leaving the foreground; stale AR poses are never persisted.
        if(arView!=null) { endSession();status="Session ended while the app was in the background. Scan again to start.";screen=Screen.HOME }
        super.onPause()
    }
    private fun endSession() {
        arView?.stop();arView=null;current=null;located=false;route=null;progress=null;progressTracker=null
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }
    private fun beginSession() {
        error=null
        if(arView!=null) { screen=if(located) Screen.FOUND else Screen.SCAN;return }
        if(!preferences.contains("width-mm")) { screen=Screen.SETTINGS;return }
        if(checkSelfPermission(Manifest.permission.CAMERA)!=PackageManager.PERMISSION_GRANTED) { permission.launch(Manifest.permission.CAMERA);return }
        try {
            val install=ArCoreApk.getInstance().requestInstall(this,!installRequested)
            if(install==ArCoreApk.InstallStatus.INSTALL_REQUESTED) { installRequested=true;waitingForInstall=true;return }
            val view=ArView(this,map,preferences.getFloat("width-mm",60f)/1000f,preferences.getFloat("height-m",1f).toDouble(),::onArUpdate)
            try { view.start() } catch(e: Exception) { view.stop();throw e }
            arView=view;screen=Screen.SCAN;status="Point at the entire toilet start sign"
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } catch(e: Exception) { error="Unable to start AR. Use an ARCore-supported Android phone with Google Play Services for AR installed. ${e.message ?: e.javaClass.simpleName}" }
    }
    private fun onArUpdate(update: ArUpdate) {
        if(update.terminal) { endSession();status=update.message;screen=Screen.HOME;return }
        current=update.position;status=update.message;located=update.located
        if(screen==Screen.SCAN && update.located && update.position!=null) screen=Screen.FOUND
        if(screen==Screen.NAVIGATE) {
            if(update.position!=null) {
                val p=progressTracker?.update(update.position!!,android.os.SystemClock.elapsedRealtime());progress=p
                arView?.guidance=route?.let { Guidance(it,p?.segment ?: 0,true) }
                if(p?.arrived==true) { screen=Screen.ARRIVAL;arView?.guidance=null }
            } else { arView?.guidance=null;progressTracker?.interruptArrival() }
        } else arView?.guidance=null
    }
    private fun startRoute() {
        val p=current ?: run { error="Wait for camera tracking before starting.";return }
        val goal=destination ?: return
        try {
            route=Router(map).route(p,goal.id);progressTracker=RouteProgress(route!!,map.arrivalRadiusMeters)
            progress=progressTracker!!.update(p,android.os.SystemClock.elapsedRealtime());screen=Screen.NAVIGATE;error=null
        } catch(e: Exception) { error=e.message ?: "Unable to calculate a route" }
    }
    private fun back() {
        when(screen) {
            Screen.HOME -> if(arView!=null) { endSession() } else finish()
            Screen.SETTINGS -> screen=Screen.HOME
            Screen.DESTINATIONS -> screen=if(arView!=null && located) Screen.FOUND else Screen.HOME
            Screen.SCAN -> { endSession();screen=Screen.DESTINATIONS }
            Screen.FOUND -> screen=Screen.DESTINATIONS
            Screen.NAVIGATE,Screen.ARRIVAL -> { arView?.guidance=null;screen=Screen.DESTINATIONS }
        }
    }
    @Composable private fun App() {
        BackHandler { back() }
        Box(Modifier.fillMaxSize()) {
            val active=arView
            if(active!=null) AndroidView(factory={active},modifier=Modifier.fillMaxSize())
            when(screen) {
                Screen.HOME -> Home()
                Screen.DESTINATIONS -> Destinations()
                Screen.SETTINGS -> Calibration()
                else -> CameraScreen()
            }
            if(error!=null) AlertDialog(onDismissRequest={error=null},title={Text("Unable to continue")},text={Text(error!!)},
                confirmButton={TextButton(onClick={error=null}) { Text("OK") }},
                dismissButton={if(checkSelfPermission(Manifest.permission.CAMERA)!=PackageManager.PERMISSION_GRANTED) TextButton(onClick={
                    startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))
                }) { Text("Settings") }})
        }
    }
    @Composable private fun Header(title: String, showBack: Boolean=true) {
        Row(Modifier.fillMaxWidth().padding(vertical=12.dp),verticalAlignment=Alignment.CenterVertically) {
            if(showBack) TextButton(onClick={back()}) { Text("‹",fontSize=32.sp,color=Ink) }
            Text(title,fontSize=22.sp,fontWeight=FontWeight.SemiBold,modifier=Modifier.weight(1f))
            if(arView!=null) TextButton(onClick={endSession();screen=Screen.HOME}) { Text("End") }
        }
    }
    @Composable private fun Action(label: String, enabled: Boolean=true, click: ()->Unit) {
        Button(onClick=click,enabled=enabled,shape=RoundedCornerShape(16.dp),modifier=Modifier.fillMaxWidth().heightIn(min=56.dp)) {
            Text(label,fontSize=16.sp,fontWeight=FontWeight.SemiBold)
        }
    }
    @Composable private fun Home() {
        Column(Modifier.fillMaxSize().background(Paper).safeDrawingPadding().padding(24.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(20.dp)) {
            Header("ARAHA",false)
            Text("Find your way\nwith confidence.",fontSize=34.sp,lineHeight=41.sp,fontWeight=FontWeight.Bold)
            Text("Indoor navigation",fontSize=18.sp)
            Card(colors=CardDefaults.cardColors(containerColor=Color.White),shape=RoundedCornerShape(24.dp)) {
                Column(Modifier.padding(20.dp),verticalArrangement=Arrangement.spacedBy(14.dp)) {
                    Text(map.floorName,fontWeight=FontWeight.SemiBold,fontSize=20.sp)
                    FloorPlan(null,Modifier.fillMaxWidth().height(220.dp))
                    Text("Toilet → Grand Ballroom",fontWeight=FontWeight.Medium)
                    Text("Choose your destination, scan the start sign, and follow the arrows.")
                }
            }
            Action("Choose destination") { screen=Screen.DESTINATIONS }
            TextButton(onClick={screen=Screen.SETTINGS}) { Text("Set up the iPhone marker") }
            if(status.isNotBlank()) Text(status,fontSize=13.sp)
            Text("Prototype · Floor plan coordinates require an on-site check.",fontSize=12.sp,color=Ink.copy(alpha=.7f))
        }
    }
    @Composable private fun Destinations() {
        var query by remember { mutableStateOf("") }
        Column(Modifier.fillMaxSize().background(Paper).safeDrawingPadding().padding(24.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(20.dp)) {
            Header("Choose destination")
            Text(map.floorName,color=Ink.copy(alpha=.7f))
            OutlinedTextField(value=query,onValueChange={query=it},label={Text("Search rooms")},singleLine=true,shape=RoundedCornerShape(16.dp),modifier=Modifier.fillMaxWidth())
            val rooms=map.rooms.filter { it.label.contains(query,true) }
            if(rooms.isEmpty()) Text("No matching rooms on this floor.")
            for(room in rooms) Card(onClick={destination=room;if(located) screen=Screen.FOUND else beginSession()},
                shape=RoundedCornerShape(24.dp),colors=CardDefaults.cardColors(containerColor=Color.White),modifier=Modifier.fillMaxWidth()) {
                Column(Modifier.padding(24.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                    Text("↗",color=Orange,fontSize=36.sp)
                    Text(room.label,fontWeight=FontWeight.SemiBold,fontSize=22.sp)
                    Text(room.description)
                    Text(if(located) "Navigate from current position →" else "Scan start sign →",color=Orange)
                }
            }
            if(located) Text(if(current!=null) "Your live location is still active. No new scan needed." else status,fontSize=14.sp)
        }
    }
    @Composable private fun CameraScreen() {
        Box(Modifier.fillMaxSize().safeDrawingPadding()) {
            Column(Modifier.align(Alignment.TopCenter).padding(20.dp).background(Color.White,RoundedCornerShape(20.dp)).padding(horizontal=12.dp)) {
                Header(destination?.label ?: "Scan start sign")
                if(screen==Screen.NAVIGATE) {
                    Text(if(current==null) "Finding your position…" else progress?.instruction ?: "Continue straight",fontSize=22.sp,fontWeight=FontWeight.Bold,modifier=Modifier.padding(horizontal=12.dp))
                    Text(if(current!=null) "In ${metres(progress?.nextDistance ?: 0.0)}" else "Your route is saved. Guidance will resume automatically.",modifier=Modifier.padding(12.dp))
                }
            }
            if(screen==Screen.SCAN) Canvas(Modifier.align(Alignment.Center).size(220.dp)) {
                val length=32.dp.toPx();val stroke=4.dp.toPx()
                for(x in listOf(0f,size.width)) for(y in listOf(0f,size.height)) {
                    drawLine(Color.White,Offset(x,y),Offset(x+if(x==0f) length else -length,y),stroke)
                    drawLine(Color.White,Offset(x,y),Offset(x,y+if(y==0f) length else -length),stroke)
                }
            }
            Surface(Modifier.align(Alignment.BottomCenter).fillMaxWidth(),shape=RoundedCornerShape(topStart=32.dp,topEnd=32.dp),color=Color.White) {
                Column(Modifier.padding(24.dp).heightIn(max=460.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(14.dp)) {
                    when(screen) {
                        Screen.SCAN -> {
                            Text("Find your starting point",fontSize=24.sp,fontWeight=FontWeight.Bold)
                            Text(status)
                            Text("Keep the entire sign on the fixed iPhone screen visible. Move the Android phone slightly to help it recognise the image.",fontSize=14.sp)
                        }
                        Screen.FOUND -> {
                            Text("✓  Location found",color=Green,fontSize=24.sp,fontWeight=FontWeight.Bold)
                            Text(destination?.label ?: "Choose a destination")
                            FloorPlan(current,Modifier.fillMaxWidth().height(150.dp))
                            if(current==null) Text(status)
                            Action("Start navigation",current!=null && destination!=null) { startRoute() }
                            TextButton(onClick={screen=Screen.DESTINATIONS}) { Text("Change destination") }
                        }
                        Screen.NAVIGATE -> {
                            Text("Next step",fontSize=14.sp,color=Ink.copy(alpha=.6f))
                            Text(progress?.instruction ?: "Follow the route",fontSize=24.sp,fontWeight=FontWeight.SemiBold)
                            Text(if(current!=null) "${metres(progress?.remaining ?: 0.0)} remaining · ${destination?.label}" else "Waiting for camera tracking. No new scan is needed.")
                            if(current!=null && progress?.onRoute==false) Text("Position is approximate. Navigation remains active.",fontSize=13.sp)
                            TextButton(onClick={arView?.guidance=null;screen=Screen.DESTINATIONS}) { Text("Change destination") }
                        }
                        Screen.ARRIVAL -> {
                            Text("✓",fontSize=48.sp,color=Green)
                            Text("You have arrived!",fontSize=28.sp,fontWeight=FontWeight.Bold)
                            Text("${destination?.label} · Entrance")
                            Text("Your location stays active while this session is open.")
                            Action("Choose another destination") { screen=Screen.DESTINATIONS }
                            TextButton(onClick={endSession();screen=Screen.HOME}) { Text("Finish navigation") }
                        }
                        else -> Unit
                    }
                }
            }
        }
    }
    @Composable private fun Calibration() {
        var width by remember { mutableStateOf(if(preferences.contains("width-mm")) preferences.getFloat("width-mm",60f).toString() else "") }
        var height by remember { mutableStateOf(preferences.getFloat("height-m",1f).toString()) }
        val w=width.toFloatOrNull();val h=height.toFloatOrNull()
        Column(Modifier.fillMaxSize().background(Paper).safeDrawingPadding().padding(24.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(20.dp)) {
            Header("Marker setup")
            Text("Display delivery/start-marker.png on the iPhone 13. Fix it upright on the wall directly opposite the toilet’s lower doorway, with the screen facing the doorway (east on the plan).")
            Text("Measure the full displayed image width, including its patterned border—not the phone or only the QR square.")
            OutlinedTextField(width,{width=it},label={Text("Displayed image width (mm)")},keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Decimal),singleLine=true,modifier=Modifier.fillMaxWidth())
            OutlinedTextField(height,{height=it},label={Text("Image centre above floor (m)")},keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Decimal),singleLine=true,modifier=Modifier.fillMaxWidth())
            Text("Marker location: the red dot across the corridor from the lower toilet doorway. Stand between the sign and the doorway to scan it. Default image-centre height: 1 m.",fontSize=14.sp)
            Action("Save and continue",w!=null && w.isFinite() && w in 20f..500f && h!=null && h.isFinite() && h in .2f..2.5f) {
                preferences.edit().putFloat("width-mm",w!!).putFloat("height-m",h!!).apply()
                if(destination!=null) beginSession() else screen=Screen.HOME
            }
        }
    }
    @Composable private fun FloorPlan(position: Vec?, modifier: Modifier) {
        val bitmap=remember { assets.open(map.mapImage.asset).use { BitmapFactory.decodeStream(it) }.asImageBitmap() }
        Box(modifier) {
            Image(bitmap,"Floor plan of the second floor",modifier=Modifier.fillMaxSize())
            Canvas(Modifier.fillMaxSize()) {
                val scale=min(size.width/map.mapImage.widthPixels,size.height/map.mapImage.heightPixels)
                val left=(size.width-map.mapImage.widthPixels*scale)/2;val top=(size.height-map.mapImage.heightPixels*scale)/2
                fun point(v: Vec)=Offset(left+((v.x/map.mapImage.metersPerPixel+map.mapImage.originPixel[0])*scale).toFloat(),top+((v.z/map.mapImage.metersPerPixel+map.mapImage.originPixel[1])*scale).toFloat())
                val nodes=map.nodes.associateBy { it.id }
                for(e in map.edges) drawLine(Orange,point(nodes.getValue(e.fromNodeId).point()),point(nodes.getValue(e.toNodeId).point()),3.dp.toPx())
                if(position!=null) { drawCircle(Color.White,7.dp.toPx(),point(position));drawCircle(Color(0xFF237CCD),5.dp.toPx(),point(position)) }
            }
        }
    }
    private fun metres(value: Double)=String.format(Locale.US,"%.1f m",value)
}
