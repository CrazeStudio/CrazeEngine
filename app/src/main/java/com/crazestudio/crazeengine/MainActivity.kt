package com.crazestudio.crazeengine

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.speech.RecognizerIntent
import android.util.Base64
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class Message(val id:String=UUID.randomUUID().toString(),val role:String,val text:String,val image:String?=null,val mime:String?=null)
data class Conversation(val id:String,val title:String,val messages:List<Message>)
private val ORANGE=Color(0xFFFF7A00); private val DARK=Color(0xFF09090B); private val SURFACE=Color(0xFF151517); private val CARD=Color(0xFF1D1D21)
private const val PREF="craze_ai"; private const val API="api"; private const val MODEL="model"; private const val PROMPT="prompt"; private const val HISTORY="history"; private const val THEME="dark"

class MainActivity:ComponentActivity(){override fun onCreate(b:Bundle?){super.onCreate(b);setContent{CrazeAiApp()}}}

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun CrazeAiApp(){
 val ctx=LocalContext.current; val prefs=remember{ctx.getSharedPreferences(PREF,Context.MODE_PRIVATE)}; val scope=rememberCoroutineScope(); val clip=LocalClipboardManager.current
 var conversations by remember{mutableStateOf(load(prefs))}; var currentId by remember{mutableStateOf(conversations.firstOrNull()?.id ?: "")}; var page by remember{mutableStateOf("chat")}
 var input by remember{mutableStateOf("")}; var apiKey by remember{mutableStateOf(prefs.getString(API,"") ?: "")}; var model by remember{mutableStateOf(prefs.getString(MODEL,"gemini-2.5-flash") ?: "gemini-2.5-flash")}; var prompt by remember{mutableStateOf(prefs.getString(PROMPT,"You are CrazeAi, a helpful and accurate AI assistant.") ?: "")}; var dark by remember{mutableStateOf(prefs.getBoolean(THEME,true))}; var loading by remember{mutableStateOf(false)}; var error by remember{mutableStateOf<String?>(null)}; var image by remember{mutableStateOf<Pair<String,String>?>(null)}; var fileName by remember{mutableStateOf<String?>(null)}; var job by remember{mutableStateOf<Job?>(null)}
 val list=rememberLazyListState(); val current=conversations.firstOrNull{it.id==currentId} ?: Conversation("","New chat",emptyList())
 fun save(){saveAll(prefs,conversations)}; fun newChat(){val c=Conversation(UUID.randomUUID().toString(),"New chat",emptyList());conversations=listOf(c)+conversations;currentId=c.id;page="chat";input="";image=null;fileName=null;save()}; fun update(ms:List<Message>){val title=ms.firstOrNull{it.role=="user"}?.text?.take(36)?.ifBlank{"New chat"}?:current.title;conversations=conversations.map{if(it.id==currentId)it.copy(title=title,messages=ms)else it};save()}
 fun send(){if(loading)return;if(apiKey.isBlank()){page="settings";error="Add your Gemini API key in Settings first.";return};if(input.isBlank()&&image==null)return;val u=Message(role="user",text=input.trim(),image=image?.first,mime=image?.second);val next=current.messages+u;update(next);input="";image=null;fileName=null;error=null;loading=true;job=scope.launch{runCatching{Gemini.chat(apiKey,model,prompt,next)}.onSuccess{update(next+Message(role="model",text=it))}.onFailure{error=it.message?:"Request failed"};loading=false;job=null}}
 val imagePicker=rememberLauncherForActivityResult(ActivityResultContracts.GetContent()){u:Uri?->if(u!=null)scope.launch{runCatching{readImage(ctx,u)}.onSuccess{image=it}.onFailure{error="Image error: ${it.message}"}}}
 val filePicker=rememberLauncherForActivityResult(ActivityResultContracts.GetContent()){u:Uri?->if(u!=null)scope.launch{runCatching{readText(ctx,u)}.onSuccess{input=if(input.isBlank())it.second else input+"\n\n"+it.second;fileName=it.first}.onFailure{error="File error: ${it.message}"}}}
 val speech=rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()){r->r.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()?.let{input=if(input.isBlank())it else "$input $it"}}
 fun voice(){if(ctx.checkSelfPermission(Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED){(ctx as? Activity)?.requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO),41)}else speech.launch(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,RecognizerIntent.LANGUAGE_MODEL_FREE_FORM))}
 val bg=if(dark)DARK else Color(0xFFF7F7F8); val fg=if(dark)Color.White else Color(0xFF171717)
 MaterialTheme(if(dark)darkColorScheme(primary=ORANGE,background=bg,surface=SURFACE) else lightColorScheme(primary=Color(0xFFB95700),background=bg,surface=Color.White)){
  Scaffold(containerColor=bg,topBar={CenterAlignedTopAppBar(title={Text(if(page=="chat")current.title else if(page=="history")"Conversations" else"Settings",maxLines=1,overflow=TextOverflow.Ellipsis,fontWeight=FontWeight.SemiBold)},navigationIcon={IconButton({if(page=="chat")page="history"else page="chat"}){Text(if(page=="chat")"☰"as String else"‹",color=ORANGE,fontSize=22.sp)}},actions={IconButton({newChat()}){Text("＋",fontSize=24.sp)}},colors=TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor=bg))},bottomBar={NavigationBar(containerColor=if(dark)SURFACE else Color.White){NavigationBarItem(page=="chat",{page="chat"},{Text("⌂")},{Text("Chat")});NavigationBarItem(page=="history",{page="history"},{Text("◷")},{Text("Chats")});NavigationBarItem(page=="settings",{page="settings"},{Text("⚙")},{Text("Settings")})}}){pad->
   when(page){"settings"->Settings(apiKey,{apiKey=it;prefs.edit().putString(API,it).apply()},model,{model=it;prefs.edit().putString(MODEL,it).apply()},prompt,{prompt=it;prefs.edit().putString(PROMPT,it).apply()},dark,{dark=it;prefs.edit().putBoolean(THEME,it).apply()});"history"->History(conversations,currentId,{currentId=it;page="chat"},{id->conversations=conversations.filterNot{it.id==id};if(currentId==id)currentId=conversations.firstOrNull()?.id?:"";save()},{newChat()});else->Chat(current.messages,input,{input=it},loading,error,image!=null,fileName,{imagePicker.launch("image/*")},{filePicker.launch("text/*")},{voice()},{send()},{job?.cancel();job=null;loading=false},{clip.setText(AnnotatedString(it))},{text->share(ctx,text)},list,fg,pad)}
  }
 }
}

