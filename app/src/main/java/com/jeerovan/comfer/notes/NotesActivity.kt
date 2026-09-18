package com.jeerovan.comfer.notes

import android.app.ActivityOptions
import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.*
import androidx.lifecycle.lifecycleScope
import com.jeerovan.comfer.R
import com.jeerovan.comfer.ui.theme.ComferTheme
import kotlinx.coroutines.*

class NotesActivity : AppCompatActivity() {
    private val model: NotesViewModel by viewModels()
    private var authenticating=false
    private var blocked by mutableStateOf(true)
    private var afterAuthentication:(()->Unit)?=null
    private var shareConsumed=false
    private val authenticate=registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        authenticating=false
        if(result.resultCode==RESULT_OK){NotesSession.authorize();blocked=false;model.start(shared());val action=afterAuthentication;afterAuthentication=null;action?.invoke()}
        else {afterAuthentication=null;finish()}
    }
    private fun shared():String? {
        if(shareConsumed||intent.action!=Intent.ACTION_SEND)return null
        shareConsumed=true
        return intent.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString()
    }
    private fun unlock(action:()->Unit = {}) {
        if(isFinishing || isDestroyed || authenticating)return
        val request=getSystemService(KeyguardManager::class.java).createConfirmDeviceCredentialIntent("Unlock Notes","Use your device PIN, pattern or password")
        if(request==null){model.error="Set a device PIN, pattern or password before protecting Notes.";return}
        afterAuthentication=action;authenticating=true;blocked=true;authenticate.launch(request)
    }
    private fun resumeNotes() { lifecycleScope.launch {
        val locked=withContext(Dispatchers.IO){NotesBackup.requiresAuthentication(this@NotesActivity)}
        if(isFinishing || isDestroyed || !lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED))return@launch
        blocked=(locked || model.draft?.note?.protected == true)&&!NotesSession.unlocked()
        if(blocked){if(!authenticating)unlock()} else model.start(shared())
    } }
    override fun onCreate(savedInstanceState:Bundle?) {
        super.onCreate(savedInstanceState);if(savedInstanceState==null)NotesSession.lock();shareConsumed=savedInstanceState?.getBoolean("shareConsumed")?:false;enableEdgeToEdge()
        setContent { ComferTheme {
            val sensitive=blocked||model.sensitive
            SideEffect { if(sensitive)window.addFlags(WindowManager.LayoutParams.FLAG_SECURE) else window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE) }
            LaunchedEffect(Unit) { while(true){delay(1000);if(lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED)&&!NotesSession.unlocked()&&model.sensitive&&!authenticating){model.background();resumeNotes()}} }
            NotesScreen(model,::finish, { action -> unlock(action) }, blocked)
        } }
    }
    override fun onResume(){super.onResume();if(!authenticating)resumeNotes()}
    override fun onPause(){if(!authenticating && model.ready)model.retry();super.onPause()}
    override fun onStop(){if(!authenticating){model.background();blocked=true};super.onStop()}
    override fun onSaveInstanceState(outState:Bundle){outState.putBoolean("shareConsumed",shareConsumed);super.onSaveInstanceState(outState)}
    override fun onNewIntent(intent:Intent){super.onNewIntent(intent);setIntent(intent);shareConsumed=false;resumeNotes()}
    companion object {
        fun open(context:Context){context.startActivity(Intent(context,NotesActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            ActivityOptions.makeCustomAnimation(context,R.anim.notification_inbox_enter,R.anim.notification_inbox_underlay).toBundle())}
    }
}

