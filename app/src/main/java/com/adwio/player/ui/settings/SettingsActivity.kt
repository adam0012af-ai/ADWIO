package com.adwio.player.ui.settings

import android.content.Intent
import android.os.Bundle
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import com.adwio.player.data.AppSettings
import com.adwio.player.data.M3uCache
import com.adwio.player.data.PlaybackHistory
import com.adwio.player.databinding.ActivitySettingsBinding
import com.adwio.player.ui.BaseFullscreenActivity
import com.adwio.player.ui.about.AboutActivity

class SettingsActivity : BaseFullscreenActivity() {
 private lateinit var b: ActivitySettingsBinding
 private lateinit var settings: AppSettings
 override fun onCreate(savedInstanceState: Bundle?) {
  super.onCreate(savedInstanceState); b=ActivitySettingsBinding.inflate(layoutInflater); setContentView(b.root); settings=AppSettings(this)
  b.backButton.setOnClickListener { finish() }
  b.generalCard.setOnClickListener { generalSettings() }
  b.streamFormatCard.setOnClickListener { choose("Stream Format", arrayOf("Auto","HLS","MPEG-TS"), arrayOf("auto","hls","ts"), settings.streamFormat){ settings.streamFormat=it } }
  b.timeFormatCard.setOnClickListener { choose("Time Format", arrayOf("System","12-hour","24-hour"), arrayOf("system","12","24"), settings.timeFormat){ settings.timeFormat=it } }
  b.parentalCard.setOnClickListener { parental() }
  b.playerSelectionCard.setOnClickListener { choose("Player Selection", arrayOf("ADWIO Player","External Player"), arrayOf("internal","external"), settings.playerEngine){ settings.playerEngine=it; settings.externalPlayerEnabled=it=="external" } }
  b.playerSettingsCard.setOnClickListener { playerSettings() }
  b.externalPlayerCard.setOnClickListener { settings.externalPlayerEnabled=!settings.externalPlayerEnabled; toast(if(settings.externalPlayerEnabled) "External player enabled" else "External player disabled") }
  b.multiscreenCard.setOnClickListener { settings.multiScreenEnabled=!settings.multiScreenEnabled; toast(if(settings.multiScreenEnabled) "Multi-Screen enabled" else "Multi-Screen disabled") }
  b.speedTestCard.setOnClickListener { speedTest() }
  b.storageCard.setOnClickListener { storage() }
  b.aboutCard.setOnClickListener { startActivity(Intent(this, AboutActivity::class.java)) }
  b.resetCard.setOnClickListener { AlertDialog.Builder(this).setTitle("Reset settings?").setMessage("Restore ADWIO settings to defaults.").setPositiveButton("Reset"){_,_->settings.reset();toast("Settings reset")}.setNegativeButton("Cancel",null).show() }
  b.generalCard.requestFocus()
 }
 private fun generalSettings(){ val labels=arrayOf("Auto-play last channel","Remember movie / episode position","Auto refresh playlists","Background playback","Picture in Picture","Auto next episode"); val checked=booleanArrayOf(settings.autoplayLastChannel,settings.rememberPosition,settings.autoRefresh,settings.backgroundPlayback,settings.pictureInPicture,settings.autoNextEpisode); AlertDialog.Builder(this).setTitle("General Settings").setMultiChoiceItems(labels,checked){_,i,v-> checked[i]=v }.setPositiveButton("Save"){_,_->settings.autoplayLastChannel=checked[0];settings.rememberPosition=checked[1];settings.autoRefresh=checked[2];settings.backgroundPlayback=checked[3];settings.pictureInPicture=checked[4];settings.autoNextEpisode=checked[5]}.setNegativeButton("Cancel",null).show() }
 private fun playerSettings(){ val items=arrayOf("Decoder: ${settings.decoderMode}","Buffer: ${settings.bufferMode}","Aspect: ${settings.aspectMode}","Controls timeout: ${settings.playerControlsTimeoutMs/1000}s"); AlertDialog.Builder(this).setTitle("Player Settings").setItems(items){_,i-> when(i){0->choose("Decoder",arrayOf("Auto","Hardware preferred","Software fallback"),arrayOf("auto","hardware","software"),settings.decoderMode){settings.decoderMode=it};1->choose("Buffer",arrayOf("Small","Normal","Large"),arrayOf("small","normal","large"),settings.bufferMode){settings.bufferMode=it};2->choose("Aspect Ratio",arrayOf("Fit","Fill","Zoom"),arrayOf("fit","fill","zoom"),settings.aspectMode){settings.aspectMode=it};3->choose("Controls timeout",arrayOf("3 sec","4 sec","6 sec","8 sec"),arrayOf("3000","4000","6000","8000"),settings.playerControlsTimeoutMs.toString()){settings.playerControlsTimeoutMs=it.toInt()}} }.show() }
 private fun parental(){ val input=EditText(this).apply{hint="4-digit PIN";inputType=2}; AlertDialog.Builder(this).setTitle(if(settings.parentalPin.isNullOrBlank())"Set Parental PIN" else "Change Parental PIN").setView(input).setPositiveButton("Save"){_,_-> val x=input.text.toString().trim(); if(x.length==4){settings.parentalPin=x;toast("Parental PIN saved")}else toast("PIN must be 4 digits")}.setNeutralButton("Disable"){_,_->settings.parentalPin=null;toast("Parental control disabled")}.setNegativeButton("Cancel",null).show() }
 private fun storage(){ AlertDialog.Builder(this).setTitle("Storage & Cache").setItems(arrayOf("Clear media cache","Clear watch history")){_,i-> if(i==0){cacheDir.deleteRecursively();M3uCache(this).clear();toast("Cache cleared")}else{PlaybackHistory(this).clearCurrentPlaylist();toast("History cleared")} }.show() }
 private fun speedTest(){ toast("Checking connection…"); Thread{ val start=System.currentTimeMillis(); val ok=runCatching{java.net.URL("https://www.google.com/generate_204").openConnection().apply{connectTimeout=4000;readTimeout=4000}.getInputStream().close();true}.getOrDefault(false); val ms=System.currentTimeMillis()-start; runOnUiThread{toast(if(ok)"Connection response: ${ms} ms" else "Connection test failed")}}.start() }
 private fun choose(title:String,labels:Array<String>,keys:Array<String>,current:String,onPick:(String)->Unit){ val idx=keys.indexOf(current).coerceAtLeast(0); AlertDialog.Builder(this).setTitle(title).setSingleChoiceItems(labels,idx){d,i->onPick(keys[i]);d.dismiss()}.show() }
 private fun toast(s:String)=Toast.makeText(this,s,Toast.LENGTH_SHORT).show()
}