@Composable fun Chat(messages:List<Message>,input:String,setInput:(String)->Unit,loading:Boolean,error:String?,hasImage:Boolean,file:String?,pickImage:()->Unit,pickFile:()->Unit,voice:()->Unit,send:()->Unit,stop:()->Unit,copy:(String)->Unit,regen:(Int)->Unit,list:LazyListState,fg:Color,pad:PaddingValues){Column(Modifier.fillMaxSize().padding(pad)){if(messages.isEmpty())Welcome();LazyColumn(Modifier.weight(1f).fillMaxWidth(),state=list,contentPadding=PaddingValues(12.dp),verticalArrangement=Arrangement.spacedBy(9.dp)){itemsIndexed(messages,key={_,m->m.id}){i,m->Bubble(m,{copy(m.text)},{if(m.role=="model")regen(i)})};if(loading)item{Row(Modifier.padding(12.dp),verticalAlignment=Alignment.CenterVertically){CircularProgressIndicator(Modifier.size(18.dp),color=ORANGE,strokeWidth=2.dp);Spacer(Modifier.width(9.dp));Text("CrazeAi is thinking…",color=fg)}}};error?.let{Text(it,color=MaterialTheme.colorScheme.error,modifier=Modifier.padding(10.dp),fontSize=12.sp)};if(hasImage||file!=null)Text(if(hasImage)"🖼 Image attached"else"📎 $file",color=Color.Gray,modifier=Modifier.padding(horizontal=14.dp));Composer(input,setInput,loading,pickImage,pickFile,voice,send,stop)}}

@Composable fun Welcome(){Column(Modifier.fillMaxSize().padding(28.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.Center){Box(Modifier.size(86.dp).clip(RoundedCornerShape(26.dp)).background(ORANGE),contentAlignment=Alignment.Center){Text("⚡",fontSize=46.sp,color=Color.Black)};Spacer(Modifier.height(18.dp));Text("CrazeAi",fontSize=34.sp,fontWeight=FontWeight.Bold);Text("Think faster. Build more.",color=Color.Gray);Spacer(Modifier.height(18.dp));Text("Ask questions, write code, analyze ideas, or attach an image.",color=Color.Gray,fontSize=14.sp)}}

@Composable fun Bubble(m:Message,copy:()->Unit,regen:()->Unit){val user=m.role=="user";Column(Modifier.fillMaxWidth(),horizontalAlignment=if(user)Alignment.End else Alignment.Start){Surface(color=if(user)ORANGE else CARD,shape=RoundedCornerShape(20.dp),modifier=Modifier.widthIn(max=390.dp)){Column(Modifier.padding(14.dp)){if(m.image!=null)Text("🖼 Image attached",fontSize=12.sp,color=if(user)Color.Black else Color.Gray);Markdown(m.text,if(user)Color.Black else Color.White);if(!user)Row{TextButton(copy){Text("Copy")};TextButton(regen){Text("Regenerate")};val c=LocalContext.current;TextButton({share(c,m.text)}){Text("Share")}}}}}}
@Composable fun Markdown(text:String,color:Color){text.split("```").forEachIndexed{i,b->if(b.isNotBlank())if(i%2==1)Surface(color=Color(0xFF0D0D0F),shape=RoundedCornerShape(10.dp)){Text(b.trim(),Modifier.padding(12.dp),color=Color(0xFFE8E8E8),fontSize=13.sp)}else Text(b.trim(),color=color,lineHeight=21.sp,modifier=Modifier.padding(bottom=4.dp))}}
@Composable fun Composer(v:String,set:(String)->Unit,loading:Boolean,image:()->Unit,file:()->Unit,voice:()->Unit,send:()->Unit,stop:()->Unit){Surface(color=SURFACE){Column(Modifier.padding(8.dp)){OutlinedTextField(v,set,Modifier.fillMaxWidth(),placeholder={Text("Message CrazeAi…")},maxLines=6,shape=RoundedCornerShape(20.dp),trailingIcon={IconButton(if(loading)stop else send){Text(if(loading)"■"else"➤",color=ORANGE,fontSize=21.sp)}});Row{IconButton(image){Text("＋")};TextButton(file){Text("File")};TextButton(voice){Text("Voice")};Spacer(Modifier.weight(1f));Text("Gemini",color=Color.Gray,fontSize=12.sp)}}}}

@Composable fun History(cs:List<Conversation>,current:String,open:(String)->Unit,delete:(String)->Unit,new:()->Unit){Column(Modifier.fillMaxSize().padding(16.dp)){Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){Text("Conversations",fontSize=28.sp,fontWeight=FontWeight.Bold,modifier=Modifier.weight(1f));Button(new){Text("New")}};Spacer(Modifier.height(12.dp));LazyColumn(verticalArrangement=Arrangement.spacedBy(8.dp)){items(cs,key={it.id}){c->Surface(color=if(c.id==current)CARD else SURFACE,shape=RoundedCornerShape(16.dp),modifier=Modifier.fillMaxWidth().clickable{open(c.id)}){Row(Modifier.padding(14.dp),verticalAlignment=Alignment.CenterVertically){Column(Modifier.weight(1f)){Text(c.title,maxLines=1,overflow=TextOverflow.Ellipsis);Text("${c.messages.size} messages",color=Color.Gray,fontSize=12.sp)};TextButton({delete(c.id)}){Text("Delete")}}}}}}}}

@Composable fun Settings(key:String,setKey:(String)->Unit,model:String,setModel:(String)->Unit,prompt:String,setPrompt:(String)->Unit,dark:Boolean,setDark:(Boolean)->Unit){LazyColumn(Modifier.fillMaxSize().padding(18.dp),verticalArrangement=Arrangement.spacedBy(14.dp)){item{Text("CrazeAi",fontSize=30.sp,fontWeight=FontWeight.Bold);Text("AI assistant settings",color=Color.Gray)};item{Card("Gemini API","Stored locally on this device."){OutlinedTextField(key,setKey,Modifier.fillMaxWidth(),label={Text("API key")},visualTransformation=PasswordVisualTransformation(),singleLine=true)}};item{Card("Model","Example: gemini-2.5-flash"){OutlinedTextField(model,setModel,Modifier.fillMaxWidth(),label={Text("Model name")},singleLine=true)}};item{Card("Assistant"){OutlinedTextField(prompt,setPrompt,Modifier.fillMaxWidth(),label={Text("System prompt")},minLines=4)}};item{Card("Appearance"){Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){Text("Dark mode",Modifier.weight(1f));Switch(dark,setDark)}}};item{Text("CrazeAi • Gemini-powered",color=Color.Gray,fontSize=13.sp)}}}
@Composable fun Card(title:String,sub:String="",content:@Composable()->Unit){Surface(color=SURFACE,shape=RoundedCornerShape(18.dp),modifier=Modifier.fillMaxWidth()){Column(Modifier.padding(15.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){Text(title,fontWeight=FontWeight.SemiBold,fontSize=17.sp);if(sub.isNotBlank())Text(sub,color=Color.Gray,fontSize=12.sp);content()}}}

fun startId():String=UUID.randomUUID().toString()
fun saveAll(p:android.content.SharedPreferences,cs:List<Conversation>){val a=JSONArray();cs.take(50).forEach{c->val o=JSONObject().put("id",c.id).put("title",c.title);val ms=JSONArray();c.messages.takeLast(100).forEach{m->ms.put(JSONObject().put("id",m.id).put("role",m.role).put("text",m.text))};o.put("messages",ms);a.put(o)};p.edit().putString(HISTORY,a.toString()).apply()}
fun load(p:android.content.SharedPreferences):List<Conversation>=runCatching{val a=JSONArray(p.getString(HISTORY,"[]"));(0 until a.length()).map{i->val o=a.getJSONObject(i);val ms=o.optJSONArray("messages")?:JSONArray();Conversation(o.getString("id"),o.optString("title","New chat"),(0 until ms.length()).map{j->val m=ms.getJSONObject(j);Message(m.optString("id",startId()),m.getString("role"),m.optString("text"))})}}.getOrDefault(emptyList())
suspend fun readImage(c:Context,u:Uri):Pair<String,String>=withContext(Dispatchers.IO){val b=c.contentResolver.openInputStream(u)?.use{it.readBytes()}?:error("Empty image");Base64.encodeToString(b,Base64.NO_WRAP) to (c.contentResolver.getType(u)?:"image/jpeg")}
suspend fun readText(c:Context,u:Uri):Pair<String,String>=withContext(Dispatchers.IO){(u.lastPathSegment?:"file") to (c.contentResolver.openInputStream(u)?.bufferedReader()?.use{it.readText()}?:error("Empty file")).takeRight(1)}
fun share(c:Context,t:String){c.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply{type="text/plain";putExtra(Intent.EXTRA_TEXT,t)},"Share CrazeAi response"))}

object Gemini{private val client=OkHttpClient();private val type="application/json; charset=utf-8".toMediaType();suspend fun chat(key:String,model:String,system:String,ms:List<Message>):String=withContext(Dispatchers.IO){require(key.isNotBlank()){"Add your Gemini API key in Settings."};val contents=JSONArray();ms.forEach{m->val parts=JSONArray().put(JSONObject().put("text",m.text));if(m.image!=null)parts.put(JSONObject().put("inlineData",JSONObject().put("mimeType",m.mime?:"image/jpeg").put("data",m.image)));contents.put(JSONObject().put("role",if(m.role=="model")"model"else"user").put("parts",parts))};val body=JSONObject().put("systemInstruction",JSONObject().put("parts",JSONArray().put(JSONObject().put("text",system)))).put("contents",contents);val req=Request.Builder().url("https://generativelanguage.googleapis.com/v1beta/models/${model.trim()}:generateContent?key=${key.trim()}").post(body.toString().toRequestBody(type)).build();client.newCall(req).execute().use{r->val raw=r.body?.string().orEmpty();if(!r.isSuccessful){val e=runCatching{JSONObject(raw).optJSONObject("error")?.optString("message")}.getOrNull();error("Gemini ${r.code}: ${e?:raw.take(250)}")};val c=JSONObject(raw).optJSONArray("candidates")?:error("Gemini returned no candidates.");if(c.length()==0)error("Gemini returned an empty response.");val parts=c.getJSONObject(0).optJSONObject("content")?.optJSONArray("parts")?:error("Gemini returned no text.");buildString{for(i in 0 until parts.length())append(parts.getJSONObject(i).optString("text"))}.ifBlank{error("Gemini returned an empty response.")}}}}
